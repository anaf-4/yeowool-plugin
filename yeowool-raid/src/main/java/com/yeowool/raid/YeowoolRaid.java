package com.yeowool.raid;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.message.MessageManager;
import com.yeowool.core.util.ConfigMerger;
import com.yeowool.raid.database.RaidDefinitionRepository;
import com.yeowool.raid.database.RaidInstanceRepository;
import com.yeowool.raid.database.RaidSchemaInitializer;
import com.yeowool.raid.party.RaidPartyLookup;
import com.yeowool.raid.worldboss.WorldBossCommand;
import com.yeowool.raid.worldboss.WorldBossRepository;
import com.yeowool.raid.worldboss.WorldBossService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class YeowoolRaid extends JavaPlugin {

    private ExecutorService executor;
    private WorldBossService worldBoss;

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

        if (getConfig().getBoolean("world-boss.enabled", true)) {
            enableWorldBoss(core);
        }

        boolean betterHudEnabled = Bukkit.getPluginManager().isPluginEnabled("BetterHud");
        getLogger().info("YeowoolRaid가 활성화되었습니다." + (betterHudEnabled ? "" : " (BetterHud 없이 채팅 폴백 모드)"));
    }

    private void enableWorldBoss(YeowoolCoreAPI core) {
        WorldBossRepository repository = new WorldBossRepository(core.dataSource());
        try {
            repository.createTables();
        } catch (Exception e) {
            getLogger().severe("월드보스 데이터베이스 초기화 실패 — 월드보스를 끕니다: " + e.getMessage());
            return;
        }
        LocalTime spawnTime;
        try {
            spawnTime = LocalTime.parse(getConfig().getString("world-boss.spawn-time", "21:00").trim());
        } catch (DateTimeParseException e) {
            getLogger().warning("world-boss.spawn-time을 읽지 못해 21:00으로 설정합니다: " + e.getMessage());
            spawnTime = LocalTime.of(21, 0);
        }
        List<Long> rankRewards = getConfig().getLongList("world-boss.rank-rewards");
        Map<Integer, List<String>> rankCommands = new HashMap<>();
        var rankSection = getConfig().getConfigurationSection("world-boss.rank-commands");
        if (rankSection != null) {
            for (String key : rankSection.getKeys(false)) {
                try {
                    rankCommands.put(Integer.parseInt(key), rankSection.getStringList(key));
                } catch (NumberFormatException e) {
                    getLogger().warning("world-boss.rank-commands의 '" + key + "'는 순위 숫자여야 합니다 — 건너뜁니다.");
                }
            }
        }
        WorldBossService.Settings settings = new WorldBossService.Settings(
                getConfig().getString("world-boss.world", "wild_world"),
                getConfig().getString("world-boss.mythic-mob", "alocTheDemonicMech"),
                spawnTime,
                Math.max(0, getConfig().getInt("world-boss.announce-minutes", 10)),
                Math.max(1, getConfig().getInt("world-boss.fight-minutes", 30)),
                rankRewards,
                getConfig().getLong("world-boss.participation-reward", 5000),
                getConfig().getDouble("world-boss.min-damage-percent", 1) / 100.0,
                rankCommands,
                getConfig().getStringList("world-boss.participation-commands"),
                getConfig().getDouble("world-boss.participation-command-chance", 20.0),
                getConfig().getLongList("world-boss.rank-stardust"),
                getConfig().getLong("world-boss.participation-stardust", 5));
        MessageManager messages = new MessageManager(this);
        this.worldBoss = new WorldBossService(this, core, messages, repository, executor, settings);
        getServer().getPluginManager().registerEvents(worldBoss, this);
        var command = getCommand("월드보스");
        if (command != null) {
            var executorCmd = new WorldBossCommand(this, messages, worldBoss, executor);
            command.setExecutor(executorCmd);
            command.setTabCompleter(executorCmd);
        }
        worldBoss.start();
        WorldBossService service = worldBoss;
        if (service.isOwner()) {
            getServer().getScheduler().runTaskTimer(this, service::ownerTick, 20L * 5, 20L * 5);
            getLogger().info("월드보스 진행 서버입니다 (" + settings.world() + ").");
        } else {
            getServer().getScheduler().runTaskTimer(this, () -> executor.execute(service::poll), 20L * 30, 20L * 30);
        }
    }

    @Override
    public void onDisable() {
        if (worldBoss != null) {
            worldBoss.shutdown();
        }
        if (executor != null) {
            executor.shutdown();
        }
    }
}
