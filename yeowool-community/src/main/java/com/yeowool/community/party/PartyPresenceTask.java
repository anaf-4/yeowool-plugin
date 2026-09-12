package com.yeowool.community.party;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ticks once per second: writes this server's online party members' live
 * health to {@code yw_party_presence} (so other servers' HUD can see them),
 * then rebuilds {@link PartyManager}'s HUD cache from a combination of local
 * (live, this tick) and remote (DB, up to ~1s old) member health — see
 * {@link PartyManager}'s class doc for why this is a short-lived cache
 * instead of the always-fresh-from-DB approach the actual party commands
 * use. A member neither online here nor with a fresh presence row (offline,
 * or just disconnected) reports 0/0 health, matching the Volya HUD layout's
 * own "health resolves to 0 → gray out" condition.
 */
public final class PartyPresenceTask extends BukkitRunnable {

    private static final long STALE_PRESENCE_MILLIS = 5_000L;

    private record LocalSnapshot(String name, double health, double maxHealth) {
    }

    private final PartyManager partyManager;
    private final String serverName;

    public PartyPresenceTask(PartyManager partyManager, String serverName) {
        this.partyManager = partyManager;
        this.serverName = serverName;
    }

    @Override
    public void run() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            partyManager.setHudCache(Map.of());
            return;
        }
        Map<UUID, LocalSnapshot> local = new HashMap<>();
        List<UUID> onlineUuids = new ArrayList<>();
        for (Player player : online) {
            double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            local.put(player.getUniqueId(), new LocalSnapshot(player.getName(), player.getHealth(), maxHealth));
            onlineUuids.add(player.getUniqueId());
        }
        partyManager.executor().execute(() -> refreshAsync(local, onlineUuids));
    }

    private void refreshAsync(Map<UUID, LocalSnapshot> local, List<UUID> onlineUuids) {
        PartyRepository repository = partyManager.repository();
        try {
            Map<UUID, Long> memberParty = repository.findPartyIdsOf(onlineUuids);
            if (memberParty.isEmpty()) {
                partyManager.setHudCache(Map.of());
                return;
            }
            for (var entry : memberParty.entrySet()) {
                LocalSnapshot snapshot = local.get(entry.getKey());
                if (snapshot != null) {
                    repository.upsertPresence(entry.getKey(), serverName, snapshot.name(), snapshot.health(), snapshot.maxHealth());
                }
            }

            Set<Long> partyIds = new HashSet<>(memberParty.values());
            Map<Long, PartyRepository.PartyRow> parties = repository.findParties(partyIds);
            Map<Long, List<PartyRepository.MemberRow>> membersByParty = repository.findMembersOf(partyIds);

            Set<UUID> allMemberUuids = membersByParty.values().stream()
                    .flatMap(List::stream).map(PartyRepository.MemberRow::uuid).collect(Collectors.toSet());
            Set<UUID> remoteUuids = allMemberUuids.stream().filter(uuid -> !local.containsKey(uuid)).collect(Collectors.toSet());
            Map<UUID, PartyRepository.PresenceRow> remotePresence = repository.findPresence(remoteUuids);

            Map<UUID, List<PartyManager.PartySlot>> snapshot = new HashMap<>();
            for (var entry : memberParty.entrySet()) {
                UUID viewer = entry.getKey();
                PartyRepository.PartyRow party = parties.get(entry.getValue());
                List<PartyRepository.MemberRow> members = membersByParty.getOrDefault(entry.getValue(), List.of());
                if (party == null || members.isEmpty()) {
                    continue;
                }
                snapshot.put(viewer, buildSlots(party, members, local, remotePresence));
            }
            partyManager.setHudCache(Map.copyOf(snapshot));
        } catch (SQLException e) {
            partyManager.plugin().getLogger().warning("파티 HUD 갱신 실패: " + e.getMessage());
        }
    }

    private List<PartyManager.PartySlot> buildSlots(PartyRepository.PartyRow party, List<PartyRepository.MemberRow> members,
                                                      Map<UUID, LocalSnapshot> local, Map<UUID, PartyRepository.PresenceRow> remotePresence) {
        List<PartyRepository.MemberRow> ordered = new ArrayList<>(members);
        ordered.sort(Comparator.comparing(member -> !member.uuid().equals(party.leader())));

        List<PartyManager.PartySlot> slots = new ArrayList<>();
        for (PartyRepository.MemberRow member : ordered) {
            if (slots.size() >= PartyManager.HUD_SLOT_LIMIT) {
                break;
            }
            boolean isLeader = member.uuid().equals(party.leader());
            LocalSnapshot localSnapshot = local.get(member.uuid());
            if (localSnapshot != null) {
                slots.add(new PartyManager.PartySlot(localSnapshot.name(), localSnapshot.health(), localSnapshot.maxHealth(), isLeader));
                continue;
            }
            PartyRepository.PresenceRow presence = remotePresence.get(member.uuid());
            if (presence != null && System.currentTimeMillis() - presence.updatedAt() < STALE_PRESENCE_MILLIS) {
                slots.add(new PartyManager.PartySlot(presence.name(), presence.health(), presence.maxHealth(), isLeader));
            } else {
                slots.add(new PartyManager.PartySlot(member.name(), 0, 0, isLeader));
            }
        }
        return slots;
    }
}
