package com.yeowool.raid;

import com.yeowool.raid.database.RaidDefinitionRepository;
import com.yeowool.raid.database.RaidInstanceRepository;
import com.yeowool.raid.party.RaidPartyLookup;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Owns raid definitions (small admin-defined dataset, fully cached — same trade-off as
 * {@code QuestManager}) and every currently-active {@link RaidSession} (in-memory only).
 */
public final class RaidManager {

    private final JavaPlugin plugin;
    private final RaidDefinitionRepository definitionRepository;
    private final RaidInstanceRepository instanceRepository;
    private final ExecutorService executor;

    private final ConcurrentHashMap<Long, RaidDefinition> definitions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<RaidInstanceSlot>> instanceSlots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, RaidInstanceAllocator> allocators = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, RaidSession> sessionsByPartyId = new ConcurrentHashMap<>();

    public RaidManager(JavaPlugin plugin, RaidDefinitionRepository definitionRepository,
                        RaidInstanceRepository instanceRepository, ExecutorService executor) {
        this.plugin = plugin;
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.executor = executor;
    }

    /**
     * Safe to call again after the initial {@code onEnable} load (e.g. a "새로고침" admin command) to
     * pick up edits made on another server sharing the same database. Only replaces an allocator when
     * it's missing or its raid's instanceCount actually changed with no active sessions in progress —
     * otherwise a reload would reset occupied-slot bookkeeping out from under running sessions.
     */
    public void loadAll() throws SQLException {
        definitions.putAll(definitionRepository.loadAll());
        instanceSlots.putAll(instanceRepository.loadAll());
        for (RaidDefinition definition : definitions.values()) {
            RaidInstanceAllocator existing = allocators.get(definition.id());
            if (existing == null) {
                allocators.put(definition.id(), new RaidInstanceAllocator(definition.instanceCount()));
            } else if (existing.slotCount() != definition.instanceCount() && !hasActiveSession(definition.id())) {
                allocators.put(definition.id(), new RaidInstanceAllocator(definition.instanceCount()));
            }
        }
        plugin.getLogger().info("보스 레이드 " + definitions.size() + "개를 불러왔습니다.");
    }

    /** True if any in-progress session currently belongs to this raid. */
    private boolean hasActiveSession(long raidId) {
        return sessionsByPartyId.values().stream()
                .anyMatch(session -> session.getState() == RaidSessionState.IN_PROGRESS && session.getRaidId() == raidId);
    }

    // ---- definitions ----

    public Collection<RaidDefinition> all() {
        return definitions.values();
    }

    public Optional<RaidDefinition> find(String name) {
        return definitions.values().stream().filter(d -> d.name().equalsIgnoreCase(name)).findFirst();
    }

    /** Mirrors QuestManager.byNpc — the mechanism a Citizens NPC uses to find which raid(s) it offers. */
    public List<RaidDefinition> byNpc(int npcId) {
        return definitions.values().stream().filter(d -> d.npcId() == npcId).toList();
    }

    public enum CreateResult { SUCCESS, ALREADY_EXISTS }

    /** Blocking — call off the main thread. */
    public CreateResult create(String name) throws SQLException {
        if (find(name).isPresent()) {
            return CreateResult.ALREADY_EXISTS;
        }
        long id = definitionRepository.insert(name);
        RaidDefinition definition = new RaidDefinition(id, name, -1, "", "", 1, 1, 6, 1200, 5, 3, List.of());
        definitions.put(id, definition);
        allocators.put(id, new RaidInstanceAllocator(definition.instanceCount()));
        return CreateResult.SUCCESS;
    }

    /** Blocking — call off the main thread. */
    public boolean delete(String name) throws SQLException {
        var definition = find(name);
        if (definition.isEmpty()) {
            return false;
        }
        definitionRepository.delete(definition.get().id());
        definitions.remove(definition.get().id());
        allocators.remove(definition.get().id());
        instanceSlots.remove(definition.get().id());
        return true;
    }

    /** Blocking — call off the main thread. */
    public boolean setNpcId(String name, int npcId) throws SQLException {
        return mutate(name, d -> {
            definitionRepository.updateNpcId(d.id(), npcId);
            return d.withNpcId(npcId);
        });
    }

    /** Blocking — call off the main thread. */
    public boolean setMythicMob(String name, String mythicMobId) throws SQLException {
        return mutate(name, d -> {
            definitionRepository.updateMythicMob(d.id(), mythicMobId);
            return d.withMythicMob(mythicMobId);
        });
    }

    /** Blocking — call off the main thread. */
    public boolean setTicket(String name, String ticketItemId, int ticketAmount) throws SQLException {
        return mutate(name, d -> {
            definitionRepository.updateTicket(d.id(), ticketItemId, ticketAmount);
            return d.withTicket(ticketItemId, ticketAmount);
        });
    }

    /** Blocking — call off the main thread. */
    public boolean setPartySize(String name, int min, int max) throws SQLException {
        return mutate(name, d -> {
            definitionRepository.updatePartySize(d.id(), min, max);
            return d.withPartySize(min, max);
        });
    }

    /** Blocking — call off the main thread. */
    public boolean setTimeLimit(String name, int seconds) throws SQLException {
        return mutate(name, d -> {
            definitionRepository.updateTimeLimit(d.id(), seconds);
            return d.withTimeLimit(seconds);
        });
    }

    /** Blocking — call off the main thread. */
    public boolean setSharedLives(String name, int lives) throws SQLException {
        return mutate(name, d -> {
            definitionRepository.updateSharedLives(d.id(), lives);
            return d.withSharedLives(lives);
        });
    }

    /** Blocking — call off the main thread. Refuses (returns false) while the raid has any session
     * in progress, since replacing the allocator mid-run would drop its occupied-slot bookkeeping and
     * risk double-assigning an arena to two parties. */
    public boolean setInstanceCount(String name, int count) throws SQLException {
        var definition = find(name);
        if (definition.isEmpty()) {
            return false;
        }
        if (hasActiveSession(definition.get().id())) {
            return false;
        }
        definitionRepository.updateInstanceCount(definition.get().id(), count);
        RaidDefinition updated = definition.get().withInstanceCount(count);
        definitions.put(updated.id(), updated);
        allocators.put(updated.id(), new RaidInstanceAllocator(count));
        return true;
    }

    /** Blocking — call off the main thread. */
    public boolean setRewardItems(String name, List<ItemStack> items) throws SQLException {
        return mutate(name, d -> {
            definitionRepository.updateRewardItems(d.id(), items);
            return d.withRewardItems(items);
        });
    }

    /** Blocking — call off the main thread. field is "entry", "boss_spawn", "exit", "bound_min", or "bound_max". */
    public boolean setInstanceSlotLocation(long raidId, int slotIndex, String field, Location location) throws SQLException {
        if (!definitions.containsKey(raidId)) {
            return false;
        }
        instanceRepository.upsertSlot(raidId, slotIndex, field, location);
        instanceSlots.put(raidId, instanceRepository.loadAll().getOrDefault(raidId, new ArrayList<>()));
        return true;
    }

    public List<RaidInstanceSlot> instanceSlotsFor(long raidId) {
        return instanceSlots.getOrDefault(raidId, List.of());
    }

    private interface Mutation {
        RaidDefinition apply(RaidDefinition current) throws SQLException;
    }

    private boolean mutate(String name, Mutation mutation) throws SQLException {
        var definition = find(name);
        if (definition.isEmpty()) {
            return false;
        }
        RaidDefinition updated = mutation.apply(definition.get());
        definitions.put(updated.id(), updated);
        return true;
    }

    // ---- sessions (in-memory, hot path) ----
    //
    // Entry is a three-step dance because the boss's real entity UUID is only known *after* it
    // spawns, but we must not spawn a boss unless we're sure the party can actually enter:
    //   1. peekEntryDenial   — read-only validation (party size / not-already-in-raid / tickets)
    //   2. reserveSlot       — atomically claims an instance slot (or throws if none free)
    //   3. startSession      — called once the boss has been spawned; creates the RaidSession
    // If step 2 succeeds but the caller can't finish (e.g. missing slot coordinates), it must call
    // releaseReservedSlot to give the slot back.

    /** Read-only — does not reserve anything. Checked again racily by reserveSlot's own isFull() check. */
    public Optional<RaidEntryDenialReason> peekEntryDenial(RaidDefinition raid, RaidPartyLookup.PartyMembers party, boolean hasEnoughTickets) {
        boolean partyAlreadyInRaid = sessionsByPartyId.containsKey(party.partyId());
        RaidInstanceAllocator allocator = allocators.computeIfAbsent(raid.id(), id -> new RaidInstanceAllocator(raid.instanceCount()));
        return RaidEntryValidator.validate(party.members().size(), raid.minPartySize(), raid.maxPartySize(),
                partyAlreadyInRaid, hasEnoughTickets, !allocator.isFull());
    }

    /** Throws IllegalStateException if no slot is free — caller must have just checked peekEntryDenial. */
    public int reserveSlot(long raidId) {
        RaidInstanceAllocator allocator = allocators.computeIfAbsent(raidId, id -> new RaidInstanceAllocator(1));
        OptionalInt slot = allocator.allocate();
        if (slot.isEmpty()) {
            throw new IllegalStateException("reserveSlot called with no free slot for raid " + raidId);
        }
        return slot.getAsInt();
    }

    public void releaseReservedSlot(long raidId, int slotIndex) {
        allocators.computeIfAbsent(raidId, id -> new RaidInstanceAllocator(1)).release(slotIndex);
    }

    /** Call once the boss has actually been spawned and its real entity UUID is known. */
    public void startSession(long raidId, int slotIndex, long partyId, java.util.Set<UUID> members, UUID bossEntityId, int sharedLives) {
        RaidSession session = new RaidSession(raidId, slotIndex, members, bossEntityId, sharedLives);
        sessionsByPartyId.put(partyId, session);
    }

    public Optional<RaidSession> activeSessionFor(UUID player) {
        return sessionsByPartyId.values().stream()
                .filter(session -> session.getState() == RaidSessionState.IN_PROGRESS && session.getPartyMembers().contains(player))
                .findFirst();
    }

    public Optional<RaidSession> sessionByBossEntity(UUID bossEntityId) {
        return sessionsByPartyId.values().stream()
                .filter(session -> session.getBossEntityId().equals(bossEntityId))
                .findFirst();
    }

    public Collection<RaidSession> activeSessions() {
        return sessionsByPartyId.values();
    }

    public void endSession(RaidSession session, RaidSessionState finalState) {
        session.setState(finalState);

        RaidInstanceSlot slot = instanceSlotsFor(session.getRaidId()).stream()
                .filter(s -> s.slotIndex() == session.getSlotIndex())
                .findFirst()
                .orElse(null);
        if (slot != null && slot.exit() != null) {
            for (UUID memberId : session.getPartyMembers()) {
                org.bukkit.entity.Player member = org.bukkit.Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.teleport(slot.exit());
                }
            }
        }
        var boss = org.bukkit.Bukkit.getEntity(session.getBossEntityId());
        if (boss != null) {
            boss.remove();
        }

        allocators.computeIfAbsent(session.getRaidId(), id -> new RaidInstanceAllocator(1)).release(session.getSlotIndex());
        sessionsByPartyId.values().remove(session);
    }
}
