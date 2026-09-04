package com.yeowool.life.farming.custom;

import com.yeowool.life.farming.custom.repository.CustomCropRepository;
import dev.lone.itemsadder.api.CustomBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Drives ItemsAdder custom crop growth: each stage is a full block
 * replacement ({@link CustomBlock#place(String, Location)}), scheduled with
 * that stage's own configured duration — unlike vanilla crops there is no
 * natural-growth event to race against, since a custom block only ever
 * changes when this service calls {@code place()} itself.
 */
public final class CustomCropTimerService {

    private final JavaPlugin plugin;
    private final CustomCropRepository repository;
    private final CustomCropRegistry registry;
    private final ExecutorService executor;

    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    public CustomCropTimerService(JavaPlugin plugin, CustomCropRepository repository, CustomCropRegistry registry, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.registry = registry;
        this.executor = executor;
    }

    private static String keyOf(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private static String keyOf(Block block) {
        return keyOf(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public boolean isPending(Block block) {
        return pending.contains(keyOf(block));
    }

    /** Called when a crop's first stage is freshly planted. */
    public void onPlanted(Block block, CustomCropDefinition crop) {
        scheduleAdvance(block, crop, 0);
    }

    public void cancel(Block block) {
        String key = keyOf(block);
        if (pending.remove(key)) {
            executor.execute(() -> {
                try {
                    repository.delete(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
                } catch (SQLException e) {
                    plugin.getLogger().warning("커스텀 작물 타이머 삭제 실패: " + e.getMessage());
                }
            });
        }
    }

    /**
     * Schedules the transition from {@code currentStage} to the next one,
     * persisting the current state first so a restart mid-growth resumes
     * correctly (see {@link #reconcileOnStartup}).
     */
    private void scheduleAdvance(Block block, CustomCropDefinition crop, int currentStage) {
        if (crop.isFinalStage(currentStage)) {
            // Nothing left to grow into; harvesting is handled by CustomFarmingListener on break.
            pending.remove(keyOf(block));
            return;
        }

        String key = keyOf(block);
        pending.add(key);
        int minutes = crop.stages().get(currentStage).minutes();
        long ticks = minutes * 60L * 20L;
        long finishAt = System.currentTimeMillis() + ticks * 50L;

        executor.execute(() -> {
            try {
                repository.upsert(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                        crop.id(), currentStage, finishAt);
            } catch (SQLException e) {
                plugin.getLogger().warning("커스텀 작물 타이머 저장 실패: " + e.getMessage());
            }
        });

        Bukkit.getScheduler().runTaskLater(plugin, () -> advance(block, crop, currentStage, key), ticks);
    }

    private void advance(Block block, CustomCropDefinition crop, int fromStage, String key) {
        if (!pending.contains(key)) {
            return; // harvested or otherwise cancelled before this fired
        }

        CustomBlock current = CustomBlock.byAlreadyPlaced(block);
        String expectedId = crop.stages().get(fromStage).blockId();
        if (current == null || !expectedId.equals(current.getNamespacedID())) {
            // Something else changed this block while we were waiting; stop tracking it.
            pending.remove(key);
            executor.execute(() -> {
                try {
                    repository.delete(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
                } catch (SQLException e) {
                    plugin.getLogger().warning("커스텀 작물 타이머 삭제 실패: " + e.getMessage());
                }
            });
            return;
        }

        int nextStage = fromStage + 1;
        CustomBlock.place(crop.stages().get(nextStage).blockId(), block.getLocation());
        scheduleAdvance(block, crop, nextStage);
    }

    /**
     * Reconciles persisted timers on startup: resumes each in-progress crop
     * with its remaining time (or advances immediately if it's overdue).
     * Must run on the main thread.
     */
    public void reconcileOnStartup() {
        executor.execute(() -> {
            try {
                var rows = repository.loadAll();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int resumed = 0;
                    for (var row : rows) {
                        World world = Bukkit.getWorld(row.world());
                        if (world == null) {
                            continue;
                        }
                        var crop = registry.getCrop(row.cropId()).orElse(null);
                        if (crop == null) {
                            continue;
                        }
                        Block block = world.getBlockAt(row.x(), row.y(), row.z());
                        String key = keyOf(block);
                        pending.add(key);

                        long remainingMs = row.finishAt() - System.currentTimeMillis();
                        long remainingTicks = Math.max(0L, remainingMs / 50L);
                        Bukkit.getScheduler().runTaskLater(plugin,
                                () -> advance(block, crop, row.currentStage(), key), remainingTicks);
                        resumed++;
                    }
                    if (resumed > 0) {
                        plugin.getLogger().info("성장 중인 커스텀 작물 " + resumed + "개를 복원했습니다.");
                    }
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("커스텀 작물 타이머 복원 실패: " + e.getMessage());
            }
        });
    }
}
