package com.yeowool.community.quest;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/**
 * {@code /일일퀘스트}/{@code /주간퀘스트} → 리더보드 화면 (DailyQuest-1.8.3
 * "LEADERBOARD" layout, corrected live against the actual in-game rendering)
 * — rank 1/2/3 at 4/3/5 (podium order), ranks 4-10 along 10-16. No other
 * icons. The DB query ({@link QuestLeaderboardQuery}) is blocking, so
 * {@link #openAsync} is the only entry point — it runs the query off-thread
 * and opens the built GUI back on the main thread. Background
 * {@code daily_quest:quest_leaderboard_bg}.
 */
public final class QuestLeaderboardGui extends YeowoolGui {

    private static final int SLOT_RANK_1 = 4;
    private static final int SLOT_RANK_2 = 3;
    private static final int SLOT_RANK_3 = 5;
    private static final int[] SLOT_RANKS_4_TO_10 = {10, 11, 12, 13, 14, 15, 16};

    public static void openAsync(QuestContext ctx, Player viewer) {
        ctx.executor().execute(() -> {
            List<QuestLeaderboardQuery.Row> top;
            try {
                top = ctx.leaderboardQuery().top(10);
            } catch (Exception e) {
                Bukkit.getLogger().severe("퀘스트 리더보드 조회 실패: " + e.getMessage());
                Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.messages().send(viewer, "quest.leaderboard-load-failed"));
                return;
            }
            List<QuestLeaderboardQuery.Row> finalTop = top;
            Bukkit.getScheduler().runTask(ctx.plugin(), () -> new QuestLeaderboardGui(ctx, finalTop).open(viewer));
        });
    }

    private QuestLeaderboardGui(QuestContext ctx, List<QuestLeaderboardQuery.Row> top) {
        super(27, QuestBackgroundImages.title(ctx.questManager().backgroundOffsetPx(), "quest_leaderboard_bg",
                Component.text("퀘스트 리더보드", NamedTextColor.GOLD)));

        if (top.size() > 0) {
            setButton(SLOT_RANK_1, GuiButton.display(buildRankIcon(top.get(0), 1)));
        }
        if (top.size() > 1) {
            setButton(SLOT_RANK_2, GuiButton.display(buildRankIcon(top.get(1), 2)));
        }
        if (top.size() > 2) {
            setButton(SLOT_RANK_3, GuiButton.display(buildRankIcon(top.get(2), 3)));
        }
        for (int i = 0; i < SLOT_RANKS_4_TO_10.length; i++) {
            int rank = i + 4;
            if (rank - 1 >= top.size()) {
                break;
            }
            setButton(SLOT_RANKS_4_TO_10[i], GuiButton.display(buildRankIcon(top.get(rank - 1), rank)));
        }
    }

    private ItemStack buildRankIcon(QuestLeaderboardQuery.Row row, int rank) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(row.uuid());
            skullMeta.setOwningPlayer(offline);
        }
        meta.displayName(Component.text(rank + "위. " + row.username(), rankColor(rank)).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("총 완료 퀘스트: " + row.total() + "회", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private NamedTextColor rankColor(int rank) {
        return switch (rank) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.WHITE;
            case 3 -> NamedTextColor.YELLOW;
            default -> NamedTextColor.GRAY;
        };
    }
}
