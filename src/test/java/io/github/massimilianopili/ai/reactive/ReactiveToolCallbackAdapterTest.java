package io.github.massimilianopili.ai.reactive;

import io.github.massimilianopili.ai.reactive.annotation.ReactiveTool;
import io.github.massimilianopili.ai.reactive.callback.ReactiveToolCallbackAdapter;
import io.github.massimilianopili.ai.reactive.provider.ReactiveMethodToolCallbackProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the core reactive tool adapter and provider.
 */
class ReactiveToolCallbackAdapterTest {

    // --- Test service with different return types ---

    static class TestService {

        @ReactiveTool(name = "mono_string", description = "Returns a Mono<String>")
        public Mono<String> monoString() {
            return Mono.just("hello reactive");
        }

        @ReactiveTool(name = "mono_map", description = "Returns a Mono<Map>")
        public Mono<Map<String, Object>> monoMap() {
            return Mono.just(Map.of("status", "ok", "count", 42));
        }

        @ReactiveTool(name = "flux_list", description = "Returns a Flux<String> collected to list")
        public Flux<String> fluxStrings() {
            return Flux.just("a", "b", "c");
        }

        @ReactiveTool(name = "sync_tool", description = "Returns a synchronous String")
        public String syncTool() {
            return "sync result";
        }

        @ReactiveTool(name = "with_params", description = "Tool with parameters")
        public Mono<String> withParams(
                @ToolParam(description = "The query") String query,
                @ToolParam(description = "Max results", required = false) Integer limit) {
            int effectiveLimit = (limit != null) ? limit : 10;
            return Mono.just("query=" + query + ",limit=" + effectiveLimit);
        }

        @ReactiveTool(name = "slow_tool", description = "Simulates a slow operation", timeoutMs = 500)
        public Mono<String> slowTool() {
            return Mono.delay(Duration.ofSeconds(2)).map(l -> "done");
        }

        @ReactiveTool(name = "error_tool", description = "Throws an error")
        public Mono<String> errorTool() {
            return Mono.error(new RuntimeException("Something went wrong"));
        }
    }

    @Test
    void monoStringTool_returnsString() {
        TestService svc = new TestService();
        var provider = ReactiveMethodToolCallbackProvider.builder()
                .toolObjects(svc)
                .build();

        ToolCallback callback = findCallback(provider, "mono_string");
        assertNotNull(callback);
        String result = callback.call("{}");
        assertEquals("hello reactive", result);
    }

    @Test
    void monoMapTool_returnsJson() {
        TestService svc = new TestService();
        var provider = ReactiveMethodToolCallbackProvider.builder()
                .toolObjects(svc)
                .build();

        ToolCallback callback = findCallback(provider, "mono_map");
        String result = callback.call("{}");
        assertTrue(result.contains("\"status\""));
        assertTrue(result.contains("\"ok\""));
        assertTrue(result.contains("42"));
    }

    @Test
    void fluxTool_collectsToList() {
        TestService svc = new TestService();
        var provider = ReactiveMethodToolCallbackProvider.builder()
                .toolObjects(svc)
                .build();

        ToolCallback callback = findCallback(provider, "flux_list");
        String result = callback.call("{}");
        assertTrue(result.contains("\"a\""));
        assertTrue(result.contains("\"b\""));
        assertTrue(result.contains("\"c\""));
    }

    @Test
    void syncTool_worksNormally() {
        TestService svc = new TestService();
        var provider = ReactiveMethodToolCallbackProvider.builder()
                .toolObjects(svc)
                .build();

        ToolCallback callback = findCallback(provider, "sync_tool");
        String result = callback.call("{}");
        assertEquals("sync result", result);
    }

    @Test
    void toolWithParams_parsesJson() {
        TestService svc = new TestService();
        var provider = ReactiveMethodToolCallbackProvider.builder()
                .toolObjects(svc)
                .build();

        ToolCallback callback = findCallback(provider, "with_params");
        String result = callback.call("{\"query\": \"test\", \"limit\": 5}");
        assertEquals("query=test,limit=5", result);
    }

    @Test
    void errorTool_returnsErrorJson() {
        TestService svc = new TestService();
        var provider = ReactiveMethodToolCallbackProvider.builder()
                .toolObjects(svc)
                .build();

        ToolCallback callback = findCallback(provider, "error_tool");
        String result = callback.call("{}");
        assertTrue(result.contains("error"));
        assertTrue(result.contains("Something went wrong"));
    }

    @Test
    void slowTool_timesOut() {
        TestService svc = new TestService();
        var provider = ReactiveMethodToolCallbackProvider.builder()
                .toolObjects(svc)
                .build();

        ToolCallback callback = findCallback(provider, "slow_tool");
        // Should timeout after 500ms (tool takes 2s)
        assertThrows(Exception.class, () -> callback.call("{}"));
    }

    @Test
    void provider_registersAllTools() {
        TestService svc = new TestService();
        var provider = ReactiveMethodToolCallbackProvider.builder()
                .toolObjects(svc)
                .build();

        ToolCallback[] callbacks = provider.getToolCallbacks();
        assertEquals(7, callbacks.length);
    }

    @Test
    void provider_defaultTimeout_overrides() {
        TestService svc = new TestService();
        var provider = ReactiveMethodToolCallbackProvider.builder()
                .toolObjects(svc)
                .defaultTimeout(Duration.ofSeconds(60))
                .build();

        // Tools without explicit timeout should get 60s
        ToolCallback[] callbacks = provider.getToolCallbacks();
        assertNotNull(callbacks);
        assertTrue(callbacks.length > 0);
    }

    // --- Helpers ---

    private ToolCallback findCallback(ReactiveMethodToolCallbackProvider provider, String name) {
        for (ToolCallback cb : provider.getToolCallbacks()) {
            if (cb.getToolDefinition().name().equals(name)) {
                return cb;
            }
        }
        return null;
    }
}
