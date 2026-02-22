package io.github.massimilianopili.ai.reactive.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Utility for serializing tool results to JSON strings.
 */
public final class ReactiveToolSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private ReactiveToolSerializer() {}

    /**
     * Serialize any object to a JSON string.
     * If the object is already a String, returns it as-is.
     */
    public static String toJson(Object result) {
        if (result == null) {
            return "null";
        }
        if (result instanceof String s) {
            return s;
        }
        try {
            return MAPPER.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"Serialization failed: " + e.getMessage() + "\"}";
        }
    }
}
