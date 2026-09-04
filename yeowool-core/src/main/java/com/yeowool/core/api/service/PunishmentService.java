package com.yeowool.core.api.service;

import com.yeowool.core.api.model.PunishmentEntry;
import com.yeowool.core.api.model.PunishmentType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Shared moderation ledger (warn/kick/mute/ban) so any plugin can both record
 * and enforce punishments without its own table. YeowoolCore itself enforces
 * {@link PunishmentType#BAN} at login (see {@code PlayerConnectionListener})
 * since that has to happen before any other plugin's join listener runs;
 * {@link PunishmentType#MUTE} is enforced by YeowoolCommunity's chat
 * listener. {@link PunishmentType#WARN} / {@link PunishmentType#KICK} are
 * just a permanent record — this service doesn't itself kick anyone, the
 * calling command does that with the {@link org.bukkit.entity.Player}
 * reference it already has.
 */
public interface PunishmentService {

    /** {@code expiresAt} of {@code null} means permanent. {@code staff} of {@code null} means console. */
    CompletableFuture<Void> record(UUID target, PunishmentType type, String reason, UUID staff, Long expiresAt);

    CompletableFuture<Optional<PunishmentEntry>> activeBan(UUID target);

    CompletableFuture<Optional<PunishmentEntry>> activeMute(UUID target);

    CompletableFuture<List<PunishmentEntry>> history(UUID target, int limit);

    /** Deactivates every currently-active entry of the given type for this target (unban/unmute). Returns how many were revoked. */
    CompletableFuture<Integer> revoke(UUID target, PunishmentType type, UUID staff);

    /**
     * Records one 경고 지급/회수 event as its own permanent {@link PunishmentType#WARN}
     * row — {@code points} is signed (positive for a grant, negative for a
     * revoke) rather than mutating a single counter, so the full history of
     * every change survives. {@code expiresAt} (null = never expires) lets
     * this specific grant's points stop counting toward the total after a
     * staff-chosen date. Returns the player's new cumulative warning total
     * (sum of every not-yet-expired WARN row's points) after this event.
     */
    CompletableFuture<Integer> recordWarning(UUID target, String reason, UUID staff, int points, Long expiresAt);

    /** Current cumulative warning total (sum of every WARN row's points, grants minus revokes). */
    CompletableFuture<Integer> totalWarningPoints(UUID target);

    /**
     * Every currently active entry of the given type, across every target,
     * whose reason starts with {@code reasonPrefix}. Meant for a periodic
     * sweep that needs to find every instance of some specific
     * automatically-created punishment (e.g. YeowoolAdmin's warning-triggered
     * auto-ban) rather than one target's history.
     */
    CompletableFuture<List<PunishmentEntry>> findActiveByReasonPrefix(PunishmentType type, String reasonPrefix);

    /**
     * {@link #totalWarningPoints(UUID)} for several targets in one round
     * trip, so a periodic sweep over many players doesn't issue one query
     * per player. A target with no WARN rows at all is simply absent from
     * the result map (equivalent to a total of 0).
     */
    CompletableFuture<Map<UUID, Integer>> totalWarningPointsForTargets(Collection<UUID> targets);
}
