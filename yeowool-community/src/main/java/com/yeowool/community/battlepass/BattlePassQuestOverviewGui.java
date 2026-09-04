package com.yeowool.community.battlepass;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.community.quest.QuestBoardGui;
import com.yeowool.community.quest.QuestManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * {@code /배틀패스} → 퀘스트: a small hub between the portal and the two real quest boards, since
 * battle pass points come from the EXISTING {@code /일일퀘스트}/{@code /주간퀘스트} systems rather
 * than a battle-pass-specific quest list - this screen just points at them. Background {@code
 * bp_quest_overview_bg}.
 */
public final class BattlePassQuestOverviewGui extends YeowoolGui {

    // quest-overview.yml in the source pack authors this background for a 2-row (18-slot)
    // window, not 3 - same mismatch class as the portal screen's own bug.
    private static final int SLOT_DAILY = 3;
    private static final int SLOT_WEEKLY = 5;
    private static final int[] BACK_SLOTS = {9, 10, 11};
    private static final int[] COMING_SOON_SLOTS = {15, 16, 17};

    public BattlePassQuestOverviewGui(BattlePassManager manager, Player viewer) {
        super(18, title(manager));

        setButton(SLOT_DAILY, GuiButton.of(periodIcon("yeowool_battlepass:bp_questbook", "일일 퀘스트"), event -> {
            if (event.getWhoClicked() instanceof Player player) {
                new QuestBoardGui(manager.questContext(), QuestManager.Period.DAILY, player).open(player);
            }
        }));
        setButton(SLOT_WEEKLY, GuiButton.of(periodIcon("yeowool_battlepass:bp_questbook_gray", "주간 퀘스트"), event -> {
            if (event.getWhoClicked() instanceof Player player) {
                new QuestBoardGui(manager.questContext(), QuestManager.Period.WEEKLY, player).open(player);
            }
        }));

        GuiButton backButton = GuiButton.of(transparentIcon("뒤로가기", NamedTextColor.GRAY), event -> {
            if (event.getWhoClicked() instanceof Player player) {
                new BattlePassPortalGui(manager.questContext().core(), manager, manager.questContext().messages(), player).open(player);
            }
        });
        for (int slot : BACK_SLOTS) {
            setButton(slot, backButton);
        }

        GuiButton comingSoon = GuiButton.display(transparentIcon("준비중", NamedTextColor.YELLOW));
        for (int slot : COMING_SOON_SLOTS) {
            setButton(slot, comingSoon);
        }
    }

    /** {@code bp_air} - transparent - with just a hover label, for buttons whose look comes entirely from the background art. */
    private ItemStack transparentIcon(String label, NamedTextColor color) {
        ItemStack stack = BattlePassBackgroundImages.icon("yeowool_battlepass:bp_air");
        stack = stack == null ? new ItemStack(Material.BARRIER) : stack.clone();
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(label, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component title(BattlePassManager manager) {
        Component fallback = Component.text("배틀패스 - 퀘스트", NamedTextColor.GOLD);
        return BattlePassBackgroundImages.title(manager.config().backgroundOffsetPx(), "bp_quest_overview_bg", fallback);
    }

    private ItemStack periodIcon(String customIconId, String label) {
        ItemStack stack = BattlePassBackgroundImages.icon(customIconId);
        if (stack == null) {
            stack = new ItemStack(Material.WRITTEN_BOOK);
        } else {
            stack = stack.clone();
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("클릭해서 진행 상황을 확인하세요", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("퀘스트를 완료하면 배틀패스 포인트를 받습니다", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }
}
