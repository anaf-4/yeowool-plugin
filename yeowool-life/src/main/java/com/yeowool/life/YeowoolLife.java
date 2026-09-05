package com.yeowool.life;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.message.MessageManager;
import com.yeowool.core.util.ConfigMerger;
import com.yeowool.life.autofarm.AutoFarmCommand;
import com.yeowool.life.autofarm.AutoFarmHarvestListener;
import com.yeowool.life.autofarm.AutoFarmJoinListener;
import com.yeowool.life.autofarm.AutoFarmManager;
import com.yeowool.life.autofarm.AutoFarmType;
import com.yeowool.life.autofarm.AutoFarmVoucherItem;
import com.yeowool.life.autofarm.AutoFarmVoucherListener;
import com.yeowool.life.bag.BagAutoCollectListener;
import com.yeowool.life.bag.BagCommand;
import com.yeowool.life.bag.BagExpandTicketCommand;
import com.yeowool.life.bag.BagManager;
import com.yeowool.life.bag.BagPlayerListener;
import com.yeowool.life.bag.TicketChestListener;
import com.yeowool.life.bag.database.BagSchemaInitializer;
import com.yeowool.life.bag.repository.BagRepository;
import com.yeowool.life.farming.CropTimerService;
import com.yeowool.life.farming.FarmingListener;
import com.yeowool.life.farming.FarmlandProtectionListener;
import com.yeowool.life.farming.GrowthInfoListener;
import com.yeowool.life.farming.custom.CustomCropRegistry;
import com.yeowool.life.farming.custom.CustomCropTimerService;
import com.yeowool.life.farming.custom.CustomFarmingListener;
import com.yeowool.life.farming.custom.CustomFarmingQualityConfig;
import com.yeowool.life.farming.custom.database.CustomFarmingSchemaInitializer;
import com.yeowool.life.farming.custom.repository.CustomCropRepository;
import com.yeowool.life.farming.database.FarmingSchemaInitializer;
import com.yeowool.life.farming.repository.CropRepository;
import com.yeowool.life.dex.DexCommand;
import com.yeowool.life.dex.DexConfigLoader;
import com.yeowool.life.dex.DexEntry;
import com.yeowool.life.fishing.BaitEquipListener;
import com.yeowool.life.fishing.FishBait;
import com.yeowool.life.fishing.FishMinigameConfig;
import com.yeowool.life.fishing.FishRarity;
import com.yeowool.life.fishing.FishRod;
import com.yeowool.life.fishing.FishSpecies;
import com.yeowool.life.fishing.FishStarConfig;
import com.yeowool.life.fishing.FishWaitTime;
import com.yeowool.life.fishing.FishAdminCommand;
import com.yeowool.life.fishing.FishGiveCommand;
import com.yeowool.life.fishing.customfishing.CustomFishingBridge;
import com.yeowool.life.fishing.customfishing.CustomFishingCatchListener;
import com.yeowool.life.fishing.FishingCompetitionCommand;
import com.yeowool.life.fishing.FishingCompetitionManager;
import com.yeowool.life.fishing.FishingListener;
import com.yeowool.life.hunting.HuntingListener;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import com.yeowool.life.logging.LoggingListener;
import com.yeowool.life.logging.tree.SaplingListener;
import com.yeowool.life.logging.tree.TreeTimerService;
import com.yeowool.life.logging.tree.database.TreeSchemaInitializer;
import com.yeowool.life.logging.tree.repository.TreeRepository;
import com.yeowool.life.job.JobCommand;
import com.yeowool.life.job.JobConfigLoader;
import com.yeowool.life.job.JobJoinListener;
import com.yeowool.life.job.JobManager;
import com.yeowool.life.job.database.JobSchemaInitializer;
import com.yeowool.life.job.repository.JobRepository;
import com.yeowool.life.job.action.JobAlchemistListener;
import com.yeowool.life.job.action.JobBlacksmithListener;
import com.yeowool.life.job.action.JobBuilderListener;
import com.yeowool.life.job.action.JobDiggerListener;
import com.yeowool.life.job.action.JobEnchanterListener;
import com.yeowool.life.job.action.JobFarmerListener;
import com.yeowool.life.job.action.JobFishermanListener;
import com.yeowool.life.job.action.JobHunterListener;
import com.yeowool.life.job.action.JobMinerListener;
import com.yeowool.life.job.action.JobWoodCutterListener;
import com.yeowool.life.mining.MiningListener;
import com.yeowool.life.ranch.RanchListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 생활 콘텐츠 (기획서 6절): 농사, 벌목, 목축, 낚시, 채광. 각 영역은 패키지로
 * 나뉘어 있으며 배포는 하나의 플러그인으로 묶는다 (13절 권장 구조).
 */
public final class YeowoolLife extends JavaPlugin {

    private ExecutorService executor;
    private BagManager bagManager;

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

        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "YeowoolLife-Worker");
            thread.setDaemon(true);
            return thread;
        });

        try {
            FarmingSchemaInitializer.initialize(core.dataSource());
            TreeSchemaInitializer.initialize(core.dataSource());
            JobSchemaInitializer.initialize(core.dataSource());
            BagSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("생활 콘텐츠 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        var config = getConfig();

        CropRepository cropRepository = new CropRepository(core.dataSource());
        CropTimerService cropTimerService = new CropTimerService(this, cropRepository, executor,
                config.getInt("farming.growth-minutes", 10));
        cropTimerService.reconcileOnStartup();
        getServer().getScheduler().runTaskTimer(this, cropTimerService::refreshFarmlandMoisture, 100L, 100L);

        TreeRepository treeRepository = new TreeRepository(core.dataSource());
        TreeTimerService treeTimerService = new TreeTimerService(this, treeRepository, executor,
                config.getInt("logging.sapling-growth-minutes", 15));
        treeTimerService.reconcileOnStartup();

        getServer().getPluginManager().registerEvents(
                new FarmingListener(core, cropTimerService, config.getLong("farming.xp-per-harvest", 5)), this);
        getServer().getPluginManager().registerEvents(new FarmlandProtectionListener(), this);

        // 자동줍기권/자동심기권
        AutoFarmManager autoFarmManager = new AutoFarmManager(core);
        AutoFarmVoucherItem autoFarmVoucherItem = new AutoFarmVoucherItem(this);
        getServer().getPluginManager().registerEvents(new AutoFarmHarvestListener(this, autoFarmManager), this);
        getServer().getPluginManager().registerEvents(new AutoFarmJoinListener(this, autoFarmManager), this);
        getServer().getPluginManager().registerEvents(
                new AutoFarmVoucherListener(autoFarmManager, autoFarmVoucherItem, messages), this);
        var autoPickupCommand = getCommand("자동줍기권");
        if (autoPickupCommand != null) {
            var executorCmd = new AutoFarmCommand(AutoFarmType.PICKUP, autoFarmVoucherItem, messages);
            autoPickupCommand.setExecutor(executorCmd);
            autoPickupCommand.setTabCompleter(executorCmd);
        }
        var autoPlantCommand = getCommand("자동심기권");
        if (autoPlantCommand != null) {
            var executorCmd = new AutoFarmCommand(AutoFarmType.PLANT, autoFarmVoucherItem, messages);
            autoPlantCommand.setExecutor(executorCmd);
            autoPlantCommand.setTabCompleter(executorCmd);
        }

        // 작물/광물/낚시/목축 가방
        BagRepository bagRepository = new BagRepository(core.dataSource());
        BagManager bagManager = new BagManager(bagRepository, executor, getLogger());
        this.bagManager = bagManager;
        getServer().getPluginManager().registerEvents(new BagPlayerListener(bagManager), this);
        getServer().getPluginManager().registerEvents(new BagAutoCollectListener(bagManager), this);
        getServer().getPluginManager().registerEvents(new TicketChestListener(bagManager, messages), this);
        Bukkit.getOnlinePlayers().forEach(online -> bagManager.loadAsync(online.getUniqueId()));
        getServer().getScheduler().runTaskTimerAsynchronously(this, bagManager::flushAllDirty, 20L * 60 * 5, 20L * 60 * 5);
        var bagCommand = getCommand("가방");
        if (bagCommand != null) {
            bagCommand.setExecutor(new BagCommand(bagManager, messages));
        }
        var bagTicketCommand = getCommand("가방확장권");
        if (bagTicketCommand != null) {
            var executorCmd = new BagExpandTicketCommand(messages);
            bagTicketCommand.setExecutor(executorCmd);
            bagTicketCommand.setTabCompleter(executorCmd);
        }

        getServer().getPluginManager().registerEvents(new GrowthInfoListener(this, cropTimerService, treeTimerService), this);
        getServer().getPluginManager().registerEvents(new SaplingListener(treeTimerService), this);
        LoggingListener loggingListener = new LoggingListener(core, config.getLong("logging.xp-per-log", 2));
        getServer().getPluginManager().registerEvents(loggingListener, this);
        // Bounds playerPlacedLogs' memory growth on a long-running server - infrequent since it's
        // pure cleanup, not gameplay-visible.
        getServer().getScheduler().runTaskTimer(this, loggingListener::pruneStaleEntries, 20L * 60 * 20, 20L * 60 * 20);
        getServer().getPluginManager().registerEvents(
                new RanchListener(core, config.getLong("ranch.xp-per-breed", 10), config.getInt("ranch.max-animals-per-chunk", 16)),
                this);
        List<FishRarity> fishRarities = loadRarities();
        Map<String, FishRod> fishRods = loadFishRods();
        Map<String, FishBait> fishBaits = loadFishBaits();
        FishWaitTime fishWaitTime = new FishWaitTime(
                config.getInt("fishing.wait-time.min-seconds", 5) * 20,
                config.getInt("fishing.wait-time.max-seconds", 30) * 20);
        FishMinigameConfig fishMinigame = new FishMinigameConfig(
                config.getLong("fishing.minigame.total-duration-ms", 2500),
                config.getLong("fishing.minigame.sweet-spot-min-width-ms", 400),
                config.getLong("fishing.minigame.sweet-spot-max-width-ms", 700),
                config.getLong("fishing.minigame.good-buffer-ms", 300),
                config.getDouble("fishing.minigame.perfect-rare-bonus", 1.3),
                config.getDouble("fishing.minigame.good-rare-bonus", 1.1),
                config.getDouble("fishing.minigame.perfect-size-bonus-percent", 25),
                config.getDouble("fishing.minigame.good-size-bonus-percent", 10),
                config.getDouble("fishing.minigame.miss-fail-chance-percent", 40));
        FishStarConfig fishStar = new FishStarConfig(
                config.getDouble("fishing.star.chance-percent", 2),
                config.getDouble("fishing.star.size-bonus-percent", 40));
        Map<Integer, Long> competitionRewards = new HashMap<>();
        var rewardSection = config.getConfigurationSection("fishing.competition.rewards");
        if (rewardSection != null) {
            for (String rank : rewardSection.getKeys(false)) {
                try {
                    competitionRewards.put(Integer.parseInt(rank), rewardSection.getLong(rank));
                } catch (NumberFormatException ignored) {
                    // 숫자가 아닌 키는 무시
                }
            }
        }
        FishingCompetitionManager competitionManager = new FishingCompetitionManager(this, core, messages,
                config.getInt("fishing.competition.start-hour", 18),
                config.getLong("fishing.competition.duration-minutes", 60),
                competitionRewards);
        competitionManager.scheduleNextStart();

        // 낚시 플레이 자체는 CustomFishing이 설치되어 있으면 그쪽 메커닉을 그대로 씀 —
        // 우리 자체 미니게임(입질 타이밍 등)은 CustomFishing이 없을 때의 대체 수단으로만 남김.
        if (!CustomFishingBridge.isEnabled()) {
            getServer().getPluginManager().registerEvents(
                    new FishingListener(this, core, messages, config.getLong("fishing.xp-per-catch", 3), fishRarities, fishRods, fishBaits,
                            fishWaitTime, fishMinigame, fishStar, competitionManager), this);
        }
        var competitionCommand = getCommand("낚시대회");
        if (competitionCommand != null) {
            competitionCommand.setExecutor(new FishingCompetitionCommand(competitionManager));
        }
        getServer().getPluginManager().registerEvents(new BaitEquipListener(core, messages, fishBaits), this);
        getServer().getPluginManager().registerEvents(
                new MiningListener(core, config.getLong("mining.xp-per-ore", 4)), this);
        getServer().getPluginManager().registerEvents(
                new HuntingListener(core, config.getLong("hunting.xp-per-kill", 3)), this);

        // /도감·/물고기지급에는 CustomFishing 물고기를 합침 (도감 표시용 목록만 —
        // 실제 낚시 메커닉은 위에서 이미 CustomFishing 쪽으로 넘어감).
        List<FishRarity> dexFishRarities = new ArrayList<>(fishRarities);
        boolean customFishingEnabled = CustomFishingBridge.isEnabled();
        if (customFishingEnabled) {
            dexFishRarities.add(CustomFishingBridge.buildRarity());
        }

        List<DexEntry> miningDex = DexConfigLoader.load(this, "mining");
        List<DexEntry> huntingDex = DexConfigLoader.load(this, "hunting");
        List<DexEntry> farmingDex = DexConfigLoader.load(this, "farming");
        var catalogCommand = getCommand("도감");
        if (catalogCommand != null) {
            int fishBackgroundOffset = config.getInt("fishing.gui-background-offset", -46);
            catalogCommand.setExecutor(new DexCommand(core, messages, dexFishRarities, miningDex, huntingDex, farmingDex, fishBackgroundOffset));
        }

        var fishAdminCommand = getCommand("낚시관리");
        if (fishAdminCommand != null) {
            int fishBackgroundOffset = config.getInt("fishing.gui-background-offset", -46);
            fishAdminCommand.setExecutor(new FishAdminCommand(messages, dexFishRarities, fishBackgroundOffset));
        }
        var fishGiveCommand = getCommand("물고기지급");
        if (fishGiveCommand != null) {
            int fishBackgroundOffset = config.getInt("fishing.gui-background-offset", -46);
            fishGiveCommand.setExecutor(new FishGiveCommand(messages, dexFishRarities, fishBackgroundOffset));
        }

        // 직업 시스템 (연금술사/대장장이/건축가/도굴꾼/인챈터/농부/어부/사냥꾼/광부/목수 - 동시에 하나만 활성화 가능)
        Map<String, com.yeowool.life.job.JobDefinition> jobDefinitions = JobConfigLoader.loadJobs(this);
        Map<String, com.yeowool.life.job.SkillNode> jobSkills = JobConfigLoader.loadSkills(this);
        JobRepository jobRepository = new JobRepository(core.dataSource());
        JobManager jobManager = new JobManager(this, core, jobRepository, executor, jobDefinitions, jobSkills,
                config.getDouble("jobs.xp-curve.base", 100), config.getDouble("jobs.xp-curve.exponent", 1.8),
                config.getInt("jobs.max-level", 50), config.getLong("jobs.bonus-currency-per-proc", 10));

        if (customFishingEnabled) {
            getServer().getPluginManager().registerEvents(
                    new CustomFishingCatchListener(core, jobManager, config.getLong("fishing.xp-per-catch", 3), competitionManager), this);
            getLogger().info("CustomFishing 연동이 활성화되었습니다 — 낚시 플레이는 CustomFishing, 어부 XP/땅 XP/도감/낚시대회는 그대로 연결됨.");
        }

        getServer().getPluginManager().registerEvents(new JobJoinListener(this, jobManager), this);
        getServer().getPluginManager().registerEvents(
                new JobAlchemistListener(jobManager, config.getLong("jobs.xp-per-action.alchemist", 6)), this);
        getServer().getPluginManager().registerEvents(
                new JobBlacksmithListener(jobManager, config.getLong("jobs.xp-per-action.blacksmith", 5)), this);
        getServer().getPluginManager().registerEvents(
                new JobBuilderListener(jobManager, config.getLong("jobs.xp-per-action.builder", 3)), this);
        getServer().getPluginManager().registerEvents(
                new JobDiggerListener(jobManager, config.getLong("jobs.xp-per-action.digger", 8)), this);
        getServer().getPluginManager().registerEvents(
                new JobEnchanterListener(jobManager, config.getLong("jobs.xp-per-action.enchanter", 6)), this);
        getServer().getPluginManager().registerEvents(
                new JobFarmerListener(jobManager, config.getLong("jobs.xp-per-action.farmer", 5)), this);
        getServer().getPluginManager().registerEvents(
                new JobFishermanListener(jobManager, config.getLong("jobs.xp-per-action.fisherman", 5)), this);
        getServer().getPluginManager().registerEvents(
                new JobHunterListener(jobManager, config.getLong("jobs.xp-per-action.hunter", 4)), this);
        getServer().getPluginManager().registerEvents(
                new JobMinerListener(jobManager, config.getLong("jobs.xp-per-action.miner", 4)), this);
        getServer().getPluginManager().registerEvents(
                new JobWoodCutterListener(jobManager, config.getLong("jobs.xp-per-action.wood_cutter", 2)), this);

        var jobCommand = getCommand("직업");
        if (jobCommand != null) {
            var executorCmd = new JobCommand(jobManager);
            jobCommand.setExecutor(executorCmd);
            jobCommand.setTabCompleter(executorCmd);
        }
        for (var player : Bukkit.getOnlinePlayers()) {
            jobManager.load(player.getUniqueId());
        }

        if (getServer().getPluginManager().isPluginEnabled("ItemsAdder")) {
            enableCustomFarming(core);
        }

        getLogger().info("YeowoolLife가 활성화되었습니다.");
    }

    private void enableCustomFarming(YeowoolCoreAPI core) {
        CustomCropRegistry registry = new CustomCropRegistry(this);
        if (registry.isEmpty()) {
            return;
        }
        try {
            CustomFarmingSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("커스텀 작물 데이터베이스 초기화 실패: " + e.getMessage());
            return;
        }

        CustomCropRepository repository = new CustomCropRepository(core.dataSource());
        CustomCropTimerService timerService = new CustomCropTimerService(this, repository, registry, executor);
        timerService.reconcileOnStartup();

        long customFarmingIncomePerHarvest = getConfig().getLong("custom-farming.income-per-harvest", 30);
        CustomFarmingQualityConfig customFarmingQuality = new CustomFarmingQualityConfig(
                getConfig().getDouble("custom-farming.quality.silver-chance-percent", 15),
                getConfig().getDouble("custom-farming.quality.gold-chance-percent", 3),
                getConfig().getDouble("custom-farming.quality.silver-multiplier", 1.5),
                getConfig().getDouble("custom-farming.quality.gold-multiplier", 3.0));
        getServer().getPluginManager().registerEvents(
                new CustomFarmingListener(core, registry, timerService, customFarmingIncomePerHarvest, customFarmingQuality), this);
        getLogger().info("ItemsAdder 커스텀 작물 시스템이 활성화되었습니다.");
    }

    @SuppressWarnings("unchecked")
    private List<FishRarity> loadRarities() {
        List<Map<?, ?>> raw = getConfig().getMapList("fishing.rarities");
        List<FishRarity> rarities = new ArrayList<>();
        for (Map<?, ?> rawEntry : raw) {
            try {
                Map<String, Object> entry = (Map<String, Object>) rawEntry;
                String name = entry.get("name").toString();
                int weight = ((Number) entry.get("weight")).intValue();
                Object colorValue = entry.get("color");
                NamedTextColor color = NamedTextColor.NAMES.value(colorValue == null ? "white" : colorValue.toString());
                List<FishSpecies> species = new ArrayList<>();
                Object fishList = entry.get("fish");
                for (Map<?, ?> fishEntry : fishList == null ? List.<Map<?, ?>>of() : (List<Map<?, ?>>) fishList) {
                    species.add(new FishSpecies(
                            fishEntry.get("id").toString(),
                            fishEntry.get("name").toString(),
                            Material.valueOf(fishEntry.get("material").toString()),
                            fishEntry.get("custom-icon") == null ? null : fishEntry.get("custom-icon").toString(),
                            fishEntry.get("description") == null ? "" : fishEntry.get("description").toString(),
                            fishEntry.get("min-size-cm") == null ? 10.0 : ((Number) fishEntry.get("min-size-cm")).doubleValue(),
                            fishEntry.get("max-size-cm") == null ? 30.0 : ((Number) fishEntry.get("max-size-cm")).doubleValue(),
                            null
                    ));
                }
                rarities.add(new FishRarity(name, weight, color == null ? NamedTextColor.WHITE : color, species));
            } catch (Exception e) {
                getLogger().warning("fishing.rarities 설정 항목이 잘못되었습니다: " + rawEntry);
            }
        }
        if (rarities.isEmpty()) {
            rarities.add(new FishRarity("일반", 1, NamedTextColor.WHITE,
                    List.of(new FishSpecies("fish", "물고기", Material.COD, null, "", 10.0, 30.0, null))));
        }
        return rarities;
    }

    private Map<String, FishRod> loadFishRods() {
        Map<String, FishRod> rods = new HashMap<>();
        var section = getConfig().getConfigurationSection("fishing.rods");
        if (section == null) {
            return rods;
        }
        for (String id : section.getKeys(false)) {
            String display = section.getString(id + ".display", id);
            String customIconId = section.getString(id + ".custom-icon", null);
            double multiplier = section.getDouble(id + ".rare-weight-multiplier", 1.0);
            if (customIconId == null) {
                continue;
            }
            rods.put(customIconId, new FishRod(id, display, customIconId, multiplier));
        }
        return rods;
    }

    private Map<String, FishBait> loadFishBaits() {
        Map<String, FishBait> baits = new HashMap<>();
        var section = getConfig().getConfigurationSection("fishing.baits");
        if (section == null) {
            return baits;
        }
        for (String id : section.getKeys(false)) {
            String display = section.getString(id + ".display", id);
            String customIconId = section.getString(id + ".custom-icon", null);
            double multiplier = section.getDouble(id + ".rare-weight-multiplier", 1.0);
            if (customIconId == null) {
                continue;
            }
            baits.put(customIconId, new FishBait(id, display, customIconId, multiplier));
        }
        return baits;
    }

    @Override
    public void onDisable() {
        if (bagManager != null) {
            bagManager.saveAllBlocking();
        }
        if (executor != null) {
            executor.shutdown();
            // executor's worker threads are daemons (see onEnable), so if the JVM
            // starts exiting while a crop/tree timer insert-delete is still queued
            // or mid-flight, it gets killed before the write ever reaches the
            // database - the exact way a growing crop's persisted state silently
            // disappears and never resumes after a restart. Block here so every
            // already-submitted save actually finishes first.
            try {
                if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    getLogger().warning("일부 저장 작업이 종료 시간(10초) 내에 끝나지 않았습니다.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
