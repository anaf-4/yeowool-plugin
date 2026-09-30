package com.yeowool.life.donation;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.DurationFormat;
import com.yeowool.life.donation.DonationRepository.Add;
import com.yeowool.life.donation.DonationRepository.Contribution;
import com.yeowool.life.donation.DonationRepository.Goal;
import com.yeowool.life.donation.DonationRepository.Project;
import com.yeowool.life.donation.DonationRules.Candidate;
import com.yeowool.life.donation.DonationRules.GoalSpec;
import com.yeowool.life.donation.DonationRules.Settings;
import com.yeowool.life.surprise.LifeBoosts;
import com.yeowool.life.surprise.SurpriseEventType;
import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * 기부 프로젝트 (서버 공동 목표): a weekly project whose goals every server fills together. A donation
 * follows the order systems' safe path: count on the main thread, clamp and add under a row lock on the
 * worker, then take the items on the main thread — undoing the progress if they're gone. Starting,
 * expiring, achieving and settling are claimed once in the DB by whichever server's minute tick gets
 * there first; each server applies the achieved buff through {@link LifeBoosts} on its own.
 */
public final class DonationService {

    /** What the window shows, loaded on the worker. {@code project} null = none running. */
    record View(Project project, long myPoints, Map<SurpriseEventType, Long> buffs) {
    }

    enum StartResult { STARTED, ALREADY_ACTIVE, NO_CANDIDATE }

    private static final String SOURCE = "YeowoolLife";
    private static final String BUFF_SOURCE = "donation";
    private static final int STORAGE_SLOTS = 36;
    private static final long BOSS_BAR_MILLIS = 60_000;

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final DonationRepository repository;
    private final Executor executor;
    private final Random random = new Random();
    private volatile Settings settings;
    private volatile long announcedUntilId;
    private final AtomicBoolean ticking = new AtomicBoolean();
    private final AtomicBoolean polling = new AtomicBoolean();
    private volatile boolean warnedEmptyPool;

    // main thread only
    private final Set<UUID> busy = new HashSet<>();
    private final Map<UUID, Long> bossBarUntil = new HashMap<>();
    private final Map<SurpriseEventType, Long> buffEnds = new EnumMap<>(SurpriseEventType.class);
    private final Map<SurpriseEventType, Double> appliedBuffs = new EnumMap<>(SurpriseEventType.class);
    private Project current;
    private BossBar bossBar;

    public DonationService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, DonationRepository repository,
                           Executor executor, Settings settings, long announcedUntilId) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.executor = executor;
        this.settings = settings;
        this.announcedUntilId = announcedUntilId;
    }

    public Settings settings() {
        return settings;
    }

    void setSettings(Settings settings) {
        this.settings = settings;
        this.warnedEmptyPool = false;
    }

    DonationRepository repository() {
        return repository;
    }

    // ---- items ----

    private static boolean itemsAdder() {
        return Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }

    /**
     * The goal key an inventory item counts for: an ItemsAdder item's namespaced id, else the Material name
     * for a plain vanilla item. Null for air and for vanilla items with a custom name or lore (renamed items).
     */
    static String keyOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        if (itemsAdder()) {
            CustomStack custom = CustomStack.byItemStack(stack);
            if (custom != null) {
                return custom.getNamespacedID();
            }
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && (meta.hasDisplayName() || meta.hasLore())) {
            return null;
        }
        return stack.getType().name();
    }

    /** A fresh icon for a goal: its stored display item, else built from the key; a barrier if neither works. */
    static ItemStack icon(String itemKey, byte[] display) {
        if (display != null) {
            try {
                return ItemStack.deserializeBytes(display);
            } catch (RuntimeException ignored) {
                // saved by another version — fall back to the key
            }
        }
        if (itemKey.contains(":")) {
            CustomStack custom = itemsAdder() ? CustomStack.getInstance(itemKey) : null;
            return custom != null ? custom.getItemStack().clone() : new ItemStack(Material.BARRIER);
        }
        Material material = Material.matchMaterial(itemKey);
        return material != null && material.isItem() ? new ItemStack(material) : new ItemStack(Material.BARRIER);
    }

    static Component itemName(String itemKey) {
        if (itemKey.contains(":")) {
            CustomStack custom = itemsAdder() ? CustomStack.getInstance(itemKey) : null;
            return custom != null ? LegacyComponentSerializer.legacySection().deserialize(custom.getDisplayName()) : Component.text(itemKey);
        }
        Material material = Material.matchMaterial(itemKey);
        return material != null ? Component.translatable(material.translationKey()) : Component.text(itemKey);
    }

    private static int count(PlayerInventory inventory, String itemKey) {
        int have = 0;
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (itemKey.equals(keyOf(stack))) {
                have += stack.getAmount();
            }
        }
        return have;
    }

    /** Takes exactly {@code amount}; false (nothing taken) if the inventory holds fewer. */
    private static boolean take(PlayerInventory inventory, String itemKey, int amount) {
        if (count(inventory, itemKey) < amount) {
            return false;
        }
        int remaining = amount;
        for (int slot = 0; slot < STORAGE_SLOTS && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (itemKey.equals(keyOf(stack))) {
                int take = Math.min(remaining, stack.getAmount());
                remaining -= take;
                stack.setAmount(stack.getAmount() - take);
                inventory.setItem(slot, stack.getAmount() > 0 ? stack : null);
            }
        }
        return true;
    }

    // ---- window ----

    /** Main thread: loads and shows the window (and the progress boss bar for a minute). */
    public void open(Player player) {
        load(player.getUniqueId(), false);
    }

    private boolean showing(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder() instanceof DonationGui;
    }

    private void refresh(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && showing(player)) {
            load(uuid, true);
        }
    }

    private void load(UUID uuid, boolean onlyIfOpen) {
        executor.execute(() -> {
            try {
                Project project = repository.active().orElse(null);
                long mine = project == null ? 0 : repository.points(project.id(), uuid);
                View view = new View(project, mine, repository.activeBuffs(System.currentTimeMillis()));
                runMain(() -> {
                    current = project;
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null && (!onlyIfOpen || showing(online))) {
                        new DonationGui(this, messages, view).open(online);
                        showBossBar(uuid);
                    }
                }, null);
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "기부 창 불러오기 실패 (" + uuid + ")", e);
                runMain(() -> sendIfOnline(uuid, "donation.error"), null);
            }
        });
    }

    // ---- donating ----

    /** Main thread: a goal was clicked; {@code all} = everything in the inventory, else one stack. */
    void donate(Player player, DonationGui gui, Goal goal, boolean all, int guiSlot) {
        Project project = gui.view().project();
        if (project == null || System.currentTimeMillis() >= project.endsAt()) {
            messages.send(player, "donation.ended");
            return;
        }
        UUID uuid = player.getUniqueId();
        if (busy.contains(uuid)) {
            return;
        }
        if (goal.progress() >= goal.target()) {
            messages.send(player, "donation.goal-full", Placeholder.component("item", itemName(goal.itemKey())));
            return;
        }
        int have = count(player.getInventory(), goal.itemKey());
        if (have == 0) {
            messages.send(player, "donation.no-items", Placeholder.component("item", itemName(goal.itemKey())));
            return;
        }
        int amount = DonationRules.clamp(all ? have : Math.min(have, icon(goal.itemKey(), null).getMaxStackSize()),
                goal.target(), goal.progress());
        busy.add(uuid);
        gui.showBusy(guiSlot);
        String name = player.getName();
        executor.execute(() -> {
            Optional<Add> result;
            try {
                // clamped to what's left right now — the other servers fill the same goals
                result = repository.addProgress(project.id(), goal.slot(), goal.itemKey(), uuid, name, amount, System.currentTimeMillis());
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "기부 처리 실패 (" + uuid + ")", e);
                finish(uuid, "donation.error");
                return;
            }
            if (result.isEmpty()) {
                finish(uuid, "donation.ended");
                return;
            }
            Add add = result.get();
            if (add.added() == 0) {
                finish(uuid, "donation.goal-full-now");
                return;
            }
            runMain(() -> guarded(uuid, () -> takeItems(uuid, project, goal, add)),
                    "기부 " + uuid + " #" + project.id() + " " + goal.itemKey() + " ×" + add.added());
        });
    }

    private void takeItems(UUID uuid, Project project, Goal goal, Add add) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !take(player.getInventory(), goal.itemKey(), add.added())) {
            executor.execute(() -> {
                try {
                    repository.undoProgress(project.id(), goal.slot(), uuid, add.added(), add.points());
                } catch (SQLException | RuntimeException e) {
                    plugin.getLogger().log(Level.SEVERE, "기부 되돌리기 실패 — 수동 확인 필요: " + uuid + " #" + project.id()
                            + " 슬롯 " + goal.slot() + " -" + add.added(), e);
                }
            });
            finishNow(uuid, "donation.items-gone");
            return;
        }
        long points = (long) add.added() * add.points();
        long money = points * settings.moneyPerPoint();
        String reason = "기부 프로젝트 (#" + project.id() + " " + goal.itemKey() + " ×" + add.added() + ")";
        if (money > 0 && !core.economyData().modifyBalance(uuid, money, SOURCE, reason)) {
            plugin.getLogger().warning("기부 온 지급 실패 — 수동 지급 필요: " + uuid + " " + money + "온 (" + reason + ")");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        messages.send(player, "donation.donated",
                Placeholder.component("item", itemName(goal.itemKey())),
                Placeholder.unparsed("amount", String.format("%,d", add.added())),
                Placeholder.unparsed("points", String.format("%,d", points)),
                Placeholder.unparsed("money", String.format("%,d", money)),
                Placeholder.unparsed("progress", String.format("%,d", add.before() + add.added())),
                Placeholder.unparsed("target", String.format("%,d", add.target())));
        showBossBar(uuid);
        executor.execute(() -> {
            try {
                afterDonation(project, goal, add);
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "기부 진행 공지/달성 처리 실패 (#" + project.id() + ") — 다음 정기 점검에서 다시 시도", e);
            } finally {
                finish(uuid, null);
            }
        });
    }

    /** Worker: item-done / milestone announcements and the achievement, each at most once via the DB. */
    private void afterDonation(Project project, Goal goal, Add add) throws SQLException {
        if (add.before() < add.target() && add.before() + add.added() >= add.target()) {
            // only the one locked transaction that crossed the target gets here
            repository.announce("donation.broadcast.item-done", Map.of("project", project.name(), "item", goal.itemKey()));
        }
        List<Goal> goals = repository.goals(project.id());
        int percent = DonationRules.overallPercent(goals);
        OptionalInt milestone = DonationRules.milestone(percent);
        if (milestone.isPresent() && repository.claimMilestone(project.id(), milestone.getAsInt())) {
            repository.announce("donation.broadcast.milestone",
                    Map.of("project", project.name(), "percent", String.valueOf(milestone.getAsInt())));
        }
        if (DonationRules.allComplete(goals)) {
            achieve(project.id());
        }
    }

    /** Worker: flips a full project to DONE (starting its buff) and settles it — both once. */
    private void achieve(int projectId) throws SQLException {
        if (repository.markDone(projectId, System.currentTimeMillis() + settings.buffHours() * 3_600_000L)) {
            settle(projectId);
        }
    }

    // ---- schedule (worker, every minute on every server) ----

    public void tick() {
        if (!ticking.compareAndSet(false, true)) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            // ponytail: a donation that just filled the last goal but hasn't confirmed its items can be flipped DONE here and then undone; sub-second window.
            for (Project project : repository.activeProjects()) {
                achieve(project.id());
                if (project.endsAt() <= now && repository.markFailed(project.id(), false)) {
                    announceFailed(project.id(), project.name());
                }
            }
            for (Project project : repository.doneUnsettled()) {
                settle(project.id());
            }
            Settings s = settings;
            ZonedDateTime start = DonationRules.periodStart(ZonedDateTime.now(), s.startDay());
            long startsAt = start.toInstant().toEpochMilli();
            long endsAt = start.plusDays(s.durationDays()).toInstant().toEpochMilli();
            if (now < endsAt && !repository.blocked(startsAt)) {
                startProject(null, startsAt, endsAt);
            }
        } catch (SQLException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "기부 프로젝트 정기 점검 실패", e);
        } finally {
            ticking.set(false);
        }
    }

    /** Worker, staff 시작: now until this week's deadline. {@code poolName} null = the reservation, else a random pool entry. */
    StartResult forceStart(String poolName) throws SQLException {
        if (repository.active().isPresent()) {
            return StartResult.ALREADY_ACTIVE;
        }
        ZonedDateTime now = ZonedDateTime.now();
        Settings s = settings;
        long nowMillis = now.toInstant().toEpochMilli();
        long endsAt = DonationRules.endFor(now, s.startDay(), s.durationDays()).toInstant().toEpochMilli();
        // this week's slot still unused: claim it, so the minute tick doesn't open a second project this week
        ZonedDateTime week = DonationRules.periodStart(now, s.startDay());
        long weekStart = week.toInstant().toEpochMilli();
        boolean ownWeek = now.isBefore(week.plusDays(s.durationDays())) && !repository.blocked(weekStart);
        return startProject(poolName, ownWeek ? weekStart : nowMillis, endsAt);
    }

    private StartResult startProject(String poolName, long startsAt, long endsAt) throws SQLException {
        Settings s = settings;
        Optional<Candidate> scheduled = poolName == null ? repository.schedule() : Optional.empty();
        Optional<Project> latest = repository.latest();
        Optional<Candidate> candidate = scheduled.isPresent() ? scheduled
                : poolName != null ? s.pool().stream().filter(c -> c.name().equals(poolName)).findFirst()
                : DonationRules.pick(random, s.pool(), latest.map(Project::name).orElse(null));
        if (candidate.isEmpty()) {
            if (poolName == null && !warnedEmptyPool) {
                warnedEmptyPool = true;
                plugin.getLogger().warning("기부 프로젝트 후보(donation.pool)도 예약도 없어 새 프로젝트를 시작하지 못했습니다.");
            }
            return StartResult.NO_CANDIDATE;
        }
        Candidate c = candidate.get();
        SurpriseEventType buff = c.buff() != null ? c.buff()
                : DonationRules.nextBuff(s.buffOrder(), latest.map(Project::buff).orElse(null));
        if (repository.start(c, buff, startsAt, endsAt, scheduled.isPresent()).isEmpty()) {
            return StartResult.ALREADY_ACTIVE;
        }
        StringBuilder items = new StringBuilder();
        for (GoalSpec goal : c.goals()) {
            items.append(items.isEmpty() ? "" : ",").append(goal.itemKey()).append('*').append(goal.target());
        }
        repository.announce("donation.broadcast.started", Map.of("project", c.name(), "items", items.toString(),
                "remaining", DurationFormat.humanize(Math.max(0, endsAt - System.currentTimeMillis())),
                "buff", buff.label(), "multiplier", multiplier(DonationRules.buffMultiplier(buff))));
        return StartResult.STARTED;
    }

    /** Worker, staff 종료: fails the running project; false if none was running. */
    boolean forceEnd() throws SQLException {
        Optional<Project> project = repository.active();
        if (project.isEmpty() || !repository.markFailed(project.get().id(), true)) {
            return false;
        }
        announceFailed(project.get().id(), project.get().name());
        return true;
    }

    private void announceFailed(int projectId, String name) throws SQLException {
        repository.announce("donation.broadcast.failed", Map.of("project", name,
                "percent", String.valueOf(DonationRules.overallPercent(repository.goals(projectId)))));
    }

    /** Worker: the one server that claims settlement pays 별조각, runs the 1위 commands and announces. */
    private void settle(int projectId) throws SQLException {
        // read before claiming so a failed read leaves it unsettled for the next tick
        List<Contribution> ranked = repository.contributions(projectId);
        Project project = repository.project(projectId).orElse(null);
        if (!repository.claimSettle(projectId)) {
            return;
        }
        Settings s = settings;
        List<Long> rewards = DonationRules.rewards(ranked, s.minScore(), s.participationStardust(), s.rankStardust());
        for (int i = 0; i < ranked.size(); i++) {
            core.stardust().grant(ranked.get(i).player(), rewards.get(i), SOURCE, "기부 프로젝트 달성 (#" + projectId + ")");
        }
        if (!ranked.isEmpty() && !s.topCommands().isEmpty()) {
            String top = ranked.get(0).name();
            runMain(() -> s.topCommands().forEach(command ->
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", top))),
                    "기부 프로젝트 1위 명령어 (" + top + "): " + s.topCommands());
        }
        Map<String, String> args = new LinkedHashMap<>();
        args.put("project", project == null ? "#" + projectId : project.name());
        SurpriseEventType buff = project == null ? SurpriseEventType.LAND_XP : project.buff();
        args.put("buff", buff.label());
        args.put("multiplier", multiplier(DonationRules.buffMultiplier(buff)));
        args.put("hours", String.valueOf(s.buffHours()));
        String[] ranks = {"first", "second", "third"};
        for (int i = 0; i < ranks.length; i++) {
            args.put(ranks[i], i < ranked.size() ? ranked.get(i).name() : "-");
        }
        repository.announce("donation.broadcast.done", args);
    }

    // ---- polling (worker, every 20 s on every server): announcements, progress snapshot, buffs ----

    public void poll() {
        if (!polling.compareAndSet(false, true)) {
            return; // previous poll still running (slow DB) — don't broadcast the same rows twice
        }
        try {
            List<DonationRepository.Announcement> announcements = repository.announcementsAfter(announcedUntilId);
            if (!announcements.isEmpty()) {
                announcedUntilId = announcements.get(announcements.size() - 1).id();
            }
            Project project = repository.active().orElse(null);
            Map<SurpriseEventType, Long> buffs = repository.activeBuffs(System.currentTimeMillis());
            runMain(() -> {
                announcements.forEach(this::broadcast);
                current = project;
                buffEnds.clear();
                buffEnds.putAll(buffs);
                syncBuffs();
            }, null);
        } catch (SQLException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "기부 프로젝트 공지/상태 조회 실패", e);
        } finally {
            polling.set(false);
        }
    }

    private void broadcast(DonationRepository.Announcement announcement) {
        List<TagResolver> placeholders = new ArrayList<>();
        announcement.args().forEach((name, value) -> placeholders.add(switch (name) {
            case "item" -> Placeholder.component(name, itemName(value));
            case "items" -> Placeholder.component(name, itemList(value));
            default -> Placeholder.unparsed(name, value);
        }));
        messages.broadcast(announcement.key(), placeholders.toArray(TagResolver[]::new));
    }

    /** "WHEAT*50000,IRON_INGOT*5000" → "밀 50,000 · 철 주괴 5,000". */
    private static Component itemList(String encoded) {
        Component list = Component.empty();
        boolean first = true;
        for (String entry : encoded.split(",")) {
            int star = entry.lastIndexOf('*');
            if (star <= 0) {
                continue;
            }
            list = list.append(Component.text(first ? "" : " · ")).append(itemName(entry.substring(0, star)))
                    .append(Component.text(" " + String.format("%,d", Long.parseLong(entry.substring(star + 1)))));
            first = false;
        }
        return list;
    }

    // ---- buffs & boss bar (main thread) ----

    /** Applies each achieved buff while it runs and reverts it once over (also when the DB is unreachable past its end). */
    private void syncBuffs() {
        long now = System.currentTimeMillis();
        for (SurpriseEventType type : SurpriseEventType.values()) {
            double wanted = buffEnds.getOrDefault(type, 0L) > now ? DonationRules.buffMultiplier(type) : 1.0;
            double applied = appliedBuffs.getOrDefault(type, 1.0);
            if (wanted != applied) {
                LifeBoosts.set(core.landStats(), BUFF_SOURCE, type, wanted);
                appliedBuffs.put(type, wanted);
                if (wanted == 1.0) {
                    messages.broadcast("donation.buff-ended", Placeholder.unparsed("buff", type.label()));
                }
            }
        }
    }

    private void showBossBar(UUID uuid) {
        bossBarUntil.put(uuid, System.currentTimeMillis() + BOSS_BAR_MILLIS);
        tickSecond();
    }

    /** Main thread, every second: buff expiry and the progress boss bar for recent viewers/donors. */
    public void tickSecond() {
        syncBuffs();
        long now = System.currentTimeMillis();
        bossBarUntil.values().removeIf(until -> until <= now);
        Project project = current;
        if (project == null || bossBarUntil.isEmpty()) {
            if (bossBar != null) {
                bossBar.removeAll();
                bossBar = null;
            }
            return;
        }
        int percent = DonationRules.overallPercent(project.goals());
        String title = "🤝 기부 프로젝트 「" + project.name() + "」 " + percent + "% — 남은 시간 "
                + DurationFormat.humanize(Math.max(0, project.endsAt() - now));
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar(title, BarColor.GREEN, BarStyle.SEGMENTED_10);
        } else {
            bossBar.setTitle(title);
        }
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, percent / 100.0)));
        for (Player player : List.copyOf(bossBar.getPlayers())) {
            if (!bossBarUntil.containsKey(player.getUniqueId())) {
                bossBar.removePlayer(player);
            }
        }
        for (UUID uuid : bossBarUntil.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                bossBar.addPlayer(player);
            }
        }
    }

    /** Main thread, from onDisable: put the buffs back and drop the boss bar. */
    public void shutdown() {
        appliedBuffs.forEach((type, value) -> LifeBoosts.set(core.landStats(), BUFF_SOURCE, type, 1.0));
        appliedBuffs.clear();
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }

    // ---- 순위 ----

    /** Main thread: top 10 of the running (else latest) project in chat. */
    public void ranking(CommandSender sender) {
        UUID viewer = sender instanceof Player player ? player.getUniqueId() : null;
        executor.execute(() -> {
            try {
                Optional<Project> project = repository.currentOrLatest();
                List<Contribution> ranked = project.isEmpty() ? List.of() : repository.contributions(project.get().id());
                runMain(() -> {
                    if (project.isEmpty()) {
                        messages.send(sender, "donation.none");
                        return;
                    }
                    messages.send(sender, "donation.rank-header", Placeholder.unparsed("project", project.get().name()));
                    if (ranked.isEmpty()) {
                        messages.send(sender, "donation.rank-empty");
                    }
                    for (int i = 0; i < ranked.size(); i++) {
                        Contribution c = ranked.get(i);
                        if (i < 10) {
                            messages.send(sender, "donation.rank-line", Placeholder.unparsed("rank", String.valueOf(i + 1)),
                                    Placeholder.unparsed("player", c.name()), Placeholder.unparsed("points", String.format("%,d", c.points())));
                        }
                        if (c.player().equals(viewer)) {
                            messages.send(sender, "donation.rank-mine", Placeholder.unparsed("rank", String.valueOf(i + 1)),
                                    Placeholder.unparsed("points", String.format("%,d", c.points())));
                        }
                    }
                }, null);
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "기부 순위 조회 실패", e);
                runMain(() -> messages.send(sender, "donation.error"), null);
            }
        });
    }

    // ---- helpers ----

    static String multiplier(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private void guarded(UUID uuid, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "기부 처리 실패 — 수동 확인 필요 (" + uuid + ")", e);
            finishNow(uuid, "donation.error");
        }
    }

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

    private void sendIfOnline(UUID uuid, String key) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            messages.send(player, key);
        }
    }

    void reply(CommandSender sender, String key, TagResolver... placeholders) {
        runMain(() -> messages.send(sender, key, placeholders), null);
    }

    /** Schedules on the main thread; while the plugin is disabling, logs {@code lostWork} (if given) for manual follow-up instead. */
    private void runMain(Runnable task, String lostWork) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        } else if (lostWork != null) {
            plugin.getLogger().warning("플러그인 종료 중이라 처리하지 못함 — 수동 처리 필요: " + lostWork);
        }
    }

    // ---- config (main thread) ----

    /** Reads the {@code donation:} block; bad pool entries are skipped with a warning. */
    public static Settings settings(ConfigurationSection config, Logger log) {
        DayOfWeek startDay;
        try {
            startDay = DayOfWeek.valueOf(config.getString("start-day", "MONDAY").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("donation.start-day는 MONDAY~SUNDAY 중 하나여야 합니다 — MONDAY로 둡니다.");
            startDay = DayOfWeek.MONDAY;
        }
        List<SurpriseEventType> buffOrder = new ArrayList<>();
        for (String key : config.getStringList("buffs")) {
            SurpriseEventType.byKey(key).ifPresentOrElse(buffOrder::add,
                    () -> log.warning("donation.buffs의 '" + key + "'는 land_xp/crop_drop/job_xp/treasure_drop 중 하나여야 합니다."));
        }
        if (buffOrder.isEmpty()) {
            buffOrder.addAll(List.of(SurpriseEventType.LAND_XP, SurpriseEventType.CROP_DROP, SurpriseEventType.JOB_XP, SurpriseEventType.TREASURE_DROP));
        }
        List<Candidate> pool = new ArrayList<>();
        for (Map<?, ?> raw : config.getMapList("pool")) {
            try {
                String name = String.valueOf(raw.get("name")).trim();
                Object buffKey = raw.get("buff");
                SurpriseEventType buff = buffKey == null ? null : SurpriseEventType.byKey(String.valueOf(buffKey)).orElse(null);
                List<GoalSpec> goals = new ArrayList<>();
                for (Object entry : (List<?>) raw.get("items")) {
                    Map<?, ?> item = (Map<?, ?>) entry;
                    String key = normalizeKey(String.valueOf(item.get("item")));
                    int amount = ((Number) item.get("amount")).intValue();
                    Object points = item.get("points");
                    if (key == null || amount < 1) {
                        throw new IllegalArgumentException("item/amount");
                    }
                    goals.add(new GoalSpec(key, null, amount, points == null ? 1 : Math.max(1, ((Number) points).intValue())));
                }
                if (name.isEmpty() || name.equals("null") || goals.isEmpty() || goals.size() > 5) {
                    throw new IllegalArgumentException("name/items");
                }
                pool.add(new Candidate(name, buff, List.copyOf(goals)));
            } catch (RuntimeException e) {
                log.warning("donation.pool 항목이 잘못되어 건너뜁니다 (name, items 1~5개 [item, amount, points] 필요): " + raw);
            }
        }
        return new Settings(startDay, Math.clamp(config.getInt("duration-days", 7), 1, 7),
                Math.max(1, config.getInt("buff-hours", 24)), List.copyOf(pool), List.copyOf(buffOrder),
                Math.max(0, config.getLong("money-per-point", 2)), Math.max(0, config.getLong("min-score", 100)),
                Math.max(0, config.getLong("participation-stardust", 5)),
                config.isList("rank-stardust") ? config.getLongList("rank-stardust") : List.of(30L, 20L, 10L),
                config.getStringList("top-commands"));
    }

    /** A config item as a goal key: {@code namespace:id} kept as is, a Material name upper-cased; null if not a known item. */
    private static String normalizeKey(String input) {
        if (input.contains(":") && !input.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            return input;
        }
        Material material = Material.matchMaterial(input);
        return material != null && material.isItem() && !material.isAir() ? material.name() : null;
    }
}
