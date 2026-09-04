package com.yeowool.admin.banneditem;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Fully cached in memory (two small material sets) since {@link BannedItemListener}
 * checks this on every inventory click — same trade-off {@code CouponManager} makes.
 */
public final class BannedItemManager {

    private final JavaPlugin plugin;
    private final BannedItemRepository repository;
    private final ExecutorService executor;

    private final Set<Material> possessionBanned = ConcurrentHashMap.newKeySet();
    private final Set<Material> craftBanned = ConcurrentHashMap.newKeySet();

    public BannedItemManager(JavaPlugin plugin, BannedItemRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    public void loadAll() throws SQLException {
        for (BannedItemRepository.Entry entry : repository.loadAll()) {
            try {
                set(entry.type()).add(Material.valueOf(entry.material()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("알 수 없는 아이템 밴 항목을 건너뜁니다: " + entry.material());
            }
        }
        plugin.getLogger().info("아이템밴 " + possessionBanned.size() + "개, 조합밴 " + craftBanned.size() + "개를 불러왔습니다.");
    }

    private Set<Material> set(BanType type) {
        return type == BanType.POSSESSION ? possessionBanned : craftBanned;
    }

    public boolean isBanned(Material material, BanType type) {
        return set(type).contains(material);
    }

    /** {@code true} if newly banned, {@code false} if it already was. */
    public boolean ban(Material material, BanType type, String bannedBy) {
        if (!set(type).add(material)) {
            return false;
        }
        long now = System.currentTimeMillis();
        executor.execute(() -> {
            try {
                repository.insert(material.name(), type, bannedBy, now);
            } catch (SQLException e) {
                plugin.getLogger().severe("아이템 밴 저장 실패 (" + material + "/" + type + "): " + e.getMessage());
            }
        });
        return true;
    }

    /** {@code true} if it was banned and is now removed. */
    public boolean unban(Material material, BanType type) {
        if (!set(type).remove(material)) {
            return false;
        }
        executor.execute(() -> {
            try {
                repository.delete(material.name(), type);
            } catch (SQLException e) {
                plugin.getLogger().severe("아이템 밴 해제 실패 (" + material + "/" + type + "): " + e.getMessage());
            }
        });
        return true;
    }

    public Set<Material> list(BanType type) {
        return set(type);
    }
}
