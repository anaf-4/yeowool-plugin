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
import com.yeowool.life.farming.customcrops.CustomCropsHarvestListener;
import com.yeowool.life.farming.customcrops.CustomCropsSeasonSyncTask;
import com.yeowool.life.cooking.addcook.CookXpListener;
import com.yeowool.life.cooking.addcook.MyRecipesCommand;
import com.yeowool.life.cooking.addcook.AddCookRecipeIndex;
import com.yeowool.life.cooking.orders.CookingOrderCatalog;
import com.yeowool.life.fishing.orders.FishingOrderCatalog;
import com.yeowool.life.fishing.orders.FishingOrderRules;
import com.yeowool.life.orders.OrderCatalog;
import com.yeowool.life.orders.OrderCommand;
import com.yeowool.life.orders.OrderNpcListener;
import com.yeowool.life.orders.OrderRepository;
import com.yeowool.life.orders.OrderRules;
import com.yeowool.life.orders.OrderService;
import com.yeowool.life.pets.MCPetsXpListener;
import com.yeowool.life.farming.database.FarmingSchemaInitializer;
import com.yeowool.life.farming.repository.CropRepository;
import com.yeowool.life.donation.DonationCommand;
import com.yeowool.life.donation.DonationRepository;
import com.yeowool.life.donation.DonationService;
import com.yeowool.life.dex.DexCommand;
import com.yeowool.life.dex.DexConfigLoader;
import com.yeowool.life.dex.DexEntry;
import com.yeowool.life.dex.DexRewardService;
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
import com.yeowool.life.fishing.customfishing.BaitUnequipCommand;
import com.yeowool.life.fishing.customfishing.CustomFishingBridge;
import com.yeowool.life.fishing.customfishing.CustomFishingCatchListener;
import com.yeowool.life.fishing.customfishing.CustomFishingMenuCommand;
import com.yeowool.life.fishing.customfishing.CustomFishingNativeFishExporter;
import com.yeowool.life.fishing.FishingListener;
import com.yeowool.life.competition.CompetitionActivity;
import com.yeowool.life.competition.CompetitionRepository;
import com.yeowool.life.competition.CompetitionSchedule;
import com.yeowool.life.competition.LifeCompetitionListener;
import com.yeowool.life.competition.LifeCompetitionService;
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
import com.yeowool.life.metals.MetalService;
import com.yeowool.life.mining.MiningListener;
import com.yeowool.life.mount.MountCatalog;
import com.yeowool.life.mount.MountCommand;
import com.yeowool.life.mount.MountDefinition;
import com.yeowool.life.mount.MountVoucherItem;
import com.yeowool.life.mount.MountVoucherListener;
import com.yeowool.life.ranch.RanchListener;
import com.yeowool.life.scrapyard.ScrapyardAdminCommand;
import com.yeowool.life.scrapyard.ScrapyardConfig;
import com.yeowool.life.scrapyard.ScrapyardListener;
import com.yeowool.life.scrapyard.ScrapyardLocationStore;
import com.yeowool.life.scrapyard.ScrapyardMobSpawnTask;
import com.yeowool.life.scrapyard.ScrapyardRepository;
import com.yeowool.life.scrapyard.ScrapyardSchemaInitializer;
import com.yeowool.life.scrapyard.ScrapyardSessionManager;
import com.yeowool.life.scrapyard.ScrapyardTickTask;
import com.yeowool.life.surprise.SurpriseEventCommand;
import com.yeowool.life.surprise.SurpriseEventRepository;
import com.yeowool.life.surprise.SurpriseEventRules;
import com.yeowool.life.surprise.SurpriseEventService;
import com.yeowool.life.surprise.SurpriseEventType;
import com.yeowool.life.treasure.TreasureCommand;
import com.yeowool.life.treasure.TreasureListener;
import com.yeowool.life.treasure.TreasureMapItem;
import com.yeowool.life.treasure.TreasureRepository;
import com.yeowool.life.treasure.TreasureService;
import com.yeowool.life.treasure.TreasureTier;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * 생활 콘텐츠 (기획서 6절): 농사, 벌목, 목축, 낚시, 채광. 각 영역은 패키지로
 * 나뉘어 있으며 배포는 하나의 플러그인으로 묶는다 (13절 권장 구조).
 */
public final class YeowoolLife extends JavaPlugin {

    private ExecutorService executor;
    private LifeCompetitionService lifeCompetition;
    private BagManager bagManager;
    private SurpriseEventService surpriseEvents;
    private DonationService donation;

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

        // CustomCrops의 계절은 서버(로비/타운/야생)마다 따로 흘러서 방치하면 어긋남 —
        // 실제 시각 기준으로 계절을 계산해서 주기적으로 맞춰줌(서버 간 통신 불필요, 각자
        // 같은 공식으로 계산하니 자연히 일치함).
        if (Bukkit.getPluginManager().isPluginEnabled("CustomCrops")) {
            if (config.getBoolean("customcrops.season-sync.enable", true)) {
                long intervalTicks = config.getLong("customcrops.season-sync.check-interval-seconds", 60) * 20L;
                new CustomCropsSeasonSyncTask(this).runTaskTimer(this, 100L, intervalTicks);
            }
            getServer().getPluginManager().registerEvents(
                    new CustomCropsHarvestListener(core, config.getLong("customcrops.xp-per-harvest", 5)), this);
        }

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
        // 낚시 플레이 자체는 CustomFishing이 설치되어 있으면 그쪽 메커닉을 그대로 씀 —
        // 우리 자체 미니게임(입질 타이밍 등)은 CustomFishing이 없을 때의 대체 수단으로만 남김.
        if (!CustomFishingBridge.isEnabled()) {
            getServer().getPluginManager().registerEvents(
                    new FishingListener(this, core, messages, config.getLong("fishing.xp-per-catch", 3), fishRarities, fishRods, fishBaits,
                            fishWaitTime, fishMinigame, fishStar), this);
        } else {
            // 낚시 메커닉이 CustomFishing으로 완전히 넘어가서, 우리 자체 물고기도 그쪽 loot
            // 풀에 직접 등록해줘야 실제로 잡을 수 있음 — 안 그러면 도감/지급 GUI에만 보이는
            // 장식용 목록으로 남음.
            CustomFishingNativeFishExporter.export(this, fishRarities);
        }
        getServer().getPluginManager().registerEvents(new BaitEquipListener(core, messages, fishBaits), this);
        MiningListener miningListener = new MiningListener(core, config.getLong("mining.xp-per-ore", 4));
        getServer().getPluginManager().registerEvents(miningListener, this);
        getServer().getScheduler().runTaskTimer(this, miningListener::pruneStaleEntries, 20L * 60 * 20, 20L * 60 * 20);
        getServer().getPluginManager().registerEvents(
                new HuntingListener(core, config.getLong("hunting.xp-per-kill", 3)), this);

        // /도감·/낚시관리·/물고기지급은 CustomFishing 물고기를 합쳐서 보여주지만, 매번 명령어를
        // 칠 때 그 순간의 상태를 새로 읽는다(아래 각 커맨드 클래스 참고) — 여기서 한 번만
        // 캐싱해두면 CustomFishing 쪽 loot 등록이 아직 안 끝난 상태를 그대로 굳혀버릴 수 있음.
        boolean customFishingEnabled = CustomFishingBridge.isEnabled();

        List<DexEntry> miningDex = DexConfigLoader.load(this, "mining");
        List<DexEntry> huntingDex = DexConfigLoader.load(this, "hunting");
        List<DexEntry> farmingDex = DexConfigLoader.load(this, "farming");
        Supplier<List<FishRarity>> fishRaritySupplier = () -> {
            if (!customFishingEnabled) {
                return fishRarities;
            }
            List<FishRarity> combined = new ArrayList<>(fishRarities);
            combined.add(CustomFishingBridge.buildRarity());
            return combined;
        };
        DexRewardService dexRewards = null;
        if (config.getBoolean("dex-rewards.enabled", true)) {
            List<DexRewardService.Milestone> milestones = new ArrayList<>();
            ConfigurationSection milestoneSection = config.getConfigurationSection("dex-rewards.milestones");
            if (milestoneSection != null) {
                for (String key : milestoneSection.getKeys(false)) {
                    int percent;
                    try {
                        percent = Integer.parseInt(key);
                    } catch (NumberFormatException e) {
                        percent = -1;
                    }
                    if (percent < 1 || percent > 100) {
                        getLogger().warning("dex-rewards.milestones의 '" + key + "'는 1~100 사이 숫자(퍼센트)여야 합니다 — 건너뜁니다.");
                        continue;
                    }
                    milestones.add(new DexRewardService.Milestone(percent,
                            milestoneSection.getLong(key + ".money", 0),
                            milestoneSection.getStringList(key + ".commands")));
                }
            }
            DexRewardService service = new DexRewardService(this, core, messages, milestones, fishRaritySupplier,
                    () -> !customFishingEnabled || !CustomFishingBridge.buildRarity().species().isEmpty(),
                    miningDex, huntingDex, farmingDex);
            getServer().getScheduler().runTaskTimer(this, service::checkAll, 20L * 60, 20L * 60);
            getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                    var player = event.getPlayer();
                    getServer().getScheduler().runTaskLater(YeowoolLife.this, () -> {
                        if (player.isOnline()) {
                            service.check(player);
                        }
                    }, 100L);
                }
            }, this);
            dexRewards = service;
        }
        var catalogCommand = getCommand("도감");
        if (catalogCommand != null) {
            int fishBackgroundOffset = config.getInt("fishing.gui-background-offset", -46);
            catalogCommand.setExecutor(new DexCommand(core, messages, fishRaritySupplier, miningDex, huntingDex, farmingDex, fishBackgroundOffset, dexRewards));
        }

        var fishAdminCommand = getCommand("낚시관리");
        if (fishAdminCommand != null) {
            int fishBackgroundOffset = config.getInt("fishing.gui-background-offset", -46);
            fishAdminCommand.setExecutor(new FishAdminCommand(messages, fishRarities, customFishingEnabled, fishBackgroundOffset));
        }
        var fishGiveCommand = getCommand("물고기지급");
        if (fishGiveCommand != null) {
            int fishBackgroundOffset = config.getInt("fishing.gui-background-offset", -46);
            fishGiveCommand.setExecutor(new FishGiveCommand(messages, fishRarities, customFishingEnabled, fishBackgroundOffset));
        }

        var customFishingMenuCommand = getCommand("커스텀물고기");
        if (customFishingMenuCommand != null && customFishingEnabled) {
            int fishBackgroundOffset = config.getInt("fishing.gui-background-offset", -46);
            customFishingMenuCommand.setExecutor(new CustomFishingMenuCommand(messages, fishBackgroundOffset));
        }
        var baitCommand = getCommand("미끼");
        if (baitCommand != null && customFishingEnabled) {
            baitCommand.setExecutor(new BaitUnequipCommand(messages));
        }

        var myRecipesCommand = getCommand("레시피");
        if (Bukkit.getPluginManager().isPluginEnabled("AddCook")) {
            if (myRecipesCommand != null) {
                myRecipesCommand.setExecutor(new MyRecipesCommand(messages));
            }
            getServer().getPluginManager().registerEvents(
                    new CookXpListener(core, config.getLong("cooking.xp-per-cook", 5)), this);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("MCPets")) {
            getServer().getPluginManager().registerEvents(new MCPetsXpListener(core,
                    config.getLong("pets.xp-per-tame", 20), config.getLong("pets.xp-per-levelup", 5)), this);
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
                    new CustomFishingCatchListener(core, jobManager, config.getLong("fishing.xp-per-catch", 3)), this);
            getLogger().info("CustomFishing 연동이 활성화되었습니다 — 낚시 플레이는 CustomFishing, 어부 XP/땅 XP/도감/생활 대회는 그대로 연결됨.");
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

        enableScrapyard(core, messages);
        enableTreasureMaps(core, messages);
        enableLifeCompetition(core, messages);
        enableMounts(core, messages);
        enableSurpriseEvents(core, messages);
        enableCookingOrders(core, messages);
        enableFishingOrders(core, messages, jobManager, fishRarities);
        enableDonation(core, messages);
        enableMetals(core, messages, jobManager, miningListener);

        getLogger().info("YeowoolLife가 활성화되었습니다.");
    }

    /**
     * 여울 폐기장 — 하루 1회 입장 제한 미니게임. 실제 맵(진입점/출구/구역/상자/
     * 몹 스폰 지점)은 코드로 미리 짓지 못하니 {@code /폐기장설정}으로 관리자가
     * 인게임에서 등록해야 실제로 동작함 — 등록 전까지는 {@code enter()}가
     * {@code NOT_CONFIGURED}를 반환해 안전하게 아무 일도 일어나지 않는다.
     */
    private void enableScrapyard(YeowoolCoreAPI core, MessageManager messages) {
        ScrapyardConfig scrapyardConfig = ScrapyardConfig.load(getConfig());
        if (!scrapyardConfig.enabled()) {
            return;
        }

        String worldName = getConfig().getString("scrapyard.world", "zombie_dungeon");
        if (Bukkit.getWorld(worldName) == null) {
            if (new java.io.File(getServer().getWorldContainer(), worldName).isDirectory()) {
                var loaded = Bukkit.createWorld(new org.bukkit.WorldCreator(worldName));
                if (loaded == null) {
                    getLogger().severe("폐기장 던전 월드(" + worldName + ")를 불러오지 못했습니다.");
                } else {
                    getLogger().info("폐기장 던전 월드(" + worldName + ")를 불러왔습니다.");
                }
            } else {
                getLogger().warning("폐기장 던전 월드 폴더(" + worldName + ")가 없습니다 — /폐기장설정으로 위치를 등록하기 전에 월드 폴더를 서버에 넣어주세요.");
            }
        }

        try {
            ScrapyardSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("폐기장 데이터베이스 초기화 실패: " + e.getMessage());
            return;
        }
        ScrapyardRepository scrapyardRepository = new ScrapyardRepository(core.dataSource());
        ScrapyardLocationStore scrapyardLocationStore = new ScrapyardLocationStore(this, scrapyardRepository, executor);
        try {
            scrapyardLocationStore.loadIntoCache();
        } catch (Exception e) {
            getLogger().severe("폐기장 위치 데이터 로드 실패: " + e.getMessage());
            return;
        }
        ScrapyardSessionManager scrapyardSessionManager = new ScrapyardSessionManager(this, core, scrapyardLocationStore, scrapyardConfig);
        getServer().getPluginManager().registerEvents(
                new ScrapyardListener(this, scrapyardSessionManager, scrapyardLocationStore, scrapyardConfig, messages), this);
        new ScrapyardTickTask(scrapyardSessionManager, scrapyardLocationStore, scrapyardConfig, messages).runTaskTimer(this, 20L, 20L);
        new ScrapyardMobSpawnTask(scrapyardSessionManager, scrapyardLocationStore, scrapyardConfig)
                .runTaskTimer(this, 20L * scrapyardConfig.mobSpawnIntervalSeconds(), 20L * scrapyardConfig.mobSpawnIntervalSeconds());
        var scrapyardAdminCommand = getCommand("폐기장설정");
        if (scrapyardAdminCommand != null) {
            var executorCmd = new ScrapyardAdminCommand(this, core, scrapyardSessionManager, scrapyardLocationStore);
            scrapyardAdminCommand.setExecutor(executorCmd);
            scrapyardAdminCommand.setTabCompleter(executorCmd);
        }
    }

    /** 보물지도 — 채광·낚시·사냥 중 지도 드롭, 야생 월드에서 발굴 (세 서버 공통, 발굴은 dig-world가 있는 서버에서만). */
    private void enableTreasureMaps(YeowoolCoreAPI core, MessageManager messages) {
        var config = getConfig();
        if (!config.getBoolean("treasure.enabled", true)) {
            return;
        }
        TreasureRepository repository = new TreasureRepository(core.dataSource());
        try {
            repository.createTables();
        } catch (Exception e) {
            getLogger().severe("보물지도 데이터베이스 초기화 실패 — 보물지도를 끕니다: " + e.getMessage());
            return;
        }
        Map<String, Double> dropChances = new HashMap<>();
        ConfigurationSection dropSection = config.getConfigurationSection("treasure.drop-chance");
        if (dropSection != null) {
            for (String key : dropSection.getKeys(false)) {
                dropChances.put(key, dropSection.getDouble(key));
            }
        }
        Map<TreasureTier, Integer> tierWeights = new EnumMap<>(TreasureTier.class);
        Map<TreasureTier, TreasureService.TierReward> rewards = new EnumMap<>(TreasureTier.class);
        Map<TreasureTier, String> itemIds = new EnumMap<>(TreasureTier.class);
        for (TreasureTier tier : TreasureTier.values()) {
            String key = tier.configKey();
            tierWeights.put(tier, config.getInt("treasure.tier-weights." + key, 0));
            rewards.put(tier, new TreasureService.TierReward(
                    config.getLong("treasure.tiers." + key + ".money-min", 0),
                    config.getLong("treasure.tiers." + key + ".money-max", 0),
                    config.getInt("treasure.tiers." + key + ".item-rolls", 0),
                    config.getLong("treasure.tiers." + key + ".stardust", 0)));
            itemIds.put(tier, config.getString("treasure.tiers." + key + ".item-id", ""));
        }
        TreasureService.Settings settings = new TreasureService.Settings(
                config.getString("treasure.dig-world", "wild_world"),
                config.getInt("treasure.center-x", 0),
                config.getInt("treasure.center-z", 0),
                config.getInt("treasure.min-radius", 500),
                config.getInt("treasure.max-radius", 3000),
                config.getLong("treasure.expire-days", 7) * 86_400_000L,
                config.getInt("treasure.daily-limit", 3),
                config.getDouble("treasure.dig-radius", 4),
                dropChances, tierWeights, rewards);
        TreasureService service = new TreasureService(this, core, messages, repository,
                new TreasureMapItem(this, itemIds), executor, settings);
        getServer().getPluginManager().registerEvents(new TreasureListener(service), this);
        var treasureCommand = getCommand("보물지도");
        if (treasureCommand != null) {
            var executorCmd = new TreasureCommand(this, messages, service, executor);
            treasureCommand.setExecutor(executorCmd);
            treasureCommand.setTabCompleter(executorCmd);
        }
        getServer().getScheduler().runTaskTimer(this, service::showHints, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, () -> executor.execute(service::announceLegendaryDigs), 20L * 60, 20L * 60);
    }

    /** {@code <path>.<순위>: 값} → rank map; non-numeric keys are skipped with a warning. */
    private Map<Integer, Long> rankMap(String path) {
        Map<Integer, Long> result = new HashMap<>();
        ConfigurationSection section = getConfig().getConfigurationSection(path);
        if (section != null) {
            for (String rank : section.getKeys(false)) {
                try {
                    result.put(Integer.parseInt(rank), section.getLong(rank));
                } catch (NumberFormatException e) {
                    getLogger().warning(path + "의 '" + rank + "'는 순위 숫자여야 합니다 — 건너뜁니다.");
                }
            }
        }
        return result;
    }

    /** 생활 대회 — 매일 같은 시각, 세 서버 합산 행동 횟수로 순위 (종목은 날짜별 순환). */
    private void enableLifeCompetition(YeowoolCoreAPI core, MessageManager messages) {
        var config = getConfig();
        if (!config.getBoolean("life-competition.enabled", true)) {
            return;
        }
        CompetitionRepository repository = new CompetitionRepository(core.dataSource());
        try {
            repository.createTables();
        } catch (Exception e) {
            getLogger().severe("생활 대회 데이터베이스 초기화 실패 — 생활 대회를 끕니다: " + e.getMessage());
            return;
        }
        List<CompetitionActivity> rotation = new ArrayList<>();
        for (String key : config.getStringList("life-competition.rotation")) {
            CompetitionActivity.byKey(key).ifPresentOrElse(rotation::add, () -> getLogger().warning(
                    "life-competition.rotation의 '" + key + "'는 fishing/mining/hunting/farming 중 하나여야 합니다 — 건너뜁니다."));
        }
        Map<Integer, Long> rewards = rankMap("life-competition.rewards");
        Map<Integer, Long> stardustRewards = rankMap("life-competition.stardust-rewards");
        CompetitionSchedule schedule = new CompetitionSchedule(ZoneId.systemDefault(),
                config.getInt("life-competition.start-hour", 18),
                config.getInt("life-competition.duration-minutes", 60),
                rotation);
        LifeCompetitionService service = new LifeCompetitionService(this, core, messages, repository, schedule, rewards, stardustRewards, executor);
        this.lifeCompetition = service;
        getServer().getPluginManager().registerEvents(new LifeCompetitionListener(service), this);
        var command = getCommand("생활대회");
        if (command != null) {
            command.setExecutor((sender, cmd, label, args) -> {
                if (sender instanceof Player player) {
                    service.showStatus(player);
                } else {
                    messages.send(sender, "general.player-only");
                }
                return true;
            });
        }
        getServer().getScheduler().runTaskTimer(this, service::tick, 20L * 10, 20L * 10);
        getServer().getScheduler().runTaskTimer(this, () -> executor.execute(service::settleAndAnnounce), 20L * 60, 20L * 60);
    }

    /** 탈것 이용권 — MCPets의 탈것(Mountable: true) 권한을 이용권 아이템으로 영구 해금, /탈것 으로 보유 목록. */
    private void enableMounts(YeowoolCoreAPI core, MessageManager messages) {
        var mcpets = getServer().getPluginManager().getPlugin("MCPets");
        if (mcpets == null || !mcpets.isEnabled() || !getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            getLogger().warning("MCPets 또는 LuckPerms가 없어 탈것 이용권을 끕니다.");
            return;
        }
        Map<String, MountDefinition> mounts;
        try {
            mounts = MountCatalog.load(new File(mcpets.getDataFolder(), "Pets"));
        } catch (RuntimeException e) { // unreadable pet folder must not take the rest of YeowoolLife down
            getLogger().warning("MCPets 펫 설정을 읽지 못해 탈것 이용권을 끕니다: " + e.getMessage());
            return;
        }
        getLogger().info("탈것 " + mounts.size() + "종을 불러왔습니다: " + mounts.keySet());
        MountVoucherItem voucherItem = new MountVoucherItem(this);
        getServer().getPluginManager().registerEvents(new MountVoucherListener(this, messages, voucherItem, mounts), this);
        MountCommand mountCommand = new MountCommand(core, messages, voucherItem, mounts);
        for (String name : List.of("탈것", "탈것이용권")) {
            var command = getCommand(name);
            if (command != null) {
                command.setExecutor(mountCommand);
                command.setTabCompleter(mountCommand);
            }
        }
    }

    /** 깜짝 이벤트 — surprise-event.worlds의 월드가 있는 서버(마을·야생)끼리 같은 짧은 버프 이벤트를 자동으로 연다. */
    private void enableSurpriseEvents(YeowoolCoreAPI core, MessageManager messages) {
        var config = getConfig();
        if (!config.getBoolean("surprise-event.enabled", true)) {
            return;
        }
        boolean participating = config.getStringList("surprise-event.worlds").stream().anyMatch(world -> Bukkit.getWorld(world) != null);
        Map<SurpriseEventType, Double> multipliers = new LinkedHashMap<>();
        for (SurpriseEventType type : SurpriseEventType.values()) {
            String path = "surprise-event.types." + type.key();
            if (!config.getBoolean(path + ".enabled", true)) {
                continue;
            }
            double multiplier = config.getDouble(path + ".multiplier", type == SurpriseEventType.TREASURE_DROP ? 3.0 : 2.0);
            if (multiplier <= 0) {
                getLogger().warning(path + ".multiplier는 0보다 커야 합니다 — 이 종류를 건너뜁니다.");
                continue;
            }
            multipliers.put(type, multiplier);
        }
        SurpriseEventService.Settings settings = new SurpriseEventService.Settings(
                Math.max(1, config.getInt("surprise-event.duration-minutes", 30)),
                config.getInt("surprise-event.interval-min-minutes", 60),
                config.getInt("surprise-event.interval-max-minutes", 120),
                multipliers);
        SurpriseEventRepository repository = new SurpriseEventRepository(core.dataSource());
        try {
            repository.createTables(System.currentTimeMillis()
                    + SurpriseEventRules.nextDelayMillis(new Random(), settings.intervalMinMinutes(), settings.intervalMaxMinutes()));
        } catch (Exception e) {
            getLogger().severe("깜짝 이벤트 데이터베이스 초기화 실패 — 깜짝 이벤트를 끕니다: " + e.getMessage());
            return;
        }
        SurpriseEventService service = new SurpriseEventService(this, core, messages, repository, settings);
        var command = getCommand("깜짝이벤트");
        if (command != null) {
            var executorCmd = new SurpriseEventCommand(this, messages, service, executor, participating);
            command.setExecutor(executorCmd);
            command.setTabCompleter(executorCmd);
        }
        if (!participating) {
            return;
        }
        this.surpriseEvents = service;
        getServer().getScheduler().runTaskTimer(this, () -> executor.execute(service::tick), 20L * 5, 20L * 20);
        getServer().getScheduler().runTaskTimer(this, service::updateBossBar, 20L, 20L);
        getLogger().info("깜짝 이벤트 참여 서버입니다. 종류: " + multipliers.keySet());
    }

    /** 요리 주문 — 식당 NPC(Citizens)의 개인 주문·VIP·세 서버 단체 주문·요리사 명성 (AddCook·ItemsAdder 필요). */
    private void enableCookingOrders(YeowoolCoreAPI core, MessageManager messages) {
        var config = getConfig();
        if (!config.getBoolean("cooking-orders.enabled", true)) {
            getLogger().info("cooking-orders.enabled가 false라 요리 주문을 끕니다.");
            return;
        }
        if (!getServer().getPluginManager().isPluginEnabled("AddCook") || !getServer().getPluginManager().isPluginEnabled("ItemsAdder")) {
            getLogger().warning("AddCook 또는 ItemsAdder가 없어 요리 주문을 끕니다.");
            return;
        }
        List<AddCookRecipeIndex.RecipeEntry> recipes = AddCookRecipeIndex.load();
        if (recipes.isEmpty()) {
            getLogger().warning("AddCook 레시피를 하나도 읽지 못해 요리 주문을 끕니다.");
            return;
        }
        OrderRepository repository = new OrderRepository(core.dataSource(), "yw_cook_", "recipe_id", false);
        OrderRules rules = new OrderRules(OrderService.settings(configSection("cooking-orders"), getLogger(),
                "dish", "quality-multiplier", CookingOrderCatalog.DEFAULTS, id -> id));
        if (!startOrders(core, messages, new CookingOrderCatalog(rules, recipes), repository, rules, "요리주문관리",
                "yeowool.life.cooking.manage", "식당")) {
            return;
        }
        getLogger().info("요리 주문이 활성화되었습니다 (레시피 " + recipes.size() + "종).");
    }

    /** 어부 주문 (수산시장 NPC) — spec docs/superpowers/specs/2026-09-30-fishing-orders-design.md. */
    private void enableFishingOrders(YeowoolCoreAPI core, MessageManager messages, JobManager jobManager, List<FishRarity> fishRarities) {
        if (!getConfig().getBoolean("fishing-orders.enabled", true)) {
            getLogger().info("fishing-orders.enabled가 false라 어부 주문을 끕니다.");
            return;
        }
        if (!CustomFishingBridge.isEnabled()) {
            getLogger().warning("CustomFishing이 없어 어부 주문을 끕니다.");
            return;
        }
        ConfigurationSection section = configSection("fishing-orders");
        OrderRules rules = new OrderRules(OrderService.settings(section, getLogger(), "fish", "star-multiplier",
                FishingOrderCatalog.DEFAULTS, CustomFishingNativeFishExporter::nativeId));
        FishingOrderCatalog catalog = new FishingOrderCatalog(new FishingOrderRules(FishingOrderCatalog.settings(section), rules),
                core, jobManager, fishRarities, getLogger());
        catalog.refresh();
        if (!startOrders(core, messages, catalog, new OrderRepository(core.dataSource(), "yw_fish_", "fish_id", true), rules,
                "어부주문관리", "yeowool.life.fishing-orders.manage", "수산시장")) {
            return;
        }
        // CustomFishing (re)loads its loots on its own schedule — keep the species index fresh
        getServer().getScheduler().runTaskTimer(this, catalog::refresh, 20L * 10, 20L * 60 * 5);
        getLogger().info("어부 주문이 활성화되었습니다.");
    }

    /** 기부 프로젝트 — 세 서버가 함께 채우는 주간 공동 목표 (달성 시 전 서버 버프 + 별조각). */
    private void enableDonation(YeowoolCoreAPI core, MessageManager messages) {
        if (!getConfig().getBoolean("donation.enabled", true)) {
            getLogger().info("donation.enabled가 false라 기부 프로젝트를 끕니다.");
            return;
        }
        DonationRepository repository = new DonationRepository(core.dataSource());
        long announcedUntil;
        try {
            repository.createTables();
            announcedUntil = repository.maxAnnouncementId();
        } catch (Exception e) {
            getLogger().severe("기부 프로젝트 데이터베이스 초기화 실패 — 기부 프로젝트를 끕니다: " + e.getMessage());
            return;
        }
        DonationService service = new DonationService(this, core, messages, repository, executor,
                DonationService.settings(configSection("donation"), getLogger()), announcedUntil);
        DonationCommand donationCommand = new DonationCommand(this, messages, service, executor, () -> {
            reloadConfig();
            return DonationService.settings(configSection("donation"), getLogger());
        });
        for (String name : List.of("기부", "기부관리")) {
            var command = getCommand(name);
            if (command != null) {
                command.setExecutor(donationCommand);
                command.setTabCompleter(donationCommand);
            }
        }
        getServer().getPluginManager().registerEvents(donationCommand, this);
        this.donation = service;
        getServer().getScheduler().runTaskTimer(this, () -> executor.execute(service::tick), 20L * 30, 20L * 60);
        getServer().getScheduler().runTaskTimer(this, () -> executor.execute(service::poll), 20L * 5, 20L * 20);
        getServer().getScheduler().runTaskTimer(this, service::tickSecond, 20L, 20L);
        getLogger().info("기부 프로젝트가 활성화되었습니다. 후보 " + service.settings().pool().size() + "개");
    }

    /** 판타지 금속 대장간 — spec docs/superpowers/specs/2026-09-30-fantasy-metals-design.md (ItemsAdder bundle_metals 팩 필요). */
    private void enableMetals(YeowoolCoreAPI core, MessageManager messages, JobManager jobManager, MiningListener miningListener) {
        if (!getServer().getPluginManager().isPluginEnabled("ItemsAdder")) {
            getLogger().warning("ItemsAdder가 없어 판타지 금속 대장간을 끕니다.");
            return;
        }
        MetalService service = MetalService.start(this, core, messages, jobManager, executor);
        if (service != null) {
            miningListener.setMetals(service);
            getLogger().info("판타지 금속 대장간이 활성화되었습니다 (금속 " + service.metalCount() + "종).");
        }
    }

    private ConfigurationSection configSection(String path) {
        ConfigurationSection section = getConfig().getConfigurationSection(path);
        return section != null ? section : getConfig().createSection(path);
    }

    /** Shared wiring of an order system: tables, command, NPC listener and the group/announcement timers. False if the DB failed. */
    private boolean startOrders(YeowoolCoreAPI core, MessageManager messages, OrderCatalog catalog, OrderRepository repository,
                                OrderRules rules, String commandName, String permission, String npcLabel) {
        long announcedUntil;
        try {
            repository.createTables();
            announcedUntil = repository.maxAnnouncementId();
        } catch (Exception e) {
            getLogger().severe(catalog.label() + " 데이터베이스 초기화 실패 — " + catalog.label() + "을 끕니다: " + e.getMessage());
            return false;
        }
        OrderService service = new OrderService(this, core, messages, catalog, repository, rules, executor, announcedUntil);
        var command = getCommand(commandName);
        if (command != null) {
            var executorCmd = new OrderCommand(this, messages, service, executor, permission);
            command.setExecutor(executorCmd);
            command.setTabCompleter(executorCmd);
        }
        if (getServer().getPluginManager().isPluginEnabled("Citizens")) {
            getServer().getPluginManager().registerEvents(new OrderNpcListener(service::isNpc, service::open), this);
        } else {
            getLogger().warning("Citizens가 없어 이 서버에서는 " + npcLabel + " NPC를 쓸 수 없습니다 (단체 주문 일정은 계속 처리).");
        }
        getServer().getScheduler().runTaskTimer(this, () -> executor.execute(service::tickGroups), 20L * 30, 20L * 60);
        getServer().getScheduler().runTaskTimer(this, () -> executor.execute(service::pollAnnouncements), 20L * 20, 20L * 20);
        return true;
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
        if (lifeCompetition != null) {
            lifeCompetition.flushNow();
        }
        if (surpriseEvents != null) {
            surpriseEvents.shutdown();
        }
        if (donation != null) {
            donation.shutdown();
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
