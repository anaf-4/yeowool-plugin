package com.yeowool.core.api.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/**
 * Common message/i18n handling backed by {@code messages.yml}, using
 * MiniMessage formatting. Other Yeowool plugins ship their own message keys
 * under their own namespace but should reuse this service instead of each
 * building their own color/prefix handling.
 */
public interface MessageService {

    /**
     * Resolves a message key (dot-path into messages.yml) to a Component,
     * applying the shared prefix and any given placeholders.
     */
    Component resolve(String key, TagResolver... placeholders);

    /**
     * Resolves without prepending the shared prefix. Useful for GUI titles,
     * lore lines, etc.
     */
    Component resolveRaw(String key, TagResolver... placeholders);

    void send(CommandSender target, String key, TagResolver... placeholders);

    void broadcast(String key, TagResolver... placeholders);

    /**
     * Reloads messages.yml from disk.
     */
    void reload();
}
