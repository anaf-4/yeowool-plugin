package com.yeowool.enhance;

import com.yeowool.enhance.database.EnhanceCostRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Per-level enhance cost (온 + 재료 개수), fully cached in memory and DB-backed
 * (unlike the rest of {@link EnhanceConfig}, which stays config.yml-driven) —
 * this is the part {@code /강화설정} lets admins tune live, in-game, without
 * a restart. Any level missing from the DB (should only happen right after
 * install, or if {@code max-level} was raised past what was last seeded) is
 * seeded on first load with a default curve: material steps through
 * {@code moafarm_items:magic_ore_1}..{@code _6} every 5 levels, currency
 * grows roughly exponentially.
 */
public final class EnhanceCostManager {

    public record CostEntry(long currency, String materialId, int materialAmount) {
    }

    private final JavaPlugin plugin;
    private final EnhanceCostRepository repository;
    private final ExecutorService executor;
    private final Map<Integer, CostEntry> costs = new ConcurrentHashMap<>();

    public EnhanceCostManager(JavaPlugin plugin, EnhanceCostRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    public void loadAll(int maxLevel) throws SQLException {
        Map<Integer, EnhanceCostRepository.CostRow> rows = repository.loadAll();
        for (Map.Entry<Integer, EnhanceCostRepository.CostRow> entry : rows.entrySet()) {
            var row = entry.getValue();
            costs.put(entry.getKey(), new CostEntry(row.currency(), row.materialId(), row.materialAmount()));
        }
        for (int level = 0; level < maxLevel; level++) {
            costs.computeIfAbsent(level, this::seedDefault);
        }
        plugin.getLogger().info("강화 비용 " + costs.size() + "단계를 불러왔습니다 (신규 시드 " + Math.max(0, maxLevel - rows.size()) + "건 포함).");
    }

    private CostEntry seedDefault(int level) {
        int oreIndex = Math.min(6, (level / 5) + 1);
        String materialId = "moafarm_items:magic_ore_" + oreIndex;
        int amount = 3 + (oreIndex - 1) * 2;
        long currency = Math.round(1000 * Math.pow(level + 1, 1.3));
        CostEntry entry = new CostEntry(currency, materialId, amount);
        persist(level, entry);
        return entry;
    }

    public CostEntry costFor(int level) {
        CostEntry entry = costs.get(level);
        return entry != null ? entry : seedDefault(level);
    }

    public void setCurrency(int level, long currency) {
        CostEntry current = costFor(level);
        CostEntry updated = new CostEntry(currency, current.materialId(), current.materialAmount());
        costs.put(level, updated);
        persist(level, updated);
    }

    public void setMaterial(int level, String materialId, int materialAmount) {
        CostEntry current = costFor(level);
        CostEntry updated = new CostEntry(current.currency(), materialId, materialAmount);
        costs.put(level, updated);
        persist(level, updated);
    }

    private void persist(int level, CostEntry entry) {
        executor.execute(() -> {
            try {
                repository.upsert(level, entry.currency(), entry.materialId(), entry.materialAmount());
            } catch (SQLException e) {
                plugin.getLogger().severe("강화 비용 저장 실패 (레벨 " + level + "): " + e.getMessage());
            }
        });
    }
}
