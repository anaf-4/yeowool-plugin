package com.yeowool.life.job;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.life.job.repository.JobRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A player has at most one active job (per plugin-design decision — switching
 * jobs never loses progress, it just stops earning XP toward the old one).
 * Leveling a job grants skill points spendable on four universal passive
 * effects ({@link SkillEffectType}) rather than active abilities, to keep
 * this a straightforward RPG-progression layer instead of a full ability
 * system. All four are resolved entirely inside {@link #grantXp} so no
 * per-job action listener needs to know skills exist.
 */
public final class JobManager {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final JobRepository repository;
    private final ExecutorService executor;
    private final Map<String, JobDefinition> jobs;
    private final Map<String, SkillNode> skills;
    private final double xpCurveBase;
    private final double xpCurveExponent;
    private final int maxLevel;
    private final long bonusCurrencyPerProc;

    private final Map<UUID, PlayerJobData> cache = new ConcurrentHashMap<>();

    public JobManager(JavaPlugin plugin, YeowoolCoreAPI core, JobRepository repository, ExecutorService executor,
                       Map<String, JobDefinition> jobs, Map<String, SkillNode> skills,
                       double xpCurveBase, double xpCurveExponent, int maxLevel, long bonusCurrencyPerProc) {
        this.plugin = plugin;
        this.core = core;
        this.repository = repository;
        this.executor = executor;
        this.jobs = jobs;
        this.skills = skills;
        this.xpCurveBase = xpCurveBase;
        this.xpCurveExponent = xpCurveExponent;
        this.maxLevel = maxLevel;
        this.bonusCurrencyPerProc = bonusCurrencyPerProc;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public Map<String, JobDefinition> jobs() {
        return jobs;
    }

    public Map<String, SkillNode> skills() {
        return skills;
    }

    public CompletableFuture<Void> load(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try {
                cache.put(uuid, repository.load(uuid));
            } catch (SQLException e) {
                plugin.getLogger().severe("직업 데이터 로드 실패 (" + uuid + "): " + e.getMessage());
                cache.put(uuid, new PlayerJobData());
            }
        }, executor);
    }

    public void unload(UUID uuid) {
        cache.remove(uuid);
    }

    public Optional<PlayerJobData> getIfLoaded(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    public long requiredXpForLevel(int level) {
        return Math.round(xpCurveBase * Math.pow(level, xpCurveExponent));
    }

    public void setActiveJob(Player player, String jobId) {
        PlayerJobData data = cache.computeIfAbsent(player.getUniqueId(), u -> new PlayerJobData());
        data.setActiveJob(jobId);
        executor.execute(() -> {
            try {
                repository.saveActiveJob(player.getUniqueId(), jobId);
            } catch (SQLException e) {
                plugin.getLogger().severe("활성 직업 저장 실패 (" + player.getUniqueId() + "): " + e.getMessage());
            }
        });
    }

    /** Grants XP toward {@code jobId} only if it's currently the player's active job; applies the XP-bonus skill and handles level-ups. */
    public void grantXp(Player player, String jobId, long baseAmount) {
        PlayerJobData data = cache.get(player.getUniqueId());
        if (data == null || !jobId.equals(data.activeJob())) {
            return;
        }
        PlayerJobData.JobProgress progress = data.progress(jobId);
        if (progress.level() >= maxLevel) {
            return;
        }

        double critChance = skillBonusPercent(progress, SkillEffectType.CRITICAL_XP_CHANCE_PERCENT);
        boolean critical = critChance > 0 && ThreadLocalRandom.current().nextDouble(100) < critChance;
        long effectiveBase = critical ? baseAmount * 2 : baseAmount;

        double bonusPercent = skillBonusPercent(progress, SkillEffectType.XP_BONUS_PERCENT);
        long amount = Math.round(effectiveBase * (1 + bonusPercent / 100.0));
        long newXp = progress.xp() + amount;

        double bonusCurrencyChance = skillBonusPercent(progress, SkillEffectType.BONUS_CURRENCY_CHANCE_PERCENT);
        if (bonusCurrencyChance > 0 && ThreadLocalRandom.current().nextDouble(100) < bonusCurrencyChance) {
            core.economyData().modifyBalance(player.getUniqueId(), bonusCurrencyPerProc, "YeowoolLife", "직업 부수입: " + jobId);
        }

        int level = progress.level();
        long required = requiredXpForLevel(level);
        int levelsGained = 0;
        while (newXp >= required && level < maxLevel) {
            newXp -= required;
            level++;
            levelsGained++;
            required = requiredXpForLevel(level);
        }
        progress.setXp(newXp);
        if (levelsGained > 0) {
            progress.setLevel(level);
            progress.setSkillPoints(progress.skillPoints() + levelsGained);
            JobDefinition def = jobs.get(jobId);
            String jobName = def != null ? def.displayName() : jobId;
            player.sendMessage(Component.text(jobName + " 레벨이 " + level + "(으)로 올랐습니다! (스킬 포인트 +" + levelsGained + ")", NamedTextColor.GOLD));
        }

        executor.execute(() -> {
            try {
                repository.saveProgress(player.getUniqueId(), jobId, progress);
            } catch (SQLException e) {
                plugin.getLogger().severe("직업 경험치 저장 실패 (" + player.getUniqueId() + "/" + jobId + "): " + e.getMessage());
            }
        });
    }

    /** Rolls the active job's EXTRA_YIELD_CHANCE_PERCENT skill for {@code jobId}; true means the caller should grant a bonus drop. */
    public boolean rollExtraYield(Player player, String jobId) {
        PlayerJobData data = cache.get(player.getUniqueId());
        if (data == null || !jobId.equals(data.activeJob())) {
            return false;
        }
        double chance = skillBonusPercent(data.progress(jobId), SkillEffectType.EXTRA_YIELD_CHANCE_PERCENT);
        return chance > 0 && ThreadLocalRandom.current().nextDouble(100) < chance;
    }

    private double skillBonusPercent(PlayerJobData.JobProgress progress, SkillEffectType type) {
        double total = 0;
        for (SkillNode node : skills.values()) {
            if (node.effectType() != type) {
                continue;
            }
            total += node.valueFor(progress.allocatedPoints(node.id()));
        }
        return total;
    }

    public enum AllocateResult { SUCCESS, NO_POINTS, MAX_POINTS, UNKNOWN_SKILL }

    public AllocateResult allocateSkillPoint(Player player, String jobId, String skillId) {
        SkillNode node = skills.get(skillId);
        if (node == null) {
            return AllocateResult.UNKNOWN_SKILL;
        }
        PlayerJobData data = cache.get(player.getUniqueId());
        if (data == null) {
            return AllocateResult.NO_POINTS;
        }
        PlayerJobData.JobProgress progress = data.progress(jobId);
        if (progress.skillPoints() <= 0) {
            return AllocateResult.NO_POINTS;
        }
        int current = progress.allocatedPoints(skillId);
        if (current >= node.maxPoints()) {
            return AllocateResult.MAX_POINTS;
        }
        int updated = current + 1;
        progress.setAllocatedPoints(skillId, updated);
        progress.setSkillPoints(progress.skillPoints() - 1);

        executor.execute(() -> {
            try {
                repository.saveSkillPoint(player.getUniqueId(), jobId, skillId, updated);
                repository.saveProgress(player.getUniqueId(), jobId, progress);
            } catch (SQLException e) {
                plugin.getLogger().severe("스킬 포인트 저장 실패 (" + player.getUniqueId() + "/" + jobId + "/" + skillId + "): " + e.getMessage());
            }
        });
        return AllocateResult.SUCCESS;
    }
}
