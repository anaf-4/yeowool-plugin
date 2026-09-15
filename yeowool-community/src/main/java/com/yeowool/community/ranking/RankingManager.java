package com.yeowool.community.ranking;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Caches the top 10 of each {@link RankingCategory}, refreshed on a timer
 * (see {@link #refresh}) rather than queried live — a leaderboard doesn't
 * need to-the-second freshness, and this keeps {@code /명예의전당} and the
 * PlaceholderAPI tokens both cheap, synchronous, main-thread lookups.
 *
 * <p>OPs are excluded (server-run/test accounts skew a small server's money
 * or playtime leaderboard). {@link #query} over-fetches a buffer past the
 * shown size specifically to absorb OPs getting filtered back out.
 */
public final class RankingManager {

    private static final int SHOWN_SIZE = 10;
    private static final int QUERY_BUFFER = 40;

    private final JavaPlugin plugin;
    private final RankingRepository repository;
    private final ExecutorService executor;

    private volatile Map<RankingCategory, List<RankingEntry>> cache = new EnumMap<>(RankingCategory.class);
    private Consumer<RankingCategory> onRefreshed = category -> {
    };

    public RankingManager(JavaPlugin plugin, RankingRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    /** Called (on the main thread) once each category's cache updates — {@link com.yeowool.community.ranking.statue.RankingStatueManager} hooks this to only touch NPCs when the top-3 actually changed. */
    public void onRefreshed(Consumer<RankingCategory> listener) {
        this.onRefreshed = listener;
    }

    public List<RankingEntry> top(RankingCategory category) {
        return cache.getOrDefault(category, List.of());
    }

    public void refreshRepeating(long periodTicks) {
        refresh();
        new BukkitRunnable() {
            @Override
            public void run() {
                refresh();
            }
        }.runTaskTimer(plugin, periodTicks, periodTicks);
    }

    public void refresh() {
        executor.execute(() -> {
            for (RankingCategory category : RankingCategory.values()) {
                List<RankingEntry> top = query(category);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<RankingCategory, List<RankingEntry>> copy = new EnumMap<>(cache);
                    copy.put(category, top);
                    cache = copy;
                    onRefreshed.accept(category);
                });
            }
        });
    }

    private List<RankingEntry> query(RankingCategory category) {
        try {
            List<RankingEntry> raw = switch (category) {
                case MONEY -> repository.topByMoney(QUERY_BUFFER);
                case LAND -> repository.topByLand(QUERY_BUFFER);
                case PLAYTIME -> repository.topByPlaytime(QUERY_BUFFER);
            };
            return raw.stream()
                    .filter(entry -> !Bukkit.getOfflinePlayer(entry.uuid()).isOp())
                    .limit(SHOWN_SIZE)
                    .toList();
        } catch (Exception e) {
            plugin.getLogger().severe("명예의 전당 순위 조회 실패 (" + category + "): " + e.getMessage());
            return List.of();
        }
    }
}
