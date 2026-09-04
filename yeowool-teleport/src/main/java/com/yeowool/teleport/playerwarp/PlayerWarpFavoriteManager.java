package com.yeowool.teleport.playerwarp;

import com.yeowool.teleport.repository.PlayerWarpFavoriteRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/** {@code player -> {"owner:name", ...}} favorited-warps cache, backing {@link PlayerWarpSavedGui}. */
public final class PlayerWarpFavoriteManager {

    private final JavaPlugin plugin;
    private final PlayerWarpFavoriteRepository repository;
    private final ExecutorService executor;

    private final Map<UUID, Set<String>> byPlayer = new ConcurrentHashMap<>();

    public PlayerWarpFavoriteManager(JavaPlugin plugin, PlayerWarpFavoriteRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    public void loadAll() throws SQLException {
        int count = 0;
        for (var row : repository.loadAll()) {
            byPlayer.computeIfAbsent(row.player(), k -> ConcurrentHashMap.newKeySet()).add(key(row.owner(), row.warpName()));
            count++;
        }
        plugin.getLogger().info("플레이어 워프 즐겨찾기 " + count + "건을 불러왔습니다.");
    }

    private static String key(UUID owner, String warpName) {
        return owner + ":" + warpName.toLowerCase();
    }

    public boolean isFavorite(UUID player, UUID owner, String warpName) {
        var set = byPlayer.get(player);
        return set != null && set.contains(key(owner, warpName));
    }

    public Set<String> favoritesOf(UUID player) {
        return byPlayer.getOrDefault(player, Set.of());
    }

    public void add(UUID player, UUID owner, String warpName) {
        byPlayer.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(key(owner, warpName));
        long now = System.currentTimeMillis();
        executor.execute(() -> {
            try {
                repository.add(player, owner, warpName, now);
            } catch (SQLException e) {
                plugin.getLogger().severe("즐겨찾기 저장 실패 (" + player + "/" + owner + "/" + warpName + "): " + e.getMessage());
            }
        });
    }

    public void remove(UUID player, UUID owner, String warpName) {
        var set = byPlayer.get(player);
        if (set != null) {
            set.remove(key(owner, warpName));
        }
        executor.execute(() -> {
            try {
                repository.remove(player, owner, warpName);
            } catch (SQLException e) {
                plugin.getLogger().severe("즐겨찾기 삭제 실패 (" + player + "/" + owner + "/" + warpName + "): " + e.getMessage());
            }
        });
    }

    public void toggle(UUID player, UUID owner, String warpName) {
        if (isFavorite(player, owner, warpName)) {
            remove(player, owner, warpName);
        } else {
            add(player, owner, warpName);
        }
    }
}
