package com.yeowool.teleport.warp;

import com.yeowool.teleport.model.Warp;
import com.yeowool.teleport.repository.WarpRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/** Warps are few and admin-curated, so (unlike homes) the whole set is cached at startup — same trade-off as {@code LandManager}. */
public final class WarpManager {

    private final JavaPlugin plugin;
    private final WarpRepository repository;
    private final ExecutorService executor;

    private final ConcurrentHashMap<String, Warp> warps = new ConcurrentHashMap<>();

    public WarpManager(JavaPlugin plugin, WarpRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    public void loadAll() throws SQLException {
        for (Warp warp : repository.loadAll()) {
            warps.put(warp.name().toLowerCase(), warp);
        }
        plugin.getLogger().info("워프 " + warps.size() + "개를 불러왔습니다.");
    }

    public Collection<Warp> all() {
        return warps.values();
    }

    public Optional<Warp> get(String name) {
        return Optional.ofNullable(warps.get(name.toLowerCase()));
    }

    public void set(Warp warp) {
        warps.put(warp.name().toLowerCase(), warp);
        executor.execute(() -> {
            try {
                repository.upsert(warp);
            } catch (SQLException e) {
                plugin.getLogger().severe("워프 저장 실패 (" + warp.name() + "): " + e.getMessage());
            }
        });
    }

    public boolean delete(String name) {
        Warp removed = warps.remove(name.toLowerCase());
        if (removed == null) {
            return false;
        }
        executor.execute(() -> {
            try {
                repository.delete(removed.name());
            } catch (SQLException e) {
                plugin.getLogger().severe("워프 삭제 실패 (" + name + "): " + e.getMessage());
            }
        });
        return true;
    }
}
