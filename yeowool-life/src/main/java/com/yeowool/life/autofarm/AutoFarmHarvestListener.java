package com.yeowool.life.autofarm;

import com.yeowool.core.api.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;

/**
 * Applies 자동줍기/자동심기 to the same fully-grown ageable crops
 * {@code FarmingListener} awards harvest XP for (밀/당근/감자/비트/네더
 * 사마귀/코코아 — the ones that just reset in place rather than needing a
 * whole new block placed, unlike 호박/수박/사탕수수/블루베리).
 */
public final class AutoFarmHarvestListener implements Listener {

    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA
    );

    /** The item consumed from the player's inventory to replant each crop. */
    private static final Map<Material, Material> SEED_FOR_CROP = Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS,
            Material.NETHER_WART, Material.NETHER_WART,
            Material.COCOA, Material.COCOA_BEANS
    );

    private final JavaPlugin plugin;
    private final AutoFarmManager manager;

    public AutoFarmHarvestListener(JavaPlugin plugin, AutoFarmManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /** 자동심기 — replanting has to happen a tick later (after the vanilla break finishes clearing the block), so it schedules rather than mutating the block right here. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (!CROPS.contains(type) || !isMature(block)) {
            return;
        }

        Player player = event.getPlayer();
        PlayerData data = manager.core().playerData().getOnline(player.getUniqueId());
        if (manager.remaining(data, AutoFarmType.PLANT) == 0) {
            return;
        }
        Material seed = SEED_FOR_CROP.get(type);
        ItemStack seedStack = new ItemStack(seed);
        if (!player.getInventory().containsAtLeast(seedStack, 1)) {
            return;
        }
        if (!manager.consume(player, data, AutoFarmType.PLANT)) {
            return;
        }
        player.getInventory().removeItem(new ItemStack(seed, 1));

        Location location = block.getLocation();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Block replant = location.getBlock();
            replant.setType(type);
            if (replant.getBlockData() instanceof Ageable ageable) {
                ageable.setAge(0);
                replant.setBlockData(ageable);
            }
        });
    }

    /** 자동줍기 — collects the harvest drops straight into the inventory instead of letting them scatter. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        Material type = event.getBlockState().getType();
        if (!CROPS.contains(type)) {
            return;
        }
        if (!(event.getBlockState().getBlockData() instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }

        Player player = event.getPlayer();
        PlayerData data = manager.core().playerData().getOnline(player.getUniqueId());
        if (!manager.consume(player, data, AutoFarmType.PICKUP)) {
            return;
        }

        event.setCancelled(true);
        for (Item item : event.getItems()) {
            var leftover = player.getInventory().addItem(item.getItemStack());
            leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
        }
    }

    private boolean isMature(Block block) {
        return block.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge();
    }
}
