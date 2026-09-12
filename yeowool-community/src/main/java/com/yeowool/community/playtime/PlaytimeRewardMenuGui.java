package com.yeowool.community.playtime;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** {@code /플레이타임보상설정}'s hub — pick which milestone's reward to edit. */
public final class PlaytimeRewardMenuGui extends YeowoolGui {

    public PlaytimeRewardMenuGui(PlaytimeRewardStore rewardStore, PlaytimeRewardAmountListener amountListener) {
        super(9, Component.text("플레이타임 보상 설정", NamedTextColor.DARK_GREEN));

        setButton(1, GuiButton.of(tierIcon(Material.CLOCK, "1시간 보상"), event ->
                new PlaytimeRewardEditorGui(rewardStore, amountListener, PlaytimeRewardStore.Tier.ONE_HOUR)
                        .open((Player) event.getWhoClicked())));
        setButton(3, GuiButton.of(tierIcon(Material.CLOCK, "6시간 보상"), event ->
                new PlaytimeRewardEditorGui(rewardStore, amountListener, PlaytimeRewardStore.Tier.SIX_HOURS)
                        .open((Player) event.getWhoClicked())));
        setButton(5, GuiButton.of(tierIcon(Material.CLOCK, "12시간 보상"), event ->
                new PlaytimeRewardEditorGui(rewardStore, amountListener, PlaytimeRewardStore.Tier.TWELVE_HOURS)
                        .open((Player) event.getWhoClicked())));
        setButton(7, GuiButton.of(tierIcon(Material.NETHER_STAR, "24시간 보상"), event ->
                new PlaytimeRewardEditorGui(rewardStore, amountListener, PlaytimeRewardStore.Tier.TWENTY_FOUR_HOURS)
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
