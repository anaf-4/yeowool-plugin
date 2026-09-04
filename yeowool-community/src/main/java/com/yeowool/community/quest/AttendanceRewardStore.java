package com.yeowool.community.quest;

import com.yeowool.core.api.model.CurrencyType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * The OP-editable reward for each attendance tier (see {@link
 * AttendanceRewardEditorGui}): a currency amount plus optional bonus items.
 * Every tier starts from {@code config.yml}'s {@code reward-on} value (온,
 * no items) until an admin saves something through the GUI, at which point
 * the database row for that tier takes over — config.yml stays the
 * "factory default" and is never overwritten.
 */
public final class AttendanceRewardStore {

    public enum Tier {
        DAILY("daily", "attendance.reward-on"),
        WEEKLY("weekly", "attendance.weekly.reward-on"),
        MONTHLY("monthly", "attendance.monthly.reward-on");

        private final String key;
        private final String configPath;

        Tier(String key, String configPath) {
            this.key = key;
            this.configPath = configPath;
        }

        public String key() {
            return key;
        }
    }

    public record TierReward(long amount, CurrencyType currency, List<ItemStack> items) {
    }

    private final JavaPlugin plugin;
    private final AttendanceRewardRepository repository;
    private final ExecutorService executor;
    private volatile Map<Tier, TierReward> effective = Map.of();

    public AttendanceRewardStore(JavaPlugin plugin, AttendanceRewardRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    public void loadIntoCache() throws SQLException {
        Map<String, AttendanceRewardRepository.ConfigRow> configRows = repository.loadConfig();
        Map<String, Map<Integer, ItemStack>> itemRows = repository.loadItems();

        Map<Tier, TierReward> built = new EnumMap<>(Tier.class);
        for (Tier tier : Tier.values()) {
            AttendanceRewardRepository.ConfigRow row = configRows.get(tier.key());
            long amount = row != null ? row.amount() : plugin.getConfig().getLong(tier.configPath, 0);
            CurrencyType currency = row != null ? row.currency() : CurrencyType.ON;
            List<ItemStack> items = List.copyOf(itemRows.getOrDefault(tier.key(), Map.of()).values());
            built.put(tier, new TierReward(amount, currency, items));
        }
        effective = built;
    }

    public TierReward get(Tier tier) {
        return effective.getOrDefault(tier, new TierReward(0, CurrencyType.ON, List.of()));
    }

    public void saveAmount(Tier tier, long amount, CurrencyType currency) {
        update(tier, current -> new TierReward(amount, currency, current.items()));
        executor.execute(() -> {
            try {
                repository.saveConfig(tier.key(), amount, currency);
            } catch (SQLException e) {
                plugin.getLogger().severe("출석 보상 금액 저장 실패 (" + tier.key() + "): " + e.getMessage());
            }
        });
    }

    public void saveItems(Tier tier, Map<Integer, ItemStack> items) {
        List<ItemStack> snapshot = new ArrayList<>(items.values());
        update(tier, current -> new TierReward(current.amount(), current.currency(), List.copyOf(snapshot)));
        Map<Integer, ItemStack> itemsCopy = new LinkedHashMap<>(items);
        executor.execute(() -> {
            try {
                repository.saveItems(tier.key(), itemsCopy);
            } catch (SQLException e) {
                plugin.getLogger().severe("출석 보상 아이템 저장 실패 (" + tier.key() + "): " + e.getMessage());
            }
        });
    }

    private synchronized void update(Tier tier, java.util.function.UnaryOperator<TierReward> mutator) {
        Map<Tier, TierReward> copy = new EnumMap<>(Tier.class);
        copy.putAll(effective);
        copy.put(tier, mutator.apply(get(tier)));
        effective = copy;
    }
}
