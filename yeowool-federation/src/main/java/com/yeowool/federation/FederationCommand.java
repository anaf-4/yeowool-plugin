package com.yeowool.federation;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.federation.land.LandInfo;
import com.yeowool.federation.land.LandLookup;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/** {@code /연합 <하위명령어>} — see messages.yml's federation.usage for the full subcommand list. */
public final class FederationCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final FederationManager manager;
    private final LandLookup landLookup;
    private final ExecutorService executor;

    public FederationCommand(JavaPlugin plugin, YeowoolCoreAPI core, FederationManager manager,
                              LandLookup landLookup, ExecutorService executor) {
        this.plugin = plugin;
        this.core = core;
        this.manager = manager;
        this.landLookup = landLookup;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            core.messages().send(sender, "federation.player-only");
            return true;
        }
        if (args.length == 0) {
            core.messages().send(player, "federation.usage");
            return true;
        }

        switch (args[0]) {
            case "생성" -> handleCreate(player, args);
            case "정보" -> handleInfo(player, args);
            case "목록" -> handleList(player);
            case "가입신청" -> handleApply(player, args);
            case "신청목록" -> handleApplicationList(player);
            case "수락" -> handleApprove(player, args);
            case "거절" -> handleReject(player, args);
            case "탈퇴" -> handleLeave(player);
            case "추방" -> handleKick(player, args);
            case "부연합장임명" -> handleAppointDeputy(player, args);
            case "부연합장해임" -> handleDismissDeputy(player, args);
            case "위임" -> handleTransfer(player, args);
            case "폐쇄" -> handleDisband(player);
            case "소개글" -> handleDescription(player, args);
            default -> core.messages().send(player, "federation.usage");
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length != 2) {
            core.messages().send(player, "federation.usage");
            return;
        }
        String name = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                var result = manager.create(land.get().id(), name);
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, "federation.create-success", Placeholder.unparsed("name", name));
                        case LAND_ALREADY_IN_FEDERATION -> core.messages().send(player, "federation.already-in-federation");
                        case NAME_TAKEN -> core.messages().send(player, "federation.name-taken", Placeholder.unparsed("name", name));
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 생성 실패: " + e.getMessage());
            }
        });
    }

    private void handleInfo(Player player, String[] args) {
        executor.execute(() -> {
            try {
                Optional<Federation> federation;
                if (args.length >= 2) {
                    federation = manager.findByName(args[1]);
                } else {
                    Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                    federation = land.isPresent() ? manager.findByLandId(land.get().id()) : Optional.empty();
                }
                if (federation.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.info-not-found"));
                    return;
                }
                Federation f = federation.get();
                List<FederationMember> members = manager.membersOf(f.id());
                StringBuilder memberLines = new StringBuilder();
                for (FederationMember member : members) {
                    Optional<LandInfo> landInfo = landLookup.findById(member.landId());
                    String landName = landInfo.map(LandInfo::landName).orElse("?");
                    memberLines.append("\n§7- ").append(landName).append(" (").append(member.role()).append(")");
                }
                String description = f.description() == null ? "(없음)" : f.description();
                runOnMain(() -> player.sendMessage("§6[" + f.name() + "] §7Lv." + f.level() + " · 소개: " + description + memberLines));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 정보 조회 실패: " + e.getMessage());
            }
        });
    }

    private void handleList(Player player) {
        executor.execute(() -> {
            try {
                List<Federation> federations = manager.listAll();
                if (federations.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.list-empty"));
                    return;
                }
                runOnMain(() -> core.messages().send(player, "federation.list-header"));
                for (Federation f : federations) {
                    int memberCount;
                    try {
                        memberCount = manager.membersOf(f.id()).size();
                    } catch (java.sql.SQLException e) {
                        memberCount = 0;
                    }
                    int finalMemberCount = memberCount;
                    runOnMain(() -> core.messages().send(player, "federation.list-line",
                            Placeholder.unparsed("name", f.name()),
                            Placeholder.unparsed("level", String.valueOf(f.level())),
                            Placeholder.unparsed("count", String.valueOf(finalMemberCount))));
                }
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 목록 조회 실패: " + e.getMessage());
            }
        });
    }

    private void handleApply(Player player, String[] args) {
        if (args.length != 2) {
            core.messages().send(player, "federation.usage");
            return;
        }
        String federationName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                var result = manager.applyToJoin(land.get().id(), federationName);
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, "federation.apply-success", Placeholder.unparsed("name", federationName));
                        case FEDERATION_NOT_FOUND -> core.messages().send(player, "federation.not-found", Placeholder.unparsed("name", federationName));
                        case ALREADY_MEMBER -> core.messages().send(player, "federation.already-member");
                        case ALREADY_APPLIED -> core.messages().send(player, "federation.already-applied");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 가입 신청 실패: " + e.getMessage());
            }
        });
    }

    private void handleApplicationList(Player player) {
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                List<java.util.UUID> applicantLandIds;
                try {
                    applicantLandIds = manager.listApplicants(land.get().id());
                } catch (IllegalStateException e) {
                    runOnMain(() -> core.messages().send(player, "federation.not-your-federation"));
                    return;
                }
                if (applicantLandIds.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-applications"));
                    return;
                }
                runOnMain(() -> core.messages().send(player, "federation.application-list-header"));
                for (java.util.UUID applicantLandId : applicantLandIds) {
                    Optional<LandInfo> applicantLand = landLookup.findById(applicantLandId);
                    String landName = applicantLand.map(LandInfo::landName).orElse("?");
                    runOnMain(() -> core.messages().send(player, "federation.application-list-line", Placeholder.unparsed("land", landName)));
                }
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 신청 목록 조회 실패: " + e.getMessage());
            }
        });
    }

    private void handleApprove(Player player, String[] args) {
        handleApprovalDecision(player, args, true);
    }

    private void handleReject(Player player, String[] args) {
        handleApprovalDecision(player, args, false);
    }

    private void handleApprovalDecision(Player player, String[] args, boolean approve) {
        if (args.length != 2) {
            core.messages().send(player, "federation.usage");
            return;
        }
        String targetLandOwnerName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> actingLand = landLookup.findByOwnerUuid(player.getUniqueId());
                Optional<LandInfo> targetLand = landLookup.findByOwnerUsername(targetLandOwnerName);
                if (actingLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                if (targetLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.target-no-land"));
                    return;
                }
                var result = approve
                        ? manager.approve(actingLand.get().id(), targetLand.get().id())
                        : manager.reject(actingLand.get().id(), targetLand.get().id());
                String landName = targetLand.get().landName();
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, approve ? "federation.accept-success" : "federation.reject-success", Placeholder.unparsed("land", landName));
                        case NOT_AUTHORIZED -> core.messages().send(player, "federation.not-your-federation");
                        case APPLICATION_NOT_FOUND -> core.messages().send(player, "federation.application-not-found");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 가입 승인/거절 실패: " + e.getMessage());
            }
        });
    }

    private void handleLeave(Player player) {
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                var result = manager.leave(land.get().id());
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, "federation.leave-success");
                        case NOT_A_MEMBER -> core.messages().send(player, "federation.not-your-federation");
                        case LEADER_MUST_TRANSFER_FIRST -> core.messages().send(player, "federation.leader-must-transfer-first");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 탈퇴 실패: " + e.getMessage());
            }
        });
    }

    private void handleKick(Player player, String[] args) {
        if (args.length != 2) {
            core.messages().send(player, "federation.usage");
            return;
        }
        String targetName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> actingLand = landLookup.findByOwnerUuid(player.getUniqueId());
                Optional<LandInfo> targetLand = landLookup.findByOwnerUsername(targetName);
                if (actingLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                if (targetLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.target-no-land"));
                    return;
                }
                var result = manager.kick(actingLand.get().id(), targetLand.get().id());
                String landName = targetLand.get().landName();
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, "federation.kick-success", Placeholder.unparsed("land", landName));
                        case NOT_AUTHORIZED -> core.messages().send(player, "federation.not-your-federation");
                        case TARGET_NOT_MEMBER -> core.messages().send(player, "federation.target-not-member");
                        case TARGET_NOT_KICKABLE -> core.messages().send(player, "federation.cannot-kick-deputy");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 추방 실패: " + e.getMessage());
            }
        });
    }

    private void handleAppointDeputy(Player player, String[] args) {
        if (args.length != 2) {
            core.messages().send(player, "federation.usage");
            return;
        }
        String targetName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> actingLand = landLookup.findByOwnerUuid(player.getUniqueId());
                Optional<LandInfo> targetLand = landLookup.findByOwnerUsername(targetName);
                if (actingLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                if (targetLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.target-no-land"));
                    return;
                }
                var result = manager.appointDeputy(actingLand.get().id(), targetLand.get().id());
                String landName = targetLand.get().landName();
                Optional<Federation> federation = manager.findByLandId(actingLand.get().id());
                int level = federation.map(Federation::level).orElse(1);
                int cap = FederationRules.deputyCap(level);
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, "federation.appoint-deputy-success", Placeholder.unparsed("land", landName));
                        case NOT_LEADER -> core.messages().send(player, "federation.leader-only");
                        case TARGET_NOT_MEMBER -> core.messages().send(player, "federation.target-not-member");
                        case ALREADY_DEPUTY -> core.messages().send(player, "federation.already-deputy");
                        case CAP_REACHED -> core.messages().send(player, "federation.deputy-cap-reached",
                                Placeholder.unparsed("level", String.valueOf(level)), Placeholder.unparsed("cap", String.valueOf(cap)));
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("부연합장 임명 실패: " + e.getMessage());
            }
        });
    }

    private void handleDismissDeputy(Player player, String[] args) {
        if (args.length != 2) {
            core.messages().send(player, "federation.usage");
            return;
        }
        String targetName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> actingLand = landLookup.findByOwnerUuid(player.getUniqueId());
                Optional<LandInfo> targetLand = landLookup.findByOwnerUsername(targetName);
                if (actingLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                if (targetLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.target-no-land"));
                    return;
                }
                var result = manager.dismissDeputy(actingLand.get().id(), targetLand.get().id());
                String landName = targetLand.get().landName();
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, "federation.dismiss-deputy-success", Placeholder.unparsed("land", landName));
                        case NOT_LEADER -> core.messages().send(player, "federation.leader-only");
                        case TARGET_NOT_DEPUTY -> core.messages().send(player, "federation.not-deputy");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("부연합장 해임 실패: " + e.getMessage());
            }
        });
    }

    private void handleTransfer(Player player, String[] args) {
        if (args.length != 2) {
            core.messages().send(player, "federation.usage");
            return;
        }
        String targetName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> actingLand = landLookup.findByOwnerUuid(player.getUniqueId());
                Optional<LandInfo> targetLand = landLookup.findByOwnerUsername(targetName);
                if (actingLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                if (targetLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.target-no-land"));
                    return;
                }
                var result = manager.transferLeadership(actingLand.get().id(), targetLand.get().id());
                String landName = targetLand.get().landName();
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, "federation.transfer-success", Placeholder.unparsed("land", landName));
                        case NOT_LEADER -> core.messages().send(player, "federation.leader-only");
                        case TARGET_NOT_MEMBER -> core.messages().send(player, "federation.transfer-target-not-member");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합장 위임 실패: " + e.getMessage());
            }
        });
    }

    private void handleDisband(Player player) {
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                Optional<Federation> federation = manager.findByLandId(land.get().id());
                if (federation.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.not-your-federation"));
                    return;
                }
                if (!federation.get().leaderLandId().equals(land.get().id())) {
                    runOnMain(() -> core.messages().send(player, "federation.leader-only"));
                    return;
                }
                String federationName = federation.get().name();
                UUID landId = land.get().id();
                runOnMain(() -> new FederationDisbandConfirmGui(plugin, core, manager, executor, landId, federationName).open(player));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 폐쇄 확인 준비 실패: " + e.getMessage());
            }
        });
    }

    private void handleDescription(Player player, String[] args) {
        if (args.length < 2) {
            core.messages().send(player, "federation.usage");
            return;
        }
        String description = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                var result = manager.updateDescription(land.get().id(), description);
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, "federation.description-success");
                        case NOT_LEADER -> core.messages().send(player, "federation.leader-only");
                        case TOO_LONG -> core.messages().send(player, "federation.description-too-long");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 소개글 수정 실패: " + e.getMessage());
            }
        });
    }

    private void runOnMain(Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
    }
}
