package com.yeowool.raid.worldboss;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** World boss reward and schedule math, kept free of Bukkit so it can be unit tested. */
public final class WorldBossRules {

    /** {@code rank} is 1.. for ranked rewards, 0 for participation only; {@code amount} includes participation. */
    public record Reward(UUID player, int rank, long amount) {
    }

    private static final Comparator<Map.Entry<UUID, Double>> BY_DAMAGE =
            Map.Entry.<UUID, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey());

    private WorldBossRules() {
    }

    /**
     * Everyone who dealt at least {@code minDamageShare} of the boss's max health gets the
     * participation reward; the top {@code rankRewards.size()} of them also get their rank reward.
     */
    public static List<Reward> rewards(Map<UUID, Double> damage, double maxHealth, List<Long> rankRewards,
                                       long participationReward, double minDamageShare) {
        double threshold = maxHealth * minDamageShare;
        List<Map.Entry<UUID, Double>> qualified = damage.entrySet().stream()
                .filter(entry -> entry.getValue() > 0 && entry.getValue() >= threshold)
                .sorted(BY_DAMAGE)
                .toList();
        List<Reward> rewards = new ArrayList<>();
        for (int i = 0; i < qualified.size(); i++) {
            boolean ranked = i < rankRewards.size();
            long amount = participationReward + (ranked ? rankRewards.get(i) : 0);
            rewards.add(new Reward(qualified.get(i).getKey(), ranked ? i + 1 : 0, amount));
        }
        return rewards;
    }

    public static List<UUID> topByDamage(Map<UUID, Double> damage, int limit) {
        return damage.entrySet().stream().sorted(BY_DAMAGE).limit(limit).map(Map.Entry::getKey).toList();
    }

    /**
     * Today's spawn time, if today's event hasn't happened yet ({@code lastEventDate} ≠ today) and
     * {@code now} is inside [spawn − announce, spawn + fight). Spawn time must be at least
     * {@code announceMinutes} after midnight.
     */
    public static Optional<ZonedDateTime> announceDue(ZonedDateTime now, LocalTime spawnTime, int announceMinutes,
                                                     int fightMinutes, String lastEventDate) {
        ZonedDateTime spawnAt = now.toLocalDate().atTime(spawnTime).atZone(now.getZone());
        if (spawnAt.toLocalDate().toString().equals(lastEventDate)) {
            return Optional.empty();
        }
        boolean open = !now.isBefore(spawnAt.minusMinutes(announceMinutes)) && now.isBefore(spawnAt.plusMinutes(fightMinutes));
        return open ? Optional.of(spawnAt) : Optional.empty();
    }
}
