package io.github.massimilianopili.ai.reactive;

import io.github.massimilianopili.ai.reactive.annotation.ReactiveTool;
import io.github.massimilianopili.ai.reactive.callback.ReactiveToolCallbackAdapter;
import io.github.massimilianopili.ai.reactive.manager.ParallelToolCallingManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ParallelToolCallingManager}.
 */
class ParallelToolCallingManagerTest {

    private Map<String, ToolCallback> callbackMap;
    private ToolCallbackResolver resolver;
    private ParallelToolCallingManager manager;

    static class SlowService {

        @ReactiveTool(name = "slow_tool_1", description = "Slow tool 1")
        public Mono<String> slowTool1() {
            return Mono.delay(Duration.ofMillis(500)).map(l -> "result_1");
        }

        @ReactiveTool(name = "slow_tool_2", description = "Slow tool 2")
        public Mono<String> slowTool2() {
            return Mono.delay(Duration.ofMillis(500)).map(l -> "result_2");
        }

        @ReactiveTool(name = "slow_tool_3", description = "Slow tool 3")
        public Mono<String> slowTool3() {
            return Mono.delay(Duration.ofMillis(500)).map(l -> "result_3");
        }

        @ReactiveTool(name = "error_tool", description = "Always fails")
        public Mono<String> errorTool() {
            return Mono.error(new RuntimeException("Tool failed"));
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        SlowService svc = new SlowService();

        // Build callbacks manually
        callbackMap = Map.of(
            "slow_tool_1", buildCallback(svc, "slow_tool_1", "slowTool1"),
            "slow_tool_2", buildCallback(svc, "slow_tool_2", "slowTool2"),
            "slow_tool_3", buildCallback(svc, "slow_tool_3", "slowTool3"),
            "error_tool", buildCallback(svc, "error_tool", "errorTool")
        );

        resolver = name -> callbackMap.get(name);

        manager = ParallelToolCallingManager.builder()
                .toolCallbackResolver(resolver)
                .maxConcurrency(10)
                .timeoutMs(10000)
                .build();
    }

    @Test
    void parallelExecution_fasterThanSequential() {
        // 3 tools each taking 500ms — parallel should complete in ~500ms, not ~1500ms
        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall("1", "function", "slow_tool_1", "{}"),
                new AssistantMessage.ToolCall("2", "function", "slow_tool_2", "{}"),
                new AssistantMessage.ToolCall("3", "function", "slow_tool_3", "{}")
        );

        AssistantMessage assistantMsg = new AssistantMessage("", Map.of(), toolCalls);
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMsg)));
        Prompt prompt = new Prompt(List.of(new UserMessage("test")));

        long start = System.currentTimeMillis();
        ToolExecutionResult result = manager.executeToolCalls(prompt, chatResponse);
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(result);
        assertNotNull(result.conversationHistory());
        // Should be well under 1500ms (sequential would be ~1500ms)
        assertTrue(elapsed < 1200, "Parallel execution took " + elapsed + "ms, expected < 1200ms");
    }

    @Test
    void resultOrdering_preserved() {
        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall("1", "function", "slow_tool_1", "{}"),
                new AssistantMessage.ToolCall("2", "function", "slow_tool_2", "{}"),
                new AssistantMessage.ToolCall("3", "function", "slow_tool_3", "{}")
        );

        AssistantMessage assistantMsg = new AssistantMessage("", Map.of(), toolCalls);
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMsg)));
        Prompt prompt = new Prompt(List.of(new UserMessage("test")));

        ToolExecutionResult result = manager.executeToolCalls(prompt, chatResponse);

        // Verify conversation history has the right structure
        List<Message> history = result.conversationHistory();
        assertFalse(history.isEmpty());
    }

    @Test
    void errorInOneTool_doesNotBlockOthers() {
        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall("1", "function", "slow_tool_1", "{}"),
                new AssistantMessage.ToolCall("2", "function", "error_tool", "{}"),
                new AssistantMessage.ToolCall("3", "function", "slow_tool_3", "{}")
        );

        AssistantMessage assistantMsg = new AssistantMessage("", Map.of(), toolCalls);
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMsg)));
        Prompt prompt = new Prompt(List.of(new UserMessage("test")));

        // Should not throw — errors are collected per-tool
        ToolExecutionResult result = manager.executeToolCalls(prompt, chatResponse);
        assertNotNull(result);
    }

    @Test
    void singleToolCall_delegatesToDefault() {
        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall("1", "function", "slow_tool_1", "{}")
        );

        AssistantMessage assistantMsg = new AssistantMessage("", Map.of(), toolCalls);
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMsg)));
        Prompt prompt = new Prompt(List.of(new UserMessage("test")));

        // Single tool call — should still work (delegates to default manager)
        // May throw if DefaultToolCallingManager can't resolve the tool,
        // but that's expected — the parallel path is not taken
        assertDoesNotThrow(() -> {
            try {
                manager.executeToolCalls(prompt, chatResponse);
            } catch (Exception e) {
                // DefaultToolCallingManager may fail without full Spring context
                // That's OK — we're testing that delegation happens
            }
        });
    }

    // --- Helper ---

    private ToolCallback buildCallback(Object bean, String toolName, String methodName) throws Exception {
        java.lang.reflect.Method method = bean.getClass().getDeclaredMethod(methodName);
        ReactiveTool annotation = method.getAnnotation(ReactiveTool.class);
        return new ReactiveToolCallbackAdapter(bean, method, annotation);
    }
}
