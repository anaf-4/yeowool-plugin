package com.yeowool.teleport.rtp;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PlayerData;
import com.yeowool.teleport.TeleportService;
import com.yeowool.teleport.playerwarp.PlayerWarpSafety;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Random;

/**
 * 무작위 순간이동 — 현재 월드 스폰 기준 반경 내 무작위 좌표를 뽑아
 * {@link PlayerWarpSafety}로 안전한 지점을 찾고, 공용 {@link TeleportService}로
 * 이동시킨다. 모든 순간이동이 공유하는 짧은 일반 쿨다운과 별개로, RTP 자체의
 * 더 긴 쿨다운을 {@link PlayerData} 설정값으로 관리한다.
 */
public final class RtpManager {

    private static final String LAST_TELEPORT_SETTING = "rtp.last-teleport";
    private static final int MAX_ATTEMPTS = 10;

    private final YeowoolCoreAPI core;
    private final TeleportService teleportService;
    private final RtpConfig config;
    private final Random random = new Random();

    public RtpManager(YeowoolCoreAPI core, TeleportService teleportService, RtpConfig config) {
        this.core = core;
        this.teleportService = teleportService;
        this.config = config;
    }

    public record CooldownStatus(boolean available, long remainingSeconds) {
    }

    public CooldownStatus cooldownStatus(Player player) {
        PlayerData data = core.playerData().getOnline(player.getUniqueId());
        long lastTeleport = parseLongOr(data.getSetting(LAST_TELEPORT_SETTING, "0"), 0);
        long elapsedMillis = System.currentTimeMillis() - lastTeleport;
        long cooldownMillis = config.cooldownSeconds() * 1000L;
        if (elapsedMillis >= cooldownMillis) {
            return new CooldownStatus(true, 0);
        }
        return new CooldownStatus(false, (cooldownMillis - elapsedMillis + 999) / 1000);
    }

    /** {@link #MAX_ATTEMPTS}번 시도 안에 안전한 지점을 못 찾으면 empty. */
    public Optional<Location> findRandomSafeLocation(World world) {
        int radius = config.radiusFor(world.getEnvironment());
        int minRadius = Math.min(config.minRadius(), radius);
        Location center = world.getSpawnLocation();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = minRadius + random.nextDouble() * (radius - minRadius);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt(x, z);
            Optional<Location> safe = PlayerWarpSafety.findSafe(new Location(world, x + 0.5, y, z + 0.5));
            if (safe.isPresent()) {
                return safe;
            }
        }
        return Optional.empty();
    }

    /** 쿨다운을 기록하고 실제 이동은 공용 {@link TeleportService}에 위임한다. */
    public void teleport(Player player, Location destination) {
        PlayerData data = core.playerData().getOnline(player.getUniqueId());
        data.setSetting(LAST_TELEPORT_SETTING, String.valueOf(System.currentTimeMillis()));
        teleportService.requestTeleport(player, destination);
    }

    private static long parseLongOr(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
