package com.yeowool.life.logging.tree;

import com.yeowool.life.logging.tree.repository.TreeRepository;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Grows saplings on a fixed timer instead of vanilla's random-tick chance,
 * mirroring {@link com.yeowool.life.farming.CropTimerService} — a sapling
 * planted at time T becomes a full tree at exactly T+{@code growthMinutes},
 * persisted so it survives a restart. A lone jungle/dark oak sapling grows
 * on its own here (skipping vanilla's 2x2-sapling requirement for giant
 * trees) — a deliberate convenience deviation from vanilla.
 */
public final class TreeTimerService {

    private static final Map<Material, TreeType> SAPLING_TREE_TYPES = Map.of(
            Material.OAK_SAPLING, TreeType.TREE,
            Material.SPRUCE_SAPLING, TreeType.REDWOOD,
            Material.BIRCH_SAPLING, TreeType.BIRCH,
            Material.JUNGLE_SAPLING, TreeType.SMALL_JUNGLE,
            Material.ACACIA_SAPLING, TreeType.ACACIA,
            Material.DARK_OAK_SAPLING, TreeType.DARK_OAK,
            Material.CHERRY_SAPLING, TreeType.CHERRY
    );

    private final JavaPlugin plugin;
    private final TreeRepository repository;
    private final ExecutorService executor;
    private final long growthTicks;

    private final Map<String, Long> pending = new ConcurrentHashMap<>();

    public TreeTimerService(JavaPlugin plugin, TreeRepository repository, ExecutorService executor, int growthMinutes) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
        this.growthTicks = growthMinutes * 60L * 20L;
    }

    public static boolean isSapling(Material material) {
        return SAPLING_TREE_TYPES.containsKey(material);
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

    public Optional<Long> remainingMillis(Block block) {
        Long finishAt = pending.get(keyOf(block));
        return finishAt == null ? Optional.empty() : Optional.of(Math.max(0L, finishAt - System.currentTimeMillis()));
    }

    public void startTimer(Block block) {
        String key = keyOf(block);
        long finishAt = System.currentTimeMillis() + growthTicks * 50L;
        pending.put(key, finishAt);

        executor.execute(() -> {
            try {
                repository.insert(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), finishAt);
            } catch (SQLException e) {
                plugin.getLogger().warning("묘목 타이머 저장 실패: " + e.getMessage());
            }
        });

        Bukkit.getScheduler().runTaskLater(plugin, () -> grow(block, key), growthTicks);
    }

    public void cancel(Block block) {
        String key = keyOf(block);
        if (pending.remove(key) != null) {
            executor.execute(() -> {
                try {
                    repository.delete(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
                } catch (SQLException e) {
                    plugin.getLogger().warning("묘목 타이머 삭제 실패: " + e.getMessage());
                }
            });
        }
    }

    private void grow(Block block, String key) {
        if (pending.remove(key) == null) {
            return; // already chopped/cancelled before the timer fired
        }
        executor.execute(() -> {
            try {
                repository.delete(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
            } catch (SQLException e) {
                plugin.getLogger().warning("묘목 타이머 삭제 실패: " + e.getMessage());
            }
        });

        TreeType treeType = SAPLING_TREE_TYPES.get(block.getType());
        if (treeType == null) {
            return; // block changed under us; nothing sensible to grow
        }
        block.getWorld().generateTree(block.getLocation(), treeType);
    }

    /**
     * Reconciles persisted timers on startup: overdue saplings grow
     * immediately, others get a fresh delayed task for their remaining time.
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

                        long remainingMs = row.finishAt() - System.currentTimeMillis();
                        long remainingTicks = Math.max(0L, remainingMs / 50L);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> grow(block, key), remainingTicks);
                    }
                    plugin.getLogger().info("성장 중인 묘목 " + rows.size() + "개를 복원했습니다.");
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("묘목 타이머 복원 실패: " + e.getMessage());
            }
        });
    }
}
