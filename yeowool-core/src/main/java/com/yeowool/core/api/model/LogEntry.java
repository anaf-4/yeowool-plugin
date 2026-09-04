package com.yeowool.core.api.model;

import java.util.Map;
import java.util.UUID;

/**
 * A single structured log line written by any Yeowool plugin through
 * {@link com.yeowool.core.api.service.LogService}. Persisted to the shared
 * {@code yw_logs} table so every economy/land/exploit-relevant change has one
 * common, queryable trail regardless of which plugin produced it.
 */
public record LogEntry(
        String pluginName,
        String category,
        UUID actor,
        String message,
        Map<String, String> data,
        long timestamp
) {
    public LogEntry(String pluginName, String category, UUID actor, String message, Map<String, String> data) {
        this(pluginName, category, actor, message, data, System.currentTimeMillis());
    }
}
