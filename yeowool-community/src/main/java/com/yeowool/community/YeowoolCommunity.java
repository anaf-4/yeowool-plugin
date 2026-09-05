package com.yeowool.community;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.message.MessageManager;
import com.yeowool.core.util.ConfigMerger;
import com.yeowool.community.battlepass.BattlePassAmountListener;
import com.yeowool.community.battlepass.BattlePassCommand;
import com.yeowool.community.battlepass.BattlePassConfig;
import com.yeowool.community.battlepass.BattlePassManager;
import com.yeowool.community.battlepass.BattlePassRewardStore;
import com.yeowool.community.battlepass.database.BattlePassSchemaInitializer;
import com.yeowool.community.battlepass.repository.BattlePassRepository;
import com.yeowool.community.chat.ChatChannelCommand;
import com.yeowool.community.menu.MenuCommand;
import com.yeowool.community.menu.MenuConfig;
import com.yeowool.community.menu.MenuContext;
import com.yeowool.community.menu.MenuKeybindListener;
import com.yeowool.community.chat.ChatChannelSendCommand;
import com.yeowool.community.chat.ChatChannelService;
import com.yeowool.community.chat.ChatListener;
import com.yeowool.community.chat.CrossServerChatBridge;
import com.yeowool.community.chat.ReplyCommand;
import com.yeowool.community.chat.WhisperCommand;
import com.yeowool.community.cosmetic.CosmeticCommand;
import com.yeowool.community.quest.AttendanceCheckCommand;
import com.yeowool.community.quest.AttendanceManager;
import com.yeowool.community.quest.AttendanceRewardAmountListener;
import com.yeowool.community.quest.AttendanceRewardCommand;
import com.yeowool.community.quest.AttendanceRewardRepository;
import com.yeowool.community.quest.AttendanceRewardSchemaInitializer;
import com.yeowool.community.quest.AttendanceRewardSeedCommand;
import com.yeowool.community.quest.AttendanceRewardStore;
import com.yeowool.community.quest.QuestBadgeConfig;
import com.yeowool.community.quest.QuestBoardCommand;
import com.yeowool.community.quest.QuestContext;
import com.yeowool.community.quest.QuestLeaderboardQuery;
import com.yeowool.community.quest.QuestManager;
import com.yeowool.community.cosmetic.CosmeticManager;
import com.yeowool.community.cosmetic.ParticleTrailTask;
import com.yeowool.community.display.PlayerIdentityJoinListener;
import com.yeowool.community.display.PlayerIdentityService;
import com.yeowool.community.display.SidebarScoreboardTask;
import com.yeowool.community.display.TablistTask;
import com.yeowool.community.event.EventCommand;
import com.yeowool.community.event.EventManager;
import com.yeowool.community.event.MissionCollectListener;
import com.yeowool.community.event.MissionEventManager;
import com.yeowool.community.event.MissionScheduler;
import com.yeowool.community.event.MissionKillListener;
import com.yeowool.community.couple.CoupleChatCommand;
import com.yeowool.community.couple.CoupleCommand;
import com.yeowool.community.couple.CoupleJoinListener;
import com.yeowool.community.couple.CoupleManager;
import com.yeowool.community.couple.database.CoupleSchemaInitializer;
import com.yeowool.community.couple.repository.CoupleRepository;
import com.yeowool.community.friend.FriendCommand;
import com.yeowool.community.friend.FriendJoinListener;
import com.yeowool.community.friend.FriendManager;
import com.yeowool.community.friend.database.FriendSchemaInitializer;
import com.yeowool.community.friend.repository.FriendRepository;
import com.yeowool.community.nickname.KoreanNicknameManager;
import com.yeowool.community.nickname.NicknameVoucherCommand;
import com.yeowool.community.nickname.NicknameVoucherListener;
import com.yeowool.community.placeholder.YeowoolPlaceholderExpansion;
import com.yeowool.community.profile.MyInfoCommand;
import com.yeowool.community.profile.PlaytimeTracker;
import com.yeowool.community.profile.ProfileCommand;
import com.yeowool.community.rankicon.RankIconCommand;
import com.yeowool.community.rankicon.RankIconManager;
import com.yeowool.community.title.AchievementCheckTask;
import com.yeowool.community.title.TitleBookCommand;
import com.yeowool.community.title.TitleCommand;
import com.yeowool.community.title.TitleCreateCommand;
import com.yeowool.community.title.TitleDeleteCommand;
import com.yeowool.community.title.TitleManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 커뮤니티 (기획서 9절 + 10절): 채팅, 프로필, 칭호+업적(하나의 통계 기반
 * 시스템으로 통합), 이벤트(XP 배율 + 참가 보상), 코스메틱(파티클/채팅 색상
 * 판매), 친구 시스템. PlaceholderAPI 확장과 사이드바/탭리스트도 여기서 관리한다.
 */
public final class YeowoolCommunity extends JavaPlugin {

    private ExecutorService executor;

    @Override
    public void onEnable() {
        YeowoolCoreAPI core = Bukkit.getServicesManager().load(YeowoolCoreAPI.class);
        if (core == null) {
            getLogger().severe("YeowoolCore API를 찾을 수 없습니다. YeowoolCore가 먼저 로드되어야 합니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ConfigMerger.mergeDefaults(this, "config.yml");
        migrateChangedDefaults();
        MessageManager messages = new MessageManager(this);
        TitleManager titleManager = new TitleManager(this, core);
        CosmeticManager cosmeticManager = new CosmeticManager(this, core);
        RankIconManager rankIconManager = new RankIconManager(this, core);
        QuestManager questManager = new QuestManager(this, core);

        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "YeowoolCommunity-Worker");
            thread.setDaemon(true);
            return thread;
        });

        QuestBadgeConfig questBadgeConfig = QuestBadgeConfig.load(getConfig());
        QuestLeaderboardQuery questLeaderboardQuery = new QuestLeaderboardQuery(core.dataSource());
        QuestContext questContext = new QuestContext(this, core, questManager, questBadgeConfig, questLeaderboardQuery, executor, messages);

        // 출석 보상 (OP가 /출석보상설정으로 금액/화폐/아이템을 직접 수정 가능)
        AttendanceRewardStore attendanceRewardStore;
        try {
            AttendanceRewardSchemaInitializer.initialize(core.dataSource());
            AttendanceRewardRepository attendanceRewardRepository = new AttendanceRewardRepository(core.dataSource());
            attendanceRewardStore = new AttendanceRewardStore(this, attendanceRewardRepository, executor);
            attendanceRewardStore.loadIntoCache();
        } catch (Exception e) {
            getLogger().severe("출석 보상 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        AttendanceManager attendanceManager = new AttendanceManager(this, core, attendanceRewardStore);
        AttendanceRewardAmountListener attendanceRewardAmountListener = new AttendanceRewardAmountListener(this, attendanceRewardStore);
        getServer().getPluginManager().registerEvents(attendanceRewardAmountListener, this);
        var attendanceRewardCommand = getCommand("출석보상설정");
        if (attendanceRewardCommand != null) {
            attendanceRewardCommand.setExecutor(new AttendanceRewardCommand(attendanceRewardStore, attendanceRewardAmountListener, messages));
        }
        var attendanceRewardSeedCommand = getCommand("출석보상초기화");
        if (attendanceRewardSeedCommand != null) {
            attendanceRewardSeedCommand.setExecutor(new AttendanceRewardSeedCommand(attendanceRewardStore));
        }

        // 배틀패스 (FREE는 기본 열림, PREMIUM은 캐시 구매 또는 관리자 지급 전까지 잠김)
        BattlePassRewardStore battlePassRewardStore;
        try {
            BattlePassSchemaInitializer.initialize(core.dataSource());
            BattlePassRepository battlePassRepository = new BattlePassRepository(core.dataSource());
            battlePassRewardStore = new BattlePassRewardStore(this, battlePassRepository, executor);
            battlePassRewardStore.loadIntoCache();
        } catch (Exception e) {
            getLogger().severe("배틀패스 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        BattlePassConfig battlePassConfig = BattlePassConfig.load(getConfig());
        BattlePassManager battlePassManager = new BattlePassManager(core, battlePassConfig, battlePassRewardStore, questContext);
        questManager.setBattlePassManager(battlePassManager);
        BattlePassAmountListener battlePassAmountListener = new BattlePassAmountListener(this, battlePassRewardStore);
        getServer().getPluginManager().registerEvents(battlePassAmountListener, this);
        var battlePassCommand = getCommand("배틀패스");
        if (battlePassCommand != null) {
            var executor = new BattlePassCommand(core, battlePassManager, battlePassRewardStore, battlePassAmountListener, messages);
            battlePassCommand.setExecutor(executor);
            battlePassCommand.setTabCompleter(executor);
        }

        // 메뉴 (/메뉴, 웅크린 채로 손 바꾸기 키(F, Shift+F 흉내))
        MenuContext menuContext = new MenuContext(core, messages, battlePassManager, MenuConfig.load(getConfig()));
        getServer().getPluginManager().registerEvents(new MenuKeybindListener(menuContext), this);
        var menuCommand = getCommand("메뉴");
        if (menuCommand != null) {
            menuCommand.setExecutor(new MenuCommand(menuContext, messages));
        }

        KoreanNicknameManager nicknameManager = new KoreanNicknameManager(core);
        PlayerIdentityService identityService = new PlayerIdentityService(core, rankIconManager, titleManager, nicknameManager);
        getServer().getPluginManager().registerEvents(new PlayerIdentityJoinListener(identityService), this);

        long cooldownMillis = getConfig().getLong("chat.cooldown-ms", 1500L);
        int localRadius = getConfig().getInt("chat.local-radius", 100);
        String proxyServerId = getConfig().getString("proxy-server-id", "lobby");
        CrossServerChatBridge crossServerBridge = new CrossServerChatBridge(this, proxyServerId);
        ChatChannelService channelService = new ChatChannelService(core, cosmeticManager, identityService, localRadius, crossServerBridge);
        getServer().getPluginManager().registerEvents(
                new ChatListener(core, channelService, messages, cooldownMillis), this);

        var channelCommand = getCommand("채널");
        if (channelCommand != null) {
            var executor = new ChatChannelCommand(channelService, messages);
            channelCommand.setExecutor(executor);
            channelCommand.setTabCompleter(executor);
        }
        bindChannelSendCommand("전체", core, channelService, ChatChannelService.Channel.GLOBAL, messages);
        bindChannelSendCommand("지역", core, channelService, ChatChannelService.Channel.LOCAL, messages);
        bindChannelSendCommand("마을", core, channelService, ChatChannelService.Channel.LAND, messages);

        var dailyQuestCommand = getCommand("일일퀘스트");
        if (dailyQuestCommand != null) {
            dailyQuestCommand.setExecutor(new QuestBoardCommand(questContext, QuestManager.Period.DAILY));
        }
        var weeklyQuestCommand = getCommand("주간퀘스트");
        if (weeklyQuestCommand != null) {
            weeklyQuestCommand.setExecutor(new QuestBoardCommand(questContext, QuestManager.Period.WEEKLY));
        }
        var attendanceCommand = getCommand("출석체크");
        if (attendanceCommand != null) {
            attendanceCommand.setExecutor(new AttendanceCheckCommand(core, attendanceManager, messages));
        }

        var whisperCommand = new WhisperCommand(this, core, messages);
        var whisperCmd = getCommand("귓속말");
        if (whisperCmd != null) {
            whisperCmd.setExecutor(whisperCommand);
            whisperCmd.setTabCompleter(whisperCommand);
        }
        var replyCmd = getCommand("답장");
        if (replyCmd != null) {
            replyCmd.setExecutor(new ReplyCommand(whisperCommand, messages));
        }

        var profileCommand = getCommand("프로필");
        if (profileCommand != null) {
            var executor = new ProfileCommand(this, core, titleManager, messages);
            profileCommand.setExecutor(executor);
            profileCommand.setTabCompleter(executor);
        }
        var myInfoCommand = getCommand("내정보");
        if (myInfoCommand != null) {
            myInfoCommand.setExecutor(new MyInfoCommand(core, titleManager, messages));
        }
        var titleCommand = getCommand("칭호");
        if (titleCommand != null) {
            var executor = new TitleCommand(this, core, titleManager, identityService, messages);
            titleCommand.setExecutor(executor);
            titleCommand.setTabCompleter(executor);
        }
        var titleCreateCommand = getCommand("칭호생성");
        if (titleCreateCommand != null) {
            titleCreateCommand.setExecutor(new TitleCreateCommand(titleManager, messages));
        }
        var titleDeleteCommand = getCommand("칭호삭제");
        if (titleDeleteCommand != null) {
            var executor = new TitleDeleteCommand(titleManager, messages);
            titleDeleteCommand.setExecutor(executor);
            titleDeleteCommand.setTabCompleter(executor);
        }
        var titleBookCommand = getCommand("칭호북");
        if (titleBookCommand != null) {
            var executor = new TitleBookCommand(core, titleManager, identityService, messages);
            titleBookCommand.setExecutor(executor);
            titleBookCommand.setTabCompleter(executor);
        }

        var rankIconCommand = getCommand("랭크아이콘");
        if (rankIconCommand != null) {
            var executor = new RankIconCommand(this, core, rankIconManager, identityService, messages);
            rankIconCommand.setExecutor(executor);
            rankIconCommand.setTabCompleter(executor);
        }

        String voucherItemId = getConfig().getString("korean-nickname.voucher-item-id", "");
        getServer().getPluginManager().registerEvents(
                new NicknameVoucherListener(this, nicknameManager, identityService), this);
        var nicknameVoucherCommand = getCommand("한글닉네임설정권");
        if (nicknameVoucherCommand != null) {
            var executor = new NicknameVoucherCommand(this, core, voucherItemId, messages);
            nicknameVoucherCommand.setExecutor(executor);
            nicknameVoucherCommand.setTabCompleter(executor);
        }

        EventManager eventManager = new EventManager(this, core);
        MissionEventManager missionManager = new MissionEventManager(this, core);
        getServer().getPluginManager().registerEvents(new MissionKillListener(missionManager), this);
        getServer().getPluginManager().registerEvents(new MissionCollectListener(missionManager), this);
        MissionScheduler missionScheduler = new MissionScheduler(missionManager, MissionScheduler.load(this));
        Bukkit.getScheduler().runTaskTimer(this, missionScheduler::checkNow, 20L * 10, 20L * 60);
        var eventCommand = getCommand("이벤트");
        if (eventCommand != null) {
            var executor = new EventCommand(this, core, eventManager, missionManager);
            eventCommand.setExecutor(executor);
            eventCommand.setTabCompleter(executor);
        }

        var cosmeticCommand = getCommand("코스메틱");
        if (cosmeticCommand != null) {
            var executor = new CosmeticCommand(core, cosmeticManager);
            cosmeticCommand.setExecutor(executor);
            cosmeticCommand.setTabCompleter(executor);
        }
        new ParticleTrailTask(core, cosmeticManager).runTaskTimer(this, 20L, 10L);

        new PlaytimeTracker(core).runTaskTimer(this, 20L * 60, 20L * 60);
        new AchievementCheckTask(this, titleManager, messages, core.sounds()).runTaskTimer(this, 20L * 30, 20L * 60 * 5);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new YeowoolPlaceholderExpansion(core, titleManager).register();
            getLogger().info("PlaceholderAPI 확장을 등록했습니다. (%yeowool_...%)");
        }

        long displayIntervalTicks = getConfig().getLong("scoreboard.update-interval-ticks", 20L);
        if (getConfig().getBoolean("scoreboard.enabled", true)) {
            new SidebarScoreboardTask(this, core, titleManager, identityService, channelService,
                    getConfig().getString("scoreboard.title", "<green><bold>여울</bold></green>"),
                    getConfig().getStringList("scoreboard.lines"))
                    .runTaskTimer(this, 0L, displayIntervalTicks);
        }
        if (getConfig().getBoolean("tablist.enabled", true)) {
            new TablistTask(this, core, titleManager, identityService, channelService,
                    getConfig().getString("tablist.header", ""),
                    getConfig().getString("tablist.footer", ""),
                    getConfig().getString("server-name", ""))
                    .runTaskTimer(this, 0L, displayIntervalTicks);
        }

        // 친구 시스템
        try {
            FriendSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("친구 시스템 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        FriendRepository friendRepository = new FriendRepository(core.dataSource());
        FriendManager friendManager = new FriendManager(this, friendRepository, executor);
        getServer().getPluginManager().registerEvents(new FriendJoinListener(this, friendManager), this);
        var friendCommand = getCommand("친구");
        if (friendCommand != null) {
            var executorCmd = new FriendCommand(this, friendManager, messages);
            friendCommand.setExecutor(executorCmd);
            friendCommand.setTabCompleter(executorCmd);
        }

        // 커플 시스템
        try {
            CoupleSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("커플 시스템 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        CoupleRepository coupleRepository = new CoupleRepository(core.dataSource());
        CoupleManager coupleManager = new CoupleManager(this, coupleRepository, executor);
        try {
            coupleManager.loadAll();
        } catch (Exception e) {
            getLogger().severe("커플 데이터 로드 실패: " + e.getMessage());
        }
        getServer().getPluginManager().registerEvents(new CoupleJoinListener(coupleManager), this);
        var coupleCommand = getCommand("커플");
        if (coupleCommand != null) {
            var executorCmd = new CoupleCommand(coupleManager, messages);
            coupleCommand.setExecutor(executorCmd);
            coupleCommand.setTabCompleter(executorCmd);
        }
        var coupleChatCommand = getCommand("커플채팅");
        if (coupleChatCommand != null) {
            coupleChatCommand.setExecutor(new CoupleChatCommand(this, core, coupleManager, messages));
        }

        getLogger().info("YeowoolCommunity가 활성화되었습니다.");
    }

    private void bindChannelSendCommand(String name, YeowoolCoreAPI core, ChatChannelService channelService,
                                         ChatChannelService.Channel channel, MessageManager messages) {
        var command = getCommand(name);
        if (command != null) {
            command.setExecutor(new ChatChannelSendCommand(this, core, channelService, channel, messages));
        }
    }

    /**
     * One-off value migrations for shipped defaults this plugin reworded
     * after servers had already picked up the old wording via
     * {@link ConfigMerger#mergeDefaults} — each only touches a value still
     * exactly equal to the specific old default it names, so a server's own
     * customization (including having already typed the new wording in
     * themselves) is never overwritten. Must run before the first
     * {@link #getConfig()} call in {@link #onEnable()} so Bukkit's cached
     * config picks up the migrated file, not the pre-migration one.
     */
    private void migrateChangedDefaults() {
        ConfigMerger.migrate(this, "config.yml", config -> {
            boolean changed = false;
            changed |= ConfigMerger.migrateScalarIfDefault(config, "tablist.header",
                    "<green><bold>🌿 YEOWOOL</bold></green>\n<gray>온: <gold><on></gold> | 토지 Lv.<landlevel></gray>",
                    "<green><bold>YEOWOOL</bold></green>\n\n<gray>여울 | 반야생 서버</gray>\n<gray>현재 서버: <yellow><servername></yellow></gray>");
            changed |= ConfigMerger.migrateScalarIfDefault(config, "tablist.footer",
                    "<gray>접속자: <gold><online>/<max_players></gold> | TPS: <gold><tps></gold></gray>",
                    "<gray>온라인 <gold><online></gold> / <gold><max_players></gold></gray>\n<gray>즐거운 여울 생활을 시작해보세요.</gray>");
            changed |= ConfigMerger.migrateListElementIfPresent(config, "scoreboard.lines",
                    "<gray>닉네임: <white><player></white></gray>",
                    "<gray>닉네임: <white><identity></white></gray>");
            changed |= ConfigMerger.appendListElementIfMissing(config, "scoreboard.lines", "<cash>",
                    "<gray>캐시: <light_purple><cash></light_purple></gray>");
            changed |= ConfigMerger.removeListElementIfPresent(config, "scoreboard.lines",
                    "<gray>접속자: <gold><online>/<max_players></gold></gray>");
            changed |= ConfigMerger.appendListElementIfMissing(config, "scoreboard.lines", "<autopickup>",
                    "<gray>자동줍기: <aqua><autopickup></aqua></gray>");
            changed |= ConfigMerger.appendListElementIfMissing(config, "scoreboard.lines", "<autoplant>",
                    "<gray>자동심기: <aqua><autoplant></aqua></gray>");
            return changed;
        });
    }

    @Override
    public void onDisable() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
