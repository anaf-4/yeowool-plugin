package com.yeowool.life.orders;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.life.orders.OrderCatalog.Taken;
import com.yeowool.life.orders.OrderRepository.Contribution;
import com.yeowool.life.orders.OrderRepository.Daily;
import com.yeowool.life.orders.OrderRepository.GroupOrder;
import com.yeowool.life.orders.OrderRepository.Order;
import com.yeowool.life.orders.OrderRules.Candidate;
import com.yeowool.life.orders.OrderRules.Difficulty;
import com.yeowool.life.orders.OrderRules.Draw;
import com.yeowool.life.orders.OrderRules.FameLevel;
import com.yeowool.life.orders.OrderRules.Settings;
import com.yeowool.life.orders.OrderRules.Tier;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One order system (요리 주문 / 어부 주문): the NPC's daily personal orders (with VIP customers), the
 * cross-server group order and fame; what is ordered comes from the {@link OrderCatalog}. A delivery
 * follows the specs' §7: count items on the main thread, add progress with a conditional UPDATE on the
 * worker, then take the items and pay on the main thread — undoing the progress if the items are gone —
 * and finally complete/reward on the worker (exactly once in the DB).
 */
public final class OrderService {

    /** Everything the window shows, loaded on the worker. {@code group} is null when none is running. */
    record View(String day, List<Order> orders, Daily daily, int fame, GroupOrder group, int myContribution) {
    }

    private static final String SOURCE = "YeowoolLife";
    /** Announcement arg holding the item id (named so for the existing 요리 주문 rows). */
    private static final String ITEM_ARG = "recipe";
    private static final int[] MILESTONES = {50, 90};

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final OrderCatalog catalog;
    private final OrderRepository repository;
    private final OrderRules rules;
    private final Executor executor;
    private final String prefix;
    private final Random random = new Random();
    private final File npcFile;
    private final Set<Integer> npcIds = new LinkedHashSet<>();
    // main thread only
    private final Set<UUID> busy = new HashSet<>();
    private volatile long announcedUntilId;
    private final AtomicBoolean ticking = new AtomicBoolean();
    private final AtomicBoolean polling = new AtomicBoolean();

    public OrderService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, OrderCatalog catalog,
                        OrderRepository repository, OrderRules rules, Executor executor, long announcedUntilId) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.catalog = catalog;
        this.repository = repository;
        this.rules = rules;
        this.executor = executor;
        this.prefix = catalog.id() + "-orders.";
        this.announcedUntilId = announcedUntilId;
        this.npcFile = new File(plugin.getDataFolder(), catalog.id() + "-npcs.yml");
        npcIds.addAll(YamlConfiguration.loadConfiguration(npcFile).getIntegerList("npc-ids"));
    }

    OrderRules rules() {
        return rules;
    }

    OrderRepository repository() {
        return repository;
    }

    OrderCatalog catalog() {
        return catalog;
    }

    /** Message key under this system's prefix ({@code delivered} → {@code cooking-orders.delivered}). */
    String key(String suffix) {
        return prefix + suffix;
    }

    TagResolver itemName(String id) {
        return Placeholder.component(catalog.itemTag(), catalog.name(id));
    }

    // ---- NPC binding (per server) ----

    public boolean isNpc(int npcId) {
        return npcIds.contains(npcId);
    }

    /** Binds or unbinds a Citizens NPC on this server; returns whether it is now bound. */
    public boolean toggleNpc(int npcId) {
        boolean bound = npcIds.add(npcId) || !npcIds.remove(npcId);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("npc-ids", new ArrayList<>(npcIds));
        try {
            yaml.save(npcFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, npcFile.getName() + " 저장 실패", e);
        }
        return bound;
    }

    // ---- items ----

    Difficulty difficultyOf(Order order) {
        return Difficulty.byKey(order.difficulty()).orElse(Difficulty.EASY);
    }

    Optional<Difficulty> groupDifficulty(String itemId) {
        return catalog.all().stream().filter(candidate -> candidate.id().equals(itemId)).findFirst().map(Candidate::difficulty);
    }

    static String today() {
        return LocalDate.now().toString();
    }

    /** A condition-free order spec for counting/taking group deliveries. */
    private static Order groupSpec(GroupOrder group) {
        return new Order(-1, group.itemId(), Difficulty.EASY.key(), false, null, 0, 0, 0, false);
    }

    // ---- opening ----

    /** Main thread: loads (drawing today's orders on first open) and shows the window. */
    public void open(Player player) {
        load(player.getUniqueId(), catalog.offered(player), false);
    }

    private boolean showing(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder() instanceof OrderGui gui && gui.service() == this;
    }

    /** Main thread: reloads the window only if the player still has this system's window open. */
    private void refresh(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && showing(player)) {
            load(uuid, catalog.offered(player), true);
        }
    }

    private void load(UUID uuid, List<Candidate> offered, boolean onlyIfOpen) {
        String day = today();
        executor.execute(() -> {
            try {
                List<Order> orders = repository.orders(uuid, day);
                if (orders.isEmpty() && !offered.isEmpty()) {
                    List<Draw> draws = rules.draw(random, offered).stream().map(draw -> catalog.decorate(random, draw)).toList();
                    if (!draws.isEmpty()) {
                        repository.insertOrdersIfAbsent(uuid, day, draws);
                        orders = repository.orders(uuid, day);
                    }
                }
                Daily daily = repository.daily(uuid, day);
                int fame = repository.fame(uuid);
                GroupOrder group = repository.activeGroup().orElse(null);
                int mine = group == null ? 0 : repository.contribution(group.id(), uuid);
                View view = new View(day, orders, daily, fame, group, mine);
                runMain(() -> {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null && (!onlyIfOpen || showing(online))) {
                        new OrderGui(this, messages, view, online).open(online);
                    }
                });
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, catalog.label() + " 불러오기 실패 (" + uuid + ")", e);
                runMain(() -> sendIfOnline(uuid, key("error")));
            }
        });
    }

    private boolean dayChanged(Player player, OrderGui gui) {
        if (today().equals(gui.view().day())) {
            return false;
        }
        messages.send(player, key("day-changed"));
        player.closeInventory();
        return true;
    }

    /** "35" / "35.5" — a size in mm shown as cm. */
    public static String cm(int mm) {
        return mm % 10 == 0 ? String.valueOf(mm / 10) : String.format("%.1f", mm / 10.0);
    }

    /** The "nothing to deliver" message for an order: 금/대어 VIP, size-conditioned, or plain. */
    private String missingKey(Order order) {
        if (order.vip()) {
            return order.vipKind() == null || order.vipKind().equals("GOLDEN") ? key("no-golden") : key("no-big");
        }
        return order.minSizeMm() > 0 ? key("no-size") : key("no-dishes");
    }

    // ---- personal delivery ----

    /** Main thread: an order slot was clicked. */
    void deliver(Player player, OrderGui gui, Order order, int guiSlot) {
        if (dayChanged(player, gui)) {
            return;
        }
        if (!catalog.exists(order.itemId())) {
            messages.send(player, key("suspended"));
            return;
        }
        UUID uuid = player.getUniqueId();
        if (busy.contains(uuid)) {
            return;
        }
        String day = gui.view().day();
        String name = player.getName();
        if (order.completed()) {
            messages.send(player, key("already-completed"));
            // an all-done bonus lost to an interrupted completion is retried here (claimBonus is once-only)
            busy.add(uuid);
            executor.execute(() -> completeIfFull(uuid, name, day, order, ""));
            return;
        }
        if (order.delivered() >= order.required()) {
            // full but never completed (the completion step was interrupted) — finish it now
            busy.add(uuid);
            gui.showBusy(guiSlot);
            executor.execute(() -> completeIfFull(uuid, name, day, order, ""));
            return;
        }
        int amount = Math.min(catalog.count(player, order), order.required() - order.delivered());
        if (amount <= 0) {
            messages.send(player, missingKey(order), itemName(order.itemId()), Placeholder.unparsed("size", cm(order.minSizeMm())));
            return;
        }
        busy.add(uuid);
        gui.showBusy(guiSlot);
        executor.execute(() -> {
            int added;
            int fame;
            try {
                // clamped to what's left right now — another server may have delivered since the window opened
                added = repository.addDelivered(uuid, day, order, amount);
                fame = repository.fame(uuid);
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, catalog.label() + " 납품 처리 실패 (" + uuid + ")", e);
                finish(uuid, key("error"));
                return;
            }
            if (added == 0) {
                finish(uuid, key("order-full"));
                return;
            }
            runMain(() -> guarded(uuid, () -> takeItems(uuid, name, day, order, added, fame)),
                    catalog.label() + " 납품 " + uuid + " " + order.itemId() + " ×" + added);
        });
    }

    private void takeItems(UUID uuid, String name, String day, Order order, int amount, int fame) {
        Player player = Bukkit.getPlayer(uuid);
        List<Taken> taken = player == null ? null : catalog.take(player, order, false, amount);
        if (taken == null) {
            executor.execute(() -> {
                try {
                    repository.undoDelivered(uuid, day, order.slot(), amount);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, catalog.label() + " 납품 되돌리기 실패 — 수동 확인 필요: " + uuid + " " + day
                            + " 슬롯 " + order.slot() + " -" + amount, e);
                }
            });
            finishNow(uuid, key("dishes-gone"));
            return;
        }
        long base = rules.moneyPerItem(order.itemId(), difficultyOf(order));
        double fameMultiplier = rules.level(fame).multiplier();
        long money = 0;
        for (Taken item : taken) {
            money += rules.itemMoney(base, item.multiplier(), order.vip(), fameMultiplier);
        }
        String reason = catalog.label() + " 납품 (" + order.itemId() + " ×" + amount + ")";
        if (money > 0 && !core.economyData().modifyBalance(uuid, money, SOURCE, reason)) {
            plugin.getLogger().warning(catalog.label() + " 온 지급 실패 — 수동 지급 필요: " + uuid + " " + money + "온 (" + reason + ")");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        messages.send(player, key("delivered"),
                itemName(order.itemId()),
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.unparsed("money", String.format("%,d", money)));
        String detail = taken.isEmpty() ? "" : taken.get(0).detail();
        executor.execute(() -> completeIfFull(uuid, name, day, order, detail));
    }

    /** Worker: completion rewards, VIP announcement and the all-done bonus — each at most once via the DB. */
    private void completeIfFull(UUID uuid, String name, String day, Order order, String detail) {
        Settings settings = rules.settings();
        try {
            boolean completedNow = repository.completeOrder(uuid, day, order.slot());
            int fameGain = 0;
            int newFame = 0;
            long jobXp = 0;
            if (completedNow) {
                Tier tier = rules.tier(difficultyOf(order));
                long stardust = order.vip() ? settings.vip().stardust() : tier.stardust();
                fameGain = order.vip() ? settings.vip().fame() : tier.fame();
                jobXp = order.vip() ? settings.vip().jobXp() : tier.jobXp();
                core.stardust().grantCapped(uuid, stardust, SOURCE, catalog.label() + " 완료 (" + order.itemId() + ")", catalog.id(), settings.stardustDailyCap());
                newFame = repository.addFame(uuid, fameGain);
                if (order.vip()) {
                    repository.announce(key("broadcast.vip-done"), Map.of("player", name, ITEM_ARG, order.itemId(), "detail", detail));
                }
            }
            // checked even when this call didn't complete anything, so a bonus lost to an interrupted run is still paid (claimBonus is once-only)
            boolean bonus = repository.allCompleted(uuid, day) && repository.claimBonus(uuid, day);
            if (bonus) {
                core.stardust().grantCapped(uuid, settings.allDoneStardust(), SOURCE, catalog.label() + " 모두 완료", catalog.id(), settings.stardustDailyCap());
            }
            if (!completedNow && !bonus) {
                finish(uuid, null);
                return;
            }
            int gained = fameGain;
            int fame = newFame;
            long xp = jobXp;
            runMain(() -> guarded(uuid, () -> completed(uuid, name, order, completedNow, gained, fame, xp, bonus)),
                    bonus ? catalog.label() + " 완료 보너스 " + settings.allDoneMoney() + "온 (" + uuid + ")" : null);
        } catch (SQLException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, catalog.label() + " 완료 처리 실패 — 수동 확인 필요: " + uuid + " " + day + " 슬롯 " + order.slot(), e);
            finish(uuid, key("error"));
        }
    }

    private void completed(UUID uuid, String name, Order order, boolean completedNow, int fameGain, int newFame, long jobXp, boolean bonus) {
        Settings settings = rules.settings();
        Player player = Bukkit.getPlayer(uuid);
        if (bonus && settings.allDoneMoney() > 0) {
            payMain(uuid, settings.allDoneMoney(), catalog.label() + " 모두 완료 보너스");
        }
        FameLevel before = rules.level(newFame - fameGain);
        FameLevel after = rules.level(newFame);
        if (player != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
            if (completedNow) {
                if (jobXp > 0) {
                    catalog.grantJobXp(player, jobXp);
                }
                messages.send(player, key("order-completed"),
                        itemName(order.itemId()),
                        Placeholder.unparsed("fame", String.valueOf(fameGain)));
            }
            if (bonus) {
                messages.send(player, key("all-done"),
                        Placeholder.unparsed("money", String.format("%,d", settings.allDoneMoney())),
                        Placeholder.unparsed("stardust", String.valueOf(settings.allDoneStardust())));
            }
        }
        if (after.fame() > before.fame()) {
            if (player != null) {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
                messages.send(player, key("level-up"), Placeholder.unparsed("level", after.name()));
            }
            // every level passed in one step gets its commands (e.g. +5 fame over two thresholds)
            for (FameLevel level : settings.fameLevels()) {
                if (level.fame() > before.fame() && level.fame() <= after.fame()) {
                    for (String command : settings.levelUpCommands().getOrDefault(level.name(), List.of())) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", name));
                    }
                }
            }
        }
        busy.remove(uuid);
        refresh(uuid);
    }

    // ---- 교체 ----

    /** Main thread: 교체 was armed and then an order was clicked. */
    void reroll(Player player, OrderGui gui, Order order, int guiSlot) {
        if (dayChanged(player, gui)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (busy.contains(uuid)) {
            return;
        }
        // an order whose item is gone from the config can be swapped for free, any time
        boolean free = !catalog.exists(order.itemId()) && !order.completed();
        if (!free) {
            String refusal = order.vip() ? key("reroll-vip")
                    : order.completed() || order.delivered() > 0 ? key("reroll-started")
                    : gui.view().daily().rerolled() ? key("reroll-used") : null;
            if (refusal != null) {
                messages.send(player, refusal);
                return;
            }
        }
        Set<String> today = new HashSet<>();
        gui.view().orders().forEach(o -> today.add(o.itemId()));
        Optional<Draw> draw = rules.drawReplacement(random, catalog.offered(player), today).map(d -> catalog.decorate(random, d));
        if (draw.isEmpty()) {
            messages.send(player, key("no-recipes"));
            return;
        }
        long cost = free ? 0 : rules.settings().rerollCost();
        if (cost > 0 && !core.economyData().modifyBalance(uuid, -cost, SOURCE, catalog.label() + " 교체")) {
            messages.send(player, key("no-money"), Placeholder.unparsed("cost", String.format("%,d", cost)));
            return;
        }
        busy.add(uuid);
        gui.showBusy(guiSlot);
        String day = gui.view().day();
        executor.execute(() -> {
            boolean claimed = false;
            boolean replaced = false;
            try {
                claimed = !free && repository.claimReroll(uuid, day);
                if (free || claimed) {
                    replaced = repository.replaceOrder(uuid, day, order, draw.get(), free);
                }
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, catalog.label() + " 교체 실패 (" + uuid + ")", e);
            }
            if (claimed && !replaced) {
                // the money is refunded below, so give the day's 교체 back too
                try {
                    repository.releaseReroll(uuid, day);
                } catch (SQLException | RuntimeException e) {
                    plugin.getLogger().log(Level.SEVERE, catalog.label() + " 교체 횟수 되돌리기 실패 — 수동 확인 필요: " + uuid + " " + day, e);
                }
            }
            boolean ok = replaced;
            runMain(() -> guarded(uuid, () -> {
                if (!ok && cost > 0) {
                    payMain(uuid, cost, catalog.label() + " 교체 취소 환불");
                }
                if (ok) {
                    sendIfOnline(uuid, key("rerolled"), itemName(draw.get().itemId()));
                } else {
                    sendIfOnline(uuid, key("reroll-failed"));
                }
                busy.remove(uuid);
                refresh(uuid);
            }), !ok && cost > 0 ? catalog.label() + " 교체 환불 " + cost + "온 (" + uuid + ")" : null);
        });
    }

    // ---- group order ----

    /** Main thread: the group-order slot was clicked. Anyone may deliver; no fame, money right away. */
    void deliverGroup(Player player, OrderGui gui, int guiSlot) {
        GroupOrder group = gui.view().group();
        if (group == null) {
            messages.send(player, key("group-none"));
            return;
        }
        if (System.currentTimeMillis() >= group.endsAt()) {
            messages.send(player, key("group-ended"));
            return;
        }
        if (!catalog.exists(group.itemId())) {
            messages.send(player, key("suspended"));
            return;
        }
        UUID uuid = player.getUniqueId();
        if (busy.contains(uuid)) {
            return;
        }
        Order spec = groupSpec(group);
        int have = catalog.count(player, spec);
        int amount = Math.min(have, group.target() - group.progress());
        if (amount <= 0) {
            messages.send(player, have == 0 ? key("no-dishes") : key("group-full"), itemName(group.itemId()));
            return;
        }
        busy.add(uuid);
        gui.showBusy(guiSlot);
        String name = player.getName();
        executor.execute(() -> {
            Optional<OrderRepository.GroupAdd> result;
            try {
                // clamped to what's left right now — other servers deliver to the same order
                result = repository.addGroupProgress(group.id(), uuid, name, amount, System.currentTimeMillis());
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "단체 " + catalog.label() + " 납품 처리 실패 (" + uuid + ")", e);
                finish(uuid, key("error"));
                return;
            }
            if (result.isEmpty()) {
                finish(uuid, key("group-ended"));
                return;
            }
            OrderRepository.GroupAdd add = result.get();
            if (add.added() == 0) {
                finish(uuid, key("group-full"));
                return;
            }
            runMain(() -> guarded(uuid, () -> takeGroupItems(uuid, group, add.added(), add.progress())),
                    "단체 " + catalog.label() + " 납품 " + uuid + " #" + group.id() + " ×" + add.added());
        });
    }

    private void takeGroupItems(UUID uuid, GroupOrder group, int amount, int newProgress) {
        Player player = Bukkit.getPlayer(uuid);
        List<Taken> taken = player == null ? null : catalog.take(player, groupSpec(group), true, amount);
        if (taken == null) {
            executor.execute(() -> {
                try {
                    repository.undoGroupProgress(group.id(), uuid, amount);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "단체 " + catalog.label() + " 납품 되돌리기 실패 — 수동 확인 필요: " + uuid + " #" + group.id() + " -" + amount, e);
                }
            });
            finishNow(uuid, key("dishes-gone"));
            return;
        }
        long base = rules.moneyPerItem(group.itemId(), groupDifficulty(group.itemId()).orElse(Difficulty.EASY));
        long money = 0;
        for (Taken item : taken) {
            money += rules.groupItemMoney(base, item.multiplier());
        }
        String reason = "단체 " + catalog.label() + " 납품 (#" + group.id() + " ×" + amount + ")";
        if (money > 0 && !core.economyData().modifyBalance(uuid, money, SOURCE, reason)) {
            plugin.getLogger().warning("단체 " + catalog.label() + " 온 지급 실패 — 수동 지급 필요: " + uuid + " " + money + "온 (" + reason + ")");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        messages.send(player, key("group-delivered"),
                itemName(group.itemId()),
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.unparsed("money", String.format("%,d", money)),
                Placeholder.unparsed("progress", String.format("%,d", newProgress)),
                Placeholder.unparsed("target", String.format("%,d", group.target())));
        executor.execute(() -> {
            try {
                int before = newProgress - amount;
                for (int percent : MILESTONES) {
                    int threshold = (int) Math.ceil(group.target() * percent / 100.0);
                    if (before < threshold && newProgress >= threshold && newProgress < group.target()) {
                        repository.announce(key("broadcast.group-milestone"), Map.of(ITEM_ARG, group.itemId(),
                                "percent", String.valueOf(percent), "progress", String.valueOf(newProgress), "target", String.valueOf(group.target())));
                    }
                }
                if (newProgress >= group.target() && repository.markGroupDone(group.id())) {
                    settle(group);
                }
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "단체 " + catalog.label() + " 달성 처리 실패 (#" + group.id() + ") — 다음 정기 점검에서 다시 시도", e);
            } finally {
                finish(uuid, null);
            }
        });
    }

    /** Worker, every minute on every server: expire, settle and start group orders (each exactly once via the DB). */
    public void tickGroups() {
        if (!ticking.compareAndSet(false, true)) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            // full orders whose DONE flip never happened (interrupted delivery) — also covers expired full ones
            // ponytail: a delivery that just filled the order but hasn't confirmed yet can be flipped DONE here and then undone; sub-second window.
            for (GroupOrder group : repository.fullActiveGroups()) {
                repository.markGroupDone(group.id());
            }
            for (GroupOrder group : repository.expiredActiveGroups(now)) {
                if (repository.markGroupFailed(group.id(), false)) {
                    announceFailed(group);
                }
            }
            for (GroupOrder group : repository.doneUnsettledGroups()) {
                settle(group);
            }
            Optional<ZonedDateTime> start = rules.currentGroupStart(ZonedDateTime.now());
            long durationMillis = rules.settings().group().durationHours() * 3_600_000L;
            if (start.isPresent()) {
                long startsAt = start.get().toInstant().toEpochMilli();
                // a slot missed or blocked for more than half its window is skipped — wait for the next one
                if (now <= startsAt + durationMillis / 2 && !repository.groupBlocked(startsAt)) {
                    rules.pickGroupItem(random, catalog.all()).ifPresent(item -> {
                        try {
                            startGroup(item.id(), rules.groupTarget(item.difficulty()), startsAt, startsAt + durationMillis);
                        } catch (SQLException e) {
                            plugin.getLogger().log(Level.SEVERE, "단체 " + catalog.label() + " 시작 실패", e);
                        }
                    });
                }
            }
        } catch (SQLException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "단체 " + catalog.label() + " 정기 점검 실패", e);
        } finally {
            ticking.set(false);
        }
    }

    /** Worker: starts a group order unless one is running (or this start already happened); announces it. */
    boolean startGroup(String itemId, int target, long startsAt, long endsAt) throws SQLException {
        if (repository.startGroup(itemId, target, startsAt, endsAt).isEmpty()) {
            return false;
        }
        long hours = Math.max(1, Math.round((endsAt - System.currentTimeMillis()) / 3_600_000.0));
        repository.announce(key("broadcast.group-started"),
                Map.of(ITEM_ARG, itemId, "target", String.format("%,d", target), "hours", String.valueOf(hours)));
        return true;
    }

    /** Worker: fails the running group order (admin 단체종료); false if none was running. */
    boolean endGroup() throws SQLException {
        Optional<GroupOrder> group = repository.activeGroup();
        if (group.isEmpty() || !repository.markGroupFailed(group.get().id(), true)) {
            return false;
        }
        announceFailed(group.get());
        return true;
    }

    private void announceFailed(GroupOrder group) throws SQLException {
        repository.announce(key("broadcast.group-failed"), Map.of(ITEM_ARG, group.itemId(),
                "progress", String.format("%,d", group.progress()), "target", String.format("%,d", group.target())));
    }

    /** Worker: the one server that claims settlement grants 별조각 to participants and the top 3, then announces. */
    private void settle(GroupOrder group) throws SQLException {
        // read before claiming so a failed read leaves it unsettled for the next tick
        List<Contribution> contributions = repository.contributions(group.id());
        if (!repository.claimSettle(group.id())) {
            return;
        }
        OrderRules.Group settings = rules.settings().group();
        for (int i = 0; i < contributions.size(); i++) {
            Contribution contribution = contributions.get(i);
            long stardust = (contribution.amount() >= settings.minContribution() ? settings.participationStardust() : 0)
                    + (i < settings.rankStardust().size() ? settings.rankStardust().get(i) : 0);
            core.stardust().grant(contribution.player(), stardust, SOURCE, "단체 " + catalog.label() + " 달성 (#" + group.id() + ")");
        }
        Map<String, String> args = new LinkedHashMap<>();
        args.put(ITEM_ARG, group.itemId());
        String[] ranks = {"first", "second", "third"};
        for (int i = 0; i < ranks.length; i++) {
            args.put(ranks[i], i < contributions.size() ? contributions.get(i).name() : "-");
        }
        repository.announce(key("broadcast.group-done"), args);
    }

    // ---- announcements (worker, every ~20 s on every server) ----

    public void pollAnnouncements() {
        if (!polling.compareAndSet(false, true)) {
            return; // previous poll still running (slow DB) — don't broadcast the same rows twice
        }
        try {
            List<OrderRepository.Announcement> announcements = repository.announcementsAfter(announcedUntilId);
            if (announcements.isEmpty()) {
                return;
            }
            announcedUntilId = announcements.get(announcements.size() - 1).id();
            runMain(() -> announcements.forEach(this::broadcast), null);
        } catch (SQLException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, catalog.label() + " 공지 조회 실패", e);
        } finally {
            polling.set(false);
        }
    }

    private void broadcast(OrderRepository.Announcement announcement) {
        List<TagResolver> placeholders = new ArrayList<>();
        announcement.args().forEach((name, value) -> placeholders.add(name.equals(ITEM_ARG)
                ? itemName(value) : Placeholder.unparsed(name, value)));
        messages.broadcast(announcement.key(), placeholders.toArray(TagResolver[]::new));
    }

    // ---- helpers ----

    /** Main thread: pays the online player now, or queues it for whichever server they're on. */
    private void payMain(UUID uuid, long amount, String reason) {
        if (Bukkit.getPlayer(uuid) != null && core.economyData().modifyBalance(uuid, amount, SOURCE, reason)) {
            return;
        }
        executor.execute(() -> {
            try {
                core.payouts().enqueue(uuid, amount, SOURCE, reason);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, catalog.label() + " 온 장부 기록 실패 — 수동 지급 필요: " + uuid + " " + amount + "온 (" + reason + ")", e);
            }
        });
    }

    /** Main thread: runs one step of a click's chain; an unexpected exception still releases the click lock. */
    private void guarded(UUID uuid, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, catalog.label() + " 처리 실패 — 수동 확인 필요 (" + uuid + ")", e);
            finishNow(uuid, key("error"));
        }
    }

    /** Worker → main: clears the click lock, tells the player {@code key} (if any) and refreshes the window. */
    private void finish(UUID uuid, String key) {
        runMain(() -> finishNow(uuid, key), null);
    }

    private void finishNow(UUID uuid, String key) {
        busy.remove(uuid);
        if (key != null) {
            sendIfOnline(uuid, key);
        }
        refresh(uuid);
    }

    private void sendIfOnline(UUID uuid, String key, TagResolver... placeholders) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            messages.send(player, key, placeholders);
        }
    }

    void reply(CommandSender sender, String key, TagResolver... placeholders) {
        runMain(() -> messages.send(sender, key, placeholders), null);
    }

    private void runMain(Runnable task) {
        runMain(task, null);
    }

    /** Schedules on the main thread; while the plugin is disabling, logs {@code lostWork} (if given) for manual follow-up instead. */
    private void runMain(Runnable task, String lostWork) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        } else if (lostWork != null) {
            plugin.getLogger().warning("플러그인 종료 중이라 처리하지 못함 — 수동 처리 필요: " + lostWork);
        }
    }

    // ---- config ----

    /**
     * Reads an order config block ({@code cooking-orders} / {@code fishing-orders}); anything missing falls back
     * to {@code defaults}. {@code unit} names the money keys ({@code money-per-<unit>}), {@code multiplierKey}
     * the normal/silver/golden block ({@code quality-multiplier} / {@code star-multiplier}); {@code overrideId} maps an
     * {@code overrides} key to the catalog's item id.
     */
    public static Settings settings(ConfigurationSection config, Logger log, String unit, String multiplierKey, Settings defaults,
                                    UnaryOperator<String> overrideId) {
        String path = config.getName();
        Map<Difficulty, Tier> tiers = new EnumMap<>(Difficulty.class);
        for (Difficulty difficulty : Difficulty.values()) {
            String key = "difficulty." + difficulty.key() + ".";
            Tier fallback = defaults.tiers().get(difficulty);
            tiers.put(difficulty, new Tier(config.getInt(key + "amount-min", fallback.amountMin()),
                    config.getInt(key + "amount-max", fallback.amountMax()),
                    config.getLong(key + "money-per-" + unit, fallback.moneyPerItem()),
                    config.getLong(key + "stardust", fallback.stardust()),
                    config.getInt(key + "fame", fallback.fame()),
                    config.getLong(key + "job-xp", fallback.jobXp())));
        }
        List<Double> fallbackMultipliers = defaults.itemMultipliers();
        List<Double> itemMultipliers = List.of(config.getDouble(multiplierKey + ".normal", fallbackMultipliers.get(0)),
                config.getDouble(multiplierKey + ".silver", fallbackMultipliers.get(1)),
                config.getDouble(multiplierKey + ".golden", fallbackMultipliers.get(2)));
        OrderRules.Vip v = defaults.vip();
        OrderRules.Vip vip = new OrderRules.Vip(config.getInt("vip.chance-percent", v.chancePercent()),
                config.getInt("vip.amount-min", v.amountMin()), config.getInt("vip.amount-max", v.amountMax()),
                config.getDouble("vip.money-multiplier", v.moneyMultiplier()), config.getLong("vip.stardust", v.stardust()),
                config.getInt("vip.fame", v.fame()), config.getLong("vip.job-xp", v.jobXp()));

        OrderRules.Group g = defaults.group();
        List<DayOfWeek> days = new ArrayList<>();
        if (config.isList("group.days")) {
            for (String day : config.getStringList("group.days")) {
                try {
                    days.add(DayOfWeek.valueOf(day.trim().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    log.warning(path + ".group.days의 '" + day + "'는 MONDAY~SUNDAY 중 하나여야 합니다 — 건너뜁니다.");
                }
            }
        } else {
            days.addAll(g.days());
        }
        LocalTime startTime;
        try {
            startTime = LocalTime.parse(config.getString("group.start-time", g.startTime().toString()));
        } catch (DateTimeParseException e) {
            log.warning(path + ".group.start-time은 \"19:00\" 형식이어야 합니다 — " + g.startTime() + "으로 둡니다.");
            startTime = g.startTime();
        }
        OrderRules.Group group = new OrderRules.Group(days, startTime,
                Math.max(1, config.getInt("group.duration-hours", g.durationHours())),
                config.getInt("group.target.easy", g.easyTarget()), config.getInt("group.target.normal", g.normalTarget()),
                config.getDouble("group.money-share", g.moneyShare()), config.getInt("group.min-contribution", g.minContribution()),
                config.getLong("group.participation-stardust", g.participationStardust()),
                config.isList("group.rank-stardust") ? config.getLongList("group.rank-stardust") : g.rankStardust());

        List<FameLevel> fameLevels = new ArrayList<>();
        if (config.isList("fame-levels")) {
            for (Map<?, ?> raw : config.getMapList("fame-levels")) {
                try {
                    fameLevels.add(new FameLevel(String.valueOf(raw.get("name")), ((Number) raw.get("fame")).intValue(),
                            ((Number) raw.get("multiplier")).doubleValue()));
                } catch (RuntimeException e) {
                    log.warning(path + ".fame-levels 항목이 잘못되었습니다: " + raw);
                }
            }
        } else {
            fameLevels.addAll(defaults.fameLevels());
        }
        fameLevels.sort(Comparator.comparingInt(FameLevel::fame));
        if (fameLevels.isEmpty() || fameLevels.get(0).fame() > 0) {
            fameLevels.add(0, defaults.fameLevels().get(0));
        }

        Map<String, List<String>> levelUpCommands = new LinkedHashMap<>();
        ConfigurationSection commands = config.getConfigurationSection("level-up-commands");
        if (commands != null) {
            for (String level : commands.getKeys(false)) {
                levelUpCommands.put(level, commands.getStringList(level));
            }
        }
        Map<String, OrderRules.Override> overrides = new LinkedHashMap<>();
        ConfigurationSection overrideSection = config.getConfigurationSection("overrides");
        if (overrideSection != null) {
            for (String id : overrideSection.getKeys(false)) {
                String difficultyKey = overrideSection.getString(id + ".difficulty");
                Difficulty difficulty = difficultyKey == null ? null : Difficulty.byKey(difficultyKey).orElse(null);
                if (difficultyKey != null && difficulty == null) {
                    log.warning(path + ".overrides." + id + ".difficulty는 easy/normal/hard 중 하나여야 합니다 — 무시합니다.");
                }
                overrides.put(overrideId.apply(id), new OrderRules.Override(difficulty, overrideSection.getLong(id + ".money-per-" + unit, 0)));
            }
        }
        return new Settings(Math.max(1, Math.min(3, config.getInt("orders-per-day", defaults.ordersPerDay()))), tiers, itemMultipliers,
                config.getLong("all-done-bonus.money", defaults.allDoneMoney()), config.getLong("all-done-bonus.stardust", defaults.allDoneStardust()),
                config.getLong("reroll-cost", defaults.rerollCost()), config.getLong("stardust-daily-cap", defaults.stardustDailyCap()), vip, group,
                fameLevels, levelUpCommands, overrides);
    }
}
