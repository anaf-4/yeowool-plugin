package com.yeowool.community.vote;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One reward's editor, like {@code AttendanceRewardEditorGui}: slots 0-26 hold the bonus items
 * (saved on close), the bottom row sets 온/별조각 through {@link VoteRewardAmountListener}, goes
 * back, and — for a milestone — deletes it.
 */
final class VoteRewardEditorGui extends YeowoolGui {

    private static final int ITEM_SLOT_COUNT = 27;
    private static final int BACK_SLOT = 36;
    private static final int ON_SLOT = 38;
    private static final int STARDUST_SLOT = 40;
    private static final int DELETE_SLOT = 44;

    private final VoteAdminCommand admin;
    private final int threshold;
    private boolean deleted;

    VoteRewardEditorGui(VoteAdminCommand admin, VoteRewardAmountListener amountListener, VoteRepository.Reward reward) {
        super(45, Component.text(label(reward.threshold()) + " (닫으면 아이템 저장)", NamedTextColor.DARK_GREEN));
        this.admin = admin;
        this.threshold = reward.threshold();

        for (int slot = 0; slot < ITEM_SLOT_COUNT; slot++) {
            setEditableSlot(slot);
        }
        for (int i = 0; i < reward.items().size() && i < ITEM_SLOT_COUNT; i++) {
            getInventory().setItem(i, reward.items().get(i));
        }

        setButton(BACK_SLOT, GuiButton.of(VoteRewardMenuGui.icon(Material.ARROW, "뒤로"), event -> {
            Player player = (Player) event.getWhoClicked();
            player.closeInventory(); // saves items first, so the menu shows them
            admin.openMenu(player);
        }));
        setButton(ON_SLOT, GuiButton.of(VoteRewardMenuGui.icon(Material.GOLD_INGOT, "온 설정",
                "현재: " + String.format("%,d온", reward.on()), "클릭하여 변경"), event -> {
            Player player = (Player) event.getWhoClicked();
            amountListener.beginEdit(player, threshold, false, () -> admin.openEditor(player, threshold));
        }));
        setButton(STARDUST_SLOT, GuiButton.of(VoteRewardMenuGui.icon(Material.AMETHYST_SHARD, "별조각 설정",
                "현재: " + reward.stardust() + "개", "클릭하여 변경"), event -> {
            Player player = (Player) event.getWhoClicked();
            amountListener.beginEdit(player, threshold, true, () -> admin.openEditor(player, threshold));
        }));
        if (threshold != VoteRules.EVERY_VOTE) {
            setButton(DELETE_SLOT, GuiButton.of(VoteRewardMenuGui.icon(Material.TNT, "이 누적 보상 삭제",
                    "아이템과 함께 삭제됩니다"), event -> {
                Player player = (Player) event.getWhoClicked();
                deleted = true;
                player.closeInventory();
                admin.deleteMilestone(player, threshold);
            }));
        }
    }

    @Override
    public void onClose(Player player) {
        if (deleted) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        ItemStack[] contents = getInventory().getContents();
        for (int slot = 0; slot < ITEM_SLOT_COUNT; slot++) {
            if (contents[slot] != null && !contents[slot].getType().isAir()) {
                items.add(contents[slot].clone());
            }
        }
        admin.saveItems(player, threshold, items);
    }

    static String label(int threshold) {
        return threshold == VoteRules.EVERY_VOTE ? "매 추천 보상" : "누적 " + threshold + "회 보상";
    }
}
