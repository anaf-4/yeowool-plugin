package com.yeowool.admin.raffle;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Tracks, per item identity (see {@link RaffleItemIdentity}), how many times
 * each player has already won that specific item across every past
 * {@code /추첨} draw — {@link #weightFor} turns that into a shrinking (but
 * never zero) draw weight, per "동일한 아이템... 확률이 줄어드는". A player who
 * has never won a given item simply isn't in that item's map, so a brand new
 * item id starts everyone at equal weight automatically — no explicit
 * "reset" needed for "다른 아이템을 추첨하면... 균등하게".
 */
public final class RaffleManager {

    private final JavaPlugin plugin;
    private final RaffleRepository repository;
    private final ExecutorService executor;
    private final double repeatWeightMultiplier;
    private final Map<String, Map<UUID, Integer>> history = new ConcurrentHashMap<>();

    public RaffleManager(JavaPlugin plugin, RaffleRepository repository, ExecutorService executor, double repeatWeightMultiplier) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
        this.repeatWeightMultiplier = repeatWeightMultiplier;
    }

    public void loadAll() throws SQLException {
        history.putAll(repository.loadAll());
        int totalRows = history.values().stream().mapToInt(Map::size).sum();
        plugin.getLogger().info("추첨 당첨 이력 " + totalRows + "건을 불러왔습니다.");
    }

    /** {@code repeatWeightMultiplier ^ (그 아이템에서 이미 당첨된 횟수)} — 한 번도 당첨된 적 없으면 1.0. */
    public double weightFor(String itemId, UUID uuid) {
        int wins = history.getOrDefault(itemId, Map.of()).getOrDefault(uuid, 0);
        return Math.pow(repeatWeightMultiplier, wins);
    }

    public void recordWin(String itemId, UUID uuid) {
        history.computeIfAbsent(itemId, k -> new HashMap<>()).merge(uuid, 1, Integer::sum);
        executor.execute(() -> {
            try {
                repository.incrementWin(itemId, uuid);
            } catch (SQLException e) {
                plugin.getLogger().severe("추첨 당첨 이력 저장 실패 (" + itemId + "/" + uuid + "): " + e.getMessage());
            }
        });
    }
}
