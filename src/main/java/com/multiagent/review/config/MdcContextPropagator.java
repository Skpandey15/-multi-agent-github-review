package com.multiagent.review.config;

import io.temporal.api.common.v1.Payload;
import io.temporal.common.context.ContextPropagator;
import io.temporal.payload.codec.PayloadCodecException;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

/**
 * Propagates MDC context (correlationId, workflowId, runId) from the
 * workflow starter into Temporal activity threads automatically.
 */
public class MdcContextPropagator implements ContextPropagator {

    private static final String KEY = "mdc-context";

    @Override
    public String getName() {
        return KEY;
    }

    @Override
    public Object getCurrentContext() {
        Map<String, String> ctx = MDC.getCopyOfContextMap();
        return ctx != null ? ctx : new HashMap<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setCurrentContext(Object context) {
        if (context instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k instanceof String key && v instanceof String value) {
                    MDC.put(key, value);
                }
            });
        }
    }

    @Override
    public Map<String, Payload> serializeContext(Object context) {
        // Serialisation handled by Temporal's default Jackson codec
        return Map.of();
    }

    @Override
    public Object deserializeContext(Map<String, Payload> header) {
        return MDC.getCopyOfContextMap();
    }
}
