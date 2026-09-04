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

/** {@code /출석보상설정}'s hub — pick which tier's reward to edit. */
public final class AttendanceRewardMenuGui extends YeowoolGui {

    public AttendanceRewardMenuGui(AttendanceRewardStore rewardStore, AttendanceRewardAmountListener amountListener) {
        super(27, Component.text("출석 보상 설정", NamedTextColor.DARK_GREEN));

        setButton(11, GuiButton.of(tierIcon(Material.CLOCK, "일일 보상"), event ->
                new AttendanceRewardEditorGui(rewardStore, amountListener, AttendanceRewardStore.Tier.DAILY)
                        .open((Player) event.getWhoClicked())));
        setButton(13, GuiButton.of(tierIcon(Material.SUNFLOWER, "주간 보상"), event ->
                new AttendanceRewardEditorGui(rewardStore, amountListener, AttendanceRewardStore.Tier.WEEKLY)
                        .open((Player) event.getWhoClicked())));
        setButton(15, GuiButton.of(tierIcon(Material.NETHER_STAR, "월간 보상"), event ->
                new AttendanceRewardEditorGui(rewardStore, amountListener, AttendanceRewardStore.Tier.MONTHLY)
                        .open((Player) event.getWhoClicked())));
    }

    private ItemStack tierIcon(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
