package com.yeowool.core.help;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Help/guide text is developer-curated content, not operator config: it lives
 * in the jar's {@code help.yml} and is never copied to the server folder, so a
 * jar deploy always ships the current text. (It used to sit in config.yml,
 * where ConfigMerger only adds missing keys, so edits to existing categories
 * never reached live servers.)
 */
final class HelpContent {

    private static YamlConfiguration cached;

    private HelpContent() {
    }

    static synchronized YamlConfiguration get(JavaPlugin plugin) {
        if (cached == null) {
            cached = new YamlConfiguration();
            try (InputStream in = plugin.getResource("help.yml")) {
                if (in != null) {
                    cached = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            } catch (java.io.IOException e) {
                plugin.getLogger().warning("help.yml을 읽지 못했습니다: " + e.getMessage());
            }
        }
        return cached;
    }
}
