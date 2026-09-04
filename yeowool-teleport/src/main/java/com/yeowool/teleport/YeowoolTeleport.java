package com.yeowool.teleport;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.message.MessageManager;
import com.yeowool.core.util.ConfigMerger;
import com.yeowool.teleport.database.TeleportSchemaInitializer;
import com.yeowool.teleport.home.HomeCommand;
import com.yeowool.teleport.home.HomeJoinListener;
import com.yeowool.teleport.home.HomeManager;
import com.yeowool.teleport.playerwarp.PlayerWarpCommand;
import com.yeowool.teleport.playerwarp.PlayerWarpConfig;
import com.yeowool.teleport.playerwarp.PlayerWarpContext;
import com.yeowool.teleport.playerwarp.PlayerWarpFavoriteManager;
import com.yeowool.teleport.playerwarp.PlayerWarpManager;
import com.yeowool.teleport.playerwarp.PlayerWarpRatingManager;
import com.yeowool.teleport.playerwarp.PlayerWarpTextInput;
import com.yeowool.teleport.repository.HomeRepository;
import com.yeowool.teleport.repository.PlayerWarpFavoriteRepository;
import com.yeowool.teleport.repository.PlayerWarpRatingRepository;
import com.yeowool.teleport.repository.PlayerWarpRepository;
import com.yeowool.teleport.repository.WarpRepository;
import com.yeowool.teleport.rtp.RtpCommand;
import com.yeowool.teleport.rtp.RtpConfig;
import com.yeowool.teleport.rtp.RtpManager;
import com.yeowool.teleport.tpa.TpaAcceptCommand;
import com.yeowool.teleport.tpa.TpaDenyCommand;
import com.yeowool.teleport.tpa.TpaManager;
import com.yeowool.teleport.tpa.TpaRequestCommand;
import com.yeowool.teleport.warp.WarpCommand;
import com.yeowool.teleport.warp.WarpGuiConfig;
import com.yeowool.teleport.warp.WarpManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 순간이동 (홈/워프/tpa): 세 명령어군 모두 {@link TeleportService}를 통해
 * 같은 지연/이동취소/쿨다운 규칙을 공유한다. 홈은 플레이어당 캐시(접속 시
 * 로드), 워프는 관리자가 소수만 만드는 서버 전체 목록이라 시작 시 전부
 * 캐시한다.
 */
public final class YeowoolTeleport extends JavaPlugin {

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
        MessageService messages = new MessageManager(this);
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "YeowoolTeleport-Worker");
            thread.setDaemon(true);
            return thread;
        });

        try {
            TeleportSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("순간이동 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        int delaySeconds = getConfig().getInt("teleport.delay-seconds", 3);
        boolean cancelOnMove = getConfig().getBoolean("teleport.cancel-on-move", true);
        long cooldownMillis = getConfig().getLong("teleport.cooldown-seconds", 5) * 1000L;
        TeleportService teleportService = new TeleportService(this, messages, delaySeconds, cancelOnMove, cooldownMillis);
        getServer().getPluginManager().registerEvents(new TeleportCancelListener(teleportService, messages), this);

        // 홈
        HomeRepository homeRepository = new HomeRepository(core.dataSource());
        int maxHomes = getConfig().getInt("teleport.max-homes", 3);
        HomeManager homeManager = new HomeManager(this, homeRepository, executor, maxHomes);
        getServer().getPluginManager().registerEvents(new HomeJoinListener(this, homeManager), this);
        for (var player : Bukkit.getOnlinePlayers()) {
            homeManager.load(player.getUniqueId());
        }
        var homeCommand = getCommand("홈");
        if (homeCommand != null) {
            var executorCmd = new HomeCommand(homeManager, teleportService, messages);
            homeCommand.setExecutor(executorCmd);
            homeCommand.setTabCompleter(executorCmd);
        }

        // 워프
        WarpRepository warpRepository = new WarpRepository(core.dataSource());
        WarpManager warpManager = new WarpManager(this, warpRepository, executor);
        try {
            warpManager.loadAll();
        } catch (Exception e) {
            getLogger().severe("워프 데이터 로드 실패: " + e.getMessage());
        }
        WarpGuiConfig warpGuiConfig = WarpGuiConfig.load(this);
        var warpCommand = getCommand("워프");
        if (warpCommand != null) {
            var executorCmd = new WarpCommand(warpManager, teleportService, warpGuiConfig, messages);
            warpCommand.setExecutor(executorCmd);
            warpCommand.setTabCompleter(executorCmd);
        }

        // 플레이어 워프 (공개, 본인 소유 + 서로 방문 — 카테고리/검색/정렬/즐겨찾기/평점/입장료/편집까지 전부)
        PlayerWarpRepository playerWarpRepository = new PlayerWarpRepository(core.dataSource());
        int maxPlayerWarps = getConfig().getInt("teleport.max-player-warps", 1);
        PlayerWarpManager playerWarpManager = new PlayerWarpManager(this, playerWarpRepository, executor, maxPlayerWarps);
        try {
            playerWarpManager.loadAll();
        } catch (Exception e) {
            getLogger().severe("플레이어 워프 데이터 로드 실패: " + e.getMessage());
        }
        PlayerWarpRatingRepository playerWarpRatingRepository = new PlayerWarpRatingRepository(core.dataSource());
        PlayerWarpRatingManager playerWarpRatingManager = new PlayerWarpRatingManager(this, playerWarpRatingRepository, executor);
        try {
            playerWarpRatingManager.loadAll();
        } catch (Exception e) {
            getLogger().severe("플레이어 워프 평점 데이터 로드 실패: " + e.getMessage());
        }
        PlayerWarpFavoriteRepository playerWarpFavoriteRepository = new PlayerWarpFavoriteRepository(core.dataSource());
        PlayerWarpFavoriteManager playerWarpFavoriteManager = new PlayerWarpFavoriteManager(this, playerWarpFavoriteRepository, executor);
        try {
            playerWarpFavoriteManager.loadAll();
        } catch (Exception e) {
            getLogger().severe("플레이어 워프 즐겨찾기 데이터 로드 실패: " + e.getMessage());
        }
        PlayerWarpConfig playerWarpConfig = PlayerWarpConfig.load(getConfig());
        PlayerWarpContext playerWarpContext = new PlayerWarpContext(this, core, messages,
                playerWarpManager, playerWarpRatingManager, playerWarpFavoriteManager, teleportService, playerWarpConfig);
        PlayerWarpTextInput playerWarpTextInput = new PlayerWarpTextInput(this, playerWarpContext);
        getServer().getPluginManager().registerEvents(playerWarpTextInput, this);
        var playerWarpCommand = getCommand("플레이어워프");
        if (playerWarpCommand != null) {
            var executorCmd = new PlayerWarpCommand(playerWarpContext, playerWarpTextInput);
            playerWarpCommand.setExecutor(executorCmd);
            playerWarpCommand.setTabCompleter(executorCmd);
        }

        // RTP (무작위 순간이동)
        RtpConfig rtpConfig = RtpConfig.load(getConfig());
        RtpManager rtpManager = new RtpManager(core, teleportService, rtpConfig);
        bindCommand("rtp", new RtpCommand(rtpManager, rtpConfig, messages));

        // tpa
        TpaManager tpaManager = new TpaManager();
        bindCommand("tpa", new TpaRequestCommand(tpaManager, TpaManager.Kind.TO, messages));
        bindCommand("tpahere", new TpaRequestCommand(tpaManager, TpaManager.Kind.HERE, messages));
        bindCommand("tpaccept", new TpaAcceptCommand(tpaManager, teleportService, messages));
        bindCommand("tpdeny", new TpaDenyCommand(tpaManager, messages));

        getLogger().info("YeowoolTeleport가 활성화되었습니다.");
    }

    private void bindCommand(String name, org.bukkit.command.CommandExecutor executorImpl) {
        var command = getCommand(name);
        if (command == null) {
            return;
        }
        command.setExecutor(executorImpl);
        if (executorImpl instanceof org.bukkit.command.TabCompleter tabCompleter) {
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
