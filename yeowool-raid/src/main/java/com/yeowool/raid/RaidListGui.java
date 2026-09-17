package com.yeowool.raid;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public final class RaidListGui extends YeowoolGui {

    public RaidListGui(Collection<RaidDefinition> raids, Consumer<RaidDefinition> onPick) {
        super(54, Component.text("보스 레이드 선택", NamedTextColor.DARK_RED));
        int slot = 0;
        for (RaidDefinition raid : raids) {
            if (slot >= 54) {
                break;
            }
            setButton(slot++, GuiButton.of(icon(raid), event -> onPick.accept(raid)));
        }
    }

    private static ItemStack icon(RaidDefinition raid) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(raid.name(), NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("인원 " + raid.minPartySize() + "~" + raid.maxPartySize() + "명", NamedTextColor.GRAY),
                Component.text("제한시간 " + (raid.timeLimitSeconds() / 60) + "분", NamedTextColor.GRAY),
                Component.text("입장권 " + raid.ticketAmount() + "개 필요", NamedTextColor.GRAY)
        ));
        item.setItemMeta(meta);
        return item;
    }
}
