package com.yeowool.market.adminshop;

import com.yeowool.core.api.model.CurrencyType;
import com.yeowool.market.npcshop.ShopDefinition;
import com.yeowool.market.npcshop.ShopItem;
import com.yeowool.market.npcshop.ShopLayout;
import com.yeowool.market.npcshop.ShopMode;
import com.yeowool.market.npcshop.ShopPricedItem;
import com.yeowool.market.npcshop.ShopRotationManager;
import com.yeowool.market.util.ItemResolver;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Backs {@code /상점생성}/{@code /상점제거}/{@code /상점페이지추가}/
 * {@code /상점페이지제거}/{@code /상점수정}/{@code /상점로테이션설정}/
 * {@code /상점가져오기}: in-game-created NPC shops, stored in the database so
 * they survive a restart without ever touching {@code config.yml} (which
 * stays reserved for hand-authored shops). A shop's pages all combine into
 * ONE {@link ShopDefinition}, each stored item tagged with its own
 * {@link ShopItem#page()} — same model {@link ShopDefinition} uses for YAML
 * shops. Rotation pool items reuse the exact same per-page item storage,
 * just under the reserved {@link #ROTATION_POOL_PAGE} key instead of a real
 * page number.
 *
 * <p>Keeps {@code liveShops} — the exact mutable map {@code YeowoolMarket}
 * also hands to {@code NPCShopCommand}/{@code CitizensShopListener} — in
 * sync, so those two consumers need no admin-shop-specific code at all: they
 * already just do {@code shops.get(id)}.
 */
public final class AdminShopStore {

    private record ShopMeta(String title, ShopMode mode, int declaredPageCount, List<Integer> rotationSlots, int rotationIntervalMinutes) {
    }

    private static final int SHOP_SIZE = 54;
    private static final int ROTATION_POOL_PAGE = -1;

    private final JavaPlugin plugin;
    private final AdminShopRepository repository;
    private final ExecutorService executor;
    private final Map<String, ShopDefinition> liveShops;
    private final ShopRotationManager rotationManager;

    private final Map<String, ShopMeta> metaById = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, Map<Integer, ItemStack>>> itemsByShop = new ConcurrentHashMap<>();

    public AdminShopStore(JavaPlugin plugin, AdminShopRepository repository, ExecutorService executor,
                           Map<String, ShopDefinition> liveShops, ShopRotationManager rotationManager) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
        this.liveShops = liveShops;
        this.rotationManager = rotationManager;
    }

    public void loadIntoCache() throws SQLException {
        Map<String, AdminShopRepository.ShopRow> shopRows = repository.loadShops();
        Map<String, Map<Integer, Map<Integer, ItemStack>>> itemRows = repository.loadItems();

        metaById.clear();
        itemsByShop.clear();
        for (AdminShopRepository.ShopRow row : shopRows.values()) {
            metaById.put(row.id(), new ShopMeta(row.title(), row.mode(), row.pageCount(), row.rotationSlots(), row.rotationIntervalMinutes()));
        }
        for (var entry : itemRows.entrySet()) {
            itemsByShop.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
        }
        for (String id : metaById.keySet()) {
            ShopDefinition shop = buildDefinition(id);
            liveShops.put(id, shop);
            if (shop.hasRotation()) {
                rotationManager.register(shop);
            }
        }
    }

    public boolean isAdminShop(String id) {
        return metaById.containsKey(id);
    }

    /**
     * Brings a config.yml shop under admin management the first time it's
     * opened in {@code /상점수정}, so OPs can edit hand-authored shops too —
     * not just ones created via {@code /상점생성}. Snapshots the shop's
     * current title/mode/items into the database (config.yml itself is never
     * touched); from that point on the live definition comes from here, same
     * as any other admin shop. No-op if {@code id} is already admin-managed.
     * Rotation and decoration aren't representable in the admin-shop model,
     * so they're dropped on adoption (set rotation up again afterward via
     * {@code /상점로테이션설정} if wanted).
     */
    public void adopt(String id, ShopDefinition existing) {
        if (metaById.containsKey(id) || existing == null) {
            return;
        }
        metaById.put(id, new ShopMeta(existing.title(), existing.mode(), Math.max(1, existing.pageCount()), List.of(), 0));

        Map<Integer, Map<Integer, ItemStack>> pages = stampItemsFromDefinition(id, existing);
        itemsByShop.put(id, pages);
        liveShops.put(id, buildDefinition(id));

        executor.execute(() -> {
            try {
                repository.insertShop(id, existing.title(), existing.mode());
                for (var pageEntry : pages.entrySet()) {
                    repository.saveItems(id, pageEntry.getKey(), pageEntry.getValue());
                }
                repository.updatePageCount(id, Math.max(1, existing.pageCount()));
            } catch (SQLException e) {
                plugin.getLogger().severe("상점 가져오기 저장 실패 (" + id + "): " + e.getMessage());
            }
        });
    }

    public enum ImportResult { SUCCESS, SOURCE_NOT_FOUND, TARGET_NOT_FOUND }

    /**
     * {@code /상점가져오기} — copies every regular-page item from {@code source}
     * (any shop, YAML or admin) into {@code targetId}, overlaying onto
     * whatever that shop already has (same page+slot overwrites, everything
     * else untouched). Adopts the target first if it isn't already
     * admin-managed. Lets an admin bulk-populate a shop by writing a big
     * item list under a scratch config.yml shop id once, instead of placing
     * each item by hand through {@code /상점수정}.
     */
    public ImportResult importItems(String targetId, ShopDefinition source) {
        if (source == null) {
            return ImportResult.SOURCE_NOT_FOUND;
        }
        boolean needsAdopt = !metaById.containsKey(targetId);
        ShopDefinition adoptFrom = null;
        if (needsAdopt) {
            adoptFrom = liveShops.get(targetId);
            if (adoptFrom == null) {
                return ImportResult.TARGET_NOT_FOUND;
            }
        }

        Map<Integer, Map<Integer, ItemStack>> pages = needsAdopt
                ? stampItemsFromDefinition(targetId, adoptFrom)
                : new ConcurrentHashMap<>(itemsByShop.getOrDefault(targetId, Map.of()));
        if (needsAdopt) {
            metaById.put(targetId, new ShopMeta(adoptFrom.title(), adoptFrom.mode(), Math.max(1, adoptFrom.pageCount()), List.of(), 0));
        }

        Map<Integer, Map<Integer, ItemStack>> imported = stampItemsFromDefinition(targetId, source);
        for (var pageEntry : imported.entrySet()) {
            pages.computeIfAbsent(pageEntry.getKey(), key -> new ConcurrentHashMap<>()).putAll(pageEntry.getValue());
        }
        itemsByShop.put(targetId, pages);
        liveShops.put(targetId, buildDefinition(targetId));

        boolean finalNeedsAdopt = needsAdopt;
        String title = needsAdopt ? adoptFrom.title() : null;
        ShopMode mode = needsAdopt ? adoptFrom.mode() : null;
        int pageCountToPersist = needsAdopt ? Math.max(1, adoptFrom.pageCount()) : 0;
        Map<Integer, Map<Integer, ItemStack>> snapshot = new LinkedHashMap<>(pages);
        executor.execute(() -> {
            try {
                if (finalNeedsAdopt) {
                    repository.insertShop(targetId, title, mode);
                    repository.updatePageCount(targetId, pageCountToPersist);
                }
                for (var pageEntry : snapshot.entrySet()) {
                    repository.saveItems(targetId, pageEntry.getKey(), pageEntry.getValue());
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("상점 가져오기 저장 실패 (" + targetId + "): " + e.getMessage());
            }
        });
        return ImportResult.SUCCESS;
    }

    /** Stamps every regular-page item of {@code source} into priced ItemStacks, skipping any at a now-reserved slot. */
    private Map<Integer, Map<Integer, ItemStack>> stampItemsFromDefinition(String contextId, ShopDefinition source) {
        Map<Integer, Map<Integer, ItemStack>> pages = new ConcurrentHashMap<>();
        for (ShopItem item : source.items()) {
            if (!ShopLayout.USABLE_SLOTS.contains(item.slot())) {
                plugin.getLogger().warning("상점 [" + contextId + "] 가져오기: 슬롯 " + item.slot() + "의 아이템(" + item.displayId()
                        + ")은 새 레이아웃에서 예약된 자리라 가져오지 못했습니다.");
                continue;
            }
            ItemStack stamped = ItemResolver.build(item, 1, plugin.getLogger());
            ShopPricedItem.stamp(plugin, stamped, item.buyPrice(), item.sellPrice(), item.currency(), item.strictMatch());
            pages.computeIfAbsent(item.page(), key -> new ConcurrentHashMap<>()).put(item.slot(), stamped);
        }
        return pages;
    }

    public Set<String> ids() {
        return Set.copyOf(metaById.keySet());
    }

    public enum CreateResult { SUCCESS, ALREADY_EXISTS }

    public CreateResult create(String id, ShopMode mode, String title) {
        if (liveShops.containsKey(id)) {
            return CreateResult.ALREADY_EXISTS;
        }
        metaById.put(id, new ShopMeta(title, mode, 1, List.of(), 0));
        itemsByShop.put(id, new ConcurrentHashMap<>());
        liveShops.put(id, buildDefinition(id));
        executor.execute(() -> {
            try {
                repository.insertShop(id, title, mode);
            } catch (SQLException e) {
                plugin.getLogger().severe("상점 생성 저장 실패 (" + id + "): " + e.getMessage());
            }
        });
        return CreateResult.SUCCESS;
    }

    /**
     * {@code SUCCESS_SESSION_ONLY} covers a config.yml shop that was never
     * admin-managed (never adopted via {@code /상점수정}): there's no DB row
     * to delete, so this only removes it from the live map for the rest of
     * this server run — {@code config.yml} itself is never rewritten, so a
     * restart brings it right back unless the operator also removes it from
     * the file by hand.
     */
    public enum DeleteResult { SUCCESS, SUCCESS_SESSION_ONLY, NOT_FOUND }

    public DeleteResult delete(String id) {
        if (metaById.remove(id) != null) {
            itemsByShop.remove(id);
            liveShops.remove(id);
            rotationManager.register(new ShopDefinition(id, "", SHOP_SIZE, ShopMode.BOTH, List.of(), null, List.of(), List.of(), 0, 1));
            executor.execute(() -> {
                try {
                    repository.deleteShop(id);
                } catch (SQLException e) {
                    plugin.getLogger().severe("상점 삭제 저장 실패 (" + id + "): " + e.getMessage());
                }
            });
            return DeleteResult.SUCCESS;
        }
        if (liveShops.remove(id) != null) {
            return DeleteResult.SUCCESS_SESSION_ONLY;
        }
        return DeleteResult.NOT_FOUND;
    }

    /** Empty if {@code id} isn't an admin shop; otherwise the new page count. */
    public Optional<Integer> addPage(String id) {
        ShopMeta meta = metaById.get(id);
        if (meta == null) {
            return Optional.empty();
        }
        int newCount = meta.declaredPageCount() + 1;
        metaById.put(id, new ShopMeta(meta.title(), meta.mode(), newCount, meta.rotationSlots(), meta.rotationIntervalMinutes()));
        liveShops.put(id, buildDefinition(id));
        executor.execute(() -> {
            try {
                repository.updatePageCount(id, newCount);
            } catch (SQLException e) {
                plugin.getLogger().severe("상점 페이지 추가 저장 실패 (" + id + "): " + e.getMessage());
            }
        });
        return Optional.of(newCount);
    }

    public enum RemovePageResult { SUCCESS, NOT_FOUND, ONLY_ONE_PAGE }

    /** Always removes the highest-indexed (last) page. */
    public RemovePageResult removePage(String id) {
        ShopMeta meta = metaById.get(id);
        if (meta == null) {
            return RemovePageResult.NOT_FOUND;
        }
        if (meta.declaredPageCount() <= 1) {
            return RemovePageResult.ONLY_ONE_PAGE;
        }
        int removedPage = meta.declaredPageCount() - 1;
        int newCount = removedPage;
        metaById.put(id, new ShopMeta(meta.title(), meta.mode(), newCount, meta.rotationSlots(), meta.rotationIntervalMinutes()));
        Map<Integer, Map<Integer, ItemStack>> pages = itemsByShop.get(id);
        if (pages != null) {
            pages.remove(removedPage);
        }
        liveShops.put(id, buildDefinition(id));
        executor.execute(() -> {
            try {
                repository.updatePageCount(id, newCount);
                repository.deletePage(id, removedPage);
            } catch (SQLException e) {
                plugin.getLogger().severe("상점 페이지 제거 저장 실패 (" + id + "): " + e.getMessage());
            }
        });
        return RemovePageResult.SUCCESS;
    }

    /** The larger of the admin's declared page count and however many pages actually have items — lets 상점페이지추가 reserve a still-empty page. */
    public int pageCount(String id) {
        ShopMeta meta = metaById.get(id);
        if (meta == null) {
            return 1;
        }
        int fromItems = 1 + itemsByShop.getOrDefault(id, Map.of()).keySet().stream()
                .filter(page -> page >= 0)
                .mapToInt(Integer::intValue).max().orElse(0);
        return Math.max(meta.declaredPageCount(), fromItems);
    }

    public Map<Integer, ItemStack> rawItemsOf(String id, int page) {
        Map<Integer, Map<Integer, ItemStack>> pages = itemsByShop.get(id);
        if (pages == null) {
            return Map.of();
        }
        return Map.copyOf(pages.getOrDefault(page, Map.of()));
    }

    public void savePage(String id, int page, Map<Integer, ItemStack> items) {
        Map<Integer, ItemStack> snapshot = new LinkedHashMap<>(items);
        itemsByShop.computeIfAbsent(id, key -> new ConcurrentHashMap<>()).put(page, Map.copyOf(snapshot));
        liveShops.put(id, buildDefinition(id));
        executor.execute(() -> {
            try {
                repository.saveItems(id, page, snapshot);
            } catch (SQLException e) {
                plugin.getLogger().severe("상점 아이템 저장 실패 (" + id + "#" + page + "): " + e.getMessage());
            }
        });
    }

    public enum RotationResult { SUCCESS, NOT_FOUND }

    /** {@code /상점로테이션설정} — sets which slots rotate and how often; rolls immediately. */
    public RotationResult setRotation(String id, int intervalMinutes, List<Integer> slots) {
        ShopMeta meta = metaById.get(id);
        if (meta == null) {
            return RotationResult.NOT_FOUND;
        }
        metaById.put(id, new ShopMeta(meta.title(), meta.mode(), meta.declaredPageCount(), List.copyOf(slots), intervalMinutes));
        ShopDefinition updated = buildDefinition(id);
        liveShops.put(id, updated);
        rotationManager.register(updated);
        executor.execute(() -> {
            try {
                repository.updateRotation(id, slots, intervalMinutes);
            } catch (SQLException e) {
                plugin.getLogger().severe("상점 로테이션 설정 저장 실패 (" + id + "): " + e.getMessage());
            }
        });
        return RotationResult.SUCCESS;
    }

    /** {@code /상점로테이션추가} — adds the held (already-priced) item to the shop's rotation pool. */
    public RotationResult addToRotationPool(String id, ItemStack stampedItem) {
        if (!metaById.containsKey(id)) {
            return RotationResult.NOT_FOUND;
        }
        Map<Integer, ItemStack> pool = new LinkedHashMap<>(rawItemsOf(id, ROTATION_POOL_PAGE));
        int nextIndex = pool.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        pool.put(nextIndex, stampedItem);
        itemsByShop.computeIfAbsent(id, key -> new ConcurrentHashMap<>()).put(ROTATION_POOL_PAGE, Map.copyOf(pool));
        ShopDefinition updated = buildDefinition(id);
        liveShops.put(id, updated);
        rotationManager.register(updated);
        Map<Integer, ItemStack> snapshot = new LinkedHashMap<>(pool);
        executor.execute(() -> {
            try {
                repository.saveItems(id, ROTATION_POOL_PAGE, snapshot);
            } catch (SQLException e) {
                plugin.getLogger().severe("상점 로테이션 풀 저장 실패 (" + id + "): " + e.getMessage());
            }
        });
        return RotationResult.SUCCESS;
    }

    /** {@code /상점로테이션제거} — clears rotation slots/interval and the whole pool. */
    public RotationResult clearRotation(String id) {
        ShopMeta meta = metaById.get(id);
        if (meta == null) {
            return RotationResult.NOT_FOUND;
        }
        metaById.put(id, new ShopMeta(meta.title(), meta.mode(), meta.declaredPageCount(), List.of(), 0));
        Map<Integer, Map<Integer, ItemStack>> pages = itemsByShop.get(id);
        if (pages != null) {
            pages.remove(ROTATION_POOL_PAGE);
        }
        ShopDefinition updated = buildDefinition(id);
        liveShops.put(id, updated);
        rotationManager.register(updated);
        executor.execute(() -> {
            try {
                repository.updateRotation(id, List.of(), 0);
                repository.deletePage(id, ROTATION_POOL_PAGE);
            } catch (SQLException e) {
                plugin.getLogger().severe("상점 로테이션 제거 저장 실패 (" + id + "): " + e.getMessage());
            }
        });
        return RotationResult.SUCCESS;
    }

    public int rotationPoolSize(String id) {
        return rawItemsOf(id, ROTATION_POOL_PAGE).size();
    }

    private ShopDefinition buildDefinition(String id) {
        ShopMeta meta = metaById.get(id);
        if (meta == null) {
            return null;
        }
        List<ShopItem> items = new ArrayList<>();
        List<ShopItem> pool = new ArrayList<>();
        for (var pageEntry : itemsByShop.getOrDefault(id, Map.of()).entrySet()) {
            int page = pageEntry.getKey();
            boolean isPool = page == ROTATION_POOL_PAGE;
            for (var slotEntry : pageEntry.getValue().entrySet()) {
                ShopItem item = toShopItem(slotEntry.getValue(), slotEntry.getKey(), isPool ? 0 : page);
                (isPool ? pool : items).add(item);
            }
        }
        return new ShopDefinition(id, meta.title(), SHOP_SIZE, meta.mode(), items, null,
                pool, meta.rotationSlots(), meta.rotationIntervalMinutes(), pageCount(id));
    }

    private ShopItem toShopItem(ItemStack stamped, int slot, int page) {
        long buy = ShopPricedItem.buyPrice(plugin, stamped);
        long sell = ShopPricedItem.sellPrice(plugin, stamped);
        CurrencyType currency = ShopPricedItem.currency(plugin, stamped);
        boolean strictMatch = ShopPricedItem.strictMatch(plugin, stamped);
        String customDisplayName = captureDisplayName(stamped);

        if (ItemResolver.isItemsAdderAvailable()) {
            CustomStack custom = CustomStack.byItemStack(stamped);
            if (custom != null) {
                return new ShopItem(null, custom.getNamespacedID(), buy, sell, slot, -1, currency, page, strictMatch, customDisplayName);
            }
        }
        ItemMeta meta = stamped.getItemMeta();
        int customModelData = meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : -1;
        return new ShopItem(stamped.getType(), null, buy, sell, slot, customModelData, currency, page, strictMatch, customDisplayName);
    }

    /**
     * {@code null} unless the placed item actually had its own custom name (e.g. a fish given
     * through {@code /낚시관리}, colored by rarity) — a plain item just uses whatever name
     * {@link com.yeowool.market.util.ItemResolver} would derive from its material/ItemsAdder id,
     * same as before this existed.
     */
    private String captureDisplayName(ItemStack stamped) {
        ItemMeta meta = stamped.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }
        return net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(meta.displayName());
    }
}
