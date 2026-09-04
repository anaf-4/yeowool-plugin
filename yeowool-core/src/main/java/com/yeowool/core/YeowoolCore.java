package com.yeowool.core;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.command.CoreCommand;
import com.yeowool.core.config.CoreConfig;
import com.yeowool.core.data.EconomyDataServiceImpl;
import com.yeowool.core.data.LandStatServiceImpl;
import com.yeowool.core.data.PlayerCache;
import com.yeowool.core.data.PlayerDataServiceImpl;
import com.yeowool.core.data.repository.LogRepository;
import com.yeowool.core.data.repository.MailboxRepository;
import com.yeowool.core.data.repository.PlayerRepository;
import com.yeowool.core.data.repository.PunishmentRepository;
import com.yeowool.core.database.DatabaseManager;
import com.yeowool.core.database.SchemaInitializer;
import com.yeowool.core.gui.GuiListener;
import com.yeowool.core.help.GuideCommand;
import com.yeowool.core.help.GuideFirstJoinListener;
import com.yeowool.core.help.GuideMissionManager;
import com.yeowool.core.help.HelpCommand;
import com.yeowool.core.help.HelpIndexChecker;
import com.yeowool.core.listener.PlayerConnectionListener;
import com.yeowool.core.log.LogManager;
import com.yeowool.core.mailbox.MailboxCommand;
import com.yeowool.core.mailbox.MailboxJoinListener;
import com.yeowool.core.mailbox.MailboxManager;
import com.yeowool.core.message.MessageManager;
import com.yeowool.core.punishment.PunishmentManager;
import com.yeowool.core.sound.SoundManager;
import com.yeowool.core.util.ConfigMerger;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for the common foundation every other Yeowool plugin builds
 * on: player data, the shared MySQL pool, caching, GUI dispatch, messages,
 * sounds, and structured logging. See {@link YeowoolCoreAPI} for the surface
 * other plugins are expected to depend on.
 */
public final class YeowoolCore extends JavaPlugin {

    private CoreConfig coreConfig;
    private DatabaseManager databaseManager;
    private PlayerDataServiceImpl playerDataService;
    private MessageManager messageManager;
    private SoundManager soundManager;

    @Override
    public void onEnable() {
        ConfigMerger.mergeDefaults(this, "config.yml");
        this.coreConfig = new CoreConfig(this);

        this.databaseManager = new DatabaseManager(this);
        try {
            databaseManager.connect(coreConfig);
            SchemaInitializer.initialize(databaseManager.getDataSource());
        } catch (Exception e) {
            getLogger().severe("데이터베이스 연결/초기화에 실패했습니다. 서버를 비활성화합니다: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PlayerRepository playerRepository = new PlayerRepository(databaseManager.getDataSource());
        LogRepository logRepository = new LogRepository(databaseManager.getDataSource());
        MailboxRepository mailboxRepository = new MailboxRepository(databaseManager.getDataSource());
        PunishmentRepository punishmentRepository = new PunishmentRepository(databaseManager.getDataSource());

        PlayerCache playerCache = new PlayerCache(coreConfig);
        this.playerDataService = new PlayerDataServiceImpl(this, playerRepository, playerCache, databaseManager.getExecutor());

        LogManager logManager = new LogManager(this, logRepository, databaseManager.getExecutor());
        EconomyDataServiceImpl economyDataService = new EconomyDataServiceImpl(playerDataService, logManager);
        LandStatServiceImpl landStatService = new LandStatServiceImpl(playerDataService);
        MailboxManager mailboxManager = new MailboxManager(this, mailboxRepository, databaseManager.getExecutor(), databaseManager.getDataSource());
        PunishmentManager punishmentManager = new PunishmentManager(this, punishmentRepository, databaseManager.getExecutor());

        this.messageManager = new MessageManager(this);
        this.soundManager = new SoundManager(this);

        YeowoolCoreAPI api = new YeowoolCoreAPIImpl(
                playerDataService,
                economyDataService,
                landStatService,
                messageManager,
                soundManager,
                logManager,
                mailboxManager,
                punishmentManager,
                databaseManager.getDataSource()
        );
        getServer().getServicesManager().register(YeowoolCoreAPI.class, api, this, ServicePriority.Normal);

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this, playerDataService, punishmentManager), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new MailboxJoinListener(this, mailboxManager, messageManager), this);

        CoreCommand coreCommand = new CoreCommand(coreConfig, messageManager, soundManager);
        var command = getCommand("yeowoolcore");
        if (command != null) {
            command.setExecutor(coreCommand);
            command.setTabCompleter(coreCommand);
        }

        var mailboxCommand = getCommand("우편함");
        if (mailboxCommand != null) {
            int mailboxBackgroundOffsetPx = getConfig().getInt("mailbox.gui-background-offset", -8);
            mailboxCommand.setExecutor(new MailboxCommand(this, mailboxManager, messageManager, mailboxBackgroundOffsetPx));
        }

        var helpCommand = getCommand("여울도움말");
        if (helpCommand != null) {
            var executorCmd = new HelpCommand(this);
            helpCommand.setExecutor(executorCmd);
            helpCommand.setTabCompleter(executorCmd);
        }
        GuideMissionManager guideMissionManager = new GuideMissionManager(this);
        var guideCommand = getCommand("길라잡이");
        if (guideCommand != null) {
            var executorCmd = new GuideCommand(this, guideMissionManager);
            guideCommand.setExecutor(executorCmd);
            guideCommand.setTabCompleter(executorCmd);
        }
        getServer().getPluginManager().registerEvents(new GuideFirstJoinListener(this, playerDataService, guideMissionManager), this);
        // 새 명령어를 추가하고 help.categories 갱신을 깜빡하는 실수가 이 프로젝트에서
        // 이미 여러 번 있었어서, 등록은 됐는데 도움말 어디에도 안 나오는 명령어를
        // 시작 시점에 자동으로 찾아 콘솔에 경고한다 (하위 명령어까지는 못 잡음 — 자세한
        // 이유는 HelpIndexChecker의 클래스 주석 참고).
        var unlistedCommands = HelpIndexChecker.findUnlistedCommands(this);
        if (!unlistedCommands.isEmpty()) {
            getLogger().warning("/여울도움말(help.categories)에 언급되지 않은 명령어가 있습니다: " + unlistedCommands);
        }

        long interval = coreConfig.getAutosaveIntervalTicks();
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> playerDataService.saveAll(), interval, interval);

        getLogger().info("YeowoolCore가 활성화되었습니다.");
    }

    @Override
    public void onDisable() {
        if (playerDataService != null) {
            playerDataService.saveAll().join();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        getLogger().info("YeowoolCore가 비활성화되었습니다.");
    }
}
