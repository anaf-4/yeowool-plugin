package com.yeowool.federation.event;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

/** {@code /연합대항 [정보|시작 <분>|종료]} — 정보 for everyone, 시작/종료 need yeowool.event.manage (same as /마을대항). */
public final class FederationEventCommand implements CommandExecutor {

    private static final String MANAGE_PERMISSION = "yeowool.event.manage";

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final FederationEventService service;
    private final ExecutorService executor;

    public FederationEventCommand(JavaPlugin plugin, MessageService messages, FederationEventService service,
                                  ExecutorService executor) {
        this.plugin = plugin;
        this.messages = messages;
        this.service = service;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equals("정보")) {
            showStatus(sender);
            return true;
        }
        switch (args[0]) {
            case "시작" -> start(sender, args);
            case "종료" -> end(sender);
            default -> messages.send(sender, "federation.event-usage");
        }
        return true;
    }

    private void showStatus(CommandSender sender) {
        executor.execute(() -> {
            try {
                var status = service.status();
                runOnMain(() -> {
                    if (status.isEmpty()) {
                        messages.send(sender, "federation.event-none");
                        return;
                    }
                    long minutes = (status.get().remainingMillis() + 59_999) / 60_000;
                    messages.send(sender, "federation.event-status-header", Placeholder.unparsed("minutes", String.valueOf(minutes)));
                    List<EventStanding> top = status.get().top();
                    if (top.isEmpty()) {
                        messages.send(sender, "federation.event-status-empty");
                        return;
                    }
                    for (int i = 0; i < top.size(); i++) {
                        messages.send(sender, "federation.event-status-line",
                                Placeholder.unparsed("rank", String.valueOf(i + 1)),
                                Placeholder.unparsed("name", top.get(i).name()),
                                Placeholder.unparsed("gained", String.format("%,d", top.get(i).gained())));
                    }
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "연합대항 현황 조회 실패", e);
            }
        });
    }

    private void start(CommandSender sender, String[] args) {
        if (!sender.hasPermission(MANAGE_PERMISSION)) {
            messages.send(sender, "federation.event-no-permission");
            return;
        }
        int minutes;
        try {
            minutes = args.length == 2 ? Integer.parseInt(args[1]) : -1;
        } catch (NumberFormatException e) {
            minutes = -1;
        }
        if (minutes < 1) {
            messages.send(sender, "federation.event-start-usage");
            return;
        }
        int duration = minutes;
        executor.execute(() -> {
            try {
                if (service.start(duration) == FederationEventService.StartResult.ALREADY_RUNNING) {
                    runOnMain(() -> messages.send(sender, "federation.event-already-running"));
                    return;
                }
                List<Announcement> announcements = service.tick(ZonedDateTime.now());
                runOnMain(() -> service.broadcast(announcements));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "연합대항 시작 실패", e);
            }
        });
    }

    private void end(CommandSender sender) {
        if (!sender.hasPermission(MANAGE_PERMISSION)) {
            messages.send(sender, "federation.event-no-permission");
            return;
        }
        executor.execute(() -> {
            try {
                if (!service.endNow()) {
                    runOnMain(() -> messages.send(sender, "federation.event-none"));
                    return;
                }
                List<Announcement> announcements = service.tick(ZonedDateTime.now());
                runOnMain(() -> {
                    messages.send(sender, "federation.event-ended-by-admin");
                    service.broadcast(announcements);
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "연합대항 종료 실패", e);
            }
        });
    }

    private void runOnMain(Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
    }
}
