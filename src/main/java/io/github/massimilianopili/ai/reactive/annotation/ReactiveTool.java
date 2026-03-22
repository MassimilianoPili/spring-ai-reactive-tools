package io.github.massimilianopili.ai.reactive.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a reactive MCP tool. The method can return:
 * <ul>
 *   <li>{@code Mono<T>} - single async result</li>
 *   <li>{@code Flux<T>} - collected into a List before serialization</li>
 *   <li>Any synchronous type - wrapped in {@code Mono.just()}</li>
 * </ul>
 *
 * <p>Unlike Spring AI's {@code @Tool}, this annotation supports reactive return types
 * natively. The library handles scheduling on {@code Schedulers.boundedElastic()}
 * and blocking at the boundary with configurable timeouts.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @ReactiveTool(name = "fetch_data", description = "Fetches data from API")
 * public Mono<Map<String, Object>> fetchData(
 *         @ToolParam(description = "The URL to fetch") String url) {
 *     return webClient.get().uri(url).retrieve().bodyToMono(Map.class);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReactiveTool {

    /** Unique tool name exposed to MCP clients. */
    String name();

    /** Human-readable description of what this tool does. */
    String description() default "";

    /** Maximum time in milliseconds to wait for the reactive result. Default: 30 seconds. */
    long timeoutMs() default 30000;

    /** Hint: this tool only reads data and does not modify state. */
    boolean readOnly() default false;

    /** Hint: this tool may perform destructive operations (delete, remove, prune). */
    boolean destructive() default false;

    /** Hint: calling this tool multiple times with the same arguments produces the same result. */
    boolean idempotent() default false;
}
