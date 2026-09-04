package com.yeowool.teleport.playerwarp;

import com.yeowool.teleport.repository.PlayerWarpRatingRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/** {@code (owner,warpName) -> (rater -> 1..5)} ratings cache, backing {@link PlayerWarpReviewGui}'s star rating and the browse GUI's "평점순" sort. */
public final class PlayerWarpRatingManager {

    private final JavaPlugin plugin;
    private final PlayerWarpRatingRepository repository;
    private final ExecutorService executor;

    private final Map<String, Map<UUID, Integer>> ratings = new ConcurrentHashMap<>();

    public PlayerWarpRatingManager(JavaPlugin plugin, PlayerWarpRatingRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    public void loadAll() throws SQLException {
        int count = 0;
        for (var row : repository.loadAll()) {
            ratings.computeIfAbsent(key(row.owner(), row.warpName()), k -> new ConcurrentHashMap<>()).put(row.rater(), row.rating());
            count++;
        }
        plugin.getLogger().info("플레이어 워프 평점 " + count + "건을 불러왔습니다.");
    }

    private static String key(UUID owner, String warpName) {
        return owner + ":" + warpName.toLowerCase();
    }

    public double averageOf(UUID owner, String warpName) {
        var ratersMap = ratings.get(key(owner, warpName));
        if (ratersMap == null || ratersMap.isEmpty()) {
            return 0.0;
        }
        return ratersMap.values().stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    public int countOf(UUID owner, String warpName) {
        var ratersMap = ratings.get(key(owner, warpName));
        return ratersMap == null ? 0 : ratersMap.size();
    }

    public Integer myRating(UUID owner, String warpName, UUID rater) {
        var ratersMap = ratings.get(key(owner, warpName));
        return ratersMap == null ? null : ratersMap.get(rater);
    }

    public void rate(UUID owner, String warpName, UUID rater, int rating) {
        ratings.computeIfAbsent(key(owner, warpName), k -> new ConcurrentHashMap<>()).put(rater, rating);
        long now = System.currentTimeMillis();
        executor.execute(() -> {
            try {
                repository.upsert(owner, warpName, rater, rating, now);
            } catch (SQLException e) {
                plugin.getLogger().severe("플레이어 워프 평점 저장 실패 (" + owner + "/" + warpName + "): " + e.getMessage());
            }
        });
    }

    public void clearFor(UUID owner, String warpName) {
        ratings.remove(key(owner, warpName));
        executor.execute(() -> {
            try {
                repository.deleteForWarp(owner, warpName);
            } catch (SQLException e) {
                plugin.getLogger().severe("플레이어 워프 평점 삭제 실패 (" + owner + "/" + warpName + "): " + e.getMessage());
            }
        });
    }
}
