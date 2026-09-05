package com.yeowool.community.quest;

import com.yeowool.core.api.model.CurrencyType;
import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /출석보상초기화} — one-shot admin utility that seeds the three
 * attendance tiers (일일/주간/월간) with a default 온 amount plus
 * 자동줍기권/자동심기권 bonus items, mirroring what an operator would
 * otherwise place through {@code /출석보상설정}'s GUI. Skips a tier that
 * already has items saved, so re-running it is harmless.
 *
 * <p>Builds the voucher items' PDC tags by hand instead of depending on
 * yeowool-life's {@code AutoFarmVoucherItem} (that module isn't deployed on
 * every server), using the exact same namespaced keys ({@code yeowoollife:
 * autofarm_voucher_type} / {@code autofarm_voucher_charges}) so
 * {@code AutoFarmVoucherListener} recognizes them identically once granted.
 */
public final class AttendanceRewardSeedCommand implements CommandExecutor {

    private record TierSpec(AttendanceRewardStore.Tier tier, long amount, long voucherCharges) {
    }

    private static final NamespacedKey VOUCHER_TYPE_KEY = new NamespacedKey("yeowoollife", "autofarm_voucher_type");
    private static final NamespacedKey VOUCHER_CHARGES_KEY = new NamespacedKey("yeowoollife", "autofarm_voucher_charges");

    private static final List<TierSpec> TIERS = List.of(
            new TierSpec(AttendanceRewardStore.Tier.DAILY, 1500, 50),
            new TierSpec(AttendanceRewardStore.Tier.WEEKLY, 15000, 500),
            new TierSpec(AttendanceRewardStore.Tier.MONTHLY, 100000, 1000)
    );

    private final AttendanceRewardStore rewardStore;

    public AttendanceRewardSeedCommand(AttendanceRewardStore rewardStore) {
        this.rewardStore = rewardStore;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        for (TierSpec spec : TIERS) {
            if (!rewardStore.get(spec.tier()).items().isEmpty()) {
                sender.sendMessage(Component.text("[" + AttendanceRewardEditorGui.tierLabel(spec.tier()) + "] 이미 아이템이 있어 건너뜁니다.", NamedTextColor.YELLOW));
                continue;
            }
            rewardStore.saveAmount(spec.tier(), spec.amount(), CurrencyType.ON);
            Map<Integer, ItemStack> items = new LinkedHashMap<>();
            items.put(0, voucher("PICKUP", "자동줍기권", "moafarm_items:roll_exp", spec.voucherCharges()));
            items.put(1, voucher("PLANT", "자동심기권", "moafarm_items:roll_level", spec.voucherCharges()));
            rewardStore.saveItems(spec.tier(), items);
            sender.sendMessage(Component.text("[" + AttendanceRewardEditorGui.tierLabel(spec.tier()) + "] "
                    + String.format("%,d", spec.amount()) + "온 + 자동줍기권/자동심기권 " + String.format("%,d", spec.voucherCharges())
                    + "회로 설정했습니다.", NamedTextColor.GREEN));
        }
        return true;
    }

    private ItemStack voucher(String type, String name, String iconId, long charges) {
        ItemStack stack = resolveIcon(iconId);
        stack.setAmount(1);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("1개당 충전량: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(String.format("%,d회", charges), NamedTextColor.AQUA)),
                Component.text("우클릭: 1개 사용", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("쉬프트+우클릭: 보유한 만큼 한번에 사용", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(VOUCHER_TYPE_KEY, PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(VOUCHER_CHARGES_KEY, PersistentDataType.LONG, charges);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack resolveIcon(String iconId) {
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance(iconId);
            if (custom != null) {
                return custom.getItemStack();
            }
        }
        return new ItemStack(Material.PAPER);
    }
}
