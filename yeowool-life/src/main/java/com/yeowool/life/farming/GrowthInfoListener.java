package com.yeowool.life.farming;

import com.yeowool.core.util.DurationFormat;
import com.yeowool.life.logging.tree.TreeTimerService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Right-clicking a crop or sapling that's on one of our fixed growth timers
 * (see {@link CropTimerService} / {@link TreeTimerService}) shows the time
 * left as a floating {@link TextDisplay} just above it, visible only to the
 * player who asked, for a few seconds.
 */
public final class GrowthInfoListener implements Listener {

    private static final long DISPLAY_TICKS = 60L; // 3 seconds

    private final JavaPlugin plugin;
    private final CropTimerService cropTimerService;
    private final TreeTimerService treeTimerService;

    /** One display per player at a time — a new query replaces the old one instead of stacking. */
    private final Map<UUID, TextDisplay> activeDisplays = new ConcurrentHashMap<>();

    public GrowthInfoListener(JavaPlugin plugin, CropTimerService cropTimerService, TreeTimerService treeTimerService) {
        this.plugin = plugin;
        this.cropTimerService = cropTimerService;
        this.treeTimerService = treeTimerService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        // PlayerInteractEvent fires once per hand on a single right-click;
        // without this guard every click would spawn two overlapping displays.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        var block = event.getClickedBlock();

        cropTimerService.remainingMillis(block).ifPresentOrElse(
                remaining -> show(event.getPlayer(), block, remaining),
                () -> treeTimerService.remainingMillis(block).ifPresent(remaining -> show(event.getPlayer(), block, remaining))
        );
    }

    private void show(Player player, org.bukkit.block.Block block, long remainingMs) {
        TextDisplay previous = activeDisplays.remove(player.getUniqueId());
        if (previous != null) {
            previous.remove();
        }

        Component text = remainingMs <= 0
                ? Component.text("다 자랐습니다!", NamedTextColor.GREEN)
                : Component.text("남은 시간: " + DurationFormat.humanize(remainingMs), NamedTextColor.YELLOW);

        var location = block.getLocation().add(0.5, 1.3, 0.5);
        TextDisplay display = block.getWorld().spawn(location, TextDisplay.class, entity -> {
            entity.text(text);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
        });
        player.showEntity(plugin, display);
        activeDisplays.put(player.getUniqueId(), display);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            activeDisplays.remove(player.getUniqueId(), display);
            display.remove();
        }, DISPLAY_TICKS);
    }
}
