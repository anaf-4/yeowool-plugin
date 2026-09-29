package com.yeowool.enhance;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * {@code /초월석 지급 <닉네임> <1|2|3> [개수]} — staff/console (world boss rewards run it from the
 * console). Delivered through the mailbox, so the target may be offline or on another server as long
 * as they've joined before.
 */
public final class TranscendStoneCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_AMOUNT = 64;

    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final EnhanceConfig config;

    public TranscendStoneCommand(YeowoolCoreAPI core, MessageService messages, EnhanceConfig config) {
        this.core = core;
        this.messages = messages;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 3 || !args[0].equals("지급")) {
            messages.send(sender, "enhance.stone-usage");
            return true;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(args[1]);
        }
        if (target == null) {
            messages.send(sender, "enhance.stone-unknown-player");
            return true;
        }
        int stage;
        int amount;
        try {
            stage = Integer.parseInt(args[2]);
            amount = args.length >= 4 ? Integer.parseInt(args[3]) : 1;
        } catch (NumberFormatException e) {
            messages.send(sender, "enhance.stone-usage");
            return true;
        }
        var transcendStage = config.transcendStage(stage);
        if (transcendStage.isEmpty() || amount < 1 || amount > MAX_AMOUNT) {
            messages.send(sender, "enhance.stone-usage");
            return true;
        }
        ItemStack stone = EnhanceMaterialResolver.createItem(transcendStage.get().stoneItemId(), amount);
        if (stone == null) {
            messages.send(sender, "enhance.stone-missing-item", Placeholder.unparsed("id", transcendStage.get().stoneItemId()));
            return true;
        }
        core.mailbox().deliverOrStore(target.getUniqueId(), stone, "YeowoolEnhance", transcendStage.get().name() + " 초월석");
        messages.send(sender, "enhance.stone-given",
                Placeholder.unparsed("player", target.getName() == null ? args[1] : target.getName()),
                Placeholder.unparsed("stage", transcendStage.get().name()),
                Placeholder.unparsed("amount", String.valueOf(amount)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("지급");
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.startsWith(args[1])).toList();
        }
        if (args.length == 3) {
            return List.of("1", "2", "3");
        }
        return List.of();
    }
}
