package com.yeowool.community.friend;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** {@code /친구 추가|수락|거절|삭제|목록} — accept/decline requests only make sense while both players are online. */
public final class FriendCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final FriendManager friendManager;
    private final MessageService messages;

    public FriendCommand(JavaPlugin plugin, FriendManager friendManager, MessageService messages) {
        this.plugin = plugin;
        this.friendManager = friendManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length == 0) {
            list(player);
            return true;
        }

        switch (args[0]) {
            case "추가" -> add(player, args);
            case "수락" -> accept(player);
            case "거절" -> decline(player);
            case "삭제" -> remove(player, args);
            case "목록" -> list(player);
            default -> messages.send(player, "friend.usage");
        }
        return true;
    }

    private void add(Player player, String[] args) {
        if (args.length != 2) {
            messages.send(player, "friend.add-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(player, "friend.player-not-found");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messages.send(player, "friend.cannot-add-self");
            return;
        }

        friendManager.areFriends(player.getUniqueId(), target.getUniqueId()).thenAccept(already ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (already) {
                        messages.send(player, "friend.already-friends");
                        return;
                    }
                    friendManager.sendRequest(player.getUniqueId(), target.getUniqueId());
                    messages.send(player, "friend.request-sent", Placeholder.unparsed("target", target.getName()));
                    messages.send(target, "friend.request-received", Placeholder.unparsed("requester", player.getName()));
                }));
    }

    private void accept(Player player) {
        var requester = friendManager.accept(player.getUniqueId());
        if (requester.isEmpty()) {
            messages.send(player, "friend.no-pending-request");
            return;
        }
        messages.send(player, "friend.accept-success");
        Player requesterPlayer = Bukkit.getPlayer(requester.get());
        if (requesterPlayer != null) {
            messages.send(requesterPlayer, "friend.accepted-by", Placeholder.unparsed("target", player.getName()));
        }
    }

    private void decline(Player player) {
        friendManager.decline(player.getUniqueId());
        messages.send(player, "friend.declined");
    }

    private void remove(Player player, String[] args) {
        if (args.length != 2) {
            messages.send(player, "friend.remove-usage");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId() == null) {
            messages.send(player, "friend.player-not-found");
            return;
        }
        friendManager.removeFriend(player.getUniqueId(), target.getUniqueId());
        messages.send(player, "friend.remove-success", Placeholder.unparsed("target", args[1]));
    }

    private void list(Player player) {
        friendManager.friendsOf(player.getUniqueId()).thenAccept(friends ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (friends.isEmpty()) {
                        messages.send(player, "friend.list-empty");
                        return;
                    }
                    new FriendListGui(friendManager, messages, friends, 0).open(player);
                }));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("추가", "수락", "거절", "삭제", "목록"), args[0]);
        }
        if (args.length == 2 && (args[0].equals("추가") || args[0].equals("삭제"))) {
            return TabCompletions.filteredOnlinePlayerNames(args[1]);
        }
        return List.of();
    }
}
