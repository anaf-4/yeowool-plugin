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

import java.util.Arrays;
import java.util.List;

/**
 * {@code /아이템설명 변경 <설명>} — sets the lore of the item in hand. Use
 * {@code \n} or {@code |} in the text to start a new line; each line is
 * parsed as MiniMessage, so color/bold/italic work per line. If the item is
 * timed, its {@code N일제} tag (see {@link ItemFlags#refreshTimedTag}) is
 * re-appended afterward so this can never accidentally remove it.
 */
public final class ItemLoreCommand implements CommandExecutor, TabCompleter {

    private final MessageService messages;
    private final ItemFlags itemFlags;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ItemLoreCommand(MessageService messages, ItemFlags itemFlags) {
        this.messages = messages;
        this.itemFlags = itemFlags;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length < 1 || !args[0].equals("변경") || args.length < 2) {
            messages.send(player, "itemtool.lore-usage");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            messages.send(player, "itemtool.need-item-in-hand");
            return true;
        }

        String raw = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String[] lines = raw.replace("\\n", "\n").split("[\n|]");

        List<Component> lore = Arrays.stream(lines)
                .map(line -> miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false))
                .toList();

        ItemMeta meta = item.getItemMeta();
        meta.lore(lore);
        item.setItemMeta(meta);
        itemFlags.refreshTimedTag(item);

        messages.send(player, "itemtool.lore-success", Placeholder.unparsed("lines", String.valueOf(lore.size())));
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
