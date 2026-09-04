package com.yeowool.teleport.playerwarp;

import com.yeowool.teleport.model.PlayerWarp;
import com.yeowool.teleport.repository.PlayerWarpRepository;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Player-owned but PUBLIC warps ({@code /플레이어워프}), browsable by anyone —
 * the whole set is kept in memory (same trade-off {@code WarpManager} makes
 * for admin warps). Pure data/cache layer: fee-charging and permission
 * checks live in the GUIs/commands that call this, not here.
 */
public final class PlayerWarpManager {

    private final JavaPlugin plugin;
    private final PlayerWarpRepository repository;
    private final ExecutorService executor;
    private final int maxPerPlayer;

    private final Map<UUID, Map<String, PlayerWarp>> byOwner = new ConcurrentHashMap<>();

    public PlayerWarpManager(JavaPlugin plugin, PlayerWarpRepository repository, ExecutorService executor, int maxPerPlayer) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
        this.maxPerPlayer = maxPerPlayer;
    }

    public void loadAll() throws SQLException {
        int count = 0;
        for (PlayerWarp warp : repository.loadAll()) {
            byOwner.computeIfAbsent(warp.owner(), k -> new ConcurrentHashMap<>()).put(warp.name().toLowerCase(), warp);
            count++;
        }
        plugin.getLogger().info("플레이어 워프 " + count + "개를 불러왔습니다.");
    }

    public Collection<PlayerWarp> all() {
        return byOwner.values().stream().flatMap(m -> m.values().stream()).toList();
    }

    public Map<String, PlayerWarp> ownedBy(UUID owner) {
        return byOwner.getOrDefault(owner, Map.of());
    }

    public Optional<PlayerWarp> get(UUID owner, String name) {
        return Optional.ofNullable(ownedBy(owner).get(name.toLowerCase()));
    }

    public boolean nameTaken(UUID owner, String name) {
        return ownedBy(owner).containsKey(name.toLowerCase());
    }

    public int maxPerPlayer() {
        return maxPerPlayer;
    }

    public enum CreateResult { SUCCESS, LIMIT_REACHED, NAME_TAKEN }

    public CreateResult create(UUID owner, String name, Location location) {
        Map<String, PlayerWarp> owned = byOwner.computeIfAbsent(owner, k -> new ConcurrentHashMap<>());
        String key = name.toLowerCase();
        if (owned.containsKey(key)) {
            return CreateResult.NAME_TAKEN;
        }
        if (owned.size() >= maxPerPlayer) {
            return CreateResult.LIMIT_REACHED;
        }
        put(PlayerWarp.create(owner, name, location));
        return CreateResult.SUCCESS;
    }

    /** Persists an already-mutated warp back to cache + DB (same owner/name as before — use {@link #rekey} if either changed). */
    public void put(PlayerWarp warp) {
        byOwner.computeIfAbsent(warp.owner(), k -> new ConcurrentHashMap<>()).put(warp.name().toLowerCase(), warp);
        executor.execute(() -> {
            try {
                repository.upsert(warp);
            } catch (SQLException e) {
                plugin.getLogger().severe("플레이어 워프 저장 실패 (" + warp.owner() + "/" + warp.name() + "): " + e.getMessage());
            }
        });
    }

    /** For rename (name changes) or ownership transfer (owner changes) — either changes the cache map key. */
    public void rekey(UUID oldOwner, String oldName, PlayerWarp updated) {
        Map<String, PlayerWarp> oldOwned = byOwner.get(oldOwner);
        if (oldOwned != null) {
            oldOwned.remove(oldName.toLowerCase());
        }
        byOwner.computeIfAbsent(updated.owner(), k -> new ConcurrentHashMap<>()).put(updated.name().toLowerCase(), updated);
        executor.execute(() -> {
            try {
                repository.rekey(oldOwner, oldName, updated);
            } catch (SQLException e) {
                plugin.getLogger().severe("플레이어 워프 이름/소유자 변경 실패 (" + oldOwner + "/" + oldName + "): " + e.getMessage());
            }
        });
    }

    public boolean delete(UUID owner, String name) {
        Map<String, PlayerWarp> owned = byOwner.get(owner);
        if (owned == null) {
            return false;
        }
        PlayerWarp removed = owned.remove(name.toLowerCase());
        if (removed == null) {
            return false;
        }
        executor.execute(() -> {
            try {
                repository.delete(owner, removed.name());
            } catch (SQLException e) {
                plugin.getLogger().severe("플레이어 워프 삭제 실패 (" + owner + "/" + name + "): " + e.getMessage());
            }
        });
        return true;
    }
}
