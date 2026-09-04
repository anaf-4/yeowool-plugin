package com.yeowool.teleport.warp;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import com.yeowool.teleport.TeleportService;
import com.yeowool.teleport.model.Warp;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /워프 [이름]} — open to everyone; bare {@code /워프} or
 * {@code /워프 목록} opens a browsable GUI (see {@link WarpGui}) instead of
 * a plain chat list. {@code /워프 설정|삭제 <이름>} require
 * {@code yeowool.teleport.warp.manage} (a dedicated node rather than blanket
 * {@code yeowool.admin}, since curating warps is a delegable world-building
 * task, not general server administration).
 */
public final class WarpCommand implements CommandExecutor, TabCompleter {

    private final WarpManager warpManager;
    private final TeleportService teleportService;
    private final WarpGuiConfig guiConfig;
    private final MessageService messages;

    public WarpCommand(WarpManager warpManager, TeleportService teleportService, WarpGuiConfig guiConfig, MessageService messages) {
        this.warpManager = warpManager;
        this.teleportService = teleportService;
        this.guiConfig = guiConfig;
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
            case "설정" -> set(player, args);
            case "삭제" -> delete(player, args);
            case "목록" -> list(player);
            default -> teleportToWarp(player, args[0]);
        }
        return true;
    }

    private void teleportToWarp(Player player, String name) {
        var warp = warpManager.get(name);
        if (warp.isEmpty()) {
            messages.send(player, "warp.not-found", Placeholder.unparsed("name", name));
            return;
        }
        var location = warp.get().toLocation();
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

    private void set(Player player, String[] args) {
        if (!player.hasPermission("yeowool.teleport.warp.manage")) {
            messages.send(player, "warp.permission-denied");
            return;
        }
        if (args.length != 2) {
            messages.send(player, "warp.set-usage");
            return;
        }
        warpManager.set(Warp.of(args[1], player.getLocation(), player.getUniqueId()));
        messages.send(player, "warp.set-success", Placeholder.unparsed("name", args[1]));
    }

    private void delete(Player player, String[] args) {
        if (!player.hasPermission("yeowool.teleport.warp.manage")) {
            messages.send(player, "warp.permission-denied");
            return;
        }
        if (args.length != 2) {
            messages.send(player, "warp.delete-usage");
            return;
        }
        if (!warpManager.delete(args[1])) {
            messages.send(player, "warp.delete-not-found", Placeholder.unparsed("name", args[1]));
            return;
        }
        messages.send(player, "warp.delete-success", Placeholder.unparsed("name", args[1]));
    }

    private void list(Player player) {
        new WarpGui(warpManager, teleportService, guiConfig, messages).open(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = warpManager.all().stream().map(Warp::name).collect(Collectors.toList());
            options.add("설정");
            options.add("삭제");
            options.add("목록");
            return TabCompletions.filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equals("삭제")) {
            return TabCompletions.filter(warpManager.all().stream().map(Warp::name).collect(Collectors.toList()), args[1]);
        }
        return List.of();
    }
}
