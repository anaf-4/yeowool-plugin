package com.yeowool.raid;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.util.ConfigMerger;
import com.yeowool.raid.database.RaidDefinitionRepository;
import com.yeowool.raid.database.RaidInstanceRepository;
import com.yeowool.raid.database.RaidSchemaInitializer;
import com.yeowool.raid.party.RaidPartyLookup;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class YeowoolRaid extends JavaPlugin {

    private ExecutorService executor;

    @Override
    public void onEnable() {
        YeowoolCoreAPI core = Bukkit.getServicesManager().load(YeowoolCoreAPI.class);
        if (core == null) {
            getLogger().severe("YeowoolCore API를 찾을 수 없습니다. YeowoolCore가 먼저 로드되어야 합니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            getLogger().severe("Citizens가 설치되어 있지 않습니다. YeowoolRaid는 Citizens 없이 동작할 수 없습니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            getLogger().severe("MythicMobs가 설치되어 있지 않습니다. YeowoolRaid는 MythicMobs 없이 동작할 수 없습니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ConfigMerger.mergeDefaults(this, "config.yml");
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "YeowoolRaid-Worker");
            thread.setDaemon(true);
            return thread;
        });

        try {
            RaidSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("레이드 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        RaidDefinitionRepository definitionRepository = new RaidDefinitionRepository(core.dataSource());
        RaidInstanceRepository instanceRepository = new RaidInstanceRepository(core.dataSource());
        RaidManager raidManager = new RaidManager(this, definitionRepository, instanceRepository, executor);
        try {
            raidManager.loadAll();
        } catch (Exception e) {
            getLogger().severe("레이드 로드 실패: " + e.getMessage());
        }

        RaidHudService hudService = new RaidHudService(
                getConfig().getString("betterhud.boss-warning-popup", "yeowool_raid_boss_warning"),
                getConfig().getString("betterhud.result-popup", "yeowool_raid_result"));
        RaidPartyLookup partyLookup = new RaidPartyLookup(core.dataSource());
        RaidEntryService entryService = new RaidEntryService(this, raidManager, partyLookup, hudService, executor);

        getServer().getPluginManager().registerEvents(new RaidNpcListener(raidManager, entryService), this);
        getServer().getPluginManager().registerEvents(new RaidBossDeathListener(raidManager, core, hudService), this);
        getServer().getPluginManager().registerEvents(new RaidPartyDeathListener(raidManager, hudService), this);
        new RaidTimeoutTask(raidManager, hudService).start(this);

        var command = getCommand("레이드");
        if (command != null) {
            RaidAdminCommand adminCommand = new RaidAdminCommand(this, raidManager, executor);
            command.setExecutor(adminCommand);
            command.setTabCompleter(adminCommand);
        }

        boolean betterHudEnabled = Bukkit.getPluginManager().isPluginEnabled("BetterHud");
        getLogger().info("YeowoolRaid가 활성화되었습니다." + (betterHudEnabled ? "" : " (BetterHud 없이 채팅 폴백 모드)"));
    }

    @Override
    public void onDisable() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
