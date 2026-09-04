package com.yeowool.community.quest;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /일일퀘스트}/{@code /주간퀘스트} → 뱃지 화면 (DailyQuest-1.8.3 "BADGES" layout) — the
 * {@code quest.badges} ladder laid out along row 1 (slots 10-17, up to 8),
 * locked ones shown with {@code locked_badge} + progress toward the
 * threshold. Background {@code daily_quest:quest_badges_bg}.
 */
public final class QuestBadgesGui extends YeowoolGui {

    private static final int[] BADGE_SLOTS = {10, 11, 12, 13, 14, 15, 16, 17};
    private static final int SLOT_BACK = 0;

    public QuestBadgesGui(QuestContext ctx, Player viewer) {
        super(27, QuestBackgroundImages.title(ctx.questManager().backgroundOffsetPx(), "quest_badges_bg",
                Component.text("퀘스트 뱃지", NamedTextColor.GREEN)));

        var data = ctx.core().playerData().getOnline(viewer.getUniqueId());
        long total = ctx.questManager().totalCompleted(data);
        List<QuestBadge> badges = ctx.badgeConfig().badges();

        for (int i = 0; i < BADGE_SLOTS.length && i < badges.size(); i++) {
            setButton(BADGE_SLOTS[i], GuiButton.display(buildBadgeIcon(badges.get(i), total)));
        }

        setButton(SLOT_BACK, GuiButton.of(QuestBoardGui.navIcon("daily_quest:arrow_left", Material.ARROW, "닫기"),
                event -> event.getWhoClicked().closeInventory()));
    }

    private ItemStack buildBadgeIcon(QuestBadge badge, long total) {
        boolean unlocked = total >= badge.threshold();
        ItemStack stack = QuestBackgroundImages.icon(unlocked ? badge.iconId() : "daily_quest:locked_badge");
        if (stack == null) {
            stack = new ItemStack(unlocked ? Material.GOLD_NUGGET : Material.GRAY_DYE);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(badge.name(), unlocked ? NamedTextColor.GOLD : NamedTextColor.RED, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(unlocked ? "획득함" : "잠김", unlocked ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("목표: 퀘스트 " + badge.threshold() + "회 완료", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        if (!unlocked) {
            lore.add(Component.text("진행도: " + total + "/" + badge.threshold(), NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
