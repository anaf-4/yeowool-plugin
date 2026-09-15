package com.yeowool.community.ranking.statue;

import com.yeowool.community.ranking.RankingCategory;
import com.yeowool.community.ranking.RankingEntry;
import com.yeowool.community.ranking.RankingManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Places a life-size player-skin Citizens NPC ("동상") at each admin-registered
 * top-3 slot per {@link RankingCategory}, swapping its skin + nameplate to
 * match whoever's actually in that position whenever {@link RankingManager}
 * refreshes ({@link #onRankingRefreshed}). Only registered/used when Citizens
 * is installed — see {@code YeowoolCommunity}.
 */
public final class RankingStatueManager {

    private static final int TOP_N = 3;

    private final JavaPlugin plugin;
    private final RankingStatueRepository repository;
    private final ExecutorService executor;

    private final Map<String, RankingStatueRepository.StatueLocation> locations = new ConcurrentHashMap<>();
    private final Map<String, String> lastShownUsername = new ConcurrentHashMap<>();

    public RankingStatueManager(JavaPlugin plugin, RankingStatueRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    public void loadAndSpawn() throws SQLException {
        locations.putAll(repository.loadAll());
        for (var stored : locations.values()) {
            respawnIfNeeded(stored);
        }
    }

    /** {@code /명예의전당 동상설정 <카테고리> <1|2|3>} — registers or moves that slot to {@code location}. */
    public void setLocation(RankingCategory category, int position, Location location) {
        String key = key(category, position);
        NPC npc = npcFor(locations.get(key));
        if (npc == null) {
            npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "명예의전당");
        }
        if (npc.isSpawned()) {
            npc.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } else {
            npc.spawn(location);
        }

        var stored = new RankingStatueRepository.StatueLocation(location.getWorld().getName(), location.getX(),
                location.getY(), location.getZ(), location.getYaw(), location.getPitch(), npc.getId());
        locations.put(key, stored);
        lastShownUsername.remove(key); // force a skin/name refresh next time rankings tick
        executor.execute(() -> {
            try {
                repository.upsert(category.name(), position, stored);
            } catch (SQLException e) {
                plugin.getLogger().severe("명예의 전당 동상 위치 저장 실패 (" + key + "): " + e.getMessage());
            }
        });
    }

    public boolean hasLocation(RankingCategory category, int position) {
        return locations.containsKey(key(category, position));
    }

    /** Called (main thread) by {@link RankingManager#onRefreshed} — only touches NPCs for the category that just updated. */
    public void onRankingRefreshed(RankingCategory category, List<RankingEntry> top) {
        for (int position = 1; position <= TOP_N; position++) {
            String key = key(category, position);
            NPC npc = npcFor(locations.get(key));
            if (npc == null) {
                continue;
            }
            RankingEntry entry = top.size() >= position ? top.get(position - 1) : null;
            String username = entry != null ? entry.username() : null;
            if (Objects.equals(username, lastShownUsername.get(key))) {
                continue; // same player still holding this rank - nothing to update
            }
            lastShownUsername.put(key, username);
            applySkinAndName(npc, position, username);
        }
    }

    private void applySkinAndName(NPC npc, int position, String username) {
        if (username == null) {
            npc.setName("§7" + position + "위 §8(없음)");
            return;
        }
        npc.getOrAddTrait(SkinTrait.class).setSkinName(username);
        npc.setName("§6" + position + "위 §f" + username);
    }

    private NPC npcFor(RankingStatueRepository.StatueLocation stored) {
        if (stored == null || stored.npcId() == null) {
            return null;
        }
        return CitizensAPI.getNPCRegistry().getById(stored.npcId());
    }

    private void respawnIfNeeded(RankingStatueRepository.StatueLocation stored) {
        NPC npc = npcFor(stored);
        if (npc == null || npc.isSpawned()) {
            return;
        }
        var world = Bukkit.getWorld(stored.world());
        if (world == null) {
            return;
        }
        npc.spawn(new Location(world, stored.x(), stored.y(), stored.z(), stored.yaw(), stored.pitch()));
    }

    private static String key(RankingCategory category, int position) {
        return category.name() + ":" + position;
    }
}
