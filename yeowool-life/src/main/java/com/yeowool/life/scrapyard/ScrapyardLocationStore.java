package com.yeowool.life.scrapyard;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * In-memory cache of every admin-registered 폐기장 location — points (entry
 * click spot, entry teleport destination — usually in the separate dungeon
 * world — exit click spot, return teleport spot, arena bounding-box
 * corners), loot chests, and mob spawn points — refreshed once at startup
 * ({@link #loadIntoCache}) and kept current by every {@code /폐기장설정}
 * mutation. Reads (block-click matching, bounds checks, mob spawning) happen
 * every tick for online players, so everything here must be a cheap
 * synchronous lookup — never a DB call.
 *
 * <p>Deliberately never caches a resolved {@link Location}/{@code World}
 * long-term — only world-name + raw coordinates ({@link
 * ScrapyardRepository}'s records). A world manager plugin (MultiWorld etc.)
 * can re-import/reload a world after this store already cached a {@code
 * World} reference, silently leaving that reference stale — every {@code
 * World.equals()} check and every teleport using it would then quietly stop
 * working. Resolving {@code Bukkit.getWorld(name)} fresh on every access
 * (see {@code toLocation()}) sidesteps that entirely; block-matching
 * ({@link #chestAt}/{@link #mobSpawnAt}) compares world name + integer
 * coordinates directly and never needs a resolved world at all.
 */
public final class ScrapyardLocationStore {

    public static final String ENTRY = "entry";
    public static final String ENTRY_DESTINATION = "entry_destination";
    public static final String EXIT = "exit";
    public static final String RETURN = "return";
    public static final String REGION_MIN = "region_min";
    public static final String REGION_MAX = "region_max";
    public static final String BOSS_SPAWN = "boss_spawn";

    private final JavaPlugin plugin;
    private final ScrapyardRepository repository;
    private final ExecutorService executor;

    private volatile Map<String, ScrapyardRepository.NamedPoint> points = Map.of();
    private final List<ScrapyardRepository.Chest> chests = new CopyOnWriteArrayList<>();
    private final List<ScrapyardRepository.MobSpawn> mobSpawns = new CopyOnWriteArrayList<>();

    public ScrapyardLocationStore(JavaPlugin plugin, ScrapyardRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    public void loadIntoCache() throws SQLException {
        points = Map.copyOf(repository.loadPoints());
        chests.clear();
        chests.addAll(repository.loadChests());
        mobSpawns.clear();
        mobSpawns.addAll(repository.loadMobSpawns());
    }

    /** Empty if the category isn't registered, or its world isn't currently loaded. */
    public Optional<Location> point(String category) {
        ScrapyardRepository.NamedPoint raw = points.get(category);
        return raw == null ? Optional.empty() : Optional.ofNullable(raw.toLocation());
    }

    public void setPoint(String category, Location location) {
        String world = location.getWorld().getName();
        var raw = new ScrapyardRepository.NamedPoint(world, location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
        Map<String, ScrapyardRepository.NamedPoint> copy = new ConcurrentHashMap<>(points);
        copy.put(category, raw);
        points = Map.copyOf(copy);
        executor.execute(() -> {
            try {
                repository.savePoint(category, raw.world(), raw.x(), raw.y(), raw.z(), raw.yaw(), raw.pitch());
            } catch (SQLException e) {
                plugin.getLogger().severe("폐기장 지점 저장 실패 (" + category + "): " + e.getMessage());
            }
        });
    }

    public boolean hasRegion() {
        return points.containsKey(REGION_MIN) && points.containsKey(REGION_MAX);
    }

    /** False if the arena bounds ({@link #REGION_MIN}/{@link #REGION_MAX}) aren't set yet, or {@code location} is in a different world. */
    public boolean isInsideRegion(Location location) {
        ScrapyardRepository.NamedPoint min = points.get(REGION_MIN);
        ScrapyardRepository.NamedPoint max = points.get(REGION_MAX);
        if (min == null || max == null || !min.world().equals(location.getWorld().getName())) {
            return false;
        }
        double x = location.getX(), y = location.getY(), z = location.getZ();
        return x >= Math.min(min.x(), max.x()) && x <= Math.max(min.x(), max.x())
                && y >= Math.min(min.y(), max.y()) && y <= Math.max(min.y(), max.y())
                && z >= Math.min(min.z(), max.z()) && z <= Math.max(min.z(), max.z());
    }

    public List<ScrapyardRepository.Chest> chests() {
        return List.copyOf(chests);
    }

    public Optional<ScrapyardRepository.Chest> chestAt(Location block) {
        String world = block.getWorld().getName();
        int x = block.getBlockX(), y = block.getBlockY(), z = block.getBlockZ();
        return chests.stream()
                .filter(chest -> chest.world().equals(world) && chest.x() == x && chest.y() == y && chest.z() == z)
                .findFirst();
    }

    public void addChest(Location location) {
        String world = location.getWorld().getName();
        int x = location.getBlockX(), y = location.getBlockY(), z = location.getBlockZ();
        executor.execute(() -> {
            try {
                int id = repository.addChest(world, x, y, z);
                chests.add(new ScrapyardRepository.Chest(id, world, x, y, z, null));
            } catch (SQLException e) {
                plugin.getLogger().severe("폐기장 상자 추가 실패: " + e.getMessage());
            }
        });
    }

    public boolean removeChestAt(Location block) {
        var found = chestAt(block).orElse(null);
        if (found == null) {
            return false;
        }
        chests.remove(found);
        executor.execute(() -> {
            try {
                repository.removeChest(found.id());
            } catch (SQLException e) {
                plugin.getLogger().severe("폐기장 상자 제거 실패: " + e.getMessage());
            }
        });
        return true;
    }

    /** Replaces the chest's cached "last opened" date immediately (for same-tick re-check) and persists async. */
    public void markChestOpened(ScrapyardRepository.Chest chest, String today) {
        chests.remove(chest);
        chests.add(new ScrapyardRepository.Chest(chest.id(), chest.world(), chest.x(), chest.y(), chest.z(), today));
        executor.execute(() -> {
            try {
                repository.markChestOpened(chest.id(), today);
            } catch (SQLException e) {
                plugin.getLogger().severe("폐기장 상자 상태 저장 실패 (" + chest.id() + "): " + e.getMessage());
            }
        });
    }

    public List<ScrapyardRepository.MobSpawn> mobSpawns() {
        return List.copyOf(mobSpawns);
    }

    public void addMobSpawn(Location location) {
        String world = location.getWorld().getName();
        double x = location.getX(), y = location.getY(), z = location.getZ();
        executor.execute(() -> {
            try {
                int id = repository.addMobSpawn(world, x, y, z);
                mobSpawns.add(new ScrapyardRepository.MobSpawn(id, world, x, y, z));
            } catch (SQLException e) {
                plugin.getLogger().severe("폐기장 몹 스폰 지점 추가 실패: " + e.getMessage());
            }
        });
    }

    public boolean removeMobSpawnAt(Location block) {
        String world = block.getWorld().getName();
        int x = block.getBlockX(), y = block.getBlockY(), z = block.getBlockZ();
        var found = mobSpawns.stream()
                .filter(spawn -> spawn.world().equals(world) && (int) spawn.x() == x && (int) spawn.y() == y && (int) spawn.z() == z)
                .findFirst().orElse(null);
        if (found == null) {
            return false;
        }
        mobSpawns.remove(found);
        executor.execute(() -> {
            try {
                repository.removeMobSpawn(found.id());
            } catch (SQLException e) {
                plugin.getLogger().severe("폐기장 몹 스폰 지점 제거 실패: " + e.getMessage());
            }
        });
        return true;
    }
}
