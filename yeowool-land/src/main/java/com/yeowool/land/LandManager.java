package com.yeowool.land;

import com.yeowool.land.model.ChunkKey;
import com.yeowool.land.model.Land;
import com.yeowool.land.model.LandPermission;
import com.yeowool.land.repository.LandRepository;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * In-memory index of every {@link Land}, backed by {@link LandRepository}.
 * All lookups (chunk protection checks run on every block event) are O(1)
 * in-memory; every mutation updates memory first, then persists
 * asynchronously so gameplay never blocks on a DB round trip.
 */
public final class LandManager {

    private final JavaPlugin plugin;
    private final LandRepository repository;
    private final ExecutorService executor;

    private final Map<UUID, Land> landsById = new ConcurrentHashMap<>();
    private final Map<ChunkKey, UUID> chunkIndex = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> ownerIndex = new ConcurrentHashMap<>();
    private final Set<String> claimBarrels = ConcurrentHashMap.newKeySet();

    public LandManager(JavaPlugin plugin, LandRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    /**
     * Blocking initial load, meant to be called once during {@code onEnable}
     * before any protection listener can run.
     */
    public void loadAll() throws SQLException {
        for (Land land : repository.loadAll()) {
            landsById.put(land.getId(), land);
            ownerIndex.put(land.getOwner(), land.getId());
            for (ChunkKey chunk : land.getChunks()) {
                chunkIndex.put(chunk, land.getId());
            }
        }
        for (var barrel : repository.loadAllBarrels()) {
            claimBarrels.add(barrelKey(barrel.world(), barrel.x(), barrel.y(), barrel.z()));
        }
        plugin.getLogger().info("토지 " + landsById.size() + "개, 등록 배럴 " + claimBarrels.size() + "개를 불러왔습니다.");
    }

    private static String barrelKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    /** Marks a placed barrel as the block that claimed/expanded a land, making it unbreakable for non-OPs. */
    public void markClaimBarrel(Block block) {
        String key = barrelKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        claimBarrels.add(key);
        executor.execute(() -> {
            try {
                repository.insertBarrel(new LandRepository.BarrelLocation(
                        block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
            } catch (SQLException e) {
                plugin.getLogger().severe("배럴 등록 저장 실패: " + e.getMessage());
            }
        });
    }

    public boolean isClaimBarrel(Block block) {
        return claimBarrels.contains(barrelKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
    }

    public Optional<Land> getLandAt(ChunkKey key) {
        UUID landId = chunkIndex.get(key);
        return landId == null ? Optional.empty() : Optional.ofNullable(landsById.get(landId));
    }

    public Optional<Land> getLandOwnedBy(UUID owner) {
        UUID landId = ownerIndex.get(owner);
        return landId == null ? Optional.empty() : Optional.ofNullable(landsById.get(landId));
    }

    /** Every land on the server, for admin tooling (yeowool.land.bypass) — never used by protection checks. */
    public Collection<Land> all() {
        return landsById.values();
    }

    /** Finds a land by the first 8 characters of its id, matching {@code /경매}/{@code /거래소}'s short-id convention. */
    public Optional<Land> findByShortId(String shortId) {
        return landsById.values().stream()
                .filter(land -> land.getId().toString().startsWith(shortId.toLowerCase()))
                .findFirst();
    }

    public enum TransferResult { SUCCESS, TARGET_ALREADY_OWNS_LAND }

    /**
     * Admin-only override (yeowool.land.bypass): force-transfers a land to a
     * new owner. Rejected if the target already owns a different land, since
     * {@link #ownerIndex} assumes one land per owner — callers should tell
     * the target to disband or transfer their existing land first.
     */
    public TransferResult transferOwnership(Land land, UUID newOwner) {
        if (getLandOwnedBy(newOwner).isPresent()) {
            return TransferResult.TARGET_ALREADY_OWNS_LAND;
        }
        UUID oldOwner = land.getOwner();
        boolean wasMember = land.isMember(newOwner);
        land.removeMember(newOwner);
        ownerIndex.remove(oldOwner);
        land.setOwner(newOwner);
        ownerIndex.put(newOwner, land.getId());

        executor.execute(() -> {
            try {
                repository.updateOwner(land.getId(), newOwner);
                // The new owner may have been a regular member row before this transfer - if we
                // don't delete it too, loadAll() on the next restart re-adds them as a "member"
                // (Land.getMembers() would then list the owner a second time with stale
                // permission tags, even though hasPermission's owner short-circuit means no
                // actual privilege bug results).
                if (wasMember) {
                    repository.deleteMember(land.getId(), newOwner);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("토지 소유권 이전 저장 실패 (" + land.getId() + "): " + e.getMessage());
            }
        });
        return TransferResult.SUCCESS;
    }

    public boolean isClaimed(ChunkKey key) {
        return chunkIndex.containsKey(key);
    }

    public Land createLand(UUID owner, ChunkKey firstChunk) {
        Land land = new Land(UUID.randomUUID(), owner);
        land.addChunk(firstChunk);

        landsById.put(land.getId(), land);
        ownerIndex.put(owner, land.getId());
        chunkIndex.put(firstChunk, land.getId());

        executor.execute(() -> {
            try {
                repository.insertLand(land);
                repository.insertChunk(land.getId(), firstChunk);
            } catch (SQLException e) {
                plugin.getLogger().severe("토지 생성 저장 실패 (" + owner + "): " + e.getMessage());
            }
        });
        return land;
    }

    public void expandLand(Land land, ChunkKey chunk) {
        land.addChunk(chunk);
        chunkIndex.put(chunk, land.getId());
        executor.execute(() -> {
            try {
                repository.insertChunk(land.getId(), chunk);
            } catch (SQLException e) {
                plugin.getLogger().severe("토지 확장 저장 실패 (" + land.getId() + "): " + e.getMessage());
            }
        });
    }

    public void addMember(Land land, UUID member, Set<LandPermission> permissions) {
        land.addMember(member, permissions);
        executor.execute(() -> {
            try {
                repository.insertMember(land.getId(), member, permissions);
            } catch (SQLException e) {
                plugin.getLogger().severe("토지 멤버 추가 저장 실패 (" + land.getId() + "): " + e.getMessage());
            }
        });
    }

    /** Updates an existing (or adds a new) member's permission flags. */
    public void setMemberPermissions(Land land, UUID member, Set<LandPermission> permissions) {
        land.setPermissions(member, permissions);
        executor.execute(() -> {
            try {
                repository.insertMember(land.getId(), member, permissions);
            } catch (SQLException e) {
                plugin.getLogger().severe("토지 멤버 권한 저장 실패 (" + land.getId() + "): " + e.getMessage());
            }
        });
    }

    public void removeMember(Land land, UUID member) {
        land.removeMember(member);
        executor.execute(() -> {
            try {
                repository.deleteMember(land.getId(), member);
            } catch (SQLException e) {
                plugin.getLogger().severe("토지 멤버 제거 저장 실패 (" + land.getId() + "): " + e.getMessage());
            }
        });
    }

    /** {@code name} may be null to clear a previously-set name back to the "이름없는 마을" fallback. */
    public void setName(Land land, String name) {
        land.setName(name);
        executor.execute(() -> {
            try {
                repository.updateName(land.getId(), name);
            } catch (SQLException e) {
                plugin.getLogger().severe("토지 이름 저장 실패 (" + land.getId() + "): " + e.getMessage());
            }
        });
    }

    public void setPvp(Land land, boolean enabled) {
        land.setPvpEnabled(enabled);
        executor.execute(() -> {
            try {
                repository.updatePvp(land.getId(), enabled);
            } catch (SQLException e) {
                plugin.getLogger().severe("토지 PVP 설정 저장 실패 (" + land.getId() + "): " + e.getMessage());
            }
        });
    }

    /**
     * Deposits (positive) or withdraws (negative) from the land's own bank.
     * Returns the new balance, or -1 without changing anything if a
     * withdrawal would go negative.
     */
    public long modifyBankBalance(Land land, long delta) {
        long result = land.addBankBalance(delta);
        if (result < 0) {
            return -1;
        }
        executor.execute(() -> {
            try {
                repository.updateBankBalance(land.getId(), result);
            } catch (SQLException e) {
                plugin.getLogger().severe("토지 은행 저장 실패 (" + land.getId() + "): " + e.getMessage());
            }
        });
        return result;
    }

    /** Fully disbands a land: releases every chunk and removes it from the index. */
    public void disbandLand(Land land) {
        landsById.remove(land.getId());
        ownerIndex.remove(land.getOwner());
        for (ChunkKey chunk : land.getChunks()) {
            chunkIndex.remove(chunk);
        }
        executor.execute(() -> {
            try {
                repository.deleteLand(land.getId());
            } catch (SQLException e) {
                plugin.getLogger().severe("토지 삭제 저장 실패 (" + land.getId() + "): " + e.getMessage());
            }
        });
    }
}
