package com.yeowool.life.cooking.orders;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.life.cooking.addcook.AddCookRecipeIndex.RecipeEntry;
import com.yeowool.life.cooking.orders.CookingOrderRepository.Contribution;
import com.yeowool.life.cooking.orders.CookingOrderRepository.Daily;
import com.yeowool.life.cooking.orders.CookingOrderRepository.GroupOrder;
import com.yeowool.life.cooking.orders.CookingOrderRepository.Order;
import com.yeowool.life.cooking.orders.CookingOrderRules.Difficulty;
import com.yeowool.life.cooking.orders.CookingOrderRules.Draw;
import com.yeowool.life.cooking.orders.CookingOrderRules.FameLevel;
import com.yeowool.life.cooking.orders.CookingOrderRules.Recipe;
import com.yeowool.life.cooking.orders.CookingOrderRules.Settings;
import com.yeowool.life.cooking.orders.CookingOrderRules.Tier;
import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 요리 주문: the restaurant NPC's daily personal orders (with VIP customers), the cross-server group
 * order and cook fame. A delivery follows spec §7: count dishes on the main thread, add progress with a
 * conditional UPDATE on the worker, then take the dishes and pay on the main thread — undoing the
 * progress if the dishes are gone — and finally complete/reward on the worker (exactly once in the DB).
 */
public final class CookingOrderService {

    /** Everything the restaurant window shows, loaded on the worker. {@code group} is null when none is running. */
    record View(String day, List<Order> orders, Daily daily, int fame, GroupOrder group, int myContribution) {
    }

    /** One accepted dish item (ItemsAdder namespaced id, or a vanilla material name) and its quality index. */
    private record Dish(String key, int quality) {
    }

    private static final String SOURCE = "YeowoolLife";
    private static final String CAP_KEY = "cooking";
    private static final int STORAGE_SLOTS = 36;
    private static final int[] MILESTONES = {50, 90};

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final CookingOrderRepository repository;
    private final CookingOrderRules rules;
    private final Executor executor;
    private final Map<String, RecipeEntry> recipes = new LinkedHashMap<>();
    private final Random random = new Random();
    private final File npcFile;
    private final Set<Integer> npcIds = new LinkedHashSet<>();
    // main thread only
    private final Set<UUID> busy = new HashSet<>();
    private volatile long announcedUntilId;
    private final AtomicBoolean ticking = new AtomicBoolean();

    public CookingOrderService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, CookingOrderRepository repository,
                               CookingOrderRules rules, Executor executor, List<RecipeEntry> recipes, long announcedUntilId) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.rules = rules;
        this.executor = executor;
        recipes.forEach(recipe -> this.recipes.put(recipe.id(), recipe));
        this.announcedUntilId = announcedUntilId;
        this.npcFile = new File(plugin.getDataFolder(), "cooking-npcs.yml");
        npcIds.addAll(YamlConfiguration.loadConfiguration(npcFile).getIntegerList("npc-ids"));
    }

    CookingOrderRules rules() {
        return rules;
    }

    CookingOrderRepository repository() {
        return repository;
    }

    // ---- NPC binding (per server) ----

    public boolean isRestaurantNpc(int npcId) {
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
            plugin.getLogger().log(Level.WARNING, "cooking-npcs.yml 저장 실패", e);
        }
        return bound;
    }

    // ---- recipes ----

    RecipeEntry recipe(String id) {
        return recipes.get(id);
    }

    Component dishName(String recipeId) {
        RecipeEntry recipe = recipes.get(recipeId);
        return recipe == null ? Component.text(recipeId) : MiniMessage.miniMessage().deserialize(recipe.displayName());
    }

    private static Recipe pure(RecipeEntry entry) {
        return new Recipe(entry.id(), entry.stages().size(), Math.min(3, entry.results().size()));
    }

    Difficulty difficultyOf(Order order) {
        return Difficulty.byKey(order.difficulty()).orElse(Difficulty.EASY);
    }

    Optional<Difficulty> groupDifficulty(String recipeId) {
        RecipeEntry recipe = recipes.get(recipeId);
        return recipe == null ? Optional.empty() : Optional.of(rules.difficulty(pure(recipe)));
    }

    private List<Recipe> learned(Player player) {
        return recipes.values().stream().filter(recipe -> player.hasPermission(recipe.permission())).map(CookingOrderService::pure).toList();
    }

    List<Recipe> allRecipes() {
        return recipes.values().stream().map(CookingOrderService::pure).toList();
    }

    static String today() {
        return LocalDate.now().toString();
    }

    // ---- opening ----

    /** Main thread: loads (drawing today's orders on first open) and shows the restaurant window. */
    public void open(Player player) {
        load(player.getUniqueId(), learned(player), false);
    }

    /** Main thread: reloads the window only if the player still has it open. */
    private void refresh(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof CookingOrderGui) {
            load(uuid, learned(player), true);
        }
    }

    private void load(UUID uuid, List<Recipe> learned, boolean onlyIfOpen) {
        String day = today();
        executor.execute(() -> {
            try {
                List<Order> orders = repository.orders(uuid, day);
                if (orders.isEmpty() && !learned.isEmpty()) {
                    repository.insertOrdersIfAbsent(uuid, day, rules.draw(random, learned));
                    orders = repository.orders(uuid, day);
                }
                Daily daily = repository.daily(uuid, day);
                int fame = repository.fame(uuid);
                GroupOrder group = repository.activeGroup().orElse(null);
                int mine = group == null ? 0 : repository.contribution(group.id(), uuid);
                View view = new View(day, orders, daily, fame, group, mine);
                runMain(() -> {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null && (!onlyIfOpen || online.getOpenInventory().getTopInventory().getHolder() instanceof CookingOrderGui)) {
                        new CookingOrderGui(this, messages, view).open(online);
                    }
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "요리 주문 불러오기 실패 (" + uuid + ")", e);
                runMain(() -> sendIfOnline(uuid, "cooking-orders.error"));
            }
        });
    }

    private boolean dayChanged(Player player, CookingOrderGui gui) {
        if (today().equals(gui.view().day())) {
            return false;
        }
        messages.send(player, "cooking-orders.day-changed");
        player.closeInventory();
        return true;
    }

    // ---- personal delivery ----

    /** Main thread: an order slot was clicked. */
    void deliver(Player player, CookingOrderGui gui, Order order, int guiSlot) {
        if (dayChanged(player, gui)) {
            return;
        }
        RecipeEntry recipe = recipes.get(order.recipeId());
        if (recipe == null) {
            messages.send(player, "cooking-orders.suspended");
            return;
        }
        if (order.completed()) {
            messages.send(player, "cooking-orders.already-completed");
            return;
        }
        UUID uuid = player.getUniqueId();
        if (busy.contains(uuid)) {
            return;
        }
        List<Dish> dishes = dishes(recipe, order.vip());
        int amount = Math.min(count(player.getInventory(), dishes), order.required() - order.delivered());
        if (amount <= 0) {
            messages.send(player, order.vip() ? "cooking-orders.no-golden" : "cooking-orders.no-dishes",
                    Placeholder.component("dish", dishName(order.recipeId())));
            return;
        }
        busy.add(uuid);
        gui.showBusy(guiSlot);
        String day = gui.view().day();
        String name = player.getName();
        executor.execute(() -> {
            boolean added;
            int fame;
            try {
                added = repository.addDelivered(uuid, day, order.slot(), order.recipeId(), amount);
                fame = repository.fame(uuid);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "요리 주문 납품 처리 실패 (" + uuid + ")", e);
                finish(uuid, "cooking-orders.error");
                return;
            }
            if (!added) {
                finish(uuid, "cooking-orders.order-full");
                return;
            }
            runMain(() -> takeDishes(uuid, name, day, order, dishes, amount, fame), "요리 주문 납품 " + uuid + " " + order.recipeId() + " ×" + amount);
        });
    }

    private void takeDishes(UUID uuid, String name, String day, Order order, List<Dish> dishes, int amount, int fame) {
        Player player = Bukkit.getPlayer(uuid);
        int[] taken = player == null ? null : remove(player.getInventory(), dishes, amount);
        if (taken == null) {
            executor.execute(() -> {
                try {
                    repository.undoDelivered(uuid, day, order.slot(), amount);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "요리 주문 납품 되돌리기 실패 — 수동 확인 필요: " + uuid + " " + day
                            + " 슬롯 " + order.slot() + " -" + amount, e);
                }
            });
            finishNow(uuid, "cooking-orders.dishes-gone");
            return;
        }
        long base = rules.moneyPerDish(order.recipeId(), difficultyOf(order));
        double fameMultiplier = rules.level(fame).multiplier();
        long money = 0;
        for (int quality = 0; quality < taken.length; quality++) {
            money += taken[quality] * rules.dishMoney(base, quality, order.vip(), fameMultiplier);
        }
        String reason = "요리 주문 납품 (" + order.recipeId() + " ×" + amount + ")";
        if (money > 0 && !core.economyData().modifyBalance(uuid, money, SOURCE, reason)) {
            plugin.getLogger().warning("요리 주문 온 지급 실패 — 수동 지급 필요: " + uuid + " " + money + "온 (" + reason + ")");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        messages.send(player, "cooking-orders.delivered",
                Placeholder.component("dish", dishName(order.recipeId())),
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.unparsed("money", String.format("%,d", money)));
        executor.execute(() -> completeIfFull(uuid, name, day, order));
    }

    /** Worker: completion rewards, VIP announcement and the all-done bonus — each at most once via the DB. */
    private void completeIfFull(UUID uuid, String name, String day, Order order) {
        Settings settings = rules.settings();
        try {
            if (!repository.completeOrder(uuid, day, order.slot())) {
                finish(uuid, null);
                return;
            }
            long stardust = order.vip() ? settings.vip().stardust() : rules.tier(difficultyOf(order)).stardust();
            int fameGain = order.vip() ? settings.vip().fame() : rules.tier(difficultyOf(order)).fame();
            core.stardust().grantCapped(uuid, stardust, SOURCE, "요리 주문 완료 (" + order.recipeId() + ")", CAP_KEY, settings.stardustDailyCap());
            int newFame = repository.addFame(uuid, fameGain);
            if (order.vip()) {
                repository.announce("cooking-orders.broadcast.vip-done", Map.of("player", name));
            }
            boolean bonus = repository.allCompleted(uuid, day) && repository.claimBonus(uuid, day);
            if (bonus) {
                core.stardust().grantCapped(uuid, settings.allDoneStardust(), SOURCE, "요리 주문 모두 완료", CAP_KEY, settings.stardustDailyCap());
            }
            runMain(() -> completed(uuid, name, order, fameGain, newFame, bonus),
                    bonus ? "요리 주문 완료 보너스 " + settings.allDoneMoney() + "온 (" + uuid + ")" : null);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "요리 주문 완료 처리 실패 — 수동 확인 필요: " + uuid + " " + day + " 슬롯 " + order.slot(), e);
            finish(uuid, "cooking-orders.error");
        }
    }

    private void completed(UUID uuid, String name, Order order, int fameGain, int newFame, boolean bonus) {
        Settings settings = rules.settings();
        Player player = Bukkit.getPlayer(uuid);
        if (bonus && settings.allDoneMoney() > 0) {
            payMain(uuid, settings.allDoneMoney(), "요리 주문 모두 완료 보너스");
        }
        FameLevel before = rules.level(newFame - fameGain);
        FameLevel after = rules.level(newFame);
        if (player != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
            messages.send(player, "cooking-orders.order-completed",
                    Placeholder.component("dish", dishName(order.recipeId())),
                    Placeholder.unparsed("fame", String.valueOf(fameGain)));
            if (bonus) {
                messages.send(player, "cooking-orders.all-done",
                        Placeholder.unparsed("money", String.format("%,d", settings.allDoneMoney())),
                        Placeholder.unparsed("stardust", String.valueOf(settings.allDoneStardust())));
            }
        }
        if (after.fame() > before.fame()) {
            if (player != null) {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
                messages.send(player, "cooking-orders.level-up", Placeholder.unparsed("level", after.name()));
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
    void reroll(Player player, CookingOrderGui gui, Order order, int guiSlot) {
        if (dayChanged(player, gui)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (busy.contains(uuid)) {
            return;
        }
        // an order whose recipe is gone from AddCook can be swapped for free, any time
        boolean free = !recipes.containsKey(order.recipeId()) && !order.completed();
        if (!free) {
            String refusal = order.vip() ? "cooking-orders.reroll-vip"
                    : order.completed() || order.delivered() > 0 ? "cooking-orders.reroll-started"
                    : gui.view().daily().rerolled() ? "cooking-orders.reroll-used" : null;
            if (refusal != null) {
                messages.send(player, refusal);
                return;
            }
        }
        Set<String> today = new HashSet<>();
        gui.view().orders().forEach(o -> today.add(o.recipeId()));
        Optional<Draw> draw = rules.drawReplacement(random, learned(player), today);
        if (draw.isEmpty()) {
            messages.send(player, "cooking-orders.no-recipes");
            return;
        }
        long cost = free ? 0 : rules.settings().rerollCost();
        if (cost > 0 && !core.economyData().modifyBalance(uuid, -cost, SOURCE, "요리 주문 교체")) {
            messages.send(player, "cooking-orders.no-money", Placeholder.unparsed("cost", String.format("%,d", cost)));
            return;
        }
        busy.add(uuid);
        gui.showBusy(guiSlot);
        String day = gui.view().day();
        executor.execute(() -> {
            boolean replaced = false;
            try {
                boolean claimed = !free && repository.claimReroll(uuid, day);
                if (free || claimed) {
                    replaced = repository.replaceOrder(uuid, day, order.slot(), order.recipeId(), draw.get(), free);
                }
                if (claimed && !replaced) {
                    repository.releaseReroll(uuid, day);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "요리 주문 교체 실패 (" + uuid + ")", e);
            }
            boolean ok = replaced;
            runMain(() -> {
                if (!ok && cost > 0) {
                    payMain(uuid, cost, "요리 주문 교체 취소 환불");
                }
                if (ok) {
                    sendIfOnline(uuid, "cooking-orders.rerolled", Placeholder.component("dish", dishName(draw.get().recipeId())));
                } else {
                    sendIfOnline(uuid, "cooking-orders.reroll-failed");
                }
                busy.remove(uuid);
                refresh(uuid);
            }, !ok && cost > 0 ? "요리 주문 교체 환불 " + cost + "온 (" + uuid + ")" : null);
        });
    }

    // ---- group order ----

    /** Main thread: the group-order slot was clicked. Anyone may deliver; no fame, money right away. */
    void deliverGroup(Player player, CookingOrderGui gui, int guiSlot) {
        GroupOrder group = gui.view().group();
        if (group == null) {
            messages.send(player, "cooking-orders.group-none");
            return;
        }
        if (System.currentTimeMillis() >= group.endsAt()) {
            messages.send(player, "cooking-orders.group-ended");
            return;
        }
        RecipeEntry recipe = recipes.get(group.recipeId());
        if (recipe == null) {
            messages.send(player, "cooking-orders.suspended");
            return;
        }
        UUID uuid = player.getUniqueId();
        if (busy.contains(uuid)) {
            return;
        }
        List<Dish> dishes = dishes(recipe, false);
        int have = count(player.getInventory(), dishes);
        int amount = Math.min(have, group.target() - group.progress());
        if (amount <= 0) {
            messages.send(player, have == 0 ? "cooking-orders.no-dishes" : "cooking-orders.group-full",
                    Placeholder.component("dish", dishName(group.recipeId())));
            return;
        }
        busy.add(uuid);
        gui.showBusy(guiSlot);
        String name = player.getName();
        executor.execute(() -> {
            OptionalInt progress;
            try {
                progress = repository.addGroupProgress(group.id(), uuid, name, amount, System.currentTimeMillis());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "단체 주문 납품 처리 실패 (" + uuid + ")", e);
                finish(uuid, "cooking-orders.error");
                return;
            }
            if (progress.isEmpty()) {
                finish(uuid, "cooking-orders.group-full");
                return;
            }
            int newProgress = progress.getAsInt();
            runMain(() -> takeGroupDishes(uuid, group, dishes, amount, newProgress),
                    "단체 주문 납품 " + uuid + " #" + group.id() + " ×" + amount);
        });
    }

    private void takeGroupDishes(UUID uuid, GroupOrder group, List<Dish> dishes, int amount, int newProgress) {
        Player player = Bukkit.getPlayer(uuid);
        int[] taken = player == null ? null : remove(player.getInventory(), dishes, amount);
        if (taken == null) {
            executor.execute(() -> {
                try {
                    repository.undoGroupProgress(group.id(), uuid, amount);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "단체 주문 납품 되돌리기 실패 — 수동 확인 필요: " + uuid + " #" + group.id() + " -" + amount, e);
                }
            });
            finishNow(uuid, "cooking-orders.dishes-gone");
            return;
        }
        long base = rules.moneyPerDish(group.recipeId(), groupDifficulty(group.recipeId()).orElse(Difficulty.EASY));
        long money = 0;
        for (int quality = 0; quality < taken.length; quality++) {
            money += taken[quality] * rules.groupDishMoney(base, quality);
        }
        String reason = "단체 요리 주문 납품 (#" + group.id() + " ×" + amount + ")";
        if (money > 0 && !core.economyData().modifyBalance(uuid, money, SOURCE, reason)) {
            plugin.getLogger().warning("단체 주문 온 지급 실패 — 수동 지급 필요: " + uuid + " " + money + "온 (" + reason + ")");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        messages.send(player, "cooking-orders.group-delivered",
                Placeholder.component("dish", dishName(group.recipeId())),
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
                        repository.announce("cooking-orders.broadcast.group-milestone", Map.of("recipe", group.recipeId(),
                                "percent", String.valueOf(percent), "progress", String.valueOf(newProgress), "target", String.valueOf(group.target())));
                    }
                }
                if (newProgress >= group.target() && repository.markGroupDone(group.id())) {
                    settle(group);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "단체 주문 달성 처리 실패 (#" + group.id() + ") — 다음 정기 점검에서 다시 시도", e);
            }
            finish(uuid, null);
        });
    }

    /** Worker, every minute on every server: expire, settle and start group orders (each exactly once via the DB). */
    public void tickGroups() {
        if (!ticking.compareAndSet(false, true)) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            for (GroupOrder group : repository.expiredActiveGroups(now)) {
                // ponytail: a delivery that just filled the order but hasn't confirmed yet can be flipped DONE here and then undone; sub-second window.
                if (group.progress() >= group.target()) {
                    repository.markGroupDone(group.id());
                } else if (repository.markGroupFailed(group.id())) {
                    announceFailed(group);
                }
            }
            for (GroupOrder group : repository.doneUnsettledGroups()) {
                settle(group);
            }
            Optional<ZonedDateTime> start = rules.currentGroupStart(ZonedDateTime.now());
            if (start.isPresent()) {
                long startsAt = start.get().toInstant().toEpochMilli();
                if (!repository.groupBlocked(startsAt)) {
                    rules.pickGroupRecipe(random, allRecipes()).ifPresent(recipe -> {
                        try {
                            startGroup(recipe.id(), rules.groupTarget(rules.difficulty(recipe)), startsAt,
                                    startsAt + rules.settings().group().durationHours() * 3_600_000L);
                        } catch (SQLException e) {
                            plugin.getLogger().log(Level.SEVERE, "단체 주문 시작 실패", e);
                        }
                    });
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "단체 주문 정기 점검 실패", e);
        } finally {
            ticking.set(false);
        }
    }

    /** Worker: starts a group order unless one is running (or this start already happened); announces it. */
    boolean startGroup(String recipeId, int target, long startsAt, long endsAt) throws SQLException {
        if (repository.startGroup(recipeId, target, startsAt, endsAt).isEmpty()) {
            return false;
        }
        long hours = Math.max(1, Math.round((endsAt - System.currentTimeMillis()) / 3_600_000.0));
        repository.announce("cooking-orders.broadcast.group-started",
                Map.of("recipe", recipeId, "target", String.format("%,d", target), "hours", String.valueOf(hours)));
        return true;
    }

    /** Worker: fails the running group order (admin 단체종료); false if none was running. */
    boolean endGroup() throws SQLException {
        Optional<GroupOrder> group = repository.activeGroup();
        if (group.isEmpty() || !repository.markGroupFailed(group.get().id())) {
            return false;
        }
        announceFailed(group.get());
        return true;
    }

    private void announceFailed(GroupOrder group) throws SQLException {
        repository.announce("cooking-orders.broadcast.group-failed", Map.of("recipe", group.recipeId(),
                "progress", String.format("%,d", group.progress()), "target", String.format("%,d", group.target())));
    }

    /** Worker: the one server that claims settlement grants 별조각 to participants and the top 3, then announces. */
    private void settle(GroupOrder group) throws SQLException {
        // read before claiming so a failed read leaves it unsettled for the next tick
        List<Contribution> contributions = repository.contributions(group.id());
        if (!repository.claimSettle(group.id())) {
            return;
        }
        CookingOrderRules.Group settings = rules.settings().group();
        for (int i = 0; i < contributions.size(); i++) {
            Contribution contribution = contributions.get(i);
            long stardust = (contribution.amount() >= settings.minContribution() ? settings.participationStardust() : 0)
                    + (i < settings.rankStardust().size() ? settings.rankStardust().get(i) : 0);
            core.stardust().grant(contribution.player(), stardust, SOURCE, "단체 요리 주문 달성 (#" + group.id() + ")");
        }
        Map<String, String> args = new LinkedHashMap<>();
        args.put("recipe", group.recipeId());
        String[] ranks = {"first", "second", "third"};
        for (int i = 0; i < ranks.length; i++) {
            args.put(ranks[i], i < contributions.size() ? contributions.get(i).name() : "-");
        }
        repository.announce("cooking-orders.broadcast.group-done", args);
    }

    // ---- announcements (worker, every ~20 s on every server) ----

    public void pollAnnouncements() {
        try {
            List<CookingOrderRepository.Announcement> announcements = repository.announcementsAfter(announcedUntilId);
            if (announcements.isEmpty()) {
                return;
            }
            announcedUntilId = announcements.get(announcements.size() - 1).id();
            runMain(() -> announcements.forEach(this::broadcast), null);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "요리 주문 공지 조회 실패", e);
        }
    }

    private void broadcast(CookingOrderRepository.Announcement announcement) {
        List<TagResolver> placeholders = new ArrayList<>();
        announcement.args().forEach((key, value) -> placeholders.add(key.equals("recipe")
                ? Placeholder.component("dish", dishName(value)) : Placeholder.unparsed(key, value)));
        messages.broadcast(announcement.key(), placeholders.toArray(TagResolver[]::new));
    }

    // ---- inventory ----

    /** Accepted dish items, 금 → 은 → 일반 (the order they're used in); VIP takes 금 only. */
    private static List<Dish> dishes(RecipeEntry recipe, boolean goldenOnly) {
        List<Dish> dishes = new ArrayList<>();
        for (int quality = Math.min(3, recipe.results().size()) - 1; quality >= 0; quality--) {
            if (goldenOnly && quality != CookingOrderRules.GOLDEN) {
                continue;
            }
            String id = recipe.results().get(quality).itemId();
            dishes.add(new Dish(id.startsWith("ia:") ? id.substring("ia:".length()) : id, quality));
        }
        return dishes;
    }

    private static String itemKey(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        CustomStack custom = CustomStack.byItemStack(stack);
        return custom != null ? custom.getNamespacedID() : stack.getType().name();
    }

    private static int count(PlayerInventory inventory, List<Dish> dishes) {
        int have = 0;
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            String key = itemKey(stack);
            if (key != null && dishes.stream().anyMatch(dish -> dish.key().equals(key))) {
                have += stack.getAmount();
            }
        }
        return have;
    }

    /** Takes exactly {@code amount} dishes (금 first); per-quality counts taken, or null (nothing taken) if too few. */
    private static int[] remove(PlayerInventory inventory, List<Dish> dishes, int amount) {
        if (count(inventory, dishes) < amount) {
            return null;
        }
        int[] taken = new int[3];
        int remaining = amount;
        for (Dish dish : dishes) {
            for (int slot = 0; slot < STORAGE_SLOTS && remaining > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (dish.key().equals(itemKey(stack))) {
                    int take = Math.min(remaining, stack.getAmount());
                    remaining -= take;
                    taken[dish.quality()] += take;
                    stack.setAmount(stack.getAmount() - take);
                    inventory.setItem(slot, stack.getAmount() > 0 ? stack : null);
                }
            }
        }
        return taken;
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
                plugin.getLogger().log(Level.SEVERE, "요리 주문 온 장부 기록 실패 — 수동 지급 필요: " + uuid + " " + amount + "온 (" + reason + ")", e);
            }
        });
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

    /** Reads the {@code cooking-orders} config block (spec §9 defaults). */
    public static Settings settings(ConfigurationSection config, Logger log) {
        Map<Difficulty, Tier> tiers = new EnumMap<>(Difficulty.class);
        Map<Difficulty, Tier> defaults = Map.of(
                Difficulty.EASY, new Tier(3, 6, 1500, 1, 1),
                Difficulty.NORMAL, new Tier(2, 4, 4000, 2, 2),
                Difficulty.HARD, new Tier(1, 3, 10000, 3, 3));
        for (Difficulty difficulty : Difficulty.values()) {
            String path = "difficulty." + difficulty.key() + ".";
            Tier fallback = defaults.get(difficulty);
            tiers.put(difficulty, new Tier(config.getInt(path + "amount-min", fallback.amountMin()),
                    config.getInt(path + "amount-max", fallback.amountMax()),
                    config.getLong(path + "money-per-dish", fallback.moneyPerDish()),
                    config.getLong(path + "stardust", fallback.stardust()),
                    config.getInt(path + "fame", fallback.fame())));
        }
        List<Double> qualityMultipliers = List.of(config.getDouble("quality-multiplier.normal", 1.0),
                config.getDouble("quality-multiplier.silver", 1.5), config.getDouble("quality-multiplier.golden", 3.0));
        CookingOrderRules.Vip vip = new CookingOrderRules.Vip(config.getInt("vip.chance-percent", 10),
                config.getInt("vip.amount-min", 1), config.getInt("vip.amount-max", 2),
                config.getDouble("vip.money-multiplier", 5), config.getLong("vip.stardust", 5), config.getInt("vip.fame", 5));

        List<DayOfWeek> days = new ArrayList<>();
        for (String day : config.getStringList("group.days")) {
            try {
                days.add(DayOfWeek.valueOf(day.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warning("cooking-orders.group.days의 '" + day + "'는 MONDAY~SUNDAY 중 하나여야 합니다 — 건너뜁니다.");
            }
        }
        LocalTime startTime;
        try {
            startTime = LocalTime.parse(config.getString("group.start-time", "19:00"));
        } catch (DateTimeParseException e) {
            log.warning("cooking-orders.group.start-time은 \"19:00\" 형식이어야 합니다 — 19:00으로 둡니다.");
            startTime = LocalTime.of(19, 0);
        }
        CookingOrderRules.Group group = new CookingOrderRules.Group(days, startTime,
                Math.max(1, config.getInt("group.duration-hours", 72)),
                config.getInt("group.target.easy", 300), config.getInt("group.target.normal", 150),
                config.getDouble("group.money-share", 0.5), config.getInt("group.min-contribution", 5),
                config.getLong("group.participation-stardust", 5),
                config.isList("group.rank-stardust") ? config.getLongList("group.rank-stardust") : List.of(15L, 10L, 5L));

        List<FameLevel> fameLevels = new ArrayList<>();
        for (Map<?, ?> raw : config.getMapList("fame-levels")) {
            try {
                fameLevels.add(new FameLevel(String.valueOf(raw.get("name")), ((Number) raw.get("fame")).intValue(),
                        ((Number) raw.get("multiplier")).doubleValue()));
            } catch (RuntimeException e) {
                log.warning("cooking-orders.fame-levels 항목이 잘못되었습니다: " + raw);
            }
        }
        fameLevels.sort(Comparator.comparingInt(FameLevel::fame));
        if (fameLevels.isEmpty() || fameLevels.get(0).fame() > 0) {
            fameLevels.add(0, new FameLevel("견습", 0, 1.0));
        }

        Map<String, List<String>> levelUpCommands = new LinkedHashMap<>();
        ConfigurationSection commands = config.getConfigurationSection("level-up-commands");
        if (commands != null) {
            for (String level : commands.getKeys(false)) {
                levelUpCommands.put(level, commands.getStringList(level));
            }
        }
        Map<String, CookingOrderRules.RecipeOverride> overrides = new LinkedHashMap<>();
        ConfigurationSection overrideSection = config.getConfigurationSection("overrides");
        if (overrideSection != null) {
            for (String recipeId : overrideSection.getKeys(false)) {
                String difficultyKey = overrideSection.getString(recipeId + ".difficulty");
                Difficulty difficulty = difficultyKey == null ? null : Difficulty.byKey(difficultyKey).orElse(null);
                if (difficultyKey != null && difficulty == null) {
                    log.warning("cooking-orders.overrides." + recipeId + ".difficulty는 easy/normal/hard 중 하나여야 합니다 — 무시합니다.");
                }
                overrides.put(recipeId, new CookingOrderRules.RecipeOverride(difficulty, overrideSection.getLong(recipeId + ".money-per-dish", 0)));
            }
        }
        return new Settings(Math.max(1, Math.min(3, config.getInt("orders-per-day", 3))), tiers, qualityMultipliers,
                config.getLong("all-done-bonus.money", 5000), config.getLong("all-done-bonus.stardust", 3),
                config.getLong("reroll-cost", 5000), config.getLong("stardust-daily-cap", 20), vip, group,
                fameLevels, levelUpCommands, overrides);
    }
}
