package com.yeowool.admin.coupon;

import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@code /쿠폰생성}/{@code /쿠폰관리 [이름] 수정 아이템} — a plain 54-slot
 * editable inventory (every slot free-edit, see {@link YeowoolGui#setEditableSlot(int)}),
 * same pattern as {@code StarterKitEditorGui}. Whatever's left in it when the
 * admin closes the window becomes the coupon's reward list, in slot order.
 */
public final class CouponRewardGui extends YeowoolGui {

    private final Consumer<List<ItemStack>> onSave;

    public CouponRewardGui(List<ItemStack> existingItems, Consumer<List<ItemStack>> onSave) {
        super(54, Component.text("쿠폰 보상 설정 (닫으면 저장됩니다)", NamedTextColor.GOLD));
        this.onSave = onSave;

        for (int slot = 0; slot < 54; slot++) {
            setEditableSlot(slot);
        }
        for (int i = 0; i < existingItems.size() && i < 54; i++) {
            getInventory().setItem(i, existingItems.get(i));
        }
    }

    @Override
    public void onClose(Player player) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                items.add(item);
            }
        }
        onSave.accept(items);
    }
}
