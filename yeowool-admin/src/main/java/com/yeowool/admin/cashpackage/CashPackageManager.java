package com.yeowool.admin.cashpackage;

import com.yeowool.admin.cashpackage.repository.CashPackageRepository;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/** Fully cached in memory — package count is expected to stay small, same trade-off {@code CouponManager} makes. */
public final class CashPackageManager {

    private final JavaPlugin plugin;
    private final CashPackageRepository repository;
    private final ExecutorService executor;

    private final ConcurrentHashMap<String, CashPackage> packages = new ConcurrentHashMap<>();

    public CashPackageManager(JavaPlugin plugin, CashPackageRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    public void loadAll() throws SQLException {
        packages.putAll(repository.loadAll());
        plugin.getLogger().info("캐시 패키지 " + packages.size() + "개를 불러왔습니다.");
    }

    public static String normalize(String name) {
        return name.trim().toLowerCase();
    }

    public Collection<CashPackage> all() {
        return packages.values();
    }

    public Optional<CashPackage> find(String name) {
        return Optional.ofNullable(packages.get(normalize(name)));
    }

    public enum CreateResult { SUCCESS, ALREADY_EXISTS }

    public CreateResult create(String name, List<ItemStack> items) {
        String key = normalize(name);
        if (packages.containsKey(key)) {
            return CreateResult.ALREADY_EXISTS;
        }
        CashPackage pkg = new CashPackage(name, cloneAll(items), System.currentTimeMillis());
        packages.put(key, pkg);
        persist(key, pkg);
        return CreateResult.SUCCESS;
    }

    public boolean updateItems(String name, List<ItemStack> newItems) {
        String key = normalize(name);
        CashPackage existing = packages.get(key);
        if (existing == null) {
            return false;
        }
        CashPackage updated = existing.withItems(cloneAll(newItems));
        packages.put(key, updated);
        persist(key, updated);
        return true;
    }

    public boolean delete(String name) {
        String key = normalize(name);
        if (packages.remove(key) == null) {
            return false;
        }
        executor.execute(() -> {
            try {
                repository.delete(key);
            } catch (SQLException e) {
                plugin.getLogger().severe("캐시 패키지 삭제 실패 (" + key + "): " + e.getMessage());
            }
        });
        return true;
    }

    private static List<ItemStack> cloneAll(List<ItemStack> items) {
        return items.stream().map(ItemStack::clone).toList();
    }

    private void persist(String key, CashPackage pkg) {
        executor.execute(() -> {
            try {
                repository.upsert(key, pkg);
            } catch (SQLException e) {
                plugin.getLogger().severe("캐시 패키지 저장 실패 (" + key + "): " + e.getMessage());
            }
        });
    }
}
