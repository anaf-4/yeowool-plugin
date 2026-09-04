package com.yeowool.community.quest;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.model.CurrencyType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /출석보상설정}'s per-tier editor: the top three rows (slots 0-26)
 * are free-edit slots for bonus items (mirroring {@code StarterKitEditorGui}
 * — place items, close to save), and the bottom row holds a "금액 설정"
 * button (opens {@link AttendanceRewardAmountListener}'s anvil) and a
 * "화폐" toggle that flips the tier between 온/캐시 immediately on click.
 */
public final class AttendanceRewardEditorGui extends YeowoolGui {

    private static final int ITEM_SLOT_COUNT = 27;
    private static final int AMOUNT_SLOT = 38;
    private static final int CURRENCY_SLOT = 40;
    private static final int CLOSE_SLOT = 42;

    private final AttendanceRewardStore rewardStore;
    private final AttendanceRewardAmountListener amountListener;
    private final AttendanceRewardStore.Tier tier;

    public AttendanceRewardEditorGui(AttendanceRewardStore rewardStore, AttendanceRewardAmountListener amountListener, AttendanceRewardStore.Tier tier) {
        super(45, Component.text(tierLabel(tier) + " 보상 설정 (닫으면 아이템이 저장됩니다)", NamedTextColor.DARK_GREEN));
        this.rewardStore = rewardStore;
        this.amountListener = amountListener;
        this.tier = tier;

        for (int slot = 0; slot < ITEM_SLOT_COUNT; slot++) {
            setEditableSlot(slot);
        }
        AttendanceRewardStore.TierReward reward = rewardStore.get(tier);
        List<ItemStack> items = reward.items();
        for (int i = 0; i < items.size() && i < ITEM_SLOT_COUNT; i++) {
            getInventory().setItem(i, items.get(i));
        }

        setButton(AMOUNT_SLOT, GuiButton.of(amountIcon(reward), event -> {
            Player admin = (Player) event.getWhoClicked();
            amountListener.beginEdit(admin, tier);
        }));
        setButton(CURRENCY_SLOT, GuiButton.of(currencyIcon(reward), event -> {
            Player admin = (Player) event.getWhoClicked();
            CurrencyType next = reward.currency() == CurrencyType.ON ? CurrencyType.CASH : CurrencyType.ON;
            rewardStore.saveAmount(tier, reward.amount(), next);
            admin.sendMessage(Component.text("화폐를 " + next.displayName() + "(으)로 변경했습니다.", NamedTextColor.GREEN));
            new AttendanceRewardEditorGui(rewardStore, amountListener, tier).open(admin);
        }));
        setButton(CLOSE_SLOT, GuiButton.of(navItem(Material.BARRIER, "닫기"), event -> event.getWhoClicked().closeInventory()));
    }

    @Override
    public void onClose(Player player) {
        Map<Integer, ItemStack> snapshot = new LinkedHashMap<>();
        ItemStack[] contents = getInventory().getContents();
        for (int slot = 0; slot < ITEM_SLOT_COUNT; slot++) {
            if (contents[slot] != null && !contents[slot].getType().isAir()) {
                snapshot.put(slot, contents[slot]);
            }
        }
        rewardStore.saveItems(tier, snapshot);
        player.sendMessage(Component.text(tierLabel(tier) + " 보상 아이템을 저장했습니다. (" + snapshot.size() + "개)", NamedTextColor.GREEN));
    }

    private ItemStack amountIcon(AttendanceRewardStore.TierReward reward) {
        ItemStack stack = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("금액 설정", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("현재: " + String.format("%,d", reward.amount()) + reward.currency().displayName(), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("클릭하여 변경", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack currencyIcon(AttendanceRewardStore.TierReward reward) {
        ItemStack stack = new ItemStack(reward.currency() == CurrencyType.CASH ? Material.SUNFLOWER : Material.EMERALD);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("화폐: " + reward.currency().displayName(), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("클릭하여 전환 (온 ↔ 캐시)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack navItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GRAY));
        stack.setItemMeta(meta);
        return stack;
    }

    static String tierLabel(AttendanceRewardStore.Tier tier) {
        return switch (tier) {
            case DAILY -> "일일";
            case WEEKLY -> "주간";
            case MONTHLY -> "월간";
        };
    }
}
