package com.yeowool.raid.worldboss;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.DurationFormat;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Daily field world boss. The owner server (the one that has {@code world-boss.world}) runs the
 * whole state machine on its main thread — IDLE → ANNOUNCED → ACTIVE → IDLE — and persists every
 * transition (bumping {@code seq}) to the shared state row; every other server polls that row and
 * announces each new {@code seq} once. Rewards go through the core payout ledger, so winners on
 * other servers or offline get paid on their next join.
 */
public final class WorldBossService implements Listener {

    public record Settings(String world, String mobId, LocalTime spawnTime, int announceMinutes, int fightMinutes,
                           List<Long> rankRewards, long participationReward, double minDamageShare) {
    }

    private static final String SOURCE = "YeowoolRaid";
    private static final String IDLE = "IDLE";
    private static final String ANNOUNCED = "ANNOUNCED";
    private static final String ACTIVE = "ACTIVE";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final WorldBossRepository repository;
    private final Executor executor;
    private final Settings settings;
    private final boolean owner;
    private final Random random = new Random();

    // main thread only
    private WorldBossRepository.State state;
    private long announcedSeq = -1;
    private boolean choosingSpot;
    private boolean loading;
    /** A boss left in an unloaded chunk by a crash mid-fight — removed when its chunk's entities load. */
    private String staleBossUuid;
    private String warnedNoSpotsDate;
    private final Map<UUID, Double> damage = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private double bossMaxHealth = 1;
    private Chunk ticketChunk;

    public WorldBossService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, WorldBossRepository repository,
                            Executor executor, Settings settings) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.executor = executor;
        this.settings = settings;
        this.owner = Bukkit.getWorld(settings.world()) != null;
    }

    public boolean isOwner() {
        return owner;
    }

    public Settings settings() {
        return settings;
    }

    public WorldBossRepository repository() {
        return repository;
    }

    /** Main thread, once from onEnable: loads the shared state; the owner cleans up a fight cut short by a restart. */
    public void start() {
        if (loading) {
            return;
        }
        loading = true;
        executor.execute(() -> {
            WorldBossRepository.State loaded;
            try {
                loaded = repository.state();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "월드보스 상태 불러오기 실패 — 잠시 후 다시 시도합니다", e);
                runOnMain(() -> loading = false);
                return;
            }
            runOnMain(() -> {
                loading = false;
                state = loaded;
                announcedSeq = loaded.seq();
                if (!owner) {
                    return;
                }
                if (ACTIVE.equals(loaded.phase())) {
                    // A crash mid-fight: the boss may still be saved in its (not yet loaded) chunk.
                    staleBossUuid = loaded.bossUuid();
                    removeBossEntity(loaded.bossUuid());
                    transition(IDLE, loaded.spot(), loaded.eventDate(), 0, 0, null, "ESCAPED", null);
                } else if (ANNOUNCED.equals(loaded.phase())
                        && System.currentTimeMillis() >= loaded.spawnAt() + settings.fightMinutes() * 60_000L) {
                    // Announced before a long outage — its fight window is long gone, don't spawn it at a random hour.
                    transition(IDLE, loaded.spot(), loaded.eventDate(), 0, 0, null, "FAILED", null);
                }
            });
        });
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (staleBossUuid == null) {
            return;
        }
        for (Entity entity : event.getEntities()) {
            if (entity.getUniqueId().toString().equals(staleBossUuid)) {
                entity.remove();
                staleBossUuid = null;
                return;
            }
        }
    }

    // ---- owner state machine (main thread) ----

    /** Main thread, every 5 seconds on the owner. */
    public void ownerTick() {
        if (!owner) {
            return;
        }
        if (state == null) {
            start(); // the first load failed (DB hiccup) — keep retrying
            return;
        }
        long now = System.currentTimeMillis();
        switch (state.phase()) {
            case IDLE -> WorldBossRules.announceDue(ZonedDateTime.now(), settings.spawnTime(), settings.announceMinutes(),
                            settings.fightMinutes(), state.eventDate())
                    .ifPresent(spawnAt -> chooseSpotAndAnnounce(spawnAt.toLocalDate().toString(), spawnAt.toInstant().toEpochMilli(), null));
            case ANNOUNCED -> {
                if (now >= state.spawnAt()) {
                    spawnBoss();
                }
            }
            case ACTIVE -> {
                Entity boss = state.bossUuid() == null ? null : Bukkit.getEntity(UUID.fromString(state.bossUuid()));
                if (now >= state.despawnAt() || boss == null || !boss.isValid()) {
                    // Timeout, or gone without dying here (despawned, /mm killall, ...) — the spot chunk is ticketed, so "not loaded" isn't the reason.
                    removeBossEntity(state.bossUuid());
                    endFight("ESCAPED", null);
                }
            }
            default -> {
            }
        }
    }

    /** Loads spots off-thread, then (main thread) announces at a random one — or skips today if none exist. */
    private void chooseSpotAndAnnounce(String eventDate, long spawnAt, CommandSender requester) {
        if (choosingSpot) {
            return;
        }
        choosingSpot = true;
        executor.execute(() -> {
            List<WorldBossRepository.Spot> spots;
            try {
                spots = repository.spots();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "월드보스 위치 불러오기 실패", e);
                spots = null;
            }
            List<WorldBossRepository.Spot> loaded = spots;
            runOnMain(() -> {
                choosingSpot = false;
                if (loaded == null || !IDLE.equals(state.phase())) {
                    return;
                }
                List<WorldBossRepository.Spot> usable = loaded.stream().filter(s -> s.world().equals(settings.world())).toList();
                if (usable.isEmpty()) {
                    if (requester != null) {
                        messages.send(requester, "worldboss.no-spots");
                    } else if (!eventDate.equals(warnedNoSpotsDate)) {
                        warnedNoSpotsDate = eventDate;
                        plugin.getLogger().warning("월드보스 위치가 없어 오늘 월드보스를 건너뜁니다 — /월드보스 위치추가 <이름>으로 등록하세요.");
                    }
                    return;
                }
                WorldBossRepository.Spot spot = usable.get(random.nextInt(usable.size()));
                transition(ANNOUNCED, spot, eventDate, spawnAt, 0, null, null, null);
                if (requester != null) {
                    messages.send(requester, "worldboss.summoned");
                }
            });
        });
    }

    private void spawnBoss() {
        WorldBossRepository.Spot spot = state.spot();
        World world = spot == null ? null : Bukkit.getWorld(spot.world());
        var mythicMob = MythicBukkit.inst().getMobManager().getMythicMob(settings.mobId());
        if (world == null || mythicMob.isEmpty()) {
            plugin.getLogger().severe("월드보스 소환 실패 — 월드(" + (spot == null ? "?" : spot.world()) + ") 또는 MythicMobs 몹(" + settings.mobId() + ")을 찾을 수 없습니다.");
            endFight("FAILED", null);
            return;
        }
        Location location = new Location(world, spot.x(), spot.y(), spot.z());
        Chunk chunk = location.getChunk();
        chunk.addPluginChunkTicket(plugin);
        ticketChunk = chunk;
        ActiveMob activeMob;
        try {
            activeMob = mythicMob.get().spawn(BukkitAdapter.adapt(location), 1.0);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "월드보스 소환 실패", e);
            activeMob = null;
        }
        if (activeMob == null) {
            endFight("FAILED", null);
            return;
        }
        Entity entity = activeMob.getEntity().getBukkitEntity();
        if (entity instanceof LivingEntity living) {
            // MythicMobs' default Despawn: true would discard it on the next tick when no player is within range.
            living.setRemoveWhenFarAway(false);
        }
        AttributeInstance maxHealth = entity instanceof LivingEntity living ? living.getAttribute(Attribute.MAX_HEALTH) : null;
        bossMaxHealth = maxHealth == null ? 1 : Math.max(1, maxHealth.getValue());
        damage.clear();
        names.clear();
        transition(ACTIVE, spot, state.eventDate(), state.spawnAt(),
                System.currentTimeMillis() + settings.fightMinutes() * 60_000L, activeMob.getUniqueId().toString(), null, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!isBoss(event.getEntity())) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        double health = event.getEntity() instanceof LivingEntity living ? living.getHealth() : event.getFinalDamage();
        damage.merge(attacker.getUniqueId(), Math.min(event.getFinalDamage(), health), Double::sum);
        names.put(attacker.getUniqueId(), attacker.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!isBoss(event.getEntity())) {
            return;
        }
        List<WorldBossRules.Reward> rewards = WorldBossRules.rewards(damage, bossMaxHealth, settings.rankRewards(),
                settings.participationReward(), settings.minDamageShare());
        List<String> topNames = new ArrayList<>();
        for (UUID uuid : WorldBossRules.topByDamage(damage, 3)) {
            topNames.add(names.getOrDefault(uuid, "?"));
        }
        executor.execute(() -> {
            for (WorldBossRules.Reward reward : rewards) {
                String reason = reward.rank() > 0 ? "월드보스 " + reward.rank() + "위" : "월드보스 참여";
                try {
                    core.payouts().enqueue(reward.player(), reward.amount(), SOURCE, reason);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "월드보스 보상 장부 기록 실패 — 수동 지급 필요: "
                            + reward.player() + " " + reward.amount() + "온 (" + reason + ")", e);
                }
            }
        });
        endFight("KILLED", String.join("|", topNames));
    }

    private boolean isBoss(Entity entity) {
        return owner && state != null && ACTIVE.equals(state.phase())
                && entity.getUniqueId().toString().equals(state.bossUuid());
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private void endFight(String outcome, String top) {
        if (ticketChunk != null) {
            ticketChunk.removePluginChunkTicket(plugin);
            ticketChunk = null;
        }
        damage.clear();
        names.clear();
        transition(IDLE, state.spot(), state.eventDate(), 0, 0, null, outcome, top);
    }

    private void removeBossEntity(String bossUuid) {
        if (bossUuid == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(UUID.fromString(bossUuid));
        if (entity != null) {
            entity.remove();
        }
    }

    /** Owner, main thread: bump seq, remember and announce locally, persist asynchronously (single-thread executor keeps order). */
    private void transition(String phase, WorldBossRepository.Spot spot, String eventDate, long spawnAt, long despawnAt,
                            String bossUuid, String outcome, String top) {
        state = new WorldBossRepository.State(state.seq() + 1, phase, spot, eventDate, spawnAt, despawnAt, bossUuid, outcome, top);
        WorldBossRepository.State snapshot = state;
        announcedSeq = snapshot.seq();
        announce(snapshot);
        executor.execute(() -> {
            try {
                repository.saveState(snapshot);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "월드보스 상태 저장 실패", e);
            }
        });
    }

    // ---- other servers ----

    /** Executor, every 30 seconds on non-owner servers: pick up the owner's transitions and announce each once. */
    public void poll() {
        if (owner) {
            return;
        }
        WorldBossRepository.State loaded;
        try {
            loaded = repository.state();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "월드보스 상태 조회 실패", e);
            return;
        }
        runOnMain(() -> {
            if (state != null && loaded.seq() < state.seq()) {
                return;
            }
            state = loaded;
            if (announcedSeq < 0) {
                announcedSeq = loaded.seq();
            } else if (loaded.seq() != announcedSeq) {
                announcedSeq = loaded.seq();
                announce(loaded);
            }
        });
    }

    private void announce(WorldBossRepository.State s) {
        String spotName = s.spot() == null ? "?" : s.spot().name();
        switch (s.phase()) {
            case ANNOUNCED -> messages.broadcast("worldboss.announced",
                    Placeholder.unparsed("spot", spotName),
                    Placeholder.unparsed("x", String.valueOf(s.spot() == null ? 0 : (long) Math.floor(s.spot().x()))),
                    Placeholder.unparsed("z", String.valueOf(s.spot() == null ? 0 : (long) Math.floor(s.spot().z()))),
                    Placeholder.unparsed("minutes", String.valueOf(Math.max(1, (s.spawnAt() - System.currentTimeMillis() + 59_999) / 60_000))));
            case ACTIVE -> messages.broadcast("worldboss.spawned",
                    Placeholder.unparsed("spot", spotName),
                    Placeholder.unparsed("x", String.valueOf((long) Math.floor(s.spot().x()))),
                    Placeholder.unparsed("z", String.valueOf((long) Math.floor(s.spot().z()))),
                    Placeholder.unparsed("minutes", String.valueOf(settings.fightMinutes())));
            case IDLE -> {
                if ("KILLED".equals(s.lastOutcome())) {
                    messages.broadcast("worldboss.killed");
                    String[] top = s.lastTop() == null || s.lastTop().isEmpty() ? new String[0] : s.lastTop().split("\\|");
                    for (int i = 0; i < top.length; i++) {
                        messages.broadcast("worldboss.rank-line",
                                Placeholder.unparsed("rank", String.valueOf(i + 1)),
                                Placeholder.unparsed("player", top[i]));
                    }
                } else if ("ESCAPED".equals(s.lastOutcome())) {
                    messages.broadcast("worldboss.escaped");
                }
            }
            default -> {
            }
        }
    }

    // ---- commands (main thread) ----

    public void summon(CommandSender sender) {
        if (!owner) {
            messages.send(sender, "worldboss.owner-only", Placeholder.unparsed("world", settings.world()));
            return;
        }
        if (state == null || !IDLE.equals(state.phase())) {
            messages.send(sender, "worldboss.already-active");
            return;
        }
        chooseSpotAndAnnounce(state.eventDate(), System.currentTimeMillis(), sender);
    }

    public void dismiss(CommandSender sender) {
        if (!owner) {
            messages.send(sender, "worldboss.owner-only", Placeholder.unparsed("world", settings.world()));
            return;
        }
        if (state == null || IDLE.equals(state.phase())) {
            messages.send(sender, "worldboss.none");
            return;
        }
        removeBossEntity(state.bossUuid());
        endFight("ESCAPED", null);
        messages.send(sender, "worldboss.dismissed");
    }

    public void showStatus(CommandSender sender) {
        WorldBossRepository.State s = state;
        long now = System.currentTimeMillis();
        if (s == null || IDLE.equals(s.phase())) {
            messages.send(sender, "worldboss.status-idle",
                    Placeholder.unparsed("time", String.format("%02d:%02d", settings.spawnTime().getHour(), settings.spawnTime().getMinute())));
        } else if (ANNOUNCED.equals(s.phase())) {
            messages.send(sender, "worldboss.status-announced",
                    Placeholder.unparsed("spot", s.spot() == null ? "?" : s.spot().name()),
                    Placeholder.unparsed("remaining", DurationFormat.humanize(Math.max(0, s.spawnAt() - now))));
        } else {
            messages.send(sender, "worldboss.status-active",
                    Placeholder.unparsed("spot", s.spot() == null ? "?" : s.spot().name()),
                    Placeholder.unparsed("x", String.valueOf(s.spot() == null ? 0 : (long) Math.floor(s.spot().x()))),
                    Placeholder.unparsed("z", String.valueOf(s.spot() == null ? 0 : (long) Math.floor(s.spot().z()))),
                    Placeholder.unparsed("remaining", DurationFormat.humanize(Math.max(0, s.despawnAt() - now))));
        }
    }

    /** Main thread, from onDisable: an ongoing fight is left to start() on the next boot, which cleans it up. */
    public void shutdown() {
        if (owner && state != null && ACTIVE.equals(state.phase())) {
            removeBossEntity(state.bossUuid()); // chunk still loaded here; start() marks the fight ESCAPED on next boot
        }
        if (ticketChunk != null) {
            ticketChunk.removePluginChunkTicket(plugin);
            ticketChunk = null;
        }
    }

    private void runOnMain(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
