package com.yeowool.core.help;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /길라잡이} — a beginner-friendly GUI overview of the server's major
 * systems (config.yml's {@code guide.categories}), meant to sit next to
 * {@link HelpCommand}'s dense command reference rather than replace it: this
 * is "what can I even do here", that is "what's the exact syntax". Clicking
 * a category doesn't open a second GUI page — it prints a short description
 * plus that category's command list (reused directly from {@code
 * help.categories}, so the two never drift apart) to chat, and leaves this
 * menu open so a new player can click through several categories in a row.
 * Opened on demand, and once automatically on a player's true first join
 * (see {@code GuideFirstJoinListener}).
 */
public final class GuideGui extends YeowoolGui {

    private static final int[] ICON_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30};

    public GuideGui(JavaPlugin plugin, GuideMissionManager missionManager) {
        super(45, Component.text("여울 길라잡이", NamedTextColor.GREEN));

        int i = 0;
        ConfigurationSection categories = plugin.getConfig().getConfigurationSection("guide.categories");
        ConfigurationSection helpCategories = plugin.getConfig().getConfigurationSection("help.categories");
        if (categories != null) {
            for (String key : categories.getKeys(false)) {
                if (i >= ICON_SLOTS.length - 1) {
                    break;
                }
                ConfigurationSection category = categories.getConfigurationSection(key);
                if (category == null) {
                    continue;
                }
                Material icon = Material.matchMaterial(category.getString("icon", "BOOK"));
                if (icon == null) {
                    icon = Material.BOOK;
                }
                String description = category.getString("description", "");
                List<String> commandLines = helpCategories != null ? helpCategories.getStringList(key) : List.of();

                setButton(ICON_SLOTS[i], GuiButton.of(buildIcon(icon, key, description), event -> {
                    Player viewer = (Player) event.getWhoClicked();
                    viewer.sendMessage(Component.text("=== " + key + " ===", NamedTextColor.GOLD));
                    viewer.sendMessage(Component.text(description, NamedTextColor.GRAY));
                    for (String line : commandLines) {
                        viewer.sendMessage(Component.text(line, NamedTextColor.YELLOW));
                    }
                }));
                i++;
            }
        }

        if (!missionManager.all().isEmpty()) {
            setButton(ICON_SLOTS[ICON_SLOTS.length - 1], GuiButton.of(buildMissionIcon(), event -> {
                Player viewer = (Player) event.getWhoClicked();
                viewer.sendMessage(Component.text("=== 시작 미션 ===", NamedTextColor.GOLD));
                for (GuideMission mission : missionManager.all()) {
                    viewer.sendMessage(Component.text(mission.number() + ". " + mission.title(), NamedTextColor.GREEN)
                            .appendNewline()
                            .append(Component.text("   " + mission.action(), NamedTextColor.YELLOW)));
                }
            }));
        }

        setButton(40, GuiButton.of(closeIcon(), event -> event.getWhoClicked().closeInventory()));
    }

    private ItemStack buildIcon(Material material, String name, String description) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(description, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("클릭하면 관련 명령어를 채팅으로 안내합니다.", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildMissionIcon() {
        ItemStack stack = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("시작 미션", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("운영진이 준비한 시작 미션을 확인합니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("클릭하면 채팅으로 안내합니다.", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack closeIcon() {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("닫기", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
