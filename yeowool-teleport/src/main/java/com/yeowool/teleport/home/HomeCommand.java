package com.yeowool.teleport.home;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import com.yeowool.teleport.TeleportService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/** {@code /홈 [이름]}, {@code /홈 설정|삭제|목록}. Bare {@code /홈} goes to the home named "home" if one exists. */
public final class HomeCommand implements CommandExecutor, TabCompleter {

    private static final String DEFAULT_NAME = "home";
    private static final int MAX_NAME_LENGTH = 32;

    private final HomeManager homeManager;
    private final TeleportService teleportService;
    private final MessageService messages;

    public HomeCommand(HomeManager homeManager, TeleportService teleportService, MessageService messages) {
        this.homeManager = homeManager;
        this.teleportService = teleportService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length == 0) {
            teleportToHome(player, DEFAULT_NAME);
            return true;
        }
        switch (args[0]) {
            case "설정" -> set(player, args);
            case "삭제" -> delete(player, args);
            case "목록" -> list(player);
            default -> teleportToHome(player, args[0]);
        }
        return true;
    }

    private void teleportToHome(Player player, String name) {
        var home = homeManager.get(player.getUniqueId(), name);
        if (home.isEmpty()) {
            messages.send(player, "home.not-found", Placeholder.unparsed("name", name));
            return;
        }
        var location = home.get().toLocation();
        if (location == null) {
            messages.send(player, "home.world-not-found");
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
        String name = args.length >= 2 ? args[1] : DEFAULT_NAME;
        if (name.length() > MAX_NAME_LENGTH) {
            messages.send(player, "home.name-too-long", Placeholder.unparsed("max", String.valueOf(MAX_NAME_LENGTH)));
            return;
        }
        var result = homeManager.set(player.getUniqueId(), name, player.getLocation());
        if (result == HomeManager.SetResult.LIMIT_REACHED) {
            messages.send(player, "home.limit-reached");
            return;
        }
        messages.send(player, "home.set-success", Placeholder.unparsed("name", name));
    }

    private void delete(Player player, String[] args) {
        String name = args.length >= 2 ? args[1] : DEFAULT_NAME;
        if (!homeManager.delete(player.getUniqueId(), name)) {
            messages.send(player, "home.delete-not-found", Placeholder.unparsed("name", name));
            return;
        }
        messages.send(player, "home.delete-success", Placeholder.unparsed("name", name));
    }

    private void list(Player player) {
        var homes = homeManager.homesOf(player.getUniqueId());
        if (homes.isEmpty()) {
            messages.send(player, "home.list-empty");
            return;
        }
        String names = homes.values().stream().map(h -> h.name()).collect(Collectors.joining(", "));
        messages.send(player, "home.list", Placeholder.unparsed("names", names));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            List<String> options = homeManager.homesOf(player.getUniqueId()).values().stream()
                    .map(h -> h.name()).collect(Collectors.toList());
            options.add("설정");
            options.add("삭제");
            options.add("목록");
            return TabCompletions.filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equals("삭제") && sender instanceof Player player) {
            return TabCompletions.filter(homeManager.homesOf(player.getUniqueId()).values().stream()
                    .map(h -> h.name()).collect(Collectors.toList()), args[1]);
        }
        return List.of();
    }
}
