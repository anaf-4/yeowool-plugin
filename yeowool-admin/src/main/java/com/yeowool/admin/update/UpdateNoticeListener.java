package com.yeowool.admin.update;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Section 11.5 (YeowoolUpdate): shows the configured update notes once per
 * version bump, tracked via a setting on the core player row (same pattern
 * as YeowoolLand's starter-kit flag) rather than spamming it on every join.
 */
public final class UpdateNoticeListener implements Listener {

    private static final String SETTING_KEY = "update.last_seen_version";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;

    public UpdateNoticeListener(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        int currentVersion = plugin.getConfig().getInt("update.version", 0);
        if (currentVersion <= 0) {
            return;
        }
        var player = event.getPlayer();
        var data = core.playerData().getOnline(player.getUniqueId());

        int lastSeen = Integer.parseInt(data.getSetting(SETTING_KEY, "0"));
        if (lastSeen >= currentVersion) {
            return;
        }
        data.setSetting(SETTING_KEY, String.valueOf(currentVersion));

        List<String> notes = plugin.getConfig().getStringList("update.notes");
        Component message = messages.resolveRaw("update.header");
        for (String note : notes) {
            message = message.append(messages.resolveRaw("update.note-line", Placeholder.unparsed("note", note)))
                    .append(Component.newline());
        }
        message = message.append(messages.resolveRaw("update.footer"));

        player.sendMessage(message);
    }
}
