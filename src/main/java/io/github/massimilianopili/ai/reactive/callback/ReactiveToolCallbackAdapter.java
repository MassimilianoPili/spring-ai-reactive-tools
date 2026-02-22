package io.github.massimilianopili.ai.reactive.callback;

import io.github.massimilianopili.ai.reactive.annotation.ReactiveTool;
import io.github.massimilianopili.ai.reactive.util.ReactiveToolSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.annotation.ToolParam;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Adapts a method annotated with {@link ReactiveTool} into a {@link ReactiveToolCallback}.
 *
 * <p>Handles method invocation via reflection, reactive type detection (Mono/Flux),
 * parameter parsing from JSON input, and result serialization.</p>
 */
public class ReactiveToolCallbackAdapter implements ReactiveToolCallback {

    private static final Logger log = LoggerFactory.getLogger(ReactiveToolCallbackAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Object targetBean;
    private final Method method;
    private final String toolName;
    private final String toolDescription;
    private final long timeoutMs;
    private final ToolDefinition toolDefinition;
    private final ReturnType returnType;

    private enum ReturnType { MONO, FLUX, SYNC }

    public ReactiveToolCallbackAdapter(Object targetBean, Method method, ReactiveTool annotation) {
        this.targetBean = targetBean;
        this.method = method;
        this.method.setAccessible(true);
        this.toolName = annotation.name();
        this.toolDescription = annotation.description();
        this.timeoutMs = annotation.timeoutMs();
        this.returnType = detectReturnType(method);
        this.toolDefinition = DefaultToolDefinition.builder()
                .name(toolName)
                .description(toolDescription)
                .inputSchema(buildInputSchema(method))
                .build();

        log.debug("Registered reactive tool '{}' -> {}.{}() [returnType={}]",
                toolName, targetBean.getClass().getSimpleName(), method.getName(), returnType);
    }

    @Override
    public Mono<String> callReactive(String toolInput) {
        return Mono.fromCallable(() -> {
            Object[] args = parseArguments(toolInput);
            Object result = method.invoke(targetBean, args);
            return result;
        })
        .flatMap(result -> {
            if (result instanceof Mono<?> mono) {
                return mono.map(ReactiveToolSerializer::toJson);
            } else if (result instanceof Flux<?> flux) {
                return flux.collectList().map(ReactiveToolSerializer::toJson);
            } else {
                return Mono.just(ReactiveToolSerializer.toJson(result));
            }
        })
        .onErrorResume(e -> {
            log.error("Error executing reactive tool '{}': {}", toolName, e.getMessage(), e);
            return Mono.just("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
        });
    }

    @Override
    public long getTimeoutMs() {
        return timeoutMs;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    // --- Private helpers ---

    private ReturnType detectReturnType(Method m) {
        Class<?> rt = m.getReturnType();
        if (Mono.class.isAssignableFrom(rt)) return ReturnType.MONO;
        if (Flux.class.isAssignableFrom(rt)) return ReturnType.FLUX;
        return ReturnType.SYNC;
    }

    private Object[] parseArguments(String toolInput) {
        Parameter[] params = method.getParameters();
        if (params.length == 0) return new Object[0];

        try {
            JsonNode root = MAPPER.readTree(toolInput);
            Object[] args = new Object[params.length];

            for (int i = 0; i < params.length; i++) {
                Parameter param = params[i];
                String paramName = resolveParamName(param);
                JsonNode valueNode = root.get(paramName);

                if (valueNode == null || valueNode.isNull()) {
                    args[i] = getDefaultValue(param.getType());
                } else {
                    args[i] = MAPPER.treeToValue(valueNode, param.getType());
                }
            }
            return args;
        } catch (Exception e) {
            log.warn("Failed to parse arguments for tool '{}': {}", toolName, e.getMessage());
            // Fallback: if single String parameter, pass raw input
            if (params.length == 1 && params[0].getType() == String.class) {
                return new Object[]{toolInput};
            }
            return new Object[params.length];
        }
    }

    private String resolveParamName(Parameter param) {
        // Check for @ToolParam annotation (from Spring AI)
        ToolParam toolParam = param.getAnnotation(ToolParam.class);
        if (toolParam != null) {
            // @ToolParam doesn't have a name() method in Spring AI 1.0.0
            // Fall back to parameter name
        }
        // Use Java parameter name (requires -parameters compiler flag)
        return param.getName();
    }

    private Object getDefaultValue(Class<?> type) {
        if (type == int.class || type == Integer.class) return 0;
        if (type == long.class || type == Long.class) return 0L;
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == double.class || type == Double.class) return 0.0;
        return null;
    }

    private String buildInputSchema(Method m) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter param : m.getParameters()) {
            String name = resolveParamName(param);
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", toJsonType(param.getType()));

            ToolParam toolParam = param.getAnnotation(ToolParam.class);
            if (toolParam != null) {
                if (!toolParam.description().isEmpty()) {
                    prop.put("description", toolParam.description());
                }
                if (toolParam.required()) {
                    required.add(name);
                }
            } else {
                required.add(name);
            }

            properties.put(name, prop);
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        try {
            return MAPPER.writeValueAsString(schema);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String toJsonType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class ||
            type == long.class || type == Long.class) return "integer";
        if (type == double.class || type == Double.class ||
            type == float.class || type == Float.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (type.isArray() || List.class.isAssignableFrom(type)) return "array";
        return "object";
    }

    private String escapeJson(String s) {
        if (s == null) return "unknown error";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
