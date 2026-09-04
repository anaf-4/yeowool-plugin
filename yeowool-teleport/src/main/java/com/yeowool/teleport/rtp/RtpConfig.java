package com.yeowool.teleport.rtp;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * {@code config.yml}'s {@code rtp} section — 차원별 반경, 자체 쿨다운, GUI 배경 오프셋. {@code enabled}는
 * 서버별로 RTP 자체를 켜고 끄는 스위치 — 로비처럼 야생 월드가 없는 서버는 이걸 false로 배포함.
 */
public record RtpConfig(boolean enabled, long cooldownSeconds, int minRadius, int overworldRadius, int netherRadius,
                         int endRadius, int backgroundOffsetPx) {

    public static RtpConfig load(FileConfiguration config) {
        var root = config.getConfigurationSection("rtp");
        if (root == null) {
            return defaults();
        }
        return new RtpConfig(
                root.getBoolean("enabled", true),
                root.getLong("cooldown-seconds", 300),
                root.getInt("min-radius", 100),
                root.getInt("radius.overworld", 5000),
                root.getInt("radius.nether", 1000),
                root.getInt("radius.end", 2000),
                root.getInt("gui-background-offset", -8)
        );
    }

    private static RtpConfig defaults() {
        return new RtpConfig(true, 300, 100, 5000, 1000, 2000, -8);
    }

    public int radiusFor(World.Environment environment) {
        return switch (environment) {
            case NETHER -> netherRadius;
            case THE_END -> endRadius;
            default -> overworldRadius;
        };
    }
}
