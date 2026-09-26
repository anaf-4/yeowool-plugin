package com.yeowool.market.merchant;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.DurationFormat;
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

/**
 * {@code /떠돌이상인}: anyone sees where the merchant is; staff
 * ({@code yeowool.event.manage}) manage candidate spots and can force it in
 * or out. DB work runs on the executor, replies come back on the main thread.
 */
public final class MerchantCommand implements CommandExecutor, TabCompleter {

    @FunctionalInterface
    private interface SqlTask {
        void run() throws SQLException;
    }

    private static final String ADMIN = "yeowool.event.manage";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9가-힣_]{1,32}");
    private static final List<String> SUBCOMMANDS = List.of("위치추가", "위치제거", "위치목록", "소환", "퇴장");

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final MerchantService service;
    private final Executor executor;

    public MerchantCommand(JavaPlugin plugin, MessageService messages, MerchantService service, Executor executor) {
        this.plugin = plugin;
        this.messages = messages;
        this.service = service;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            status(sender);
            return true;
        }
        if (!sender.hasPermission(ADMIN)) {
            messages.send(sender, "merchant.no-permission");
            return true;
        }
        switch (args[0]) {
            case "위치추가" -> addSpot(sender, args);
            case "위치제거" -> removeSpot(sender, args);
            case "위치목록" -> listSpots(sender);
            case "소환" -> summon(sender);
            case "퇴장" -> dismiss(sender);
            default -> messages.send(sender, "merchant.usage");
        }
        return true;
    }

    private void status(CommandSender sender) {
        MerchantRepository.State state = service.current();
        if (state == null || !state.active()) {
            messages.send(sender, "merchant.none");
            return;
        }
        MerchantRepository.Spot spot = state.spot();
        messages.send(sender, "merchant.status",
                Placeholder.unparsed("server", service.settings().serverName(spot.serverId())),
                Placeholder.unparsed("spot", spot.name()),
                Placeholder.unparsed("x", String.valueOf((long) Math.floor(spot.x()))),
                Placeholder.unparsed("y", String.valueOf((long) Math.floor(spot.y()))),
                Placeholder.unparsed("z", String.valueOf((long) Math.floor(spot.z()))),
                Placeholder.unparsed("remaining", DurationFormat.humanize(Math.max(0, state.despawnAt() - System.currentTimeMillis()))));
    }

    private void addSpot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        if (args.length < 2 || !NAME.matcher(args[1]).matches()) {
            messages.send(sender, "merchant.invalid-name");
            return;
        }
        Location loc = player.getLocation();
        MerchantRepository.Spot spot = new MerchantRepository.Spot(args[1], service.settings().thisServerId(),
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        async(sender, () -> {
            service.repository().saveSpot(spot);
            reply(sender, "merchant.spot-added", Placeholder.unparsed("name", spot.name()));
        });
    }

    private void removeSpot(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "merchant.usage");
            return;
        }
        String name = args[1];
        async(sender, () -> reply(sender, service.repository().deleteSpot(name) ? "merchant.spot-removed" : "merchant.spot-not-found",
                Placeholder.unparsed("name", name)));
    }

    private void listSpots(CommandSender sender) {
        async(sender, () -> {
            List<MerchantRepository.Spot> spots = service.repository().spots();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (spots.isEmpty()) {
                    messages.send(sender, "merchant.spot-list-empty");
                    return;
                }
                for (MerchantRepository.Spot spot : spots) {
                    messages.send(sender, "merchant.spot-line",
                            Placeholder.unparsed("name", spot.name()),
                            Placeholder.unparsed("server", service.settings().serverName(spot.serverId())),
                            Placeholder.unparsed("world", spot.world()),
                            Placeholder.unparsed("x", String.valueOf((long) Math.floor(spot.x()))),
                            Placeholder.unparsed("y", String.valueOf((long) Math.floor(spot.y()))),
                            Placeholder.unparsed("z", String.valueOf((long) Math.floor(spot.z()))));
                }
            });
        });
    }

    private void summon(CommandSender sender) {
        async(sender, () -> {
            if (service.repository().spots().isEmpty()) {
                reply(sender, "merchant.no-spots");
            } else if (service.repository().requestSpawnNow()) {
                service.tick();
                reply(sender, "merchant.summoned");
            } else {
                reply(sender, "merchant.already-active");
            }
        });
    }

    private void dismiss(CommandSender sender) {
        async(sender, () -> {
            if (service.repository().requestDespawnNow()) {
                service.tick();
                reply(sender, "merchant.dismissed");
            } else {
                reply(sender, "merchant.none");
            }
        });
    }

    private void async(CommandSender sender, SqlTask task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "떠돌이 상인 명령 처리 실패", e);
                reply(sender, "merchant.error");
            }
        });
    }

    private void reply(CommandSender sender, String key, TagResolver... placeholders) {
        Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, key, placeholders));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission(ADMIN)) {
            return SUBCOMMANDS.stream().filter(sub -> sub.startsWith(args[0])).toList();
        }
        return List.of();
    }
}
