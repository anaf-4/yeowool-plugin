package com.yeowool.admin.itemtool;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * {@code /기간제 설정 <기간>} — marks the item in the OP's main hand as
 * time-limited. The countdown doesn't start yet: per the requirement, it
 * only begins once a non-OP player actually holds it (see
 * {@link TimedItemTask}), so an OP can freely carry/store it beforehand.
 */
public final class TimedItemCommand implements CommandExecutor, TabCompleter {

    private final MessageService messages;
    private final ItemFlags itemFlags;

    public TimedItemCommand(MessageService messages, ItemFlags itemFlags) {
        this.messages = messages;
        this.itemFlags = itemFlags;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }

        if (args.length == 1 && args[0].equals("해제")) {
            ItemStack item = player.getInventory().getItemInMainHand();
            itemFlags.clearTimed(item);
            messages.send(player, "itemtool.timed-cleared");
            return true;
        }

        if (args.length != 2 || !args[0].equals("설정")) {
            messages.send(player, "itemtool.timed-usage");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            messages.send(player, "itemtool.need-item-in-hand");
            return true;
        }

        long durationMs = DurationParser.parseToMillis(args[1]);
        if (durationMs <= 0) {
            messages.send(player, "itemtool.timed-invalid-duration");
            return true;
        }

        itemFlags.setTimedDurationMs(item, durationMs);
        messages.send(player, "itemtool.timed-set-success",
                Placeholder.unparsed("duration", DurationParser.humanize(durationMs)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("설정", "해제"), args[0]);
        }
        if (args.length == 2 && args[0].equals("설정")) {
            return TabCompletions.filter(List.of("30m", "1h", "6h", "1d", "7d"), args[1]);
        }
        return List.of();
    }
}
