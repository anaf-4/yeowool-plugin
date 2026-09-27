package com.yeowool.life.surprise;

import com.yeowool.core.api.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/** {@code /깜짝이벤트}: status for everyone; start/end for staff on participating servers. */
public final class SurpriseEventCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "yeowool.event.manage";
    private static final List<String> TYPE_INPUTS = Arrays.stream(SurpriseEventType.values())
            .map(type -> type.label().replace(" ", "")).toList();

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final SurpriseEventService service;
    private final Executor executor;
    private final boolean participating;

    public SurpriseEventCommand(JavaPlugin plugin, MessageService messages, SurpriseEventService service,
                                Executor executor, boolean participating) {
        this.plugin = plugin;
        this.messages = messages;
        this.service = service;
        this.executor = executor;
        this.participating = participating;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!participating) {
            messages.send(sender, "surprise.not-here");
            return true;
        }
        if (args.length == 0) {
            service.status(sender);
            return true;
        }
        if (!sender.hasPermission(ADMIN)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        switch (args[0]) {
            case "시작" -> {
                Optional<SurpriseEventType> requested = Optional.empty();
                if (args.length >= 2) {
                    requested = SurpriseEventType.parse(args[1]);
                    if (requested.isEmpty()) {
                        messages.send(sender, "surprise.unknown-type");
                        return true;
                    }
                }
                Optional<SurpriseEventType> type = requested;
                async(sender, () -> reply(sender, service.forceStart(type) ? "surprise.admin-started" : "surprise.already-active"));
            }
            case "종료" -> async(sender, () -> reply(sender, service.forceEnd() ? "surprise.admin-ended" : "surprise.none"));
            default -> messages.send(sender, "surprise.usage");
        }
        return true;
    }

    @FunctionalInterface
    private interface SqlTask {
        void run() throws SQLException;
    }

    private void async(CommandSender sender, SqlTask task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "깜짝 이벤트 명령 처리 실패", e);
                reply(sender, "surprise.error");
            }
        });
    }

    private void reply(CommandSender sender, String key) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, key));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!participating || !sender.hasPermission(ADMIN)) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("시작", "종료").stream().filter(s -> s.startsWith(args[0])).toList();
        }
        if (args.length == 2 && args[0].equals("시작")) {
            return TYPE_INPUTS.stream().filter(s -> s.startsWith(args[1])).toList();
        }
        return List.of();
    }
}
