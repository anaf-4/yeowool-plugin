package com.yeowool.life.farming;

import com.yeowool.life.farming.repository.CropRepository;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Drives the plugin plan's flat "모든 작물 성장시간 = 10분" rule: growth is not
 * left to vanilla random ticks (too light/RNG dependent to hit a fixed time)
 * but instead scheduled as a single delayed task per planted crop, persisted
 * to {@code yw_farm_crops} so an in-progress crop survives a restart instead
 * of freezing at its current stage forever. While a block is pending here,
 * {@link FarmingListener} cancels its natural {@code BlockGrowEvent} so it
 * can't finish early or out of sync with the timer.
 *
 * <p>Also keeps the farmland under every tracked crop at full moisture (see
 * {@link #refreshFarmlandMoisture}) so it never looks/acts dry — growth
 * speed is already fixed by this timer regardless of moisture, so this is
 * purely so farmland never needs a nearby water source.
 */
public final class CropTimerService {

    private final JavaPlugin plugin;
    private final CropRepository repository;
    private final ExecutorService executor;
    private final long growthTicks;

    /** Block location key -> the epoch millis it finishes growing at. */
    private final Map<String, Long> pending = new ConcurrentHashMap<>();

    /**
     * Every location with a crop we planted-and-are-managing, whether still
     * growing or fully grown and just waiting to be harvested — unlike
     * {@link #pending}, an entry here isn't removed at {@link #finish}, only
     * at {@link #cancel} (break/harvest). Used purely to keep the farmland
     * moist for as long as the crop is actually sitting there.
     */
    private final Set<String> plantedLocations = ConcurrentHashMap.newKeySet();

    public CropTimerService(JavaPlugin plugin, CropRepository repository, ExecutorService executor, int growthMinutes) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
        this.growthTicks = growthMinutes * 60L * 20L;
    }

    private static String keyOf(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private static String keyOf(Block block) {
        return keyOf(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public boolean isPending(Block block) {
        return pending.containsKey(keyOf(block));
    }

    /** Milliseconds left until this tracked crop finishes, if it's one we're tracking. */
    public Optional<Long> remainingMillis(Block block) {
        Long finishAt = pending.get(keyOf(block));
        return finishAt == null ? Optional.empty() : Optional.of(Math.max(0L, finishAt - System.currentTimeMillis()));
    }

    public void startTimer(Block block) {
        String key = keyOf(block);
        long finishAt = System.currentTimeMillis() + growthTicks * 50L;
        pending.put(key, finishAt);
        plantedLocations.add(key);

        executor.execute(() -> {
            try {
                repository.insert(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), finishAt);
            } catch (SQLException e) {
                plugin.getLogger().warning("작물 타이머 저장 실패: " + e.getMessage());
            }
        });

        Bukkit.getScheduler().runTaskLater(plugin, () -> finish(block, key), growthTicks);
    }

    public void cancel(Block block) {
        String key = keyOf(block);
        plantedLocations.remove(key);
        if (pending.remove(key) != null) {
            executor.execute(() -> {
                try {
                    repository.delete(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
                } catch (SQLException e) {
                    plugin.getLogger().warning("작물 타이머 삭제 실패: " + e.getMessage());
                }
            });
        }
    }

    private void finish(Block block, String key) {
        if (pending.remove(key) == null) {
            return; // already harvested/cancelled before the timer fired
        }
        if (block.getBlockData() instanceof Ageable ageable) {
            ageable.setAge(ageable.getMaximumAge());
            block.setBlockData(ageable);
        }
        executor.execute(() -> {
            try {
                repository.delete(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
            } catch (SQLException e) {
                plugin.getLogger().warning("작물 타이머 삭제 실패: " + e.getMessage());
            }
        });
    }

    /**
     * Reconciles persisted timers on startup: overdue crops are finished
     * immediately, others get a fresh delayed task for their remaining time.
     * Must run on the main thread (touches blocks/chunks).
     */
    public void reconcileOnStartup() {
        executor.execute(() -> {
            try {
                var rows = repository.loadAll();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (var row : rows) {
                        World world = Bukkit.getWorld(row.world());
                        if (world == null) {
                            continue;
                        }
                        Block block = world.getBlockAt(row.x(), row.y(), row.z());
                        String key = keyOf(block);
                        pending.put(key, row.finishAt());
                        plantedLocations.add(key);

                        long remainingMs = row.finishAt() - System.currentTimeMillis();
                        long remainingTicks = Math.max(0L, remainingMs / 50L);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> finish(block, key), remainingTicks);
                    }
                    plugin.getLogger().info("성장 중인 작물 " + rows.size() + "개를 복원했습니다.");
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("작물 타이머 복원 실패: " + e.getMessage());
            }
        });
    }

    /**
     * Sets the farmland under every planted crop (growing or already fully
     * grown) to max moisture so it never dries out — meant to be called
     * periodically (see {@code YeowoolLife}). Cheap since it only touches
     * locations we planted a managed crop on, not every farmland block on
     * the server.
     */
    public void refreshFarmlandMoisture() {
        for (String key : plantedLocations) {
            String[] parts = key.split(":");
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                continue;
            }
            Block crop = world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            Block below = crop.getRelative(org.bukkit.block.BlockFace.DOWN);
            if (below.getBlockData() instanceof Farmland farmland && farmland.getMoisture() < farmland.getMaximumMoisture()) {
                farmland.setMoisture(farmland.getMaximumMoisture());
                below.setBlockData(farmland);
            }
        }
    }
}
