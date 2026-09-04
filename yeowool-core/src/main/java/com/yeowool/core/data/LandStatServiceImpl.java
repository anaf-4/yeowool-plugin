package com.yeowool.core.data;

import com.yeowool.core.api.event.PlayerLandXpChangeEvent;
import com.yeowool.core.api.model.PlayerData;
import com.yeowool.core.api.service.LandStatService;
import com.yeowool.core.api.service.PlayerDataService;
import org.bukkit.Bukkit;

import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

public final class LandStatServiceImpl implements LandStatService {

    private final PlayerDataService playerDataService;
    private volatile double xpMultiplier = 1.0;

    public LandStatServiceImpl(PlayerDataService playerDataService) {
        this.playerDataService = playerDataService;
    }

    private PlayerData require(UUID uuid) {
        return playerDataService.getIfLoaded(uuid).orElseThrow(() ->
                new NoSuchElementException("PlayerData for " + uuid + " is not loaded; call playerData().load() first"));
    }

    @Override
    public int getLandLevel(UUID uuid) {
        return require(uuid).getLandLevel();
    }

    @Override
    public void setLandLevel(UUID uuid, int level) {
        require(uuid).setLandLevel(level);
    }

    @Override
    public long getLandXp(UUID uuid) {
        return require(uuid).getLandXp();
    }

    @Override
    public long addLandXp(UUID uuid, long amount) {
        long adjusted = Math.round(amount * xpMultiplier);
        long newTotal = require(uuid).addLandXp(adjusted);
        Bukkit.getPluginManager().callEvent(new PlayerLandXpChangeEvent(uuid, adjusted, newTotal));
        return newTotal;
    }

    @Override
    public void setXpMultiplier(double multiplier) {
        this.xpMultiplier = multiplier;
    }

    @Override
    public double getXpMultiplier() {
        return xpMultiplier;
    }

    @Override
    public Set<UUID> getLandIds(UUID uuid) {
        return require(uuid).getLandIds();
    }

    @Override
    public void addLandId(UUID uuid, UUID landId) {
        require(uuid).addLandId(landId);
    }

    @Override
    public void removeLandId(UUID uuid, UUID landId) {
        require(uuid).removeLandId(landId);
    }
}
