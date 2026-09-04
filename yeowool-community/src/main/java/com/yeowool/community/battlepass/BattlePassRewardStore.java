package com.yeowool.community.battlepass;

import com.yeowool.community.battlepass.repository.BattlePassRepository;
import com.yeowool.core.api.model.CurrencyType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;

/**
 * In-memory cache of every configured tier's reward (per {@link BattlePassTrack}), backed by
 * {@code yw_battlepass_reward_config}/{@code yw_battlepass_reward_items} — same
 * config-lives-in-DB-with-in-memory-cache shape as {@code AttendanceRewardStore}, just keyed by
 * (track, tier) instead of a fixed 3-value enum since tiers are open-ended and admin-defined.
 */
public final class BattlePassRewardStore {

    /** A tier with no configured reward is treated as "not defined" - {@link Long#MAX_VALUE} required points means it can never be reached by accident. */
    public static final TierReward UNDEFINED = new TierReward(Long.MAX_VALUE, 0, CurrencyType.ON, List.of());

    public record TierReward(long requiredPoints, long amount, CurrencyType currency, List<ItemStack> items) {
    }

    private final JavaPlugin plugin;
    private final BattlePassRepository repository;
    private final ExecutorService executor;
    private volatile Map<BattlePassTrack, TreeMap<Integer, TierReward>> effective = Map.of();

    public BattlePassRewardStore(JavaPlugin plugin, BattlePassRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    /**
     * Synchronized to match {@link #update} - today this only ever runs once in {@code onEnable()}
     * before the command/listener are wired, so nothing can race it, but a future hot-reload path
     * calling this again while the plugin is live could otherwise lose a concurrent admin edit to
     * a last-writer-wins race between this method's unsynchronized full-map swap and update()'s
     * synchronized copy-on-write.
     */
    public synchronized void loadIntoCache() throws SQLException {
        var configRows = repository.loadRewardConfig();
        var itemRows = repository.loadRewardItems();

        Map<BattlePassTrack, TreeMap<Integer, TierReward>> built = new EnumMap<>(BattlePassTrack.class);
        for (BattlePassTrack track : BattlePassTrack.values()) {
            TreeMap<Integer, TierReward> tiers = new TreeMap<>();
            for (var entry : configRows.getOrDefault(track, Map.of()).entrySet()) {
                int tier = entry.getKey();
                var row = entry.getValue();
                List<ItemStack> items = List.copyOf(itemRows.getOrDefault(track, Map.of())
                        .getOrDefault(tier, Map.of()).values());
                tiers.put(tier, new TierReward(row.requiredPoints(), row.amount(), row.currency(), items));
            }
            built.put(track, tiers);
        }
        effective = built;
    }

    public TierReward get(BattlePassTrack track, int tier) {
        return effective.getOrDefault(track, new TreeMap<>()).getOrDefault(tier, UNDEFINED);
    }

    /** Every configured tier number for this track, ascending. */
    public List<Integer> configuredTiers(BattlePassTrack track) {
        return new ArrayList<>(effective.getOrDefault(track, new TreeMap<>()).keySet());
    }

    public int maxConfiguredTier(BattlePassTrack track) {
        TreeMap<Integer, TierReward> tiers = effective.get(track);
        return tiers == null || tiers.isEmpty() ? 0 : tiers.lastKey();
    }

    public void saveConfig(BattlePassTrack track, int tier, long requiredPoints, long amount, CurrencyType currency) {
        update(track, tier, current -> new TierReward(requiredPoints, amount, currency, current.items()));
        executor.execute(() -> {
            try {
                repository.saveRewardConfig(track, tier, requiredPoints, amount, currency);
            } catch (SQLException e) {
                plugin.getLogger().severe("배틀패스 보상 설정 저장 실패 (" + track + "/" + tier + "): " + e.getMessage());
            }
        });
    }

    public void saveItems(BattlePassTrack track, int tier, Map<Integer, ItemStack> items) {
        List<ItemStack> snapshot = List.copyOf(items.values());
        update(track, tier, current -> new TierReward(current.requiredPoints(), current.amount(), current.currency(), snapshot));
        Map<Integer, ItemStack> itemsCopy = Map.copyOf(items);
        executor.execute(() -> {
            try {
                repository.saveRewardItems(track, tier, itemsCopy);
            } catch (SQLException e) {
                plugin.getLogger().severe("배틀패스 보상 아이템 저장 실패 (" + track + "/" + tier + "): " + e.getMessage());
            }
        });
    }

    private synchronized void update(BattlePassTrack track, int tier, java.util.function.UnaryOperator<TierReward> mutator) {
        Map<BattlePassTrack, TreeMap<Integer, TierReward>> copy = new EnumMap<>(BattlePassTrack.class);
        for (var entry : effective.entrySet()) {
            copy.put(entry.getKey(), new TreeMap<>(entry.getValue()));
        }
        copy.computeIfAbsent(track, t -> new TreeMap<>()).put(tier, mutator.apply(get(track, tier)));
        effective = copy;
    }
}
