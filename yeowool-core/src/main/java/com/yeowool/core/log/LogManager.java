package com.yeowool.core.log;

import com.yeowool.core.api.model.LogEntry;
import com.yeowool.core.api.service.LogService;
import com.yeowool.core.data.repository.LogRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public final class LogManager implements LogService {

    private final JavaPlugin plugin;
    private final LogRepository repository;
    private final ExecutorService executor;

    public LogManager(JavaPlugin plugin, LogRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    @Override
    public void log(String pluginName, String category, UUID actor, String message, Map<String, String> data) {
        LogEntry entry = new LogEntry(pluginName, category, actor, message, data);
        executor.execute(() -> {
            try {
                repository.insert(entry);
            } catch (Exception e) {
                plugin.getLogger().warning("로그 기록 실패 [" + pluginName + "/" + category + "]: " + e.getMessage());
            }
        });
    }
}
