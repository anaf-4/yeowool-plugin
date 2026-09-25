package com.yeowool.federation;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.federation.land.LandInfo;
import com.yeowool.federation.land.LandLookup;
import com.yeowool.federation.land.PlayerFederationResolver;
import com.yeowool.federation.shop.FederationLevelCache;
import com.yeowool.federation.shop.FederationShop;
import com.yeowool.federation.shop.FederationShopGui;
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

    /** Matches the {@code yw_federations.name} column's {@code VARCHAR(32)} limit. */
    private static final int NAME_MAX_LENGTH = 32;

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final FederationManager manager;
    private final LandLookup landLookup;
    private final PlayerFederationResolver resolver;
    private final FederationLevelCache levelCache;
    private final List<FederationShop> shops;
    private final ExecutorService executor;

    public FederationCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, FederationManager manager,
                              LandLookup landLookup, PlayerFederationResolver resolver, FederationLevelCache levelCache,
                              List<FederationShop> shops, ExecutorService executor) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.manager = manager;
        this.landLookup = landLookup;
        this.resolver = resolver;
        this.levelCache = levelCache;
        this.shops = shops;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "federation.player-only");
            return true;
        }
        if (args.length == 0) {
            messages.send(player, "federation.usage");
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
            case "은행" -> handleBank(player, args);
            case "업그레이드" -> handleUpgrade(player);
            case "상점" -> handleShop(player);
            default -> messages.send(player, "federation.usage");
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length != 2) {
            messages.send(player, "federation.usage");
            return;
        }
        String name = args[1];
        if (name.length() > NAME_MAX_LENGTH) {
            messages.send(player, "federation.name-too-long");
            return;
        }
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.no-land"));
                    return;
                }
                var result = manager.create(land.get().id(), name);
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> messages.send(player, "federation.create-success", Placeholder.unparsed("name", name));
                        case LAND_ALREADY_IN_FEDERATION -> messages.send(player, "federation.already-in-federation");
                        case NAME_TAKEN -> messages.send(player, "federation.name-taken", Placeholder.unparsed("name", name));
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 생성 실패", e);
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
                    runOnMain(() -> messages.send(player, "federation.info-not-found"));
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
                FederationProgress progress = manager.progressOf(f.id()).orElse(new FederationProgress(0, 0));
                FederationLevelConfig levelConfig = manager.levelConfig();
                String progressLine = "\n§7은행: §e" + formatAmount(progress.bankBalance()) + "온"
                        + " §7· 활동량: §e" + formatAmount(progress.activity())
                        + "§7/" + formatAmount(levelConfig.activityForNextLevel(f.level()))
                        + " §7· 다음 레벨 비용: §e" + formatAmount(levelConfig.costForNextLevel(f.level())) + "온"
                        + " §7· 마을: §e" + members.size() + "§7/" + levelConfig.memberCap(f.level());
                runOnMain(() -> player.sendMessage("§6[" + f.name() + "] §7Lv." + f.level() + " · 소개: " + description + progressLine + memberLines));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 정보 조회 실패", e);
            }
        });
    }

    private void handleList(Player player) {
        executor.execute(() -> {
            try {
                List<Federation> federations = manager.listAll();
                if (federations.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.list-empty"));
                    return;
                }
                runOnMain(() -> messages.send(player, "federation.list-header"));
                for (Federation f : federations) {
                    int memberCount;
                    try {
                        memberCount = manager.membersOf(f.id()).size();
                    } catch (java.sql.SQLException e) {
                        memberCount = 0;
                    }
                    int finalMemberCount = memberCount;
                    runOnMain(() -> messages.send(player, "federation.list-line",
                            Placeholder.unparsed("name", f.name()),
                            Placeholder.unparsed("level", String.valueOf(f.level())),
                            Placeholder.unparsed("count", String.valueOf(finalMemberCount))));
                }
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 목록 조회 실패", e);
            }
        });
    }

    private void handleApply(Player player, String[] args) {
        if (args.length != 2) {
            messages.send(player, "federation.usage");
            return;
        }
        String federationName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.no-land"));
                    return;
                }
                var result = manager.applyToJoin(land.get().id(), federationName);
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> messages.send(player, "federation.apply-success", Placeholder.unparsed("name", federationName));
                        case FEDERATION_NOT_FOUND -> messages.send(player, "federation.not-found", Placeholder.unparsed("name", federationName));
                        case ALREADY_MEMBER -> messages.send(player, "federation.already-in-federation");
                        case ALREADY_APPLIED -> messages.send(player, "federation.already-applied");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 가입 신청 실패", e);
            }
        });
    }

    private void handleApplicationList(Player player) {
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.no-land"));
                    return;
                }
                List<java.util.UUID> applicantLandIds;
                try {
                    applicantLandIds = manager.listApplicants(land.get().id());
                } catch (IllegalStateException e) {
                    runOnMain(() -> messages.send(player, "federation.not-your-federation"));
                    return;
                }
                if (applicantLandIds.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.no-applications"));
                    return;
                }
                runOnMain(() -> messages.send(player, "federation.application-list-header"));
                for (java.util.UUID applicantLandId : applicantLandIds) {
                    Optional<LandInfo> applicantLand = landLookup.findById(applicantLandId);
                    String landName = applicantLand.map(LandInfo::landName).orElse("?");
                    runOnMain(() -> messages.send(player, "federation.application-list-line", Placeholder.unparsed("land", landName)));
                }
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 신청 목록 조회 실패", e);
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
            messages.send(player, "federation.usage");
            return;
        }
        String targetLandOwnerName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> actingLand = landLookup.findByOwnerUuid(player.getUniqueId());
                Optional<LandInfo> targetLand = landLookup.findByOwnerUsername(targetLandOwnerName);
                if (actingLand.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.no-land"));
                    return;
                }
                if (targetLand.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.target-no-land"));
                    return;
                }
                var result = approve
                        ? manager.approve(actingLand.get().id(), targetLand.get().id())
                        : manager.reject(actingLand.get().id(), targetLand.get().id());
                String landName = targetLand.get().landName();
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> messages.send(player, approve ? "federation.accept-success" : "federation.reject-success", Placeholder.unparsed("land", landName));
                        case NOT_AUTHORIZED -> messages.send(player, "federation.not-your-federation");
                        case APPLICATION_NOT_FOUND -> messages.send(player, "federation.application-not-found");
                        case ALREADY_MEMBER -> messages.send(player, "federation.already-in-federation");
                        case MEMBER_CAP_REACHED -> messages.send(player, "federation.member-cap-reached");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 가입 승인/거절 실패", e);
            }
        });
    }

    private void handleLeave(Player player) {
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.no-land"));
                    return;
                }
                var result = manager.leave(land.get().id());
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> messages.send(player, "federation.leave-success");
                        case NOT_A_MEMBER -> messages.send(player, "federation.not-a-member");
                        case LEADER_MUST_TRANSFER_FIRST -> messages.send(player, "federation.leader-must-transfer-first");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 탈퇴 실패", e);
            }
        });
    }

    private void handleKick(Player player, String[] args) {
        if (args.length != 2) {
            messages.send(player, "federation.usage");
            return;
        }
        String targetName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> actingLand = landLookup.findByOwnerUuid(player.getUniqueId());
                Optional<LandInfo> targetLand = landLookup.findByOwnerUsername(targetName);
                if (actingLand.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.no-land"));
                    return;
                }
                if (targetLand.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.target-no-land"));
                    return;
                }
                var result = manager.kick(actingLand.get().id(), targetLand.get().id());
                String landName = targetLand.get().landName();
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> messages.send(player, "federation.kick-success", Placeholder.unparsed("land", landName));
                        case NOT_AUTHORIZED -> messages.send(player, "federation.not-a-member");
                        case TARGET_NOT_MEMBER -> messages.send(player, "federation.target-not-member");
                        case TARGET_NOT_KICKABLE -> messages.send(player, "federation.cannot-kick-deputy");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 추방 실패", e);
            }
        });
    }

    private void handleAppointDeputy(Player player, String[] args) {
        if (args.length != 2) {
            messages.send(player, "federation.usage");
            return;
        }
        String targetName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> actingLand = landLookup.findByOwnerUuid(player.getUniqueId());
                Optional<LandInfo> targetLand = landLookup.findByOwnerUsername(targetName);
                if (actingLand.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.no-land"));
                    return;
                }
                if (targetLand.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.target-no-land"));
                    return;
                }
                var result = manager.appointDeputy(actingLand.get().id(), targetLand.get().id());
                String landName = targetLand.get().landName();
                Optional<Federation> federation = manager.findByLandId(actingLand.get().id());
                int level = federation.map(Federation::level).orElse(1);
                int cap = FederationRules.deputyCap(level);
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> messages.send(player, "federation.appoint-deputy-success", Placeholder.unparsed("land", landName));
                        case NOT_LEADER -> messages.send(player, "federation.leader-only");
                        case TARGET_NOT_MEMBER -> messages.send(player, "federation.target-not-member");
                        case ALREADY_DEPUTY -> messages.send(player, "federation.already-deputy");
                        case TARGET_IS_LEADER -> messages.send(player, "federation.target-is-leader");
                        case CAP_REACHED -> messages.send(player, "federation.deputy-cap-reached",
                                Placeholder.unparsed("level", String.valueOf(level)), Placeholder.unparsed("cap", String.valueOf(cap)));
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "부연합장 임명 실패", e);
            }
        });
    }

    private void handleDismissDeputy(Player player, String[] args) {
        if (args.length != 2) {
            messages.send(player, "federation.usage");
            return;
        }
        String targetName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> actingLand = landLookup.findByOwnerUuid(player.getUniqueId());
                Optional<LandInfo> targetLand = landLookup.findByOwnerUsername(targetName);
                if (actingLand.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.no-land"));
                    return;
                }
                if (targetLand.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.target-no-land"));
                    return;
                }
                var result = manager.dismissDeputy(actingLand.get().id(), targetLand.get().id());
                String landName = targetLand.get().landName();
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> messages.send(player, "federation.dismiss-deputy-success", Placeholder.unparsed("land", landName));
                        case NOT_LEADER -> messages.send(player, "federation.leader-only");
                        case TARGET_NOT_DEPUTY -> messages.send(player, "federation.not-deputy");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "부연합장 해임 실패", e);
            }
        });
    }

    private void handleTransfer(Player player, String[] args) {
        if (args.length != 2) {
            messages.send(player, "federation.usage");
            return;
        }
        String targetName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> actingLand = landLookup.findByOwnerUuid(player.getUniqueId());
                Optional<LandInfo> targetLand = landLookup.findByOwnerUsername(targetName);
                if (actingLand.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.no-land"));
                    return;
                }
                if (targetLand.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.target-no-land"));
                    return;
                }
                var result = manager.transferLeadership(actingLand.get().id(), targetLand.get().id());
                String landName = targetLand.get().landName();
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> messages.send(player, "federation.transfer-success", Placeholder.unparsed("land", landName));
                        case NOT_LEADER -> messages.send(player, "federation.leader-only");
                        case TARGET_NOT_MEMBER -> messages.send(player, "federation.transfer-target-not-member");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합장 위임 실패", e);
            }
        });
    }

    private void handleDisband(Player player) {
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.no-land"));
                    return;
                }
                Optional<Federation> federation = manager.findByLandId(land.get().id());
                if (federation.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.not-a-member"));
                    return;
                }
                if (!federation.get().leaderLandId().equals(land.get().id())) {
                    runOnMain(() -> messages.send(player, "federation.leader-only"));
                    return;
                }
                String federationName = federation.get().name();
                UUID landId = land.get().id();
                runOnMain(() -> new FederationDisbandConfirmGui(plugin, messages, manager, executor, landId, federationName).open(player));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 폐쇄 확인 준비 실패", e);
            }
        });
    }

    private void handleDescription(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "federation.usage");
            return;
        }
        String description = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.no-land"));
                    return;
                }
                var result = manager.updateDescription(land.get().id(), description);
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> messages.send(player, "federation.description-success");
                        case NOT_LEADER -> messages.send(player, "federation.leader-only");
                        case TOO_LONG -> messages.send(player, "federation.description-too-long");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 소개글 수정 실패", e);
            }
        });
    }

    private void handleBank(Player player, String[] args) {
        if (args.length == 1) {
            showBankBalance(player);
            return;
        }
        if (args.length != 3 || (!args[1].equals("입금") && !args[1].equals("출금"))) {
            messages.send(player, "federation.bank-usage");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            messages.send(player, "federation.bank-invalid-amount");
            return;
        }
        if (amount <= 0) {
            messages.send(player, "federation.bank-invalid-amount");
            return;
        }
        if (args[1].equals("입금")) {
            deposit(player, amount);
        } else {
            withdraw(player, amount);
        }
    }

    private void showBankBalance(Player player) {
        executor.execute(() -> {
            try {
                Optional<UUID> federationId = resolver.findFederationId(player.getUniqueId());
                if (federationId.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.no-federation"));
                    return;
                }
                long balance = manager.progressOf(federationId.get()).map(FederationProgress::bankBalance).orElse(0L);
                runOnMain(() -> messages.send(player, "federation.bank-balance",
                        Placeholder.unparsed("amount", formatAmount(balance))));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 은행 조회 실패", e);
            }
        });
    }

    /** Wallet first (main thread), then the DB; a failed DB deposit refunds the wallet. */
    private void deposit(Player player, long amount) {
        executor.execute(() -> {
            try {
                Optional<UUID> federationId = resolver.findFederationId(player.getUniqueId());
                if (federationId.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.no-federation"));
                    return;
                }
                UUID targetFederation = federationId.get();
                runOnMain(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (!core.economyData().modifyBalance(player.getUniqueId(), -amount, "YeowoolFederation", "연합 은행 입금")) {
                        messages.send(player, "federation.bank-insufficient-wallet");
                        return;
                    }
                    executor.execute(() -> {
                        boolean deposited;
                        try {
                            deposited = manager.deposit(targetFederation, amount);
                        } catch (java.sql.SQLException e) {
                            plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 은행 입금 실패", e);
                            deposited = false;
                        }
                        boolean success = deposited;
                        runOnMain(() -> {
                            if (success) {
                                messages.send(player, "federation.bank-deposit-success",
                                        Placeholder.unparsed("amount", formatAmount(amount)));
                            } else if (player.isOnline()) {
                                core.economyData().modifyBalance(player.getUniqueId(), amount, "YeowoolFederation", "연합 은행 입금 실패 환불");
                                messages.send(player, "federation.bank-deposit-failed");
                            } else {
                                plugin.getLogger().severe("연합 은행 입금 실패 — 플레이어가 서버를 떠나 환불하지 못했습니다. 수동 환불 필요: "
                                        + player.getUniqueId() + " / " + amount + "온");
                            }
                        });
                    });
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 은행 입금 준비 실패", e);
            }
        });
    }

    /** DB first (atomic "subtract only if enough"), wallet only after it succeeded. */
    private void withdraw(Player player, long amount) {
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.leader-only"));
                    return;
                }
                Optional<Federation> federation = manager.findByLandId(land.get().id());
                if (federation.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.leader-only"));
                    return;
                }
                UUID federationId = federation.get().id();
                var result = manager.withdraw(land.get().id(), amount);
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> {
                            if (player.isOnline()) {
                                core.economyData().modifyBalance(player.getUniqueId(), amount, "YeowoolFederation", "연합 은행 출금");
                                messages.send(player, "federation.bank-withdraw-success",
                                        Placeholder.unparsed("amount", formatAmount(amount)));
                            } else {
                                executor.execute(() -> {
                                    try {
                                        manager.deposit(federationId, amount);
                                    } catch (java.sql.SQLException e) {
                                        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                                "연합 은행 출금 복구 실패 — 수동 복구 필요: " + federationId + " / " + amount + "온", e);
                                    }
                                });
                                plugin.getLogger().warning("연합 은행 출금 후 플레이어가 서버를 떠나 금액을 연합 은행으로 되돌렸습니다: "
                                        + player.getUniqueId() + " / " + amount + "온");
                            }
                        }
                        case NOT_LEADER -> messages.send(player, "federation.leader-only");
                        case INSUFFICIENT_BANK -> messages.send(player, "federation.bank-insufficient-bank");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 은행 출금 실패", e);
            }
        });
    }

    private void handleUpgrade(Player player) {
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.leader-only"));
                    return;
                }
                var outcome = manager.upgrade(land.get().id());
                int cap = manager.levelConfig().memberCap(outcome.level());
                runOnMain(() -> {
                    switch (outcome.result()) {
                        case SUCCESS -> messages.send(player, "federation.upgrade-success",
                                Placeholder.unparsed("level", String.valueOf(outcome.level())),
                                Placeholder.unparsed("cap", String.valueOf(cap)));
                        case NOT_LEADER -> messages.send(player, "federation.leader-only");
                        case NOT_ENOUGH_ACTIVITY -> messages.send(player, "federation.upgrade-not-enough-activity",
                                Placeholder.unparsed("current", formatAmount(outcome.activity())),
                                Placeholder.unparsed("required", formatAmount(outcome.requiredActivity())));
                        case NOT_ENOUGH_BANK -> messages.send(player, "federation.upgrade-not-enough-bank",
                                Placeholder.unparsed("cost", formatAmount(outcome.cost())),
                                Placeholder.unparsed("bank", formatAmount(outcome.bank())));
                        case CHANGED -> messages.send(player, "federation.upgrade-changed");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 업그레이드 실패", e);
            }
        });
    }

    private void handleShop(Player player) {
        if (shops.isEmpty()) {
            messages.send(player, "federation.shop-none");
            return;
        }
        executor.execute(() -> {
            try {
                int level = levelCache.lookupLevel(player.getUniqueId());
                runOnMain(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    levelCache.put(player.getUniqueId(), level);
                    if (level == 0) {
                        messages.send(player, "federation.no-federation");
                        return;
                    }
                    new FederationShopGui(shops, level).open(player);
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 상점 열기 실패", e);
            }
        });
    }

    private static String formatAmount(long amount) {
        return String.format("%,d", amount);
    }

    private void runOnMain(Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
    }
}
