package com.yeowool.land;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.message.MessageManager;
import com.yeowool.core.util.ConfigMerger;
import com.yeowool.land.command.LandCommand;
import com.yeowool.land.command.LandTeleportCommand;
import com.yeowool.land.command.LandTeleportJoinListener;
import com.yeowool.land.command.VillageRankingCommand;
import com.yeowool.land.database.LandSchemaInitializer;
import com.yeowool.land.level.LandLevelTable;
import com.yeowool.land.listener.BarrelClaimListener;
import com.yeowool.land.listener.LandEntryNotifyListener;
import com.yeowool.land.listener.LandLevelListener;
import com.yeowool.land.listener.ProtectionListener;
import com.yeowool.land.repository.LandRepository;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 토지 시스템: 배럴 등록, 청크 소유권, 토지 생성/확장, 보호, 레벨, 은행,
 * 업그레이드. 계획서 4.1/4.2/4.3절을 하나의 배포 단위로 묶었다 (section 13의
 * 8~10개 모듈 권장안을 따름). 첫 접속 스타터 지급은 YeowoolAdmin의
 * "/여울관리 기본템" GUI로 이전되었다.
 */
public final class YeowoolLand extends JavaPlugin {

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
        MessageManager messages = new MessageManager(this);
        LandLevelTable levelTable = new LandLevelTable(this);

        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "YeowoolLand-Worker");
            thread.setDaemon(true);
            return thread;
        });

        LandRepository repository = new LandRepository(core.dataSource());
        LandManager landManager = new LandManager(this, repository, executor);
        try {
            LandSchemaInitializer.initialize(core.dataSource());
            landManager.loadAll();
        } catch (Exception e) {
            getLogger().severe("토지 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new BarrelClaimListener(core, landManager, levelTable, messages), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(landManager, messages), this);
        getServer().getPluginManager().registerEvents(new LandLevelListener(core, levelTable, messages), this);
        getServer().getPluginManager().registerEvents(new LandEntryNotifyListener(landManager), this);

        var landCommand = getCommand("토지");
        if (landCommand != null) {
            Particle boundaryParticle = parseParticle(getConfig().getString("boundary-preview.particle", "FLAME"));
            int boundaryDuration = getConfig().getInt("boundary-preview.duration-seconds", 10);
            int boundaryInterval = getConfig().getInt("boundary-preview.interval-ticks", 10);
            var executor2 = new LandCommand(this, core, landManager, levelTable, messages, boundaryParticle, boundaryDuration, boundaryInterval);
            landCommand.setExecutor(executor2);
            landCommand.setTabCompleter(executor2);
        }

        var villageRankingCommand = getCommand("마을랭킹");
        if (villageRankingCommand != null) {
            villageRankingCommand.setExecutor(new VillageRankingCommand(core, landManager, messages));
        }

        String thisServerId = getConfig().getString("land-teleport.this-server-id", "lobby");
        String landServerId = getConfig().getString("land-teleport.land-server-id", "town");
        var landTeleportCommand = new LandTeleportCommand(this, core, landManager, messages, landServerId, thisServerId);
        var landTeleportCmd = getCommand("토지이동");
        if (landTeleportCmd != null) {
            landTeleportCmd.setExecutor(landTeleportCommand);
        }
        if (thisServerId.equals(landServerId)) {
            getServer().getPluginManager().registerEvents(new LandTeleportJoinListener(this, core, landTeleportCommand), this);
        }

        getLogger().info("YeowoolLand가 활성화되었습니다.");
    }

    private Particle parseParticle(String name) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException e) {
            getLogger().warning("알 수 없는 파티클: " + name + " - FLAME으로 대체합니다.");
            return Particle.FLAME;
        }
    }

    @Override
    public void onDisable() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
