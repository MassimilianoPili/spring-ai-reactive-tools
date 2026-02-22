package io.github.massimilianopili.ai.reactive.callback;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * Extension of Spring AI's {@link ToolCallback} that supports reactive execution.
 *
 * <p>The {@link #callReactive(String)} method returns a {@code Mono<String>} that
 * represents the async tool execution. The synchronous {@link #call(String)} method
 * bridges to the reactive version by subscribing on {@code Schedulers.boundedElastic()}
 * and blocking with the configured timeout.</p>
 */
public interface ReactiveToolCallback extends ToolCallback {

    /**
     * Execute the tool reactively.
     *
     * @param toolInput JSON string with tool parameters
     * @return Mono emitting the serialized result
     */
    Mono<String> callReactive(String toolInput);

    /**
     * Maximum time to wait for the reactive result.
     */
    long getTimeoutMs();

    /**
     * Synchronous bridge: subscribes on boundedElastic and blocks with timeout.
     * This is what Spring AI's MCP server calls.
     */
    @Override
    default String call(String toolInput) {
        return callReactive(toolInput)
                .subscribeOn(Schedulers.boundedElastic())
                .block(Duration.ofMillis(getTimeoutMs()));
    }

    /**
     * Synchronous bridge with ToolContext support.
     * Spring AI MCP server calls this overload passing a ToolContext.
     * We delegate to callReactive ignoring the context.
     */
    @Override
    default String call(String toolInput, ToolContext toolContext) {
        return callReactive(toolInput)
                .subscribeOn(Schedulers.boundedElastic())
                .block(Duration.ofMillis(getTimeoutMs()));
    }
}
