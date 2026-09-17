package com.yeowool.raid;

import com.yeowool.raid.item.RaidTicketUtil;
import com.yeowool.raid.party.RaidPartyLookup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public final class RaidEntryService {

    private final JavaPlugin plugin;
    private final RaidManager raidManager;
    private final RaidPartyLookup partyLookup;
    private final RaidHudService hudService;
    private final ExecutorService executor;

    public RaidEntryService(JavaPlugin plugin, RaidManager raidManager, RaidPartyLookup partyLookup,
                             RaidHudService hudService, ExecutorService executor) {
        this.plugin = plugin;
        this.raidManager = raidManager;
        this.partyLookup = partyLookup;
        this.hudService = hudService;
        this.executor = executor;
    }

    public void attemptEntry(Player leader, RaidDefinition raid) {
        executor.execute(() -> {
            Optional<RaidPartyLookup.PartyMembers> party;
            try {
                party = partyLookup.findPartyMembers(leader.getUniqueId());
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("파티 조회 실패 (" + leader.getName() + "): " + e.getMessage());
                return;
            }
            if (party.isEmpty() || !party.get().leader().equals(leader.getUniqueId())) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        leader.sendMessage(Component.text("파티장만 레이드에 입장할 수 있습니다.", NamedTextColor.RED)));
                return;
            }
            boolean hasEnoughTickets = RaidTicketUtil.hasEnough(leader, raid.ticketItemId(), raid.ticketAmount());
            List<RaidInstanceSlot> slots = raidManager.instanceSlotsFor(raid.id());

            Bukkit.getScheduler().runTask(plugin, () -> completeEntry(leader, raid, party.get(), hasEnoughTickets, slots));
        });
    }

    /**
     * Runs on the main thread: finds a free instance slot first (without allocating it), spawns the
     * boss and reads its real UUID from the returned {@code ActiveMob}, and only then calls
     * {@code raidManager.tryEnter(...)} with that real UUID — so a session is never created keyed to
     * a UUID nothing was actually spawned with.
     */
    private void completeEntry(Player leader, RaidDefinition raid, RaidPartyLookup.PartyMembers party,
                                boolean hasEnoughTickets, List<RaidInstanceSlot> slots) {
        var mythicMob = io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager().getMythicMob(raid.mythicMobId());
        if (mythicMob.isEmpty()) {
            leader.sendMessage(Component.text("이 레이드의 몹 설정이 잘못되었습니다. 관리자에게 문의하세요.", NamedTextColor.RED));
            return;
        }
        var denial = raidManager.peekEntryDenial(raid, party, hasEnoughTickets);
        if (denial.isPresent()) {
            leader.sendMessage(Component.text(denialMessage(denial.get()), NamedTextColor.RED));
            return;
        }
        int slotIndex = raidManager.reserveSlot(raid.id());
        var slot = slots.stream().filter(s -> s.slotIndex() == slotIndex).findFirst();
        if (slot.isEmpty()) {
            plugin.getLogger().severe("레이드 " + raid.name() + " 슬롯 " + slotIndex + " 좌표가 설정되지 않았습니다.");
            raidManager.releaseReservedSlot(raid.id(), slotIndex);
            return;
        }

        RaidTicketUtil.remove(leader, raid.ticketItemId(), raid.ticketAmount());
        for (UUID memberId : party.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.teleport(slot.get().entry());
            }
        }

        var activeMob = mythicMob.get().spawn(
                io.lumine.mythic.bukkit.BukkitAdapter.adapt(slot.get().bossSpawn()), 1.0);
        UUID bossEntityId = activeMob.getUniqueId();

        raidManager.startSession(raid.id(), slotIndex, party.partyId(), party.members(), bossEntityId, raid.sharedLives());
    }

    private String denialMessage(RaidEntryDenialReason reason) {
        return switch (reason) {
            case PARTY_TOO_SMALL -> "레이드 최소 인원을 채우지 못했습니다.";
            case PARTY_TOO_LARGE -> "레이드 최대 인원을 초과했습니다.";
            case PARTY_ALREADY_IN_RAID -> "이미 레이드를 진행 중인 파티입니다.";
            case NOT_ENOUGH_TICKETS -> "입장권이 부족합니다.";
            case NO_FREE_INSTANCE -> "모든 인스턴스가 사용 중입니다. 잠시 후 다시 시도해주세요.";
        };
    }
}
