package com.yeowool.teleport.home;

import com.yeowool.teleport.model.Home;
import com.yeowool.teleport.repository.HomeRepository;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Homes are cached per-player, loaded on join and unloaded shortly after
 * quit (see {@link HomeJoinListener}) — unlike server-wide warps, there's no
 * benefit to holding every player's homes in memory at once.
 */
public final class HomeManager {

    private final JavaPlugin plugin;
    private final HomeRepository repository;
    private final ExecutorService executor;
    private final int maxHomes;

    private final Map<UUID, Map<String, Home>> cache = new ConcurrentHashMap<>();

    public HomeManager(JavaPlugin plugin, HomeRepository repository, ExecutorService executor, int maxHomes) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
        this.maxHomes = maxHomes;
    }

    public CompletableFuture<Void> load(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try {
                Map<String, Home> homes = new ConcurrentHashMap<>();
                for (Home home : repository.loadHomes(uuid)) {
                    homes.put(home.name().toLowerCase(), home);
                }
                cache.put(uuid, homes);
            } catch (SQLException e) {
                plugin.getLogger().severe("홈 데이터 로드 실패 (" + uuid + "): " + e.getMessage());
                cache.put(uuid, new ConcurrentHashMap<>());
            }
        }, executor);
    }

    public void unload(UUID uuid) {
        cache.remove(uuid);
    }

    public Map<String, Home> homesOf(UUID uuid) {
        return cache.getOrDefault(uuid, Map.of());
    }

    public Optional<Home> get(UUID uuid, String name) {
        return Optional.ofNullable(homesOf(uuid).get(name.toLowerCase()));
    }

    public enum SetResult { SUCCESS, LIMIT_REACHED }

    public SetResult set(UUID uuid, String name, Location location) {
        Map<String, Home> homes = cache.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>());
        String key = name.toLowerCase();
        if (!homes.containsKey(key) && homes.size() >= maxHomes) {
            return SetResult.LIMIT_REACHED;
        }
        Home home = Home.of(name, location);
        homes.put(key, home);
        executor.execute(() -> {
            try {
                repository.upsert(uuid, home);
            } catch (SQLException e) {
                plugin.getLogger().severe("홈 저장 실패 (" + uuid + "/" + name + "): " + e.getMessage());
            }
        });
        return SetResult.SUCCESS;
    }

    public boolean delete(UUID uuid, String name) {
        Map<String, Home> homes = cache.get(uuid);
        if (homes == null) {
            return false;
        }
        Home removed = homes.remove(name.toLowerCase());
        if (removed == null) {
            return false;
        }
        executor.execute(() -> {
            try {
                repository.delete(uuid, removed.name());
            } catch (SQLException e) {
                plugin.getLogger().severe("홈 삭제 실패 (" + uuid + "/" + name + "): " + e.getMessage());
            }
        });
        return true;
    }
}
