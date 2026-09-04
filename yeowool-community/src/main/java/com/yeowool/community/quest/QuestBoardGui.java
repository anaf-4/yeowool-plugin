package com.yeowool.community.quest;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /일일퀘스트}/{@code /주간퀘스트}'s "Quest Board" screen (DailyQuest-1.8.3 layout, corrected
 * live against the actual in-game rendering) — profile head block at
 * 0/1/9/10, leaderboard button at 8, badges button at 17, quests grouped
 * into E/M/H rows (3/4/5/6, 12/13/14/15, 21/22/23/24). No barrier/close
 * button — closed the vanilla way. Background {@code daily_quest:daily_quest_bg}.
 */
public final class QuestBoardGui extends YeowoolGui {

    private static final Map<QuestDifficulty, int[]> ROW_SLOTS = Map.of(
            QuestDifficulty.EASY, new int[]{3, 4, 5, 6},
            QuestDifficulty.MEDIUM, new int[]{12, 13, 14, 15},
            QuestDifficulty.HARD, new int[]{21, 22, 23, 24});

    private static final int[] SLOT_PROFILE = {0, 1, 9, 10};
    private static final int SLOT_LEADERBOARD = 8;
    private static final int SLOT_BADGES = 17;

    private final QuestContext ctx;
    private final QuestManager.Period period;
    private final Player viewer;

    public QuestBoardGui(QuestContext ctx, QuestManager.Period period, Player viewer) {
        super(27, QuestBackgroundImages.title(ctx.questManager().backgroundOffsetPx(), "daily_quest_bg",
                Component.text(period == QuestManager.Period.DAILY ? "일일 퀘스트" : "주간 퀘스트", NamedTextColor.DARK_AQUA)));
        this.ctx = ctx;
        this.period = period;
        this.viewer = viewer;

        var data = ctx.core().playerData().getOnline(viewer.getUniqueId());
        List<QuestManager.QuestProgress> quests = ctx.questManager().activeQuests(data, period);

        for (var entry : ROW_SLOTS.entrySet()) {
            List<QuestManager.QuestProgress> tierQuests = quests.stream().filter(q -> q.quest().difficulty() == entry.getKey()).toList();
            int[] slots = entry.getValue();
            for (int i = 0; i < slots.length; i++) {
                if (i >= tierQuests.size()) {
                    continue;
                }
                QuestManager.QuestProgress quest = tierQuests.get(i);
                setButton(slots[i], GuiButton.of(buildQuestIcon(quest), event -> claim(quest.quest().id())));
            }
        }

        ItemStack profile = profileIcon();
        for (int slot : SLOT_PROFILE) {
            setButton(slot, GuiButton.display(profile));
        }
        setButton(SLOT_LEADERBOARD, GuiButton.of(navIcon("daily_quest:button_leaderboard", Material.GOLD_INGOT, "리더보드"),
                event -> QuestLeaderboardGui.openAsync(ctx, (Player) event.getWhoClicked())));
        setButton(SLOT_BADGES, GuiButton.of(navIcon("daily_quest:button_badges", Material.SHIELD, "뱃지"),
                event -> new QuestBadgesGui(ctx, (Player) event.getWhoClicked()).open((Player) event.getWhoClicked())));
    }

    private void claim(String questId) {
        var data = ctx.core().playerData().getOnline(viewer.getUniqueId());
        long rewardOn = ctx.questManager().activeQuests(data, period).stream()
                .filter(q -> q.quest().id().equals(questId)).findFirst()
                .map(q -> q.quest().rewardOn()).orElse(0L);
        var result = ctx.questManager().claimQuest(viewer, period, questId);
        switch (result) {
            case SUCCESS -> ctx.messages().send(viewer, "quest.claim-success", Placeholder.unparsed("reward", String.format("%,d", rewardOn)));
            case NOT_COMPLETE -> ctx.messages().send(viewer, "quest.not-complete");
            case ALREADY_CLAIMED -> ctx.messages().send(viewer, "quest.already-claimed");
            case NOT_ACTIVE_QUEST -> {
            }
        }
        new QuestBoardGui(ctx, period, viewer).open(viewer);
    }

    private ItemStack profileIcon() {
        var data = ctx.core().playerData().getOnline(viewer.getUniqueId());
        long total = ctx.questManager().totalCompleted(data);
        QuestBadge badge = ctx.badgeConfig().currentBadge(total);
        List<QuestManager.QuestProgress> quests = ctx.questManager().activeQuests(data, period);
        long claimedCount = quests.stream().filter(QuestManager.QuestProgress::claimed).count();

        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(viewer);
        }
        meta.displayName(Component.text(viewer.getName() + "의 퀘스트 정보", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("총 완료: ", NamedTextColor.GRAY).append(Component.text(total, NamedTextColor.GREEN)).decoration(TextDecoration.ITALIC, false),
                Component.text("뱃지: ", NamedTextColor.GRAY).append(Component.text(badge.name(), NamedTextColor.LIGHT_PURPLE)).decoration(TextDecoration.ITALIC, false),
                Component.text((period == QuestManager.Period.DAILY ? "오늘" : "이번 주") + " 완료: " + claimedCount + "/" + quests.size(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("새로고침까지: " + formatRefreshCountdown(), NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private String formatRefreshCountdown() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDateTime next = period == QuestManager.Period.DAILY
                ? now.toLocalDate().plusDays(1).atStartOfDay()
                : now.toLocalDate().with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).atStartOfDay();
        Duration remaining = Duration.between(now, next);
        long hours = remaining.toHours();
        long minutes = remaining.toMinutesPart();
        return hours + "시간 " + minutes + "분";
    }

    private ItemStack buildQuestIcon(QuestManager.QuestProgress quest) {
        boolean complete = quest.isComplete();
        QuestDifficulty difficulty = quest.quest().difficulty();
        ItemStack stack = QuestBackgroundImages.icon(difficulty.iconId());
        if (stack == null) {
            Material material = quest.claimed() ? Material.GRAY_DYE : complete ? Material.CHEST : Material.BOOK;
            stack = new ItemStack(material);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(quest.quest().display(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(difficulty.label(), difficulty.color()).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("진행도: " + quest.progress() + "/" + quest.quest().target(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("보상: " + String.format("%,d", quest.quest().rewardOn()) + "온", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add((quest.claimed()
                ? Component.text("이미 받았습니다.", NamedTextColor.DARK_GRAY)
                : complete
                ? Component.text("클릭하여 보상 받기", NamedTextColor.GREEN)
                : Component.text("진행 중...", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    static ItemStack navIcon(String customIconId, Material fallback, String name) {
        ItemStack stack = QuestBackgroundImages.icon(customIconId);
        if (stack == null) {
            stack = new ItemStack(fallback);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    static ItemStack navItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
