package com.yeowool.admin.itemtool;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * {@code /아이템이름 변경 <이름>} — renames the item in hand using MiniMessage
 * formatting, so an admin can freely mix color, bold/italic/underline, etc.
 * (e.g. {@code <bold><gold>전설의 검</gold></bold>}).
 */
public final class ItemNameCommand implements CommandExecutor, TabCompleter {

    private final MessageService messages;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ItemNameCommand(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length < 1 || !args[0].equals("변경") || args.length < 2) {
            messages.send(player, "itemtool.name-usage");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            messages.send(player, "itemtool.need-item-in-hand");
            return true;
        }

        String raw = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        Component name = miniMessage.deserialize(raw).decoration(TextDecoration.ITALIC, false);

        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);

        messages.send(player, "itemtool.name-success", Placeholder.component("name", name));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("변경"), args[0]);
        }
        return List.of();
    }
}
