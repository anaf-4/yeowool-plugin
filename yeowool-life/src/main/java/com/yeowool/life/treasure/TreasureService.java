package com.yeowool.life.treasure;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Treasure map flows: rolling a map drop, the per-second direction hint,
 * digging, and the cross-server legendary announcement. Maps are real items
 * (tradeable); the DB row decides whether a map can still be dug.
 */
public final class TreasureService {

    public record TierReward(long moneyMin, long moneyMax, int itemRolls) {
    }

    public record Settings(String digWorld, int centerX, int centerZ, int minRadius, int maxRadius, long expireMillis,
                           int dailyLimit, double digRadius, Map<String, Double> dropChances,
                           Map<TreasureTier, Integer> tierWeights, Map<TreasureTier, TierReward> rewards) {
    }

    private static final String SOURCE = "YeowoolLife";
    private static final double NEAR_DISTANCE = 20;

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final TreasureRepository repository;
    private final TreasureMapItem mapItem;
    private final Executor executor;
    private final Settings settings;
    private final Random random = new Random();
    private final Set<Long> digging = ConcurrentHashMap.newKeySet();
    private volatile long announcedUntil = System.currentTimeMillis();

    public TreasureService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, TreasureRepository repository,
                           TreasureMapItem mapItem, Executor executor, Settings settings) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.mapItem = mapItem;
        this.executor = executor;
        this.settings = settings;
    }

    public TreasureRepository repository() {
        return repository;
    }

    public TreasureMapItem mapItem() {
        return mapItem;
    }

    // ---- getting a map ----

    /** Main thread: a mining/fishing/hunting action just finished. */
    public void onAction(Player player, String activity) {
        double chance = settings.dropChances().getOrDefault(activity, 0.0);
        if (chance <= 0 || random.nextDouble() >= chance) {
            return;
        }
        grant(player, TreasureRules.pickTier(random, settings.tierWeights()), true);
    }

    /**
     * Main thread. Creates the map row and hands the item over (inventory, or
     * mailbox if full). Natural drops respect the daily limit; admin grants don't.
     */
    public void grant(Player player, TreasureTier tier, boolean natural) {
        UUID uuid = player.getUniqueId();
        int[] spot = TreasureRules.randomSpot(random, settings.centerX(), settings.centerZ(), settings.minRadius(), settings.maxRadius());
        long now = System.currentTimeMillis();
        long expiresAt = now + settings.expireMillis();
        long startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        executor.execute(() -> {
            long id;
            try {
                // ponytail: count and insert aren't atomic — two simultaneous drops can exceed the daily limit by one; drops are rare.
                if (natural && repository.countFoundSince(uuid, startOfDay) >= settings.dailyLimit()) {
                    return;
                }
                id = repository.insertMap(uuid, tier, spot[0], spot[1], now, expiresAt, natural);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "보물지도 생성 실패 (" + uuid + ")", e);
                return;
            }
            TreasureMapItem.MapData data = new TreasureMapItem.MapData(id, tier, spot[0], spot[1], expiresAt);
            runOnMain(() -> {
                core.mailbox().deliverOrStore(uuid, mapItem.create(data), SOURCE, "보물지도");
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    messages.send(online, "treasure.found", Placeholder.unparsed("tier", tier.label()));
                    online.playSound(online.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.8f);
                }
            }, "보물지도 #" + id + " 지급 (" + uuid + ")");
        });
    }

    // ---- hint (main thread, every second) ----

    public void showHints() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Optional<TreasureMapItem.MapData> data = mapItem.read(player.getInventory().getItemInMainHand());
            if (data.isPresent()) {
                player.sendActionBar(hint(player, data.get(), now));
            }
        }
    }

    private Component hint(Player player, TreasureMapItem.MapData data, long now) {
        if (data.expired(now)) {
            return messages.resolveRaw("treasure.hint-expired");
        }
        if (!player.getWorld().getName().equals(settings.digWorld())) {
            return messages.resolveRaw("treasure.hint-other-world");
        }
        Location loc = player.getLocation();
        double dx = data.x() + 0.5 - loc.getX();
        double dz = data.z() + 0.5 - loc.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance <= settings.digRadius()) {
            return messages.resolveRaw("treasure.hint-here");
        }
        String rough = String.format("%,d", TreasureRules.roughDistance(distance));
        if (distance <= NEAR_DISTANCE) {
            return messages.resolveRaw("treasure.hint-near", Placeholder.unparsed("distance", rough));
        }
        return messages.resolveRaw("treasure.hint",
                Placeholder.unparsed("direction", TreasureRules.direction(dx, dz)),
                Placeholder.unparsed("distance", rough));
    }

    // ---- digging ----

    /** Main thread: the player sneak-right-clicked a block while holding {@code data}'s map. */
    public void dig(Player player, TreasureMapItem.MapData data) {
        if (!player.getWorld().getName().equals(settings.digWorld())) {
            player.sendActionBar(messages.resolveRaw("treasure.wrong-world"));
            return;
        }
        long now = System.currentTimeMillis();
        if (data.expired(now)) {
            player.sendActionBar(messages.resolveRaw("treasure.expired"));
            return;
        }
        Location loc = player.getLocation();
        if (Math.hypot(data.x() + 0.5 - loc.getX(), data.z() + 0.5 - loc.getZ()) > settings.digRadius()) {
            player.sendActionBar(messages.resolveRaw("treasure.nothing-here"));
            return;
        }
        if (!digging.add(data.id())) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        // Take the map before the async claim so it can't be dropped or traded away mid-dig and resold as a "fresh" map.
        ItemStack held = player.getInventory().getItemInMainHand();
        ItemStack taken = held.asOne();
        player.getInventory().setItemInMainHand(held.getAmount() > 1 ? held.asQuantity(held.getAmount() - 1) : null);
        executor.execute(() -> {
            boolean claimed;
            try {
                claimed = repository.claim(data.id(), uuid, name, now);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "보물 발굴 처리 실패 (지도 #" + data.id() + ")", e);
                digging.remove(data.id());
                runOnMain(() -> {
                    core.mailbox().deliverOrStore(uuid, taken, SOURCE, "보물지도 반환");
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null) {
                        messages.send(online, "treasure.error");
                    }
                }, "보물지도 #" + data.id() + " 반환 (" + uuid + ")");
                return;
            }
            List<ItemStack> pool = List.of();
            if (claimed) {
                try {
                    pool = repository.loadRewards(data.tier());
                } catch (SQLException | RuntimeException e) {
                    plugin.getLogger().log(Level.SEVERE, "보물 보상 후보 불러오기 실패 (" + data.tier() + ") — 온만 지급", e);
                }
            }
            List<ItemStack> rewardPool = pool;
            runOnMain(() -> {
                digging.remove(data.id());
                finishDig(uuid, data, claimed, rewardPool);
            }, "보물 발굴 보상 (" + uuid + ", 지도 #" + data.id() + ", claimed=" + claimed + ")");
        });
    }

    private void finishDig(UUID uuid, TreasureMapItem.MapData data, boolean claimed, List<ItemStack> pool) {
        Player player = Bukkit.getPlayer(uuid);
        if (!claimed) {
            if (player != null) {
                messages.send(player, "treasure.already-dug");
            }
            return;
        }
        TierReward reward = settings.rewards().get(data.tier());
        long money = TreasureRules.randomMoney(random, reward.moneyMin(), reward.moneyMax());
        List<ItemStack> items = TreasureRules.pickItems(pool, reward.itemRolls(), random);
        for (ItemStack item : items) {
            core.mailbox().deliverOrStore(uuid, item.clone(), SOURCE, "보물 발굴 (" + data.tier().label() + ")");
        }
        if (player == null) {
            // ponytail: sub-tick window between the claim and here — money can't be credited offline, so leave a trail for staff.
            plugin.getLogger().warning("보물 발굴 직후 접속 종료로 온 보상 미지급 — 수동 지급 필요: " + uuid + " " + money + "온 (지도 #" + data.id() + ")");
            return;
        }
        if (money > 0 && !core.economyData().modifyBalance(uuid, money, SOURCE, "보물 발굴 (" + data.tier().label() + ")")) {
            plugin.getLogger().warning("보물 온 보상 지급 실패 — 수동 지급 필요: " + uuid + " " + money + "온 (지도 #" + data.id() + ")");
        }
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 0.5, 0), 40, 0.8, 0.5, 0.8);
        player.playSound(loc, data.tier() == TreasureTier.LEGENDARY ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        messages.send(player, "treasure.dug",
                Placeholder.unparsed("tier", data.tier().label()),
                Placeholder.unparsed("money", String.format("%,d", money)),
                Placeholder.unparsed("items", String.valueOf(items.size())));
    }


    // ---- legendary announcement (executor, every minute on every server) ----

    public void announceLegendaryDigs() {
        try {
            List<TreasureRepository.LegendaryDig> digs = repository.legendaryDugSince(announcedUntil);
            if (digs.isEmpty()) {
                return;
            }
            announcedUntil = digs.get(digs.size() - 1).dugAt();
            runOnMain(() -> {
                for (TreasureRepository.LegendaryDig dig : digs) {
                    messages.broadcast("treasure.legendary-broadcast",
                            Placeholder.unparsed("player", dig.diggerName() == null ? "누군가" : dig.diggerName()));
                }
            }, null);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "전설 보물 공지 조회 실패", e);
        }
    }

    /** Schedules on the main thread; while the plugin is disabling, logs {@code lostWork} (if given) for manual follow-up instead. */
    private void runOnMain(Runnable task, String lostWork) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        } else if (lostWork != null) {
            plugin.getLogger().warning("플러그인 종료 중이라 처리하지 못함 — 수동 처리 필요: " + lostWork);
        }
    }
}
