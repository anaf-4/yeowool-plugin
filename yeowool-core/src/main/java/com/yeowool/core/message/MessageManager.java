package com.yeowool.core.message;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.ConfigMerger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads {@code messages.yml} into a flattened dot-path map (e.g.
 * {@code general.no-permission}) and resolves it through MiniMessage. Kept
 * separate from {@code config.yml} so operators can hand message editing to
 * someone else without touching database settings.
 */
public final class MessageManager implements MessageService {

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, String> messages = new LinkedHashMap<>();
    private String prefix = "";

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    @Override
    public void reload() {
        ConfigMerger.mergeDefaults(plugin, "messages.yml");
        File file = new File(plugin.getDataFolder(), "messages.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        messages.clear();
        prefix = config.getString("prefix", "");
        // getValues(true) already keys entries by their full dotted path (e.g.
        // "general.no-permission"); it also includes each intermediate section
        // itself under its own key, which we skip since only leaf strings are
        // usable messages.
        for (Map.Entry<String, Object> entry : config.getValues(true).entrySet()) {
            if (entry.getValue() instanceof String value) {
                messages.put(entry.getKey(), value);
            }
        }
    }

    @Override
    public Component resolve(String key, TagResolver... placeholders) {
        return miniMessage.deserialize(prefix + raw(key), placeholders);
    }

    @Override
    public Component resolveRaw(String key, TagResolver... placeholders) {
        return miniMessage.deserialize(raw(key), placeholders);
    }

    private String raw(String key) {
        return messages.getOrDefault(key, "<red>[missing message: " + key + "]</red>");
    }

    @Override
    public void send(CommandSender target, String key, TagResolver... placeholders) {
        target.sendMessage(resolve(key, placeholders));
    }

    @Override
    public void broadcast(String key, TagResolver... placeholders) {
        Component component = resolve(key, placeholders);
        plugin.getServer().broadcast(component);
    }
}
