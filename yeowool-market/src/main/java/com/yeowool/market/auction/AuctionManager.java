package com.yeowool.market.auction;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.CurrencyType;
import com.yeowool.market.auction.repository.AuctionRepository;
import com.yeowool.market.util.CurrencyOps;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;

/**
 * In-memory auction cache + escrowed bidding (plugin plan 7.4) — the sole
 * marketplace now ({@code /경매}; the old instant-buy-only {@code /거래소}
 * was retired since a buy-now-priced auction already covers that case). All
 * bid mutation happens on the main thread (GUI clicks), so refunding the
 * previous bidder before accepting a new one is race-free thanks to
 * Bukkit's single-threaded event handling.
 * <p>
 * {@code byExpiry} indexes every listing by its (immutable — a bid never
 * changes {@code endAtMillis}) expiry timestamp, so both {@link #all()} and
 * especially {@link #expired(long)} avoid re-sorting every listing on every
 * call: {@code all()} just walks the already-sorted map, and {@code
 * expired()} only touches the (usually tiny or empty) head of it instead of
 * scanning every still-live auction just to filter them out again.
 */
public final class AuctionManager {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final AuctionRepository repository;
    private final ExecutorService executor;
    private final ConcurrentHashMap<UUID, AuctionListing> listings = new ConcurrentHashMap<>();
    private final ConcurrentSkipListMap<Long, Set<UUID>> byExpiry = new ConcurrentSkipListMap<>();

    public AuctionManager(JavaPlugin plugin, YeowoolCoreAPI core, AuctionRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.core = core;
        this.repository = repository;
        this.executor = executor;
    }

    public void loadAll() throws SQLException {
        for (AuctionListing listing : repository.loadAll()) {
            listings.put(listing.id(), listing);
            indexByExpiry(listing);
        }
        plugin.getLogger().info("경매 " + listings.size() + "건을 불러왔습니다.");
    }

    private void indexByExpiry(AuctionListing listing) {
        byExpiry.computeIfAbsent(listing.endAtMillis(), k -> ConcurrentHashMap.newKeySet()).add(listing.id());
    }

    private void unindexByExpiry(AuctionListing listing) {
        byExpiry.computeIfPresent(listing.endAtMillis(), (k, ids) -> {
            ids.remove(listing.id());
            return ids.isEmpty() ? null : ids;
        });
    }

    public List<AuctionListing> all() {
        List<AuctionListing> result = new ArrayList<>(listings.size());
        for (Set<UUID> ids : byExpiry.values()) {
            for (UUID id : ids) {
                AuctionListing listing = listings.get(id);
                if (listing != null) {
                    result.add(listing);
                }
            }
        }
        return result;
    }

    /** Every listing whose {@code endAtMillis} is at or before {@code now} — only touches the expired head of the index. */
    public List<AuctionListing> expired(long now) {
        List<AuctionListing> result = new ArrayList<>();
        for (Set<UUID> ids : byExpiry.headMap(now, true).values()) {
            for (UUID id : ids) {
                AuctionListing listing = listings.get(id);
                if (listing != null) {
                    result.add(listing);
                }
            }
        }
        return result;
    }

    public Optional<AuctionListing> get(UUID id) {
        return Optional.ofNullable(listings.get(id));
    }

    public List<AuctionListing> byOwner(UUID owner) {
        return listings.values().stream().filter(l -> l.seller().equals(owner)).toList();
    }

    public AuctionListing create(UUID seller, ItemStack item, long startingBid, long buyNowPrice, int minutes, CurrencyType currency) {
        AuctionListing listing = new AuctionListing(UUID.randomUUID(), seller, item.clone(), startingBid, buyNowPrice,
                0, null, currency, System.currentTimeMillis() + minutes * 60_000L, System.currentTimeMillis());
        listings.put(listing.id(), listing);
        indexByExpiry(listing);
        executor.execute(() -> {
            try {
                repository.insert(listing);
            } catch (SQLException e) {
                plugin.getLogger().severe("경매 등록 저장 실패: " + e.getMessage());
            }
        });
        return listing;
    }

    public enum BidResult { SUCCESS, TOO_LOW, INSUFFICIENT_FUNDS, NOT_FOUND, OWN_LISTING }

    public BidResult bid(UUID auctionId, Player bidder, long amount) {
        AuctionListing listing = listings.get(auctionId);
        if (listing == null) {
            return BidResult.NOT_FOUND;
        }
        if (listing.seller().equals(bidder.getUniqueId())) {
            return BidResult.OWN_LISTING;
        }
        if (amount < listing.minNextBid(minIncrement(listing))) {
            return BidResult.TOO_LOW;
        }
        if (!CurrencyOps.hasBalance(core, bidder.getUniqueId(), listing.currency(), amount)) {
            return BidResult.INSUFFICIENT_FUNDS;
        }

        CurrencyOps.modify(core, bidder.getUniqueId(), listing.currency(), -amount, "YeowoolMarket", "경매 입찰: " + listing.id());
        if (listing.hasBid()) {
            refund(listing.currentBidder(), listing.currentBid(), listing.currency(), "경매 입찰 취소(상회 입찰 발생)");
        }

        AuctionListing updated = listing.withBid(bidder.getUniqueId(), amount);
        listings.put(auctionId, updated);
        executor.execute(() -> {
            try {
                repository.updateBid(auctionId, amount, bidder.getUniqueId());
            } catch (SQLException e) {
                plugin.getLogger().severe("경매 입찰 저장 실패: " + e.getMessage());
            }
        });
        return BidResult.SUCCESS;
    }

    public long minIncrement(AuctionListing listing) {
        return Math.max(1, listing.startingBid() / 20); // 5% of the starting bid, at least 1
    }

    /**
     * Buys the listing out immediately at its buy-now price, refunding any
     * existing escrowed bidder first, then settles it in the same call so
     * the buyer gets the item right away instead of waiting for expiry.
     */
    public BidResult buyNow(UUID auctionId, Player buyer, double feePercent) {
        AuctionListing listing = listings.get(auctionId);
        if (listing == null || !listing.hasBuyNow()) {
            return BidResult.NOT_FOUND;
        }
        if (listing.seller().equals(buyer.getUniqueId())) {
            return BidResult.OWN_LISTING;
        }
        if (!CurrencyOps.hasBalance(core, buyer.getUniqueId(), listing.currency(), listing.buyNowPrice())) {
            return BidResult.INSUFFICIENT_FUNDS;
        }

        if (listing.hasBid()) {
            refund(listing.currentBidder(), listing.currentBid(), listing.currency(), "경매 즉시구매로 인한 입찰 취소");
        }
        CurrencyOps.modify(core, buyer.getUniqueId(), listing.currency(), -listing.buyNowPrice(), "YeowoolMarket",
                "경매 즉시구매: " + listing.item().getType());
        settle(listing.withBid(buyer.getUniqueId(), listing.buyNowPrice()), feePercent);
        return BidResult.SUCCESS;
    }

    public enum CancelResult { SUCCESS, NOT_FOUND, NOT_OWNER, HAS_BID }

    /** Only cancelable while no one has bid yet — once there's a bid, the auction has to run its course so the bidder's escrowed money isn't yanked back mid-auction. */
    public CancelResult cancel(UUID auctionId, Player canceller) {
        AuctionListing listing = listings.get(auctionId);
        if (listing == null) {
            return CancelResult.NOT_FOUND;
        }
        if (!listing.seller().equals(canceller.getUniqueId())) {
            return CancelResult.NOT_OWNER;
        }
        if (listing.hasBid()) {
            return CancelResult.HAS_BID;
        }

        listings.remove(auctionId);
        unindexByExpiry(listing);
        executor.execute(() -> {
            try {
                repository.delete(auctionId);
            } catch (SQLException e) {
                plugin.getLogger().severe("경매 취소 삭제 실패: " + e.getMessage());
            }
        });

        var leftover = canceller.getInventory().addItem(listing.item());
        leftover.values().forEach(extra -> canceller.getWorld().dropItemNaturally(canceller.getLocation(), extra));
        return CancelResult.SUCCESS;
    }

    private void refund(UUID uuid, long amount, CurrencyType currency, String reason) {
        core.playerData().load(uuid, Bukkit.getOfflinePlayer(uuid).getName())
                .thenAccept(data -> Bukkit.getScheduler().runTask(plugin, () ->
                        CurrencyOps.modify(core, uuid, currency, amount, "YeowoolMarket", reason)))
                .exceptionally(ex -> {
                    plugin.getLogger().severe("경매 환불 실패 (" + uuid + ", " + amount + currency.displayName() + ", " + reason + "): " + ex.getMessage());
                    return null;
                });
    }

    /** Ends an auction: pays the seller (minus fee) and gives the winner their item, or returns the item if unsold. */
    public void settle(AuctionListing listing, double feePercent) {
        listings.remove(listing.id());
        unindexByExpiry(listing);
        executor.execute(() -> {
            try {
                repository.delete(listing.id());
            } catch (SQLException e) {
                plugin.getLogger().severe("경매 정산 삭제 실패: " + e.getMessage());
            }
        });

        if (!listing.hasBid()) {
            giveItem(listing.seller(), listing.item());
            notify(listing.seller(), "경매 [" + itemName(listing) + "]가 유찰되어 아이템이 반환되었습니다.");
            return;
        }

        long fee = Math.round(listing.currentBid() * (feePercent / 100.0));
        long sellerReceives = listing.currentBid() - fee;
        String currencyLabel = listing.currency().displayName();

        // The seller's "낙찰되었습니다" notice (in-game + Discord DM) must only fire once the
        // payout actually lands - it used to fire unconditionally right after kicking off the
        // async load, so a failed load/modify (DB blip, etc.) silently destroyed the seller's
        // money while still telling them they got paid, with no error logged anywhere.
        core.playerData().load(listing.seller(), Bukkit.getOfflinePlayer(listing.seller()).getName())
                .thenAccept(data -> Bukkit.getScheduler().runTask(plugin, () -> {
                    CurrencyOps.modify(core, listing.seller(), listing.currency(), sellerReceives, "YeowoolMarket",
                            "경매 판매 (수수료 " + fee + currencyLabel + " 소각): " + itemName(listing));
                    String soldMessage = "경매 [" + itemName(listing) + "]가 " + sellerReceives + currencyLabel + "에 낙찰되었습니다.";
                    notify(listing.seller(), soldMessage);
                    // 낙찰자는 아이템이 우편함으로 갈 때(giveItem → core.mailbox()) 이미 알림이 큐에 쌓이므로,
                    // 여기서는 아이템이 아니라 대금을 받는 판매자 쪽만 별도로 알려주면 된다.
                    queueDiscordDm(listing.seller(), soldMessage);
                }))
                .exceptionally(ex -> {
                    plugin.getLogger().severe("경매 정산 대금 지급 실패 (판매자 " + listing.seller() + ", "
                            + sellerReceives + currencyLabel + "): " + ex.getMessage());
                    return null;
                });

        giveItem(listing.currentBidder(), listing.item());
        notify(listing.currentBidder(), "경매 [" + itemName(listing) + "]에 낙찰되었습니다!");
    }

    /**
     * {@code yw_discord_dm_queue}에 알림 요청을 적재한다 - 여울 플러그인들은 디스코드에
     * 직접 접속하지 않는다는 기존 규칙을 그대로 지켜서(YeowoolDiscord 참고), 실제 발송은
     * yeowool-web의 백그라운드 작업이 이 큐를 읽어서 처리한다.
     */
    private void queueDiscordDm(UUID recipient, String message) {
        executor.execute(() -> {
            try (var connection = core.dataSource().getConnection();
                 var insert = connection.prepareStatement(
                         "INSERT INTO yw_discord_dm_queue (uuid, message, created_at) VALUES (?, ?, ?)")) {
                insert.setString(1, recipient.toString());
                insert.setString(2, message);
                insert.setLong(3, System.currentTimeMillis());
                insert.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("디스코드 알림 큐 적재 실패 (" + recipient + "): " + e.getMessage());
            }
        });
    }

    private String itemName(AuctionListing listing) {
        return listing.item().getType().name();
    }

    private void giveItem(UUID uuid, ItemStack item) {
        core.mailbox().deliverOrStore(uuid, item, "YeowoolMarket", "경매 낙찰: " + item.getType());
    }

    private void notify(UUID uuid, String message) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.sendMessage(net.kyori.adventure.text.Component.text(message, net.kyori.adventure.text.format.NamedTextColor.GOLD));
        }
    }
}
