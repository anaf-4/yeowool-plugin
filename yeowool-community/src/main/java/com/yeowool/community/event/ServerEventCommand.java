package com.yeowool.community.event;

import com.yeowool.core.util.DurationFormat;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * {@code /서버이벤트 <XP|작물드랍> <배율>} — a quick preset alternative to
 * {@code /이벤트 시작 <이름> <배율> <분>} for the two boost types admins
 * actually use day-to-day: fixed 1시간 duration, no name to type out, console
 * usable (no player-only checks — a global multiplier doesn't need a
 * location). Shares {@link EventManager} with {@code /이벤트}, so starting
 * one replaces the other.
 */
public final class ServerEventCommand implements CommandExecutor, TabCompleter {

    private static final int DURATION_MINUTES = 60;

    private final EventManager eventManager;

    public ServerEventCommand(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            info(sender);
            return true;
        }

        switch (args[0]) {
            case "종료" -> stop(sender);
            case "정보" -> info(sender);
            default -> start(sender, args);
        }
        return true;
    }

    private void start(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yeowool.event.manage")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(Component.text("사용법: /서버이벤트 <XP|작물드랍|종료|정보> <배율>", NamedTextColor.RED));
            return;
        }
        EventManager.Type type = switch (args[0].toUpperCase()) {
            case "XP", "경험치" -> EventManager.Type.XP;
            case "작물드랍", "작물드롭" -> EventManager.Type.CROP_DROP;
            default -> null;
        };
        if (type == null) {
            sender.sendMessage(Component.text("사용법: /서버이벤트 <XP|작물드랍|종료|정보> <배율>", NamedTextColor.RED));
            return;
        }
        double multiplier;
        try {
            multiplier = Double.parseDouble(args[1].endsWith("배") ? args[1].substring(0, args[1].length() - 1) : args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("배율은 숫자여야 합니다 (예: 2, 2배).", NamedTextColor.RED));
            return;
        }
        if (multiplier <= 0) {
            sender.sendMessage(Component.text("배율은 0보다 커야 합니다.", NamedTextColor.RED));
            return;
        }
        eventManager.start(type.label() + " 이벤트", type, multiplier, DURATION_MINUTES);
    }

    private void stop(CommandSender sender) {
        if (!sender.hasPermission("yeowool.event.manage")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return;
        }
        if (!eventManager.isActive()) {
            sender.sendMessage(Component.text("진행 중인 이벤트가 없습니다.", NamedTextColor.RED));
            return;
        }
        eventManager.stop(true);
    }

    private void info(CommandSender sender) {
        var active = eventManager.active();
        if (active.isEmpty()) {
            sender.sendMessage(Component.text("진행 중인 이벤트가 없습니다.", NamedTextColor.GRAY));
            return;
        }
        long remainingMs = active.get().endAtMillis() - System.currentTimeMillis();
        sender.sendMessage(Component.text("이벤트: " + active.get().name() + " (" + active.get().type().label() + " "
                + active.get().multiplier() + "배, 남은 시간: " + DurationFormat.humanize(Math.max(0, remainingMs)) + ")",
                NamedTextColor.GOLD));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("XP", "작물드랍", "종료", "정보"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("XP") || args[0].equals("작물드랍"))) {
            return TabCompletions.filter(List.of("2배", "3배"), args[1]);
        }
        return List.of();
    }
}
