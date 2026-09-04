package com.yeowool.community.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/**
 * One clickable button in a {@link MenuHubGui}/{@link MenuCategoryGui} - hover label/lore and an
 * action, plus an optional {@code iconId} ("namespace:key" ItemsAdder item). If {@code iconId} is
 * null or not a real registered item, falls back to {@code fallbackMaterial} when given, else the
 * transparent icon.
 */
public record MenuEntry(Component label, List<Component> lore, String iconId, Material fallbackMaterial, Consumer<Player> action) {

    public MenuEntry(Component label, List<Component> lore, Consumer<Player> action) {
        this(label, lore, null, null, action);
    }

    public MenuEntry(Component label, List<Component> lore, String iconId, Consumer<Player> action) {
        this(label, lore, iconId, null, action);
    }
}
