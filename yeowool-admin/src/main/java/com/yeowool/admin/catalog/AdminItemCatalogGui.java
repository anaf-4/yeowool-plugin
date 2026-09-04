package com.yeowool.admin.catalog;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * {@code /관리자아이템} — a quick-give catalog for server-specific items an
 * admin regularly needs (the land-claim barrel, event items, ...) instead of
 * hunting through creative mode. Clicking an entry gives one stack.
 */
public final class AdminItemCatalogGui extends YeowoolGui {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public AdminItemCatalogGui(MessageService messages, List<CatalogEntry> entries) {
        super(Math.max(9, Math.min(54, ((entries.size() / 9) + 1) * 9)), Component.text("관리자 아이템", NamedTextColor.DARK_RED));

        int slot = 0;
        for (CatalogEntry entry : entries) {
            if (slot >= getInventory().getSize()) {
                break;
            }
            setButton(slot++, GuiButton.of(buildIcon(entry), event -> {
                Player player = (Player) event.getWhoClicked();
                ItemStack stack = baseItem(entry, entry.amount());
                if (entry.displayName() != null || !entry.lore().isEmpty()) {
                    applyDisplay(stack, entry);
                }
                var leftover = player.getInventory().addItem(stack);
                leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
                messages.send(player, "catalog.grant-success",
                        Placeholder.unparsed("item", entry.material().name()),
                        Placeholder.unparsed("amount", String.valueOf(entry.amount())));
            }));
        }
    }

    private ItemStack buildIcon(CatalogEntry entry) {
        ItemStack stack = baseItem(entry, Math.max(1, entry.amount()));
        applyDisplay(stack, entry);
        return stack;
    }

    private ItemStack baseItem(CatalogEntry entry, int amount) {
        if (entry.isCustomItem() && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance(entry.customItemId());
            if (custom != null) {
                ItemStack stack = custom.getItemStack();
                stack.setAmount(amount);
                return stack;
            }
        }
        return new ItemStack(entry.material(), amount);
    }

    private void applyDisplay(ItemStack stack, CatalogEntry entry) {
        ItemMeta meta = stack.getItemMeta();
        if (entry.displayName() != null) {
            meta.displayName(miniMessage.deserialize(entry.displayName()).decoration(TextDecoration.ITALIC, false));
        }
        if (!entry.lore().isEmpty()) {
            meta.lore(entry.lore().stream()
                    .map(line -> miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
        if (entry.hasCustomModelData()) {
            meta.setCustomModelData(entry.customModelData());
        }
        stack.setItemMeta(meta);
    }
}
