package io.github.massimilianopili.ai.reactive.manager;

import io.github.massimilianopili.ai.reactive.callback.ReactiveToolCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.DefaultToolExecutionResult;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ToolCallingManager} that executes multiple tool calls in parallel.
 *
 * <p>When an LLM responds with multiple independent tool calls, the default
 * {@link DefaultToolCallingManager} executes them sequentially. This implementation
 * runs them concurrently, reducing total latency to the duration of the slowest tool
 * instead of the sum of all tools.</p>
 *
 * <p>For tools implementing {@link ReactiveToolCallback}, the reactive
 * {@code callReactive()} method is used directly. Regular {@link ToolCallback}
 * instances are wrapped in a thread pool executor.</p>
 *
 * <p>Enable via configuration:</p>
 * <pre>
 * spring.ai.reactive-tools.parallel.enabled=true
 * spring.ai.reactive-tools.parallel.max-concurrency=10
 * spring.ai.reactive-tools.parallel.timeout-ms=30000
 * </pre>
 */
public class ParallelToolCallingManager implements ToolCallingManager {

    private static final Logger log = LoggerFactory.getLogger(ParallelToolCallingManager.class);

    private final ToolCallingManager delegate;
    private final ToolCallbackResolver toolCallbackResolver;
    private final int maxConcurrency;
    private final long timeoutMs;

    public ParallelToolCallingManager(ToolCallingManager delegate,
                                      ToolCallbackResolver toolCallbackResolver,
                                      int maxConcurrency,
                                      long timeoutMs) {
        this.delegate = delegate;
        this.toolCallbackResolver = toolCallbackResolver;
        this.maxConcurrency = maxConcurrency;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();

        if (toolCalls == null || toolCalls.size() <= 1) {
            // Single or no tool call — delegate to sequential execution
            return delegate.executeToolCalls(prompt, chatResponse);
        }

        log.info("Executing {} tool calls in parallel using virtual threads",
                toolCalls.size());

        // Virtual threads: lightweight, ideal for I/O-bound tool calls (HTTP, DB, filesystem)
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            List<CompletableFuture<ToolResponseMessage.ToolResponse>> futures = new ArrayList<>();

            for (AssistantMessage.ToolCall toolCall : toolCalls) {
                CompletableFuture<ToolResponseMessage.ToolResponse> future =
                        CompletableFuture.supplyAsync(() -> executeToolCall(toolCall), executor);
                futures.add(future);
            }

            // Wait for all to complete with timeout
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);

            // Collect results in order
            List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
            boolean returnDirect = false;

            for (int i = 0; i < futures.size(); i++) {
                ToolResponseMessage.ToolResponse response = futures.get(i).get();
                responses.add(response);

                // Check returnDirect on the callback
                ToolCallback callback = resolveCallback(toolCalls.get(i).name());
                if (callback != null && callback.getToolMetadata() != null
                        && callback.getToolMetadata().returnDirect()) {
                    returnDirect = true;
                }
            }

            // Build conversation history
            List<Message> conversationHistory = new ArrayList<>(prompt.getInstructions());
            conversationHistory.add(assistantMessage);
            conversationHistory.add(new ToolResponseMessage(responses));

            return DefaultToolExecutionResult.builder()
                    .conversationHistory(conversationHistory)
                    .returnDirect(returnDirect)
                    .build();

        } catch (Exception e) {
            log.error("Parallel tool execution failed, falling back to sequential: {}",
                    e.getMessage());
            return delegate.executeToolCalls(prompt, chatResponse);
        } finally {
            executor.shutdown();
        }
    }

    private ToolResponseMessage.ToolResponse executeToolCall(AssistantMessage.ToolCall toolCall) {
        String toolName = toolCall.name();
        String toolInput = toolCall.arguments();
        String toolCallId = toolCall.id();

        try {
            ToolCallback callback = resolveCallback(toolName);
            if (callback == null) {
                log.error("No callback found for tool: {}", toolName);
                return new ToolResponseMessage.ToolResponse(toolCallId, toolName,
                        "{\"error\": \"Tool not found: " + toolName + "\"}");
            }

            String result;
            if (callback instanceof ReactiveToolCallback reactiveCallback) {
                // Use reactive path — non-blocking
                result = reactiveCallback.callReactive(toolInput)
                        .subscribeOn(Schedulers.boundedElastic())
                        .block(Duration.ofMillis(timeoutMs));
            } else {
                // Regular callback — blocking call
                result = callback.call(toolInput);
            }

            log.debug("Tool '{}' completed successfully", toolName);
            return new ToolResponseMessage.ToolResponse(toolCallId, toolName, result);

        } catch (Exception e) {
            log.error("Error executing tool '{}': {}", toolName, e.getMessage(), e);
            return new ToolResponseMessage.ToolResponse(toolCallId, toolName,
                    "{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    private ToolCallback resolveCallback(String toolName) {
        try {
            return toolCallbackResolver.resolve(toolName);
        } catch (Exception e) {
            log.warn("Failed to resolve callback for tool '{}': {}", toolName, e.getMessage());
            return null;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ToolCallingManager delegate;
        private ToolCallbackResolver toolCallbackResolver;
        private int maxConcurrency = 10;
        private long timeoutMs = 30000;

        public Builder delegate(ToolCallingManager delegate) {
            this.delegate = delegate;
            return this;
        }

        public Builder toolCallbackResolver(ToolCallbackResolver toolCallbackResolver) {
            this.toolCallbackResolver = toolCallbackResolver;
            return this;
        }

        public Builder maxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public ParallelToolCallingManager build() {
            if (delegate == null) {
                delegate = ToolCallingManager.builder().build();
            }
            return new ParallelToolCallingManager(delegate, toolCallbackResolver,
                    maxConcurrency, timeoutMs);
        }
    }
}
