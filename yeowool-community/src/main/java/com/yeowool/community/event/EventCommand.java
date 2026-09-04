package com.yeowool.community.event;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/**
 * {@code /이벤트 시작\|종료\|정보\|보상받기} — see {@link EventManager}. The
 * claimable reward (on + items) is one fixed set from config.yml rather
 * than per-event custom loot, to keep this a buildable slice of the plugin
 * plan's full "이벤트 아이템/보상" vision.
 */
public final class EventCommand implements CommandExecutor, TabCompleter {

    private static final String CLAIM_SETTING_PREFIX = "event.claimed.";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final EventManager eventManager;
    private final MissionEventManager missionManager;

    public EventCommand(JavaPlugin plugin, YeowoolCoreAPI core, EventManager eventManager, MissionEventManager missionManager) {
        this.plugin = plugin;
        this.core = core;
        this.eventManager = eventManager;
        this.missionManager = missionManager;
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
            case "보상받기" -> claim(sender);
            case "미션시작" -> missionStart(sender, args);
            case "미션종료" -> missionStop(sender);
            case "미션정보" -> missionInfo(sender);
            default -> sender.sendMessage(Component.text("사용법: /이벤트 [시작|종료|정보|보상받기|미션시작|미션종료|미션정보]", NamedTextColor.RED));
        }
        return true;
    }

    private void missionStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yeowool.event.manage")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return;
        }
        if (args.length != 5) {
            sender.sendMessage(Component.text("사용법: /이벤트 미션시작 <처치|수집> <대상(EntityType 또는 Material)> <목표수> <분>", NamedTextColor.RED));
            return;
        }
        MissionEventManager.Kind kind = switch (args[1]) {
            case "처치" -> MissionEventManager.Kind.KILL;
            case "수집" -> MissionEventManager.Kind.COLLECT;
            default -> null;
        };
        if (kind == null) {
            sender.sendMessage(Component.text("종류는 처치 또는 수집이어야 합니다.", NamedTextColor.RED));
            return;
        }
        boolean validTarget = kind == MissionEventManager.Kind.KILL
                ? isValidEnum(org.bukkit.entity.EntityType.class, args[2])
                : isValidEnum(Material.class, args[2]);
        if (!validTarget) {
            sender.sendMessage(Component.text("대상 ID가 올바르지 않습니다: " + args[2], NamedTextColor.RED));
            return;
        }
        int goal;
        int minutes;
        try {
            goal = Integer.parseInt(args[3]);
            minutes = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("목표수/분은 숫자여야 합니다.", NamedTextColor.RED));
            return;
        }
        if (goal <= 0 || minutes <= 0) {
            sender.sendMessage(Component.text("목표수와 분은 0보다 커야 합니다.", NamedTextColor.RED));
            return;
        }
        missionManager.start(kind, args[2].toUpperCase(), goal, minutes);
    }

    private boolean isValidEnum(Class<? extends Enum<?>> enumClass, String name) {
        for (Enum<?> constant : enumClass.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private void missionStop(CommandSender sender) {
        if (!sender.hasPermission("yeowool.event.manage")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return;
        }
        if (!missionManager.isActive()) {
            sender.sendMessage(Component.text("진행 중인 미션 이벤트가 없습니다.", NamedTextColor.RED));
            return;
        }
        missionManager.stop(true);
    }

    private void missionInfo(CommandSender sender) {
        var active = missionManager.active();
        if (active.isEmpty()) {
            sender.sendMessage(Component.text("진행 중인 미션 이벤트가 없습니다.", NamedTextColor.GRAY));
            return;
        }
        var mission = active.get();
        long remainingMs = mission.endAtMillis() - System.currentTimeMillis();
        String verb = mission.kind() == MissionEventManager.Kind.KILL ? "처치" : "수집";
        sender.sendMessage(Component.text("미션: " + mission.targetId() + " " + mission.goal() + "개 " + verb
                + " (남은 시간: " + com.yeowool.core.util.DurationFormat.humanize(Math.max(0, remainingMs)) + ")", NamedTextColor.GOLD));
        if (sender instanceof Player player) {
            sender.sendMessage(Component.text("내 진행도: " + missionManager.progressFor(player.getUniqueId()) + "/" + mission.goal(), NamedTextColor.YELLOW));
        }
    }

    private void start(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yeowool.event.manage")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return;
        }
        if (args.length != 4) {
            sender.sendMessage(Component.text("사용법: /이벤트 시작 <이름> <배율> <분>", NamedTextColor.RED));
            return;
        }
        double multiplier;
        int minutes;
        try {
            multiplier = Double.parseDouble(args[2]);
            minutes = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("배율/분은 숫자여야 합니다.", NamedTextColor.RED));
            return;
        }
        if (multiplier <= 0 || minutes <= 0) {
            sender.sendMessage(Component.text("배율과 분은 0보다 커야 합니다.", NamedTextColor.RED));
            return;
        }
        eventManager.start(args[1], multiplier, minutes);
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
        sender.sendMessage(Component.text("이벤트: " + active.get().name() + " (경험치 " + active.get().multiplier()
                + "배, 남은 시간: " + com.yeowool.core.util.DurationFormat.humanize(Math.max(0, remainingMs)) + ")", NamedTextColor.GOLD));
    }

    private void claim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용할 수 있습니다.", NamedTextColor.RED));
            return;
        }
        var active = eventManager.active();
        if (active.isEmpty()) {
            player.sendMessage(Component.text("진행 중인 이벤트가 없습니다.", NamedTextColor.RED));
            return;
        }
        String settingKey = CLAIM_SETTING_PREFIX + active.get().name();
        var data = core.playerData().getOnline(player.getUniqueId());
        if (data.getSetting(settingKey, "false").equals("true")) {
            player.sendMessage(Component.text("이미 이번 이벤트 보상을 받았습니다.", NamedTextColor.RED));
            return;
        }
        data.setSetting(settingKey, "true");

        long rewardOn = plugin.getConfig().getLong("event.reward-on", 0);
        if (rewardOn > 0) {
            core.economyData().modifyBalance(player.getUniqueId(), rewardOn, "YeowoolCommunity",
                    "이벤트 보상: " + active.get().name());
        }
        for (Map<?, ?> entry : plugin.getConfig().getMapList("event.reward-items")) {
            try {
                Material material = Material.valueOf(entry.get("material").toString());
                int amount = ((Number) entry.get("amount")).intValue();
                var leftover = player.getInventory().addItem(new ItemStack(material, amount));
                leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            } catch (Exception e) {
                plugin.getLogger().warning("event.reward-items 항목이 잘못되었습니다: " + entry);
            }
        }

        core.sounds().play(player, "success");
        player.sendMessage(Component.text("이벤트 보상을 받았습니다!", NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("시작", "종료", "정보", "보상받기", "미션시작", "미션종료", "미션정보"), args[0]);
        }
        if (args.length == 2 && args[0].equals("미션시작")) {
            return TabCompletions.filter(List.of("처치", "수집"), args[1]);
        }
        return List.of();
    }
}
