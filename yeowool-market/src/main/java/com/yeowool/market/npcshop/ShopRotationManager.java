package com.yeowool.market.npcshop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "한정 판매" layer on top of {@link ShopDefinition}'s always-shown items:
 * for shops with rotation configured, holds the currently-active random
 * subset of {@link ShopDefinition#rotationPool()} placed into
 * {@link ShopDefinition#rotationSlots()}. Rotated items always render
 * regardless of which page {@link NPCShopGui} is currently showing — only
 * {@code shop.items()} itself is split across pages via {@link ShopItem#page()}.
 *
 * <p>{@link #tick()} drives every rotating shop off ONE shared timer
 * ({@code YeowoolMarket} calls it once a minute) instead of a separate
 * {@code runTaskTimer} per shop — each shop just remembers its own interval
 * and last-roll time. {@link #register} lets an admin shop join or leave
 * rotation at runtime (e.g. via {@code /상점로테이션설정}), which a
 * YAML-configured shop never needs since its rotation is fixed at startup.
 */
public final class ShopRotationManager {

    private record RotationState(int intervalMinutes, long lastRollMillis) {
    }

    private final Map<String, List<ShopItem>> activeRotatedItems = new ConcurrentHashMap<>();
    private final Map<String, RotationState> states = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public ShopRotationManager(Map<String, ShopDefinition> shops) {
        for (ShopDefinition shop : shops.values()) {
            if (shop.hasRotation()) {
                roll(shop);
                states.put(shop.id(), new RotationState(shop.rotationIntervalMinutes(), System.currentTimeMillis()));
            }
        }
    }

    /** The currently rotated-in items for this shop (empty if it has no rotation configured). */
    public List<ShopItem> activeItemsFor(ShopDefinition shop) {
        return activeRotatedItems.getOrDefault(shop.id(), List.of());
    }

    /** Called once a minute by {@code YeowoolMarket}; re-rolls whichever registered shops are due. */
    public void tick(Map<String, ShopDefinition> liveShops) {
        long now = System.currentTimeMillis();
        for (var entry : states.entrySet()) {
            RotationState state = entry.getValue();
            if (now - state.lastRollMillis() < state.intervalMinutes() * 60_000L) {
                continue;
            }
            ShopDefinition shop = liveShops.get(entry.getKey());
            if (shop != null && shop.hasRotation()) {
                roll(shop);
                states.put(entry.getKey(), new RotationState(shop.rotationIntervalMinutes(), now));
            }
        }
    }

    /**
     * Registers (or updates) an admin-created shop's rotation schedule and
     * rolls it immediately, or un-registers it if it no longer has rotation
     * configured — used by {@code AdminShopStore} whenever an OP changes a
     * shop's rotation setup through a command.
     */
    public void register(ShopDefinition shop) {
        if (!shop.hasRotation()) {
            states.remove(shop.id());
            activeRotatedItems.remove(shop.id());
            return;
        }
        roll(shop);
        states.put(shop.id(), new RotationState(shop.rotationIntervalMinutes(), System.currentTimeMillis()));
    }

    /** Re-rolls this shop's rotated slots from its pool. No-op if the shop has no rotation configured. */
    public void roll(ShopDefinition shop) {
        if (!shop.hasRotation()) {
            return;
        }
        List<ShopItem> pool = new ArrayList<>(shop.rotationPool());
        Collections.shuffle(pool, random);
        List<Integer> slots = shop.rotationSlots();

        List<ShopItem> chosen = new ArrayList<>();
        for (int i = 0; i < slots.size() && i < pool.size(); i++) {
            ShopItem base = pool.get(i);
            chosen.add(new ShopItem(base.material(), base.itemId(), base.buyPrice(), base.sellPrice(), slots.get(i),
                    base.customModelData(), base.currency(), 0, base.strictMatch(), base.customDisplayName()));
        }
        activeRotatedItems.put(shop.id(), chosen);
    }
}
