package com.yeowool.land.villageevent;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import com.yeowool.land.LandManager;
import com.yeowool.land.command.LandCommand;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * {@code /마을대항 [시작 <분>|종료|정보]} — 시작/종료는 {@code yeowool.event.manage}
 * 재사용(기존 {@code /이벤트}·미션 이벤트와 같은 권한), 정보는 누구나 가능.
 */
public final class VillageEventCommand implements CommandExecutor, TabCompleter {

    private static final int STANDINGS_TOP_N = 5;

    private final LandManager landManager;
    private final VillageEventManager eventManager;
    private final MessageService messages;

    public VillageEventCommand(LandManager landManager, VillageEventManager eventManager, MessageService messages) {
        this.landManager = landManager;
        this.eventManager = eventManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            info(sender);
            return true;
        }
        switch (args[0]) {
            case "시작" -> start(sender, args);
            case "종료" -> stop(sender);
            case "정보" -> info(sender);
            default -> messages.send(sender, "village-event.usage");
        }
        return true;
    }

    private void start(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yeowool.event.manage")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (args.length != 2) {
            messages.send(sender, "village-event.start-usage");
            return;
        }
        int minutes;
        try {
            minutes = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            messages.send(sender, "general.invalid-amount");
            return;
        }
        if (minutes <= 0) {
            messages.send(sender, "general.invalid-amount");
            return;
        }
        if (!eventManager.start(minutes)) {
            messages.send(sender, "village-event.already-active");
        }
    }

    private void stop(CommandSender sender) {
        if (!sender.hasPermission("yeowool.event.manage")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (!eventManager.endEarly()) {
            messages.send(sender, "village-event.not-active");
        }
    }

    private void info(CommandSender sender) {
        if (!eventManager.isActive()) {
            messages.send(sender, "village-event.not-active");
            return;
        }
        long remainingSeconds = eventManager.remainingMillis() / 1000;
        messages.send(sender, "village-event.info-header",
                Placeholder.unparsed("minutes", String.valueOf(remainingSeconds / 60)),
                Placeholder.unparsed("seconds", String.valueOf(remainingSeconds % 60)));

        var standings = eventManager.standings(STANDINGS_TOP_N);
        if (standings.isEmpty()) {
            messages.send(sender, "village-event.info-empty");
            return;
        }
        int rank = 1;
        for (var entry : standings) {
            String village = landManager.getLandOwnedBy(entry.getKey())
                    .map(LandCommand::displayName)
                    .orElse("알 수 없음");
            String ownerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            messages.send(sender, "village-event.info-line",
                    Placeholder.unparsed("rank", String.valueOf(rank++)),
                    Placeholder.unparsed("village", village),
                    Placeholder.unparsed("owner", ownerName != null ? ownerName : "알 수 없음"),
                    Placeholder.unparsed("xp", String.format("%,d", entry.getValue())));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("시작", "종료", "정보"), args[0]);
        }
        return List.of();
    }
}
