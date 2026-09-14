package com.yeowool.life.pets;

import com.yeowool.core.api.YeowoolCoreAPI;
import fr.nocsy.mcpets.events.PetLevelUpEvent;
import fr.nocsy.mcpets.events.PetTamedByPlayerEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Grants land XP for taming an MCPets pet and for each level it gains, the same way every other life-skill listener does. */
public final class MCPetsXpListener implements Listener {

    private final YeowoolCoreAPI core;
    private final long xpPerTame;
    private final long xpPerLevelUp;

    public MCPetsXpListener(YeowoolCoreAPI core, long xpPerTame, long xpPerLevelUp) {
        this.core = core;
        this.xpPerTame = xpPerTame;
        this.xpPerLevelUp = xpPerLevelUp;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTamed(PetTamedByPlayerEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        core.landStats().addLandXp(uuid, xpPerTame);
        core.playerData().getIfLoaded(uuid).ifPresent(data -> data.addStatistic("life.pets.tamed", 1));
    }

    @EventHandler
    public void onLevelUp(PetLevelUpEvent event) {
        var owner = event.getPet().getOwner();
        if (owner == null) {
            return;
        }
        core.landStats().addLandXp(owner, xpPerLevelUp);
    }
}
