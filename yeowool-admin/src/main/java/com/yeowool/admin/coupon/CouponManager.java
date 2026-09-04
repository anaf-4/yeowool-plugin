package com.yeowool.admin.coupon;

import com.yeowool.admin.coupon.repository.CouponRepository;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Fully cached in memory (coupon count and per-coupon redemption count are
 * both expected to stay small — same trade-off {@code AuctionManager}/
 * {@code PlayerShopManager} make), since the redemption check runs inside
 * {@link CouponRedeemListener}'s {@code PrepareAnvilEvent} handler on every
 * keystroke and must never block on a DB round trip.
 */
public final class CouponManager {

    private final JavaPlugin plugin;
    private final CouponRepository repository;
    private final ExecutorService executor;

    private final ConcurrentHashMap<String, Coupon> coupons = new ConcurrentHashMap<>();
    private final ConcurrentHashMap.KeySetView<String, Boolean> redemptions = ConcurrentHashMap.newKeySet();

    public CouponManager(JavaPlugin plugin, CouponRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    public void loadAll() throws SQLException {
        coupons.putAll(repository.loadAllCoupons());
        redemptions.addAll(repository.loadAllRedemptions());
        plugin.getLogger().info("쿠폰 " + coupons.size() + "개, 사용 기록 " + redemptions.size() + "건을 불러왔습니다.");
    }

    public static String normalize(String code) {
        return code.trim().toLowerCase();
    }

    public Collection<Coupon> all() {
        return coupons.values();
    }

    public Optional<Coupon> findByCode(String code) {
        return Optional.ofNullable(coupons.get(normalize(code)));
    }

    public enum CreateResult { SUCCESS, ALREADY_EXISTS }

    public CreateResult create(String code, ItemStack rewardItem, long expiresAt) {
        String key = normalize(code);
        if (coupons.containsKey(key)) {
            return CreateResult.ALREADY_EXISTS;
        }
        Coupon coupon = new Coupon(code, rewardItem.clone(), System.currentTimeMillis(), expiresAt);
        coupons.put(key, coupon);
        persist(key, coupon);
        return CreateResult.SUCCESS;
    }

    public boolean delete(String code) {
        String key = normalize(code);
        if (coupons.remove(key) == null) {
            return false;
        }
        redemptions.removeIf(entry -> entry.startsWith(key + ":"));
        executor.execute(() -> {
            try {
                repository.delete(key);
            } catch (SQLException e) {
                plugin.getLogger().severe("쿠폰 삭제 실패 (" + key + "): " + e.getMessage());
            }
        });
        return true;
    }

    public boolean updateRewardItem(String code, ItemStack newItem) {
        String key = normalize(code);
        Coupon existing = coupons.get(key);
        if (existing == null) {
            return false;
        }
        Coupon updated = existing.withRewardItem(newItem.clone());
        coupons.put(key, updated);
        persist(key, updated);
        return true;
    }

    public boolean updateExpiry(String code, long newExpiresAt) {
        String key = normalize(code);
        Coupon existing = coupons.get(key);
        if (existing == null) {
            return false;
        }
        Coupon updated = existing.withExpiresAt(newExpiresAt);
        coupons.put(key, updated);
        persist(key, updated);
        return true;
    }

    public boolean isRedeemed(String code, UUID uuid) {
        return redemptions.contains(normalize(code) + ":" + uuid);
    }

    /** Marks the coupon redeemed for this player. Caller (the anvil listener) is responsible for actually granting the reward item. */
    public void markRedeemed(String code, UUID uuid) {
        String key = normalize(code);
        redemptions.add(key + ":" + uuid);
        long now = System.currentTimeMillis();
        executor.execute(() -> {
            try {
                repository.insertRedemption(key, uuid, now);
            } catch (SQLException e) {
                plugin.getLogger().severe("쿠폰 사용 기록 저장 실패 (" + key + "/" + uuid + "): " + e.getMessage());
            }
        });
    }

    private void persist(String key, Coupon coupon) {
        executor.execute(() -> {
            try {
                repository.upsert(key, coupon);
            } catch (SQLException e) {
                plugin.getLogger().severe("쿠폰 저장 실패 (" + key + "): " + e.getMessage());
            }
        });
    }
}
