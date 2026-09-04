package com.yeowool.core.api.service;

import java.util.Map;
import java.util.UUID;

/**
 * Shared structured logging, persisted to the {@code yw_logs} table. Every
 * plugin in the suite should route economy changes, land ownership changes,
 * and anti-exploit findings through here so operators have one place to
 * search history instead of grepping per-plugin log files.
 */
public interface LogService {

    void log(String pluginName, String category, UUID actor, String message, Map<String, String> data);

    default void log(String pluginName, String category, UUID actor, String message) {
        log(pluginName, category, actor, message, Map.of());
    }
}
