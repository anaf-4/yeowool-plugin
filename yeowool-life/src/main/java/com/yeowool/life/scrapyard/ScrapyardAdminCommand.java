package com.yeowool.life.scrapyard;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.util.OfflinePlayerResolver;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/**
 * {@code /폐기장설정} — OP가 폐기장의 물리적 위치들을 인게임에서 직접 등록.
 * 진입점/출구/상자/몹스폰은 바라보고 있는 블록을, 진입목적지/귀환지점/구역
 * 모서리는 지금 서 있는 위치를 등록한다. 진입점(포탈 트리거 블록)과
 * 진입목적지(실제로 순간이동할 위치 — 별도 던전 월드 안)는 서로 다른 곳이라
 * 따로 등록해야 함: 진입점에서 우클릭하면 진입목적지로 순간이동한다.
 * {@code 초기화 <이름>}만 예외적으로 위치가 필요 없어서 콘솔에서도 실행 가능.
 */
public final class ScrapyardAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "진입점", "진입목적지", "출구", "귀환지점", "구역1", "구역2", "상자추가", "상자제거", "몹스폰추가", "몹스폰제거", "보스스폰", "초기화", "정보");

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final ScrapyardSessionManager sessionManager;
    private final ScrapyardLocationStore locationStore;

    public ScrapyardAdminCommand(JavaPlugin plugin, YeowoolCoreAPI core, ScrapyardSessionManager sessionManager, ScrapyardLocationStore locationStore) {
        this.plugin = plugin;
        this.core = core;
        this.sessionManager = sessionManager;
        this.locationStore = locationStore;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("사용법: /폐기장설정 <" + String.join("/", SUBCOMMANDS) + ">", NamedTextColor.YELLOW));
            return true;
        }
        if (args[0].equals("초기화")) {
            resetLock(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용할 수 있습니다.", NamedTextColor.RED));
            return true;
        }

        switch (args[0]) {
            case "진입점" -> setLookedAtPoint(player, ScrapyardLocationStore.ENTRY, "진입점");
            case "진입목적지" -> {
                locationStore.setPoint(ScrapyardLocationStore.ENTRY_DESTINATION, player.getLocation());
                player.sendMessage(Component.text("진입목적지를 현재 위치로 설정했습니다.", NamedTextColor.GREEN));
            }
            case "출구" -> setLookedAtPoint(player, ScrapyardLocationStore.EXIT, "출구");
            case "귀환지점" -> {
                locationStore.setPoint(ScrapyardLocationStore.RETURN, player.getLocation());
                player.sendMessage(Component.text("귀환지점을 현재 위치로 설정했습니다.", NamedTextColor.GREEN));
            }
            case "구역1" -> {
                locationStore.setPoint(ScrapyardLocationStore.REGION_MIN, player.getLocation());
                player.sendMessage(Component.text("구역 모서리 1을 현재 위치로 설정했습니다.", NamedTextColor.GREEN));
            }
            case "구역2" -> {
                locationStore.setPoint(ScrapyardLocationStore.REGION_MAX, player.getLocation());
                player.sendMessage(Component.text("구역 모서리 2를 현재 위치로 설정했습니다.", NamedTextColor.GREEN));
            }
            case "상자추가" -> addChest(player);
            case "상자제거" -> removeChest(player);
            case "몹스폰추가" -> addMobSpawn(player);
            case "몹스폰제거" -> removeMobSpawn(player);
            case "보스스폰" -> {
                locationStore.setPoint(ScrapyardLocationStore.BOSS_SPAWN, player.getLocation());
                player.sendMessage(Component.text("보스 스폰 지점을 현재 위치로 설정했습니다.", NamedTextColor.GREEN));
            }
            case "정보" -> info(player);
            default -> player.sendMessage(Component.text("알 수 없는 하위 명령어입니다.", NamedTextColor.RED));
        }
        return true;
    }

    private void setLookedAtPoint(Player player, String category, String label) {
        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            player.sendMessage(Component.text("6블록 이내의 블록을 바라보고 있어야 합니다.", NamedTextColor.RED));
            return;
        }
        Location location = target.getLocation().add(0.5, 0.5, 0.5);
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(0);
        locationStore.setPoint(category, location);
        player.sendMessage(Component.text(label + "을(를) 바라보던 블록 위치로 설정했습니다.", NamedTextColor.GREEN));
    }

    private void addChest(Player player) {
        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            player.sendMessage(Component.text("6블록 이내의 블록을 바라보고 있어야 합니다.", NamedTextColor.RED));
            return;
        }
        locationStore.addChest(target.getLocation());
        player.sendMessage(Component.text("바라보던 블록을 폐기물 상자로 등록했습니다.", NamedTextColor.GREEN));
    }

    private void removeChest(Player player) {
        Block target = player.getTargetBlockExact(6);
        if (target == null || !locationStore.removeChestAt(target.getLocation())) {
            player.sendMessage(Component.text("바라보는 블록은 등록된 상자가 아닙니다.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("상자 등록을 해제했습니다.", NamedTextColor.GREEN));
    }

    private void addMobSpawn(Player player) {
        locationStore.addMobSpawn(player.getLocation());
        player.sendMessage(Component.text("현재 위치를 몹 스폰 지점으로 등록했습니다.", NamedTextColor.GREEN));
    }

    private void removeMobSpawn(Player player) {
        Block target = player.getTargetBlockExact(6);
        if (target == null || !locationStore.removeMobSpawnAt(target.getLocation())) {
            player.sendMessage(Component.text("바라보는 블록 위치는 등록된 몹 스폰 지점이 아닙니다.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("몹 스폰 지점을 제거했습니다.", NamedTextColor.GREEN));
    }

    private void info(Player player) {
        player.sendMessage(Component.text("=== 폐기장 설정 현황 ===", NamedTextColor.GOLD));
        player.sendMessage(status("진입점", locationStore.point(ScrapyardLocationStore.ENTRY).isPresent()));
        player.sendMessage(status("진입목적지", locationStore.point(ScrapyardLocationStore.ENTRY_DESTINATION).isPresent()));
        player.sendMessage(status("출구", locationStore.point(ScrapyardLocationStore.EXIT).isPresent()));
        player.sendMessage(status("귀환지점", locationStore.point(ScrapyardLocationStore.RETURN).isPresent()));
        player.sendMessage(status("구역", locationStore.hasRegion()));
        player.sendMessage(Component.text("상자: " + locationStore.chests().size() + "개", NamedTextColor.GRAY));
        player.sendMessage(Component.text("몹 스폰 지점: " + locationStore.mobSpawns().size() + "개", NamedTextColor.GRAY));
        player.sendMessage(status("보스 스폰 지점", locationStore.point(ScrapyardLocationStore.BOSS_SPAWN).isPresent()));
    }

    private Component status(String label, boolean set) {
        return Component.text(label + ": " + (set ? "설정됨" : "미설정"), set ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    /** {@code /폐기장설정 초기화 <이름>} — 오늘 이미 입장한 사람만 다시 입장할 수 있게 오늘의 입장 기록을 지운다. */
    private void resetLock(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(Component.text("사용법: /폐기장설정 초기화 <이름>", NamedTextColor.YELLOW));
            return;
        }
        String targetName = args[1];
        OfflinePlayerResolver.resolve(plugin, targetName, target ->
                core.playerData().load(target.getUniqueId(), targetName).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!sessionManager.isLockedToday(target.getUniqueId())) {
                        sender.sendMessage(Component.text(targetName + "님은 오늘 아직 폐기장에 입장하지 않았습니다.", NamedTextColor.RED));
                        return;
                    }
                    sessionManager.resetLockToday(core.playerData().getOnline(target.getUniqueId()));
                    sender.sendMessage(Component.text(targetName + "님의 오늘 폐기장 입장 기록을 초기화했습니다. 다시 입장할 수 있습니다.", NamedTextColor.GREEN));
                })),
                () -> sender.sendMessage(Component.text("존재하지 않는 플레이어입니다: " + targetName, NamedTextColor.RED)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2 && args[0].equals("초기화")) {
            List<String> lockedToday = Bukkit.getOnlinePlayers().stream()
                    .filter(online -> sessionManager.isLockedToday(online.getUniqueId()))
                    .map(Player::getName)
                    .toList();
            return TabCompletions.filter(lockedToday, args[1]);
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
