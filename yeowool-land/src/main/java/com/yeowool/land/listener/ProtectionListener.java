package com.yeowool.land.listener;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.land.LandManager;
import com.yeowool.land.model.ChunkKey;
import com.yeowool.land.model.Land;
import com.yeowool.land.model.LandPermission;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

/**
 * Section 4.2 of the plugin plan (YeowoolLandProtection), folded into
 * YeowoolLand as one module: block break/place, container/door interaction,
 * explosions, fire spread, and animal damage are all denied inside a claimed
 * land for anyone who isn't the owner or a member — except staff holding
 * {@code yeowool.land.bypass}, who can moderate any land regardless.
 */
public final class ProtectionListener implements Listener {

    private static final Set<org.bukkit.Material> PROTECTED_INTERACTABLES = Set.of(
            org.bukkit.Material.CHEST, org.bukkit.Material.TRAPPED_CHEST, org.bukkit.Material.BARREL,
            org.bukkit.Material.FURNACE, org.bukkit.Material.BLAST_FURNACE, org.bukkit.Material.SMOKER,
            org.bukkit.Material.SHULKER_BOX, org.bukkit.Material.DISPENSER, org.bukkit.Material.DROPPER,
            org.bukkit.Material.HOPPER, org.bukkit.Material.OAK_DOOR, org.bukkit.Material.IRON_DOOR,
            org.bukkit.Material.OAK_TRAPDOOR, org.bukkit.Material.OAK_FENCE_GATE,
            org.bukkit.Material.LEVER, org.bukkit.Material.ANVIL
    );

    private final LandManager landManager;
    private final MessageService messages;

    public ProtectionListener(LandManager landManager, MessageService messages) {
        this.landManager = landManager;
        this.messages = messages;
    }

    /** Section 11.1: staff with this node can moderate any land regardless of membership (grief cleanup, investigations). */
    private boolean hasBypass(Player player) {
        return player.hasPermission("yeowool.land.bypass");
    }

    private boolean isOutsider(Location location, Player player) {
        if (hasBypass(player)) {
            return false;
        }
        Optional<Land> land = landManager.getLandAt(ChunkKey.of(location.getWorld(), location.getBlockX(), location.getBlockZ()));
        return land.isPresent() && !land.get().isMember(player.getUniqueId());
    }

    /** True if this location is claimed and the player lacks the given permission there (owner and bypass staff always pass). */
    private boolean lacksPermission(Location location, Player player, LandPermission permission) {
        if (hasBypass(player)) {
            return false;
        }
        Optional<Land> land = landManager.getLandAt(ChunkKey.of(location.getWorld(), location.getBlockX(), location.getBlockZ()));
        return land.isPresent() && !land.get().hasPermission(player.getUniqueId(), permission);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (landManager.isClaimBarrel(event.getBlock()) && !event.getPlayer().isOp() && !hasBypass(event.getPlayer())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "land.barrel-unbreakable");
            return;
        }
        if (lacksPermission(event.getBlock().getLocation(), event.getPlayer(), LandPermission.BUILD)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "land.protected");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (lacksPermission(event.getBlock().getLocation(), event.getPlayer(), LandPermission.BUILD)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "land.protected");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!PROTECTED_INTERACTABLES.contains(block.getType())) {
            return;
        }
        if (lacksPermission(block.getLocation(), event.getPlayer(), LandPermission.CONTAINERS)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "land.protected");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (landManager.isClaimed(ChunkKey.of(block.getChunk()))) {
                iterator.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD
                && landManager.isClaimed(ChunkKey.of(event.getBlock().getChunk()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAnimalDamage(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null) {
            return;
        }

        if (event.getEntity() instanceof Player victim) {
            Optional<Land> land = landManager.getLandAt(ChunkKey.of(victim.getLocation().getWorld(),
                    victim.getLocation().getBlockX(), victim.getLocation().getBlockZ()));
            if (land.isPresent() && !land.get().isPvpEnabled()) {
                event.setCancelled(true);
                messages.send(attacker, "land.protected");
            }
            return;
        }

        if (!(event.getEntity() instanceof Animals animal)) {
            return;
        }
        if (isOutsider(animal.getLocation(), attacker)) {
            event.setCancelled(true);
            messages.send(attacker, "land.protected");
        }
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
