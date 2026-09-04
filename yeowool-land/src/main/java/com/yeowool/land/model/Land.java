package com.yeowool.land.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A player's claimed territory: one or more chunks plus a member roster.
 * Each member carries its own {@link LandPermission} flags — "건축만
 * 가능", "상자만 가능" — rather than every member always getting full
 * access; the owner implicitly has every permission regardless of what's
 * stored for them. Level/XP are intentionally not stored here — they live
 * on the owner's {@code PlayerData} in YeowoolCore (see the plugin plan's
 * Player struct), this class only tracks what YeowoolLand itself owns.
 */
public final class Land {

    private final UUID id;
    private volatile UUID owner;
    private volatile String name;
    private final Set<ChunkKey> chunks = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<LandPermission>> memberPermissions = new ConcurrentHashMap<>();
    private volatile boolean pvpEnabled = false;
    private final AtomicLong bankBalance = new AtomicLong(0L);

    public Land(UUID id, UUID owner) {
        this.id = id;
        this.owner = owner;
    }

    public UUID getId() {
        return id;
    }

    /** Null until an owner sets one via {@code /토지 이름} — callers should fall back to a placeholder. */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getOwner() {
        return owner;
    }

    /** Admin-only override (yeowool.land.bypass force-transfer) — normal play never changes a land's owner. */
    public void setOwner(UUID newOwner) {
        this.owner = newOwner;
    }

    public Set<ChunkKey> getChunks() {
        return Set.copyOf(chunks);
    }

    public void addChunk(ChunkKey key) {
        chunks.add(key);
    }

    public int getChunkCount() {
        return chunks.size();
    }

    public Set<UUID> getMembers() {
        return Set.copyOf(memberPermissions.keySet());
    }

    public void addMember(UUID uuid, Set<LandPermission> permissions) {
        memberPermissions.put(uuid, EnumSet.copyOf(permissions.isEmpty() ? EnumSet.noneOf(LandPermission.class) : permissions));
    }

    public void setPermissions(UUID uuid, Set<LandPermission> permissions) {
        memberPermissions.put(uuid, permissions.isEmpty() ? EnumSet.noneOf(LandPermission.class) : EnumSet.copyOf(permissions));
    }

    public Set<LandPermission> getPermissions(UUID uuid) {
        if (owner.equals(uuid)) {
            return EnumSet.allOf(LandPermission.class);
        }
        return memberPermissions.getOrDefault(uuid, Set.of());
    }

    public void removeMember(UUID uuid) {
        memberPermissions.remove(uuid);
    }

    /** True if the owner, or on the member roster at all (regardless of which flags they have). */
    public boolean isMember(UUID uuid) {
        return owner.equals(uuid) || memberPermissions.containsKey(uuid);
    }

    public boolean hasPermission(UUID uuid, LandPermission permission) {
        if (owner.equals(uuid)) {
            return true;
        }
        Set<LandPermission> permissions = memberPermissions.get(uuid);
        return permissions != null && permissions.contains(permission);
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
    }

    public long getBankBalance() {
        return bankBalance.get();
    }

    public void setBankBalance(long value) {
        bankBalance.set(value);
    }

    /** Returns the new balance, or -1 without changing anything if it would go negative. */
    public long addBankBalance(long delta) {
        while (true) {
            long current = bankBalance.get();
            long updated = current + delta;
            if (updated < 0) {
                return -1;
            }
            if (bankBalance.compareAndSet(current, updated)) {
                return updated;
            }
        }
    }
}
