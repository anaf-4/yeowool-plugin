package com.yeowool.community.party;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

/**
 * A party is a name-unique, leader-led group of up to {@code max-size}
 * players (2-{@link #HUD_SLOT_LIMIT}, the {@link #HUD_SLOT_LIMIT} cap coming
 * from how many slots the Volya Party HUD layout actually has room for).
 * Membership lives in {@code yw_party}/{@code yw_party_member} — the same
 * shared MySQL every other cross-server feature in this project uses — so
 * every mutation here is a DB round trip (async, like {@code FriendManager}),
 * never a long-lived in-memory cache: unlike couples/friends (which barely
 * change and are read once at boot), a party's membership changes constantly
 * during active play and must be correct the instant another server reads it
 * (e.g. a `/파티 정보` or the HUD). Only the HUD's own read path ({@link
 * PartyPresenceTask}) gets a short-lived (~1s) cache, since that one needs a
 * non-blocking synchronous read on every placeholder resolution.
 */
public final class PartyManager {

    public static final int HUD_SLOT_LIMIT = 4;
    private static final Pattern NAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9]{2,16}$");

    /** {@code FREE} — {@code /파티 가입 <이름>} joins immediately (today's only behavior). {@code APPROVAL} — it queues a request the leader must {@code /파티 수락}. */
    public enum JoinMode { FREE, APPROVAL }

    private final JavaPlugin plugin;
    private final PartyRepository repository;
    private final ExecutorService executor;

    public PartyManager(JavaPlugin plugin, PartyRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    /** Same rule {@link #create} enforces server-side — exposed so {@code PartyCreateListener}'s anvil can validate as the admin types, before ever hitting the DB. */
    public static boolean isValidName(String name) {
        return NAME_PATTERN.matcher(name).matches();
    }

    public record PartyInfo(long id, String name, UUID leader, int maxSize, JoinMode joinMode, List<PartyRepository.MemberRow> members) {
    }

    public enum CreateResult { OK, ALREADY_IN_PARTY, NAME_TAKEN, INVALID_NAME, INVALID_MAX_SIZE, ERROR }

    public record CreateOutcome(CreateResult result, PartyInfo party) {
    }

    public CompletableFuture<CreateOutcome> create(UUID leader, String leaderName, String partyName, int maxSize, JoinMode joinMode) {
        return CompletableFuture.supplyAsync(() -> {
            if (!NAME_PATTERN.matcher(partyName).matches()) {
                return new CreateOutcome(CreateResult.INVALID_NAME, null);
            }
            if (maxSize < 2 || maxSize > HUD_SLOT_LIMIT) {
                return new CreateOutcome(CreateResult.INVALID_MAX_SIZE, null);
            }
            try {
                if (repository.findPartyIdOf(leader).isPresent()) {
                    return new CreateOutcome(CreateResult.ALREADY_IN_PARTY, null);
                }
                if (repository.findByName(partyName).isPresent()) {
                    return new CreateOutcome(CreateResult.NAME_TAKEN, null);
                }
                long partyId = repository.createParty(partyName, leader, leaderName, maxSize, joinMode);
                var members = repository.findMembers(partyId);
                return new CreateOutcome(CreateResult.OK, new PartyInfo(partyId, partyName, leader, maxSize, joinMode, members));
            } catch (SQLException e) {
                plugin.getLogger().severe("파티 생성 실패: " + e.getMessage());
                return new CreateOutcome(CreateResult.ERROR, null);
            }
        }, executor);
    }

    public enum LeaveResult { OK_LEFT, OK_DISBANDED, NOT_IN_PARTY, ERROR }

    /** If the leader leaves, the earliest-joined remaining member is promoted; leaving as the last member disbands it. */
    public record LeaveOutcome(LeaveResult result, String partyName, UUID newLeader) {
    }

    public CompletableFuture<LeaveOutcome> leave(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<Long> partyId = repository.findPartyIdOf(uuid);
                if (partyId.isEmpty()) {
                    return new LeaveOutcome(LeaveResult.NOT_IN_PARTY, null, null);
                }
                var party = repository.findById(partyId.get()).orElse(null);
                if (party == null) {
                    repository.removeMember(uuid);
                    return new LeaveOutcome(LeaveResult.NOT_IN_PARTY, null, null);
                }
                repository.removeMember(uuid);
                var remaining = repository.findMembers(partyId.get());
                if (remaining.isEmpty()) {
                    repository.deleteParty(partyId.get());
                    return new LeaveOutcome(LeaveResult.OK_DISBANDED, party.name(), null);
                }
                if (party.leader().equals(uuid)) {
                    UUID newLeader = remaining.get(0).uuid();
                    repository.updateLeader(partyId.get(), newLeader);
                    return new LeaveOutcome(LeaveResult.OK_LEFT, party.name(), newLeader);
                }
                return new LeaveOutcome(LeaveResult.OK_LEFT, party.name(), party.leader());
            } catch (SQLException e) {
                plugin.getLogger().severe("파티 탈퇴 실패: " + e.getMessage());
                return new LeaveOutcome(LeaveResult.ERROR, null, null);
            }
        }, executor);
    }

    public enum DisbandResult { OK, NOT_IN_PARTY, NOT_LEADER, ERROR }

    public record DisbandOutcome(DisbandResult result, String partyName, List<PartyRepository.MemberRow> members) {
    }

    public CompletableFuture<DisbandOutcome> disband(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<Long> partyId = repository.findPartyIdOf(uuid);
                if (partyId.isEmpty()) {
                    return new DisbandOutcome(DisbandResult.NOT_IN_PARTY, null, null);
                }
                var party = repository.findById(partyId.get()).orElse(null);
                if (party == null) {
                    return new DisbandOutcome(DisbandResult.NOT_IN_PARTY, null, null);
                }
                if (!party.leader().equals(uuid)) {
                    return new DisbandOutcome(DisbandResult.NOT_LEADER, null, null);
                }
                var members = repository.findMembers(partyId.get());
                repository.deleteParty(partyId.get());
                return new DisbandOutcome(DisbandResult.OK, party.name(), members);
            } catch (SQLException e) {
                plugin.getLogger().severe("파티 삭제 실패: " + e.getMessage());
                return new DisbandOutcome(DisbandResult.ERROR, null, null);
            }
        }, executor);
    }

    public enum JoinResult { OK, REQUEST_SENT, ALREADY_IN_PARTY, ALREADY_REQUESTED, PARTY_NOT_FOUND, PARTY_FULL, ERROR }

    public record JoinOutcome(JoinResult result, PartyInfo party) {
    }

    /** {@code APPROVAL} parties queue a request ({@link #approve}/{@link #deny}) instead of joining immediately. */
    public CompletableFuture<JoinOutcome> join(UUID uuid, String name, String partyName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (repository.findPartyIdOf(uuid).isPresent()) {
                    return new JoinOutcome(JoinResult.ALREADY_IN_PARTY, null);
                }
                var party = repository.findByName(partyName).orElse(null);
                if (party == null) {
                    return new JoinOutcome(JoinResult.PARTY_NOT_FOUND, null);
                }
                if (repository.countMembers(party.id()) >= party.maxSize()) {
                    return new JoinOutcome(JoinResult.PARTY_FULL, null);
                }
                if (party.joinMode() == JoinMode.APPROVAL) {
                    if (repository.findJoinRequestByName(party.id(), name).isPresent()) {
                        return new JoinOutcome(JoinResult.ALREADY_REQUESTED, null);
                    }
                    repository.addJoinRequest(party.id(), uuid, name);
                    return new JoinOutcome(JoinResult.REQUEST_SENT,
                            new PartyInfo(party.id(), party.name(), party.leader(), party.maxSize(), party.joinMode(), List.of()));
                }
                repository.addMember(party.id(), uuid, name);
                var members = repository.findMembers(party.id());
                return new JoinOutcome(JoinResult.OK, new PartyInfo(party.id(), party.name(), party.leader(), party.maxSize(), party.joinMode(), members));
            } catch (SQLException e) {
                plugin.getLogger().severe("파티 가입 실패: " + e.getMessage());
                return new JoinOutcome(JoinResult.ERROR, null);
            }
        }, executor);
    }

    public enum ApproveResult { OK, NOT_LEADER, NOT_IN_PARTY, NO_SUCH_REQUEST, PARTY_FULL, ERROR }

    public record ApproveOutcome(ApproveResult result, UUID requester, PartyInfo party) {
    }

    public CompletableFuture<ApproveOutcome> approve(UUID leaderUuid, String requesterName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<Long> partyId = repository.findPartyIdOf(leaderUuid);
                if (partyId.isEmpty()) {
                    return new ApproveOutcome(ApproveResult.NOT_IN_PARTY, null, null);
                }
                var party = repository.findById(partyId.get()).orElse(null);
                if (party == null || !party.leader().equals(leaderUuid)) {
                    return new ApproveOutcome(ApproveResult.NOT_LEADER, null, null);
                }
                var request = repository.findJoinRequestByName(party.id(), requesterName).orElse(null);
                if (request == null) {
                    return new ApproveOutcome(ApproveResult.NO_SUCH_REQUEST, null, null);
                }
                if (repository.countMembers(party.id()) >= party.maxSize()) {
                    return new ApproveOutcome(ApproveResult.PARTY_FULL, null, null);
                }
                repository.addMember(party.id(), request.uuid(), request.name());
                repository.removeJoinRequest(request.uuid());
                var members = repository.findMembers(party.id());
                return new ApproveOutcome(ApproveResult.OK, request.uuid(),
                        new PartyInfo(party.id(), party.name(), party.leader(), party.maxSize(), party.joinMode(), members));
            } catch (SQLException e) {
                plugin.getLogger().severe("파티 가입 승인 실패: " + e.getMessage());
                return new ApproveOutcome(ApproveResult.ERROR, null, null);
            }
        }, executor);
    }

    public enum DenyResult { OK, NOT_LEADER, NOT_IN_PARTY, NO_SUCH_REQUEST, ERROR }

    public record DenyOutcome(DenyResult result, UUID requester, String partyName) {
    }

    public CompletableFuture<DenyOutcome> deny(UUID leaderUuid, String requesterName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<Long> partyId = repository.findPartyIdOf(leaderUuid);
                if (partyId.isEmpty()) {
                    return new DenyOutcome(DenyResult.NOT_IN_PARTY, null, null);
                }
                var party = repository.findById(partyId.get()).orElse(null);
                if (party == null || !party.leader().equals(leaderUuid)) {
                    return new DenyOutcome(DenyResult.NOT_LEADER, null, null);
                }
                var request = repository.findJoinRequestByName(party.id(), requesterName).orElse(null);
                if (request == null) {
                    return new DenyOutcome(DenyResult.NO_SUCH_REQUEST, null, null);
                }
                repository.removeJoinRequest(request.uuid());
                return new DenyOutcome(DenyResult.OK, request.uuid(), party.name());
            } catch (SQLException e) {
                plugin.getLogger().severe("파티 가입 거절 실패: " + e.getMessage());
                return new DenyOutcome(DenyResult.ERROR, null, null);
            }
        }, executor);
    }

    public CompletableFuture<Optional<PartyInfo>> info(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<Long> partyId = repository.findPartyIdOf(uuid);
                if (partyId.isEmpty()) {
                    return Optional.<PartyInfo>empty();
                }
                var party = repository.findById(partyId.get()).orElse(null);
                if (party == null) {
                    return Optional.<PartyInfo>empty();
                }
                var members = repository.findMembers(partyId.get());
                return Optional.of(new PartyInfo(party.id(), party.name(), party.leader(), party.maxSize(), party.joinMode(), members));
            } catch (SQLException e) {
                plugin.getLogger().severe("파티 정보 조회 실패: " + e.getMessage());
                return Optional.<PartyInfo>empty();
            }
        }, executor);
    }

    /** For {@code /파티 정보} to show the leader who's waiting on approval — empty for non-leaders or FREE parties. */
    public CompletableFuture<List<PartyRepository.JoinRequestRow>> pendingRequests(long partyId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.findJoinRequestsOf(partyId);
            } catch (SQLException e) {
                plugin.getLogger().severe("파티 가입 신청 목록 조회 실패: " + e.getMessage());
                return List.<PartyRepository.JoinRequestRow>of();
            }
        }, executor);
    }

    /** Called when a player disconnects — their party's presence row would otherwise stay stale until it's overwritten. */
    public void clearPresence(UUID uuid) {
        executor.execute(() -> {
            try {
                repository.removePresence(uuid);
            } catch (SQLException e) {
                plugin.getLogger().warning("파티 프레즌스 정리 실패 (" + uuid + "): " + e.getMessage());
            }
        });
    }

    /** One HUD row — {@link PartyPresenceTask} rebuilds these every ~second; {@link #hudSlots} just reads the latest snapshot. */
    public record PartySlot(String name, double health, double maxHealth, boolean leader) {
    }

    private volatile Map<UUID, List<PartySlot>> hudCache = Map.of();

    /** Non-blocking — safe to call from a PlaceholderAPI request. Empty if {@code viewer} isn't in a party or the cache hasn't ticked yet. */
    public List<PartySlot> hudSlots(UUID viewer) {
        return hudCache.getOrDefault(viewer, List.of());
    }

    void setHudCache(Map<UUID, List<PartySlot>> snapshot) {
        this.hudCache = snapshot;
    }

    PartyRepository repository() {
        return repository;
    }

    ExecutorService executor() {
        return executor;
    }

    JavaPlugin plugin() {
        return plugin;
    }
}
