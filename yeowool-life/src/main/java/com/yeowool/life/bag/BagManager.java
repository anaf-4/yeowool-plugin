package com.yeowool.life.bag;

import com.yeowool.life.bag.repository.BagRepository;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * In-memory {@link PlayerBag} cache, one {@link EnumMap} of the 4
 * {@link BagType}s per online player. Mutations (auto-collect, GUI close,
 * ticket expansion) happen directly on the cached array on the main thread;
 * {@link BagRepository} I/O only ever runs on {@link #executor}. Bags start
 * empty at {@link PlayerBag#DEFAULT_CAPACITY} the first time a player is
 * seen — no explicit "grant" step needed.
 */
public final class BagManager {

    private final BagRepository repository;
    private final ExecutorService executor;
    private final Logger logger;
    private final Map<UUID, EnumMap<BagType, PlayerBag>> cache = new ConcurrentHashMap<>();

    /**
     * Which bag type (if any) each player currently has {@link BagGui} open for. {@link BagGui}
     * snapshots contents once at open time and overwrites the bag wholesale on close, so an
     * auto-collect ({@link #offer}) landing on the same bag while it's open would be silently
     * discarded by that overwrite - tracked here so {@link #offer} can simply decline while the
     * GUI is open instead (the item is left for vanilla pickup, which no-ops since the player's
     * real inventory is also full, so it just waits on the ground until the GUI is closed).
     */
    private final Map<UUID, BagType> openGuis = new ConcurrentHashMap<>();

    public BagManager(BagRepository repository, ExecutorService executor, Logger logger) {
        this.repository = repository;
        this.executor = executor;
        this.logger = logger;
    }

    public boolean isLoaded(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public void loadAsync(UUID uuid) {
        executor.execute(() -> {
            try {
                Map<String, BagRepository.Row> rows = repository.load(uuid);
                EnumMap<BagType, PlayerBag> bags = new EnumMap<>(BagType.class);
                for (BagType type : BagType.values()) {
                    BagRepository.Row row = rows.get(type.name());
                    bags.put(type, row == null
                            ? new PlayerBag(PlayerBag.DEFAULT_CAPACITY, null)
                            : new PlayerBag(row.capacity(), row.contents()));
                }
                cache.put(uuid, bags);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "가방 데이터 로드 실패: " + uuid, e);
            }
        });
    }

    /** Flushes dirty bags then drops {@code uuid} from the cache — call on quit. */
    public void unloadAsync(UUID uuid) {
        openGuis.remove(uuid);
        EnumMap<BagType, PlayerBag> bags = cache.remove(uuid);
        if (bags == null) {
            return;
        }
        executor.execute(() -> flush(uuid, bags));
    }

    public void markGuiOpen(UUID uuid, BagType type) {
        openGuis.put(uuid, type);
    }

    public void markGuiClosed(UUID uuid, BagType type) {
        openGuis.remove(uuid, type);
    }

    /** Flushes every dirty bag for every currently-cached player, blocking — call from {@code onDisable}. */
    public void saveAllBlocking() {
        cache.forEach(this::flush);
    }

    private void flush(UUID uuid, EnumMap<BagType, PlayerBag> bags) {
        for (var entry : bags.entrySet()) {
            PlayerBag bag = entry.getValue();
            if (!bag.dirty()) {
                continue;
            }
            try {
                repository.save(uuid, entry.getKey().name(), bag.capacity(), bag.contents());
                bag.markClean();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "가방 데이터 저장 실패: " + uuid + "/" + entry.getKey(), e);
            }
        }
    }

    /** Periodic sweep across all cached (online) players — call from a repeating async task. */
    public void flushAllDirty() {
        cache.forEach(this::flush);
    }

    private PlayerBag bag(UUID uuid, BagType type) {
        EnumMap<BagType, PlayerBag> bags = cache.get(uuid);
        return bags == null ? null : bags.get(type);
    }

    public int capacity(UUID uuid, BagType type) {
        PlayerBag bag = bag(uuid, type);
        return bag == null ? PlayerBag.DEFAULT_CAPACITY : bag.capacity();
    }

    public int usedSlots(UUID uuid, BagType type) {
        PlayerBag bag = bag(uuid, type);
        if (bag == null) {
            return 0;
        }
        int used = 0;
        for (ItemStack item : bag.contents()) {
            if (item != null) {
                used++;
            }
        }
        return used;
    }

    /** Snapshot of the bag's current contents, or {@code null} if not loaded yet. */
    public ItemStack[] contents(UUID uuid, BagType type) {
        PlayerBag bag = bag(uuid, type);
        return bag == null ? null : bag.contents();
    }

    public void applyGuiContents(UUID uuid, BagType type, ItemStack[] newContents) {
        PlayerBag bag = bag(uuid, type);
        if (bag != null) {
            bag.setContents(newContents);
        }
    }

    /** {@code true} if the bag is already at {@link PlayerBag#MAX_CAPACITY}. */
    public boolean isAtMaxCapacity(UUID uuid, BagType type) {
        return capacity(uuid, type) >= PlayerBag.MAX_CAPACITY;
    }

    /** Expands the bag by 1 slot; returns {@code false} if it was already at max capacity. */
    public boolean expand(UUID uuid, BagType type, int by) {
        PlayerBag bag = bag(uuid, type);
        return bag != null && bag.expand(by);
    }

    /**
     * Routes {@code item} into whichever bag matches it, if the player's
     * inventory has no room left for it. Returns {@code true} if the item
     * was fully or partially absorbed (caller should cancel the pickup and,
     * for a partial absorb, put {@code leftover()} back on the ground).
     */
    public BagOfferResult offer(Player player, ItemStack item) {
        BagType type = BagMatcher.classify(item);
        if (type == null) {
            return BagOfferResult.notApplicable();
        }
        if (type.equals(openGuis.get(player.getUniqueId()))) {
            return BagOfferResult.notApplicable();
        }
        PlayerBag bag = bag(player.getUniqueId(), type);
        if (bag == null) {
            return BagOfferResult.notApplicable();
        }
        ItemStack leftover = bag.addItem(item);
        if (leftover != null && leftover.getAmount() == item.getAmount()) {
            return BagOfferResult.notApplicable();
        }
        return BagOfferResult.absorbed(type, leftover);
    }

    public record BagOfferResult(boolean handled, BagType type, ItemStack leftover) {
        static BagOfferResult notApplicable() {
            return new BagOfferResult(false, null, null);
        }

        static BagOfferResult absorbed(BagType type, ItemStack leftover) {
            return new BagOfferResult(true, type, leftover);
        }
    }
}
