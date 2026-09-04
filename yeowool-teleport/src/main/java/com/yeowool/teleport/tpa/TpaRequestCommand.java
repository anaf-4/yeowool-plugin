package com.yeowool.teleport.tpa;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /tpa <닉네임>} (요청자가 상대에게 감) or {@code /tpahere <닉네임>} (상대를 요청자에게 부름), selected via {@code kind}. */
public final class TpaRequestCommand implements CommandExecutor, TabCompleter {

    private final TpaManager tpaManager;
    private final TpaManager.Kind kind;
    private final MessageService messages;

    public TpaRequestCommand(TpaManager tpaManager, TpaManager.Kind kind, MessageService messages) {
        this.tpaManager = tpaManager;
        this.kind = kind;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length != 1) {
            messages.send(sender, "tpa.usage", Placeholder.unparsed("label", label));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messages.send(player, "tpa.target-not-found");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messages.send(player, "tpa.cannot-target-self");
            return true;
        }

        tpaManager.request(player.getUniqueId(), target.getUniqueId(), kind);
        String receivedKey = kind == TpaManager.Kind.TO ? "tpa.request-received-to" : "tpa.request-received-here";
        messages.send(player, "tpa.request-sent", Placeholder.unparsed("target", target.getName()));
        messages.send(target, receivedKey, Placeholder.unparsed("requester", player.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filteredOnlinePlayerNames(args[0]);
        }
        return List.of();
    }
}
