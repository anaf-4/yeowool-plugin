package com.yeowool.community.battlepass;

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
 * {@code /배틀패스보상설정}'s per-track-per-tier editor: the top three rows are free-edit slots
 * for bonus items (place items, close to save — mirrors {@code AttendanceRewardEditorGui}), the
 * bottom row holds "필요 포인트"/"금액"/"화폐" buttons.
 */
public final class BattlePassRewardEditorGui extends YeowoolGui {

    private static final int ITEM_SLOT_COUNT = 27;
    private static final int PREV_TIER_SLOT = 36;
    private static final int REQUIRED_POINTS_SLOT = 37;
    private static final int TIER_DISPLAY_SLOT = 38;
    private static final int AMOUNT_SLOT = 39;
    private static final int CURRENCY_SLOT = 41;
    private static final int CLOSE_SLOT = 43;
    private static final int NEXT_TIER_SLOT = 44;

    private final BattlePassRewardStore rewardStore;
    private final BattlePassAmountListener amountListener;
    private final BattlePassTrack track;
    private final int tier;

    public BattlePassRewardEditorGui(BattlePassRewardStore rewardStore, BattlePassAmountListener amountListener,
                                      BattlePassTrack track, int tier) {
        super(45, Component.text(track.label() + " Tier " + tier + " 보상 설정 (닫으면 아이템이 저장됩니다)", NamedTextColor.DARK_GREEN));
        this.rewardStore = rewardStore;
        this.amountListener = amountListener;
        this.track = track;
        this.tier = tier;

        for (int slot = 0; slot < ITEM_SLOT_COUNT; slot++) {
            setEditableSlot(slot);
        }
        BattlePassRewardStore.TierReward reward = rewardStore.get(track, tier);
        List<ItemStack> items = reward.items();
        for (int i = 0; i < items.size() && i < ITEM_SLOT_COUNT; i++) {
            getInventory().setItem(i, items.get(i));
        }

        setButton(REQUIRED_POINTS_SLOT, GuiButton.of(requiredPointsIcon(reward), event -> {
            if (event.getWhoClicked() instanceof Player admin) {
                amountListener.beginEdit(admin, track, tier, BattlePassAmountListener.Field.REQUIRED_POINTS);
            }
        }));
        setButton(AMOUNT_SLOT, GuiButton.of(amountIcon(reward), event -> {
            if (event.getWhoClicked() instanceof Player admin) {
                amountListener.beginEdit(admin, track, tier, BattlePassAmountListener.Field.AMOUNT);
            }
        }));
        setButton(CURRENCY_SLOT, GuiButton.of(currencyIcon(reward), event -> {
            if (!(event.getWhoClicked() instanceof Player admin)) {
                return;
            }
            CurrencyType next = reward.currency() == CurrencyType.ON ? CurrencyType.CASH : CurrencyType.ON;
            rewardStore.saveConfig(track, tier, reward.requiredPoints(), reward.amount(), next);
            admin.sendMessage(Component.text("화폐를 " + next.displayName() + "(으)로 변경했습니다.", NamedTextColor.GREEN));
            new BattlePassRewardEditorGui(rewardStore, amountListener, track, tier).open(admin);
        }));
        setButton(CLOSE_SLOT, GuiButton.of(navItem(Material.BARRIER, "닫기"), event -> event.getWhoClicked().closeInventory()));
        setButton(TIER_DISPLAY_SLOT, GuiButton.display(tierDisplayIcon()));

        if (tier > 1) {
            setButton(PREV_TIER_SLOT, GuiButton.of(navItem(Material.ARROW, "이전 티어 (Tier " + (tier - 1) + ")"), event -> {
                if (event.getWhoClicked() instanceof Player admin) {
                    new BattlePassRewardEditorGui(rewardStore, amountListener, track, tier - 1).open(admin);
                }
            }));
        } else {
            setButton(PREV_TIER_SLOT, GuiButton.display(navItem(Material.GRAY_STAINED_GLASS_PANE, "첫 티어입니다")));
        }
        setButton(NEXT_TIER_SLOT, GuiButton.of(navItem(Material.ARROW, "다음 티어 (Tier " + (tier + 1) + ")"), event -> {
            if (event.getWhoClicked() instanceof Player admin) {
                new BattlePassRewardEditorGui(rewardStore, amountListener, track, tier + 1).open(admin);
            }
        }));
    }

    private ItemStack tierDisplayIcon() {
        ItemStack stack = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(track.label() + " Tier " + tier, NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("◀▶ 버튼으로 다른 티어로 이동", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
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
        rewardStore.saveItems(track, tier, snapshot);
        player.sendMessage(Component.text(track.label() + " Tier " + tier + " 보상 아이템을 저장했습니다. (" + snapshot.size() + "개)", NamedTextColor.GREEN));
    }

    private ItemStack requiredPointsIcon(BattlePassRewardStore.TierReward reward) {
        ItemStack stack = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("필요 포인트", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        String requiredText = reward.requiredPoints() == Long.MAX_VALUE ? "설정 안 됨" : String.format("%,d", reward.requiredPoints());
        meta.lore(List.of(
                Component.text("현재: " + requiredText, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("클릭하여 변경", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack amountIcon(BattlePassRewardStore.TierReward reward) {
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

    private ItemStack currencyIcon(BattlePassRewardStore.TierReward reward) {
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
}
