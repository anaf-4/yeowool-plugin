package com.yeowool.admin.starterkit;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Caches the admin-configured starter kit (see {@link StarterKitEditorGui})
 * and gives it to new players. Nothing is given until an admin has actually
 * saved a kit at least once, per "관리자가 직접 초반 기본템을 설정할 수 있도록".
 */
public final class StarterKitService {

    private final JavaPlugin plugin;
    private final StarterKitRepository repository;
    private final ExecutorService executor;
    private volatile Map<Integer, ItemStack> cachedKit = Map.of();

    public StarterKitService(JavaPlugin plugin, StarterKitRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    public void loadIntoCache() throws SQLException {
        cachedKit = Map.copyOf(repository.loadAll());
    }

    public Map<Integer, ItemStack> currentKit() {
        return cachedKit;
    }

    public void save(Map<Integer, ItemStack> items) {
        Map<Integer, ItemStack> snapshot = new LinkedHashMap<>(items);
        cachedKit = Map.copyOf(snapshot);
        executor.execute(() -> {
            try {
                repository.replaceAll(snapshot);
            } catch (SQLException e) {
                plugin.getLogger().severe("기본템 저장 실패: " + e.getMessage());
            }
        });
    }

    public void give(Player player) {
        for (ItemStack item : cachedKit.values()) {
            var leftover = player.getInventory().addItem(item.clone());
            leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
        }
    }
}
