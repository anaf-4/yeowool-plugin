package com.yeowool.core.data;

import com.yeowool.core.api.model.PlayerData;
import com.yeowool.core.api.service.PlayerDataService;
import com.yeowool.core.data.repository.PlayerRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public final class PlayerDataServiceImpl implements PlayerDataService {

    private final JavaPlugin plugin;
    private final PlayerRepository repository;
    private final PlayerCache cache;
    private final ExecutorService executor;

    // In-flight loads, keyed by uuid, so a double join/command race only hits the DB once.
    private final ConcurrentHashMap<UUID, CompletableFuture<PlayerData>> pendingLoads = new ConcurrentHashMap<>();

    public PlayerDataServiceImpl(JavaPlugin plugin, PlayerRepository repository, PlayerCache cache, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.cache = cache;
        this.executor = executor;
    }

    @Override
    public Optional<PlayerData> getIfLoaded(UUID uuid) {
        return cache.get(uuid);
    }

    @Override
    public PlayerData getOnline(UUID uuid) {
        return cache.get(uuid).orElseThrow(() ->
                new NoSuchElementException("PlayerData for " + uuid + " is not loaded; is the player online?"));
    }

    @Override
    public CompletableFuture<PlayerData> load(UUID uuid, String username) {
        Optional<PlayerData> cached = cache.get(uuid);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get());
        }

        return pendingLoads.computeIfAbsent(uuid, id -> CompletableFuture.supplyAsync(() -> {
            try {
                return repository.findOrCreate(id, username);
            } catch (Exception e) {
                plugin.getLogger().severe("플레이어 데이터 로드 실패 (" + id + "): " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor).whenComplete((data, error) -> {
            pendingLoads.remove(id);
            if (data != null) {
                cache.put(data);
            }
        }));
    }

    @Override
    public CompletableFuture<Void> save(PlayerData data) {
        if (!data.isDirty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                repository.save(data);
            } catch (Exception e) {
                plugin.getLogger().severe("플레이어 데이터 저장 실패 (" + data.getUuid() + "): " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> saveAll() {
        CompletableFuture<?>[] futures = java.util.stream.StreamSupport.stream(cache.all().spliterator(), false)
                .filter(PlayerData::isDirty)
                .map(this::save)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    @Override
    public void unload(UUID uuid) {
        cache.invalidate(uuid);
    }
}
