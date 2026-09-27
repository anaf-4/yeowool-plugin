package com.yeowool.raid.worldboss;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.regex.Pattern;

/** {@code /월드보스}: status for everyone; spot management and summon/dismiss for staff. */
public final class WorldBossCommand implements CommandExecutor, TabCompleter {

    @FunctionalInterface
    private interface SqlTask {
        void run() throws SQLException;
    }

    private static final String ADMIN = "yeowool.event.manage";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9가-힣_]{1,32}");
    private static final List<String> SUBCOMMANDS = List.of("위치추가", "위치제거", "위치목록", "소환", "제거");

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final WorldBossService service;
    private final Executor executor;

    public WorldBossCommand(JavaPlugin plugin, MessageService messages, WorldBossService service, Executor executor) {
        this.plugin = plugin;
        this.messages = messages;
        this.service = service;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            service.showStatus(sender);
            return true;
        }
        if (!sender.hasPermission(ADMIN)) {
            messages.send(sender, "worldboss.no-permission");
            return true;
        }
        switch (args[0]) {
            case "위치추가" -> addSpot(sender, args);
            case "위치제거" -> removeSpot(sender, args);
            case "위치목록" -> listSpots(sender);
            case "소환" -> service.summon(sender);
            case "제거" -> service.dismiss(sender);
            default -> messages.send(sender, "worldboss.usage");
        }
        return true;
    }

    private void addSpot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "worldboss.player-only");
            return;
        }
        if (!player.getWorld().getName().equals(service.settings().world())) {
            messages.send(sender, "worldboss.wrong-world", Placeholder.unparsed("world", service.settings().world()));
            return;
        }
        if (args.length < 2 || !NAME.matcher(args[1]).matches()) {
            messages.send(sender, "worldboss.invalid-name");
            return;
        }
        Location loc = player.getLocation();
        WorldBossRepository.Spot spot = new WorldBossRepository.Spot(args[1], loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
        async(sender, () -> {
            service.repository().saveSpot(spot);
            reply(sender, "worldboss.spot-added", Placeholder.unparsed("name", spot.name()));
        });
    }

    private void removeSpot(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "worldboss.usage");
            return;
        }
        String name = args[1];
        async(sender, () -> reply(sender, service.repository().deleteSpot(name) ? "worldboss.spot-removed" : "worldboss.spot-not-found",
                Placeholder.unparsed("name", name)));
    }

    private void listSpots(CommandSender sender) {
        async(sender, () -> {
            List<WorldBossRepository.Spot> spots = service.repository().spots();
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (spots.isEmpty()) {
                    messages.send(sender, "worldboss.spot-list-empty");
                    return;
                }
                for (WorldBossRepository.Spot spot : spots) {
                    messages.send(sender, "worldboss.spot-line",
                            Placeholder.unparsed("name", spot.name()),
                            Placeholder.unparsed("world", spot.world()),
                            Placeholder.unparsed("x", String.valueOf((long) Math.floor(spot.x()))),
                            Placeholder.unparsed("y", String.valueOf((long) Math.floor(spot.y()))),
                            Placeholder.unparsed("z", String.valueOf((long) Math.floor(spot.z()))));
                }
            });
        });
    }

    private void async(CommandSender sender, SqlTask task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "월드보스 명령 처리 실패", e);
                reply(sender, "worldboss.error");
            }
        });
    }

    private void reply(CommandSender sender, String key, TagResolver... placeholders) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, key, placeholders));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission(ADMIN)) {
            return SUBCOMMANDS.stream().filter(sub -> sub.startsWith(args[0])).toList();
        }
        return List.of();
    }
}
