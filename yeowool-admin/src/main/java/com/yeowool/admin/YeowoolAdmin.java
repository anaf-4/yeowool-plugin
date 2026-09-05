package com.yeowool.admin;

import com.yeowool.admin.antiexploit.AntiExploitListener;
import com.yeowool.admin.antiexploit.DuplicationScanTask;
import com.yeowool.admin.antiexploit.MacroDetectionListener;
import com.yeowool.admin.backup.BackupService;
import com.yeowool.admin.banneditem.BanType;
import com.yeowool.admin.banneditem.BannedItemCommand;
import com.yeowool.admin.banneditem.BannedItemListener;
import com.yeowool.admin.banneditem.BannedItemManager;
import com.yeowool.admin.banneditem.BannedItemRepository;
import com.yeowool.admin.banneditem.database.BannedItemSchemaInitializer;
import com.yeowool.admin.catalog.AdminItemCatalogCommand;
import com.yeowool.admin.catalog.CatalogConfigLoader;
import com.yeowool.admin.check.CheckCommand;
import com.yeowool.admin.check.CheckRedeemListener;
import com.yeowool.admin.command.AdminCommand;
import com.yeowool.admin.command.CashGrantCommand;
import com.yeowool.admin.coupon.CouponCommand;
import com.yeowool.admin.coupon.CouponCreateCommand;
import com.yeowool.admin.coupon.CouponManageCommand;
import com.yeowool.admin.coupon.CouponManager;
import com.yeowool.admin.coupon.CouponRedeemListener;
import com.yeowool.admin.coupon.database.CouponSchemaInitializer;
import com.yeowool.admin.coupon.repository.CouponRepository;
import com.yeowool.admin.itemtool.BoundCommand;
import com.yeowool.admin.itemtool.BoundItemProtectionListener;
import com.yeowool.admin.itemtool.ItemCopyCommand;
import com.yeowool.admin.itemtool.ItemDurabilityCommand;
import com.yeowool.admin.itemtool.ItemFlags;
import com.yeowool.admin.itemtool.ItemLoreCommand;
import com.yeowool.admin.itemtool.ItemNameCommand;
import com.yeowool.admin.itemtool.TimedItemCommand;
import com.yeowool.admin.itemtool.TimedItemTask;
import com.yeowool.admin.logviewer.LogQueryService;
import com.yeowool.admin.npctag.NpcTagCommand;
import com.yeowool.admin.raffle.RaffleCommand;
import com.yeowool.admin.raffle.RaffleManager;
import com.yeowool.admin.raffle.RaffleRepository;
import com.yeowool.admin.raffle.database.RaffleSchemaInitializer;
import com.yeowool.admin.moderation.BanCommand;
import com.yeowool.admin.moderation.KickCommand;
import com.yeowool.admin.moderation.MuteCommand;
import com.yeowool.admin.moderation.PunishmentHistoryCommand;
import com.yeowool.admin.moderation.UnbanCommand;
import com.yeowool.admin.moderation.UnmuteCommand;
import com.yeowool.admin.moderation.WarnAutoBanReviewTask;
import com.yeowool.admin.moderation.WarnCommand;
import com.yeowool.admin.report.ReportCommand;
import com.yeowool.admin.mining.OreSummonCommand;
import com.yeowool.admin.restart.RestartScheduleCommand;
import com.yeowool.admin.restart.RestartScheduleRepository;
import com.yeowool.admin.restart.RestartScheduleSchemaInitializer;
import com.yeowool.admin.restart.RestartScheduleStore;
import com.yeowool.admin.restart.ScheduledRestartTask;
import com.yeowool.admin.starterkit.StarterKitJoinListener;
import com.yeowool.admin.starterkit.StarterKitRepository;
import com.yeowool.admin.starterkit.StarterKitSchemaInitializer;
import com.yeowool.admin.starterkit.StarterKitService;
import com.yeowool.admin.update.UpdateNoticeListener;
import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.CurrencyType;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.message.MessageManager;
import com.yeowool.core.util.ConfigMerger;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 운영 및 관리 (기획서 11.1/11.3/11.4/11.5, 5절 첫 접속 시스템 포함):
 * 통합 관리 명령어, 백업, 부정거래 탐지, 업데이트 공지, 기본템 GUI 편집,
 * 귀속/기간제 아이템 도구를 하나의 배포 단위로 묶었다.
 */
public final class YeowoolAdmin extends JavaPlugin {

    private ExecutorService executor;
    private MessageService messages;

    @Override
    public void onEnable() {
        YeowoolCoreAPI core = Bukkit.getServicesManager().load(YeowoolCoreAPI.class);
        if (core == null) {
            getLogger().severe("YeowoolCore API를 찾을 수 없습니다. YeowoolCore가 먼저 로드되어야 합니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ConfigMerger.mergeDefaults(this, "config.yml");
        this.messages = new MessageManager(this);
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "YeowoolAdmin-Worker");
            thread.setDaemon(true);
            return thread;
        });

        BackupService backupService = new BackupService(this, core.dataSource());
        LogQueryService logQueryService = new LogQueryService(core.dataSource());

        // 첫 접속 기본템 (기획서 5절)
        StarterKitService starterKitService;
        try {
            StarterKitSchemaInitializer.initialize(core.dataSource());
            StarterKitRepository starterKitRepository = new StarterKitRepository(core.dataSource());
            starterKitService = new StarterKitService(this, starterKitRepository, executor);
            starterKitService.loadIntoCache();
        } catch (Exception e) {
            getLogger().severe("기본템 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(new StarterKitJoinListener(core, starterKitService), this);

        var command = getCommand("여울관리");
        if (command != null) {
            var executorCmd = new AdminCommand(this, core, messages, backupService, starterKitService, logQueryService, executor);
            command.setExecutor(executorCmd);
            command.setTabCompleter(executorCmd);
        }

        long onAlertThreshold = getConfig().getLong("anti-exploit.balance-alert-threshold", 1_000_000L);
        long cashAlertThreshold = getConfig().getLong("anti-exploit.cash-alert-threshold", 50_000L);
        long structuringWindowSeconds = getConfig().getLong("anti-exploit.structuring-window-seconds", 300L);
        getServer().getPluginManager().registerEvents(
                new AntiExploitListener(core, messages, onAlertThreshold, cashAlertThreshold, structuringWindowSeconds), this);
        getServer().getPluginManager().registerEvents(new UpdateNoticeListener(this, core, messages), this);

        DuplicationScanTask duplicationScanTask = new DuplicationScanTask(core, messages);
        getServer().getPluginManager().registerEvents(duplicationScanTask, this);
        long duplicationScanIntervalTicks = getConfig().getLong("anti-exploit.duplication-scan-interval-ticks", 20L * 60 * 10);
        if (duplicationScanIntervalTicks > 0) {
            duplicationScanTask.runTaskTimer(this, duplicationScanIntervalTicks, duplicationScanIntervalTicks);
        }
        getServer().getPluginManager().registerEvents(new MacroDetectionListener(core, messages), this);

        // 귀속/기간제/이름/설명 아이템 도구
        ItemFlags itemFlags = new ItemFlags(this);
        bindCommand("귀속", new BoundCommand(messages, itemFlags));
        var timedCommand = new TimedItemCommand(messages, itemFlags);
        bindCommand("기간제", timedCommand, timedCommand);
        var itemNameCommand = new ItemNameCommand(messages);
        bindCommand("아이템이름", itemNameCommand, itemNameCommand);
        var itemLoreCommand = new ItemLoreCommand(messages, itemFlags);
        bindCommand("아이템설명", itemLoreCommand, itemLoreCommand);
        var itemCopyCommand = new ItemCopyCommand(messages);
        bindCommand("아이템", itemCopyCommand, itemCopyCommand);
        var itemDurabilityCommand = new ItemDurabilityCommand(messages);
        bindCommand("아이템내구도", itemDurabilityCommand, itemDurabilityCommand);
        getServer().getPluginManager().registerEvents(new BoundItemProtectionListener(messages, itemFlags), this);
        new TimedItemTask(messages, itemFlags).runTaskTimer(this, 20L * 5, 20L * 5);

        // 관리자 아이템 카탈로그
        bindCommand("관리자아이템", new AdminItemCatalogCommand(messages, CatalogConfigLoader.load(this)));

        // 플레이어 신고
        var reportCommand = new ReportCommand(core, messages);
        bindCommand("신고", reportCommand, reportCommand);

        // 모더레이션 (경고/추방/정지/음소거)
        int warnAutoBanThreshold = getConfig().getInt("moderation.warning.auto-ban-threshold", 10);
        long warnAutoBanDurationMinutes = getConfig().getLong("moderation.warning.auto-ban-duration-minutes", 1440);
        var warnCommand = new WarnCommand(this, core, messages, warnAutoBanThreshold, warnAutoBanDurationMinutes);
        bindCommand("경고", warnCommand, warnCommand);
        long warnAutoBanReviewIntervalTicks = getConfig().getLong("moderation.warning.auto-ban-review-interval-ticks", 20L * 60 * 5);
        if (warnAutoBanReviewIntervalTicks > 0) {
            new WarnAutoBanReviewTask(this, core, messages, warnAutoBanThreshold)
                    .runTaskTimer(this, warnAutoBanReviewIntervalTicks, warnAutoBanReviewIntervalTicks);
        }
        var kickCommand = new KickCommand(core, messages);
        bindCommand("추방", kickCommand, kickCommand);
        var banCommand = new BanCommand(core, messages);
        bindCommand("정지", banCommand, banCommand);
        bindCommand("정지해제", new UnbanCommand(this, core, messages));
        var muteCommand = new MuteCommand(core, messages);
        bindCommand("음소거", muteCommand, muteCommand);
        bindCommand("음소거해제", new UnmuteCommand(this, core, messages));
        var punishmentHistoryCommand = new PunishmentHistoryCommand(this, core, messages);
        bindCommand("제재기록", punishmentHistoryCommand, punishmentHistoryCommand);


        // 쿠폰 시스템 (모루에 쿠폰 이름을 입력해 보상 수령)
        try {
            CouponSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("쿠폰 시스템 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        CouponRepository couponRepository = new CouponRepository(core.dataSource());
        CouponManager couponManager = new CouponManager(this, couponRepository, executor);
        try {
            couponManager.loadAll();
        } catch (Exception e) {
            getLogger().severe("쿠폰 데이터 로드 실패: " + e.getMessage());
        }
        getServer().getPluginManager().registerEvents(new CouponRedeemListener(this, messages, couponManager), this);
        bindCommand("쿠폰", new CouponCommand(this, messages));
        bindCommand("쿠폰생성", new CouponCreateCommand(messages, couponManager));
        var couponManageCommand = new CouponManageCommand(messages, couponManager);
        bindCommand("쿠폰관리", couponManageCommand, couponManageCommand);

        // 수표 (관리자가 직접 금액을 지정해 지급하는 물리 아이템 - 우클릭 사용)
        getServer().getPluginManager().registerEvents(new CheckRedeemListener(this, core, messages), this);
        bindCommand("수표", new CheckCommand(this, messages, CurrencyType.ON, "수표"));
        bindCommand("유료수표", new CheckCommand(this, messages, CurrencyType.CASH, "유료수표"));

        // 캐시 지급/차감 (콘솔에서도 사용 가능 - 추후 결제 대행사 웹훅을 콘솔 명령으로 연동할 때 이 명령어를 그대로 사용)
        var cashGrantCommand = new CashGrantCommand(this, core, messages);
        bindCommand("캐시지급", cashGrantCommand, cashGrantCommand);

        // 아이템밴 / 조합밴
        try {
            BannedItemSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("아이템밴 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        BannedItemRepository bannedItemRepository = new BannedItemRepository(core.dataSource());
        BannedItemManager bannedItemManager = new BannedItemManager(this, bannedItemRepository, executor);
        try {
            bannedItemManager.loadAll();
        } catch (Exception e) {
            getLogger().severe("아이템밴 데이터 로드 실패: " + e.getMessage());
        }
        BannedItemListener bannedItemListener = new BannedItemListener(core, messages, bannedItemManager);
        getServer().getPluginManager().registerEvents(bannedItemListener, this);
        var itemBanCommand = new BannedItemCommand(messages, bannedItemManager, bannedItemListener, BanType.POSSESSION);
        bindCommand("아이템밴", itemBanCommand, itemBanCommand);
        var craftBanCommand = new BannedItemCommand(messages, bannedItemManager, bannedItemListener, BanType.CRAFT);
        bindCommand("조합밴", craftBanCommand, craftBanCommand);

        // NPC 태그 (scottgb_npc_tags 팩 아이콘을 바라보는 엔티티 이름표에 붙임)
        var npcTagCommand = getCommand("npc태그");
        if (npcTagCommand != null) {
            var executorCmd = new NpcTagCommand(this, messages);
            npcTagCommand.setExecutor(executorCmd);
            npcTagCommand.setTabCompleter(executorCmd);
        }

        // 추첨
        try {
            RaffleSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("추첨 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        RaffleRepository raffleRepository = new RaffleRepository(core.dataSource());
        double raffleRepeatWeightMultiplier = getConfig().getDouble("raffle.repeat-weight-multiplier", 0.5);
        RaffleManager raffleManager = new RaffleManager(this, raffleRepository, executor, raffleRepeatWeightMultiplier);
        try {
            raffleManager.loadAll();
        } catch (Exception e) {
            getLogger().severe("추첨 이력 로드 실패: " + e.getMessage());
        }
        bindCommand("추첨", new RaffleCommand(this, core, messages, raffleManager));

        // 예약 자동 재부팅 (운영진이 /서버재부팅설정으로 시각 지정, 10/5/1분 전 채팅 안내 후 재부팅 직전 데이터 저장)
        try {
            RestartScheduleSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("자동 재부팅 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        RestartScheduleRepository restartScheduleRepository = new RestartScheduleRepository(core.dataSource());
        String restartServerId = getConfig().getString("restart.this-server-id", "lobby");
        String restartDefaultTimes = getConfig().getString("restart.default-times", "");
        RestartScheduleStore restartScheduleStore = new RestartScheduleStore(this, restartScheduleRepository, executor, restartServerId, restartDefaultTimes);
        try {
            restartScheduleStore.loadIntoCache();
        } catch (Exception e) {
            getLogger().severe("자동 재부팅 일정 로드 실패: " + e.getMessage());
        }
        bindCommand("서버재부팅설정", new RestartScheduleCommand(restartScheduleStore, messages));
        new ScheduledRestartTask(this, core, messages, restartScheduleStore).runTaskTimer(this, 20L, 20L);

        OreSummonCommand oreSummonCommand = new OreSummonCommand();
        bindCommand("광석소환", oreSummonCommand, oreSummonCommand);

        long autoBackupIntervalTicks = getConfig().getLong("backup.auto-interval-ticks", 20L * 60 * 60 * 6);
        if (autoBackupIntervalTicks > 0) {
            getServer().getScheduler().runTaskTimer(this, () -> executor.execute(() -> {
                try {
                    backupService.runBackup();
                    getLogger().info("자동 백업이 완료되었습니다.");
                } catch (Exception e) {
                    getLogger().severe("자동 백업 실패: " + e.getMessage());
                    Bukkit.getScheduler().runTask(this, () -> alertStaff("자동 백업이 실패했습니다: " + e.getMessage()));
                }
            }), autoBackupIntervalTicks, autoBackupIntervalTicks);
        }

        getLogger().info("YeowoolAdmin이 활성화되었습니다.");
    }

    private void alertStaff(String message) {
        for (var player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("yeowool.admin.alerts")) {
                messages.send(player, "admin.staff-alert", Placeholder.unparsed("message", message));
            }
        }
    }

    private void bindCommand(String name, org.bukkit.command.CommandExecutor executorImpl) {
        var command = getCommand(name);
        if (command != null) {
            command.setExecutor(executorImpl);
        }
    }

    private void bindCommand(String name, org.bukkit.command.CommandExecutor executorImpl, org.bukkit.command.TabCompleter tabCompleter) {
        var command = getCommand(name);
        if (command != null) {
            command.setExecutor(executorImpl);
            command.setTabCompleter(tabCompleter);
        }
    }

    @Override
    public void onDisable() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
