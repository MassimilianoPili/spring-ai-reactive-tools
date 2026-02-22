package io.github.massimilianopili.ai.reactive.provider;

import io.github.massimilianopili.ai.reactive.annotation.ReactiveTool;
import io.github.massimilianopili.ai.reactive.callback.ReactiveToolCallbackAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans bean objects for methods annotated with {@link ReactiveTool} and creates
 * {@link ReactiveToolCallbackAdapter} instances for each.
 *
 * <p>Implements Spring AI's {@link ToolCallbackProvider} interface, making it a
 * drop-in replacement for {@code MethodToolCallbackProvider} when using reactive tools.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * @Bean
 * public ToolCallbackProvider reactiveTools(MyService svc) {
 *     return ReactiveMethodToolCallbackProvider.builder()
 *         .toolObjects(svc)
 *         .defaultTimeout(Duration.ofSeconds(30))
 *         .build();
 * }
 * }</pre>
 */
public class ReactiveMethodToolCallbackProvider implements ToolCallbackProvider {

    private static final Logger log = LoggerFactory.getLogger(ReactiveMethodToolCallbackProvider.class);

    private final ToolCallback[] callbacks;

    private ReactiveMethodToolCallbackProvider(ToolCallback[] callbacks) {
        this.callbacks = callbacks;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return callbacks;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<Object> toolObjects = new ArrayList<>();
        private long defaultTimeoutMs = 30000;

        /**
         * Add bean objects to scan for @ReactiveTool methods.
         */
        public Builder toolObjects(Object... objects) {
            for (Object obj : objects) {
                if (obj != null) {
                    toolObjects.add(obj);
                }
            }
            return this;
        }

        /**
         * Set the default timeout for tools that don't specify one.
         */
        public Builder defaultTimeout(Duration timeout) {
            this.defaultTimeoutMs = timeout.toMillis();
            return this;
        }

        /**
         * Set the default timeout in milliseconds.
         */
        public Builder defaultTimeoutMs(long timeoutMs) {
            this.defaultTimeoutMs = timeoutMs;
            return this;
        }

        public ReactiveMethodToolCallbackProvider build() {
            List<ToolCallback> callbacks = new ArrayList<>();

            for (Object bean : toolObjects) {
                scanBean(bean, callbacks);
            }

            log.info("ReactiveMethodToolCallbackProvider: registered {} reactive tool(s)", callbacks.size());
            return new ReactiveMethodToolCallbackProvider(callbacks.toArray(new ToolCallback[0]));
        }

        private void scanBean(Object bean, List<ToolCallback> callbacks) {
            Class<?> clazz = bean.getClass();

            for (Method method : clazz.getDeclaredMethods()) {
                ReactiveTool annotation = method.getAnnotation(ReactiveTool.class);
                if (annotation == null) continue;

                // Override timeout with default if not explicitly set in annotation
                ReactiveTool effectiveAnnotation = annotation;
                if (annotation.timeoutMs() == 30000 && defaultTimeoutMs != 30000) {
                    // Create a proxy annotation with the default timeout
                    effectiveAnnotation = new ReactiveToolProxy(
                            annotation.name(),
                            annotation.description(),
                            defaultTimeoutMs
                    );
                }

                callbacks.add(new ReactiveToolCallbackAdapter(bean, method, effectiveAnnotation));
            }
        }
    }

    /**
     * Internal proxy for ReactiveTool annotation to override timeout.
     */
    private record ReactiveToolProxy(String name, String description, long timeoutMs) implements ReactiveTool {
        @Override
        public Class<ReactiveTool> annotationType() {
            return ReactiveTool.class;
        }
    }
}
