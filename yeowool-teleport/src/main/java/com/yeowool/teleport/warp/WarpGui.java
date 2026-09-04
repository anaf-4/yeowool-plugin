package com.yeowool.teleport.warp;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.teleport.TeleportService;
import com.yeowool.teleport.model.Warp;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** {@code /워프} — browse and click a warp to teleport. Appearance (filler + icon) is config-driven, see {@link WarpGuiConfig}. */
public final class WarpGui extends YeowoolGui {

    private final MessageService messages;

    public WarpGui(WarpManager warpManager, TeleportService teleportService, WarpGuiConfig guiConfig, MessageService messages) {
        super(guiConfig.size(), Component.text("워프 목록", NamedTextColor.DARK_AQUA));
        this.messages = messages;

        if (guiConfig.hasDecoration()) {
            ItemStack filler = buildDecoration(guiConfig);
            for (int slot = 0; slot < guiConfig.size(); slot++) {
                setButton(slot, GuiButton.display(filler));
            }
        }

        int slot = 0;
        for (Warp warp : warpManager.all()) {
            if (slot >= guiConfig.size()) {
                break;
            }
            setButton(slot, GuiButton.of(buildIcon(warp, guiConfig), event -> {
                Player player = (Player) event.getWhoClicked();
                player.closeInventory();
                teleportTo(player, warp, teleportService);
            }));
            slot++;
        }

        if (warpManager.all().isEmpty()) {
            ItemStack empty = new ItemStack(org.bukkit.Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.displayName(Component.text("등록된 워프가 없습니다", NamedTextColor.GRAY));
            empty.setItemMeta(meta);
            setButton(guiConfig.size() / 2, GuiButton.display(empty));
        }
    }

    private void teleportTo(Player player, Warp warp, TeleportService teleportService) {
        var location = warp.toLocation();
        if (location == null) {
            messages.send(player, "warp.world-not-found");
            return;
        }
        long cooldown = teleportService.remainingCooldownSeconds(player);
        if (cooldown > 0) {
            messages.send(player, "general.cooldown", Placeholder.unparsed("seconds", String.valueOf(cooldown)));
            return;
        }
        teleportService.requestTeleport(player, location);
    }

    private ItemStack buildDecoration(WarpGuiConfig guiConfig) {
        ItemStack stack = new ItemStack(guiConfig.decorationMaterial());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(" "));
        if (guiConfig.hasDecorationCustomModelData()) {
            meta.setCustomModelData(guiConfig.decorationCustomModelData());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildIcon(Warp warp, WarpGuiConfig guiConfig) {
        ItemStack stack = new ItemStack(guiConfig.iconMaterial());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(warp.name(), NamedTextColor.AQUA));
        if (guiConfig.hasIconCustomModelData()) {
            meta.setCustomModelData(guiConfig.iconCustomModelData());
        }
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("월드: " + warp.world(), NamedTextColor.GRAY));
        lore.add(Component.text(String.format("좌표: %.0f, %.0f, %.0f", warp.x(), warp.y(), warp.z()), NamedTextColor.GRAY));
        lore.add(Component.text("클릭하여 이동", NamedTextColor.GREEN));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
