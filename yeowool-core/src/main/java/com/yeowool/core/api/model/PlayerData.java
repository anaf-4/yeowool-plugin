package com.yeowool.core.api.model;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory representation of a single player's core data row.
 * One instance is cached per online (or recently online) player; all mutation
 * goes through the atomic/concurrent fields below so join/quit and async
 * database I/O never race each other.
 *
 * <p><b>Other plugins:</b> prefer {@code core.economyData()} /
 * {@code core.landStats()} over calling {@link #addOnBalance}/{@link #addLandXp}
 * here directly — those services additionally guarantee every economy change
 * is logged and that a land-XP change fires {@code PlayerLandXpChangeEvent}
 * for level-up checks. Settings/statistics have no such side effect and are
 * fine to mutate directly via {@link #setSetting}/{@link #addStatistic}.
 */
public final class PlayerData {

    private final UUID uuid;
    private volatile String username;

    private final AtomicLong onBalance = new AtomicLong(0L);
    private final AtomicLong bankBalance = new AtomicLong(0L);
    private final AtomicLong cashBalance = new AtomicLong(0L);

    private volatile int landLevel = 1;
    private final AtomicLong landXp = new AtomicLong(0L);
    private final Set<UUID> landIds = ConcurrentHashMap.newKeySet();

    private final ConcurrentHashMap<String, String> settings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> statistics = new ConcurrentHashMap<>();

    private final long firstJoin;
    private volatile long lastSeen;

    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public PlayerData(UUID uuid, String username, long firstJoin, long lastSeen) {
        this.uuid = uuid;
        this.username = username;
        this.firstJoin = firstJoin;
        this.lastSeen = lastSeen;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
        markDirty();
    }

    public long getOnBalance() {
        return onBalance.get();
    }

    public void setOnBalance(long value) {
        onBalance.set(Math.max(0L, value));
        markDirty();
    }

    public long addOnBalance(long delta) {
        long result = onBalance.updateAndGet(current -> Math.max(0L, current + delta));
        markDirty();
        return result;
    }

    public long getBankBalance() {
        return bankBalance.get();
    }

    public void setBankBalance(long value) {
        bankBalance.set(Math.max(0L, value));
        markDirty();
    }

    public long addBankBalance(long delta) {
        long result = bankBalance.updateAndGet(current -> Math.max(0L, current + delta));
        markDirty();
        return result;
    }

    public long getCashBalance() {
        return cashBalance.get();
    }

    public void setCashBalance(long value) {
        cashBalance.set(Math.max(0L, value));
        markDirty();
    }

    public long addCashBalance(long delta) {
        long result = cashBalance.updateAndGet(current -> Math.max(0L, current + delta));
        markDirty();
        return result;
    }

    public int getLandLevel() {
        return landLevel;
    }

    public void setLandLevel(int landLevel) {
        this.landLevel = landLevel;
        markDirty();
    }

    public long getLandXp() {
        return landXp.get();
    }

    public long addLandXp(long delta) {
        long result = landXp.addAndGet(delta);
        markDirty();
        return result;
    }

    public Set<UUID> getLandIds() {
        return Set.copyOf(landIds);
    }

    public void addLandId(UUID landId) {
        if (landIds.add(landId)) {
            markDirty();
        }
    }

    public void removeLandId(UUID landId) {
        if (landIds.remove(landId)) {
            markDirty();
        }
    }

    public void restoreLandIds(Set<UUID> ids) {
        landIds.addAll(ids);
    }

    public String getSetting(String key, String defaultValue) {
        return settings.getOrDefault(key, defaultValue);
    }

    public void setSetting(String key, String value) {
        settings.put(key, value);
        markDirty();
    }

    public java.util.Map<String, String> exportSettings() {
        return java.util.Map.copyOf(settings);
    }

    public long getStatistic(String key) {
        return statistics.getOrDefault(key, 0L);
    }

    public java.util.Map<String, Long> exportStatistics() {
        return java.util.Map.copyOf(statistics);
    }

    public long addStatistic(String key, long delta) {
        long result = statistics.merge(key, delta, Long::sum);
        markDirty();
        return result;
    }

    /**
     * "High score" variant of {@link #addStatistic} — keeps {@code value} only
     * if it's higher than whatever's already stored (e.g. a personal-best fish
     * size in millimeters), returning whether it actually became the new
     * record. Uses the same {@code statistics} map/persistence, so no schema
     * or save-path changes are needed for this kind of record-keeping.
     */
    public boolean recordMaxStatistic(String key, long value) {
        boolean[] changed = {false};
        statistics.merge(key, value, (oldValue, newValue) -> {
            if (newValue > oldValue) {
                changed[0] = true;
                return newValue;
            }
            return oldValue;
        });
        if (changed[0]) {
            markDirty();
        }
        return changed[0];
    }

    public void restoreSettings(java.util.Map<String, String> source) {
        settings.putAll(source);
    }

    public void restoreStatistics(java.util.Map<String, Long> source) {
        statistics.putAll(source);
    }

    public long getFirstJoin() {
        return firstJoin;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
        markDirty();
    }

    private void markDirty() {
        dirty.set(true);
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public void clearDirty() {
        dirty.set(false);
    }
}
