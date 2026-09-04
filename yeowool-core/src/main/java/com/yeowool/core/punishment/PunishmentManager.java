package com.yeowool.core.punishment;

import com.yeowool.core.api.model.PunishmentEntry;
import com.yeowool.core.api.model.PunishmentType;
import com.yeowool.core.api.service.PunishmentService;
import com.yeowool.core.data.repository.PunishmentRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public final class PunishmentManager implements PunishmentService {

    private final JavaPlugin plugin;
    private final PunishmentRepository repository;
    private final ExecutorService executor;

    public PunishmentManager(JavaPlugin plugin, PunishmentRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Void> record(UUID target, PunishmentType type, String reason, UUID staff, Long expiresAt) {
        return CompletableFuture.runAsync(() -> {
            try {
                repository.insert(target, type, reason, staff, System.currentTimeMillis(), expiresAt);
            } catch (SQLException e) {
                plugin.getLogger().severe("제재 기록 실패 (" + target + "/" + type + "): " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<PunishmentEntry>> activeBan(UUID target) {
        return findActive(target, PunishmentType.BAN);
    }

    @Override
    public CompletableFuture<Optional<PunishmentEntry>> activeMute(UUID target) {
        return findActive(target, PunishmentType.MUTE);
    }

    private CompletableFuture<Optional<PunishmentEntry>> findActive(UUID target, PunishmentType type) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.findActive(target, type);
            } catch (SQLException e) {
                plugin.getLogger().severe("제재 조회 실패 (" + target + "/" + type + "): " + e.getMessage());
                return Optional.<PunishmentEntry>empty();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<PunishmentEntry>> history(UUID target, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.history(target, limit);
            } catch (SQLException e) {
                plugin.getLogger().severe("제재 이력 조회 실패 (" + target + "): " + e.getMessage());
                return List.<PunishmentEntry>of();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> revoke(UUID target, PunishmentType type, UUID staff) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.revokeActive(target, type, staff);
            } catch (SQLException e) {
                plugin.getLogger().severe("제재 해제 실패 (" + target + "/" + type + "): " + e.getMessage());
                return 0;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> recordWarning(UUID target, String reason, UUID staff, int points, Long expiresAt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                repository.insertWarning(target, reason, staff, System.currentTimeMillis(), points, expiresAt);
                return repository.sumWarningPoints(target);
            } catch (SQLException e) {
                plugin.getLogger().severe("경고 기록 실패 (" + target + "): " + e.getMessage());
                return 0;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> totalWarningPoints(UUID target) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.sumWarningPoints(target);
            } catch (SQLException e) {
                plugin.getLogger().severe("경고 합계 조회 실패 (" + target + "): " + e.getMessage());
                return 0;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<PunishmentEntry>> findActiveByReasonPrefix(PunishmentType type, String reasonPrefix) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.findActiveByReasonPrefix(type, reasonPrefix);
            } catch (SQLException e) {
                plugin.getLogger().severe("제재 조회 실패 (" + type + "/" + reasonPrefix + "): " + e.getMessage());
                return List.<PunishmentEntry>of();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Map<UUID, Integer>> totalWarningPointsForTargets(Collection<UUID> targets) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.sumWarningPointsForTargets(targets);
            } catch (SQLException e) {
                plugin.getLogger().severe("경고 합계 일괄 조회 실패: " + e.getMessage());
                return Map.<UUID, Integer>of();
            }
        }, executor);
    }
}
