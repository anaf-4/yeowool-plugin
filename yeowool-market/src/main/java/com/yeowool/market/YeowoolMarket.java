package com.yeowool.market;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.message.MessageManager;
import com.yeowool.core.util.ConfigMerger;
import com.yeowool.market.adminshop.AdminShopRepository;
import com.yeowool.market.adminshop.AdminShopSchemaInitializer;
import com.yeowool.market.adminshop.AdminShopStore;
import com.yeowool.market.auction.AuctionContext;
import com.yeowool.market.auction.AuctionExpiryTask;
import com.yeowool.market.auction.AuctionManager;
import com.yeowool.market.auction.database.AuctionSchemaInitializer;
import com.yeowool.market.auction.repository.AuctionRepository;
import com.yeowool.market.citizens.CitizensShopListener;
import com.yeowool.market.citizens.ShopLocationCommand;
import com.yeowool.market.citizens.ShopTeleportJoinListener;
import com.yeowool.market.command.AuctionCommand;
import com.yeowool.market.command.NPCShopCommand;
import com.yeowool.market.command.ShopCreateCommand;
import com.yeowool.market.command.ShopDeleteCommand;
import com.yeowool.market.adminshop.ShopPriceAnvilListener;
import com.yeowool.market.command.ShopEditCommand;
import com.yeowool.market.command.ShopImportCommand;
import com.yeowool.market.command.ShopPageAddCommand;
import com.yeowool.market.command.ShopPageRemoveCommand;
import com.yeowool.market.command.ShopPriceCommand;
import com.yeowool.market.command.ShopRotationAddCommand;
import com.yeowool.market.command.ShopRotationClearCommand;
import com.yeowool.market.command.ShopRotationSetCommand;
import com.yeowool.market.command.TradeCommand;
import com.yeowool.market.merchant.MerchantCommand;
import com.yeowool.market.merchant.MerchantRepository;
import com.yeowool.market.merchant.MerchantRules;
import com.yeowool.market.merchant.MerchantService;
import com.yeowool.market.npcshop.ShopConfigLoader;
import com.yeowool.market.npcshop.ShopDefinition;
import com.yeowool.market.npcshop.ShopLayout;
import com.yeowool.market.npcshop.ShopRotationManager;
import com.yeowool.market.questboard.QuestBoardFurnitureListener;
import com.yeowool.market.questboard.QuestBoardListener;
import com.yeowool.market.questboard.QuestBoardService;
import com.yeowool.market.questboard.QuestPayoutClaimer;
import com.yeowool.market.questboard.QuestRepository;
import com.yeowool.market.trade.TradeChatInputListener;
import com.yeowool.market.trade.TradeManager;
import com.yeowool.market.trade.TradeQuitListener;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 상점 및 거래 (기획서 7절): NPC 상점, 플레이어 상점, 직접 거래, 경매장.
 */
public final class YeowoolMarket extends JavaPlugin {

    private ExecutorService executor;
    private MerchantService merchantService;
    private QuestBoardService questBoard;
    private QuestPayoutClaimer payoutClaimer;

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
            Thread thread = new Thread(runnable, "YeowoolMarket-Worker");
            thread.setDaemon(true);
            return thread;
        });

        // NPC 상점
        Map<String, ShopDefinition> shops = ShopConfigLoader.loadShops(this);
        if (shops.size() > ShopLayout.MAIN_MENU_SLOTS.size()) {
            getLogger().warning("상점이 " + shops.size() + "개 등록되어 있지만 메인 메뉴에는 " + ShopLayout.MAIN_MENU_SLOTS.size()
                    + "개까지만 표시됩니다 (등록 순서상 뒤쪽 상점은 /상점 <ID>로만 접근 가능): " + shops.keySet());
        }
        ShopRotationManager rotationManager = new ShopRotationManager(shops);
        // One shared timer for every rotating shop (config.yml or /상점생성) instead of a
        // separate runTaskTimer per shop — ShopRotationManager itself tracks each one's interval.
        getServer().getScheduler().runTaskTimer(this, () -> rotationManager.tick(shops), 20L * 60, 20L * 60);

        // 인게임 상점 생성/수정 (/상점생성, /상점제거, /상점페이지추가, /상점페이지제거, /상점수정, /상점아이템설정, /상점아이템가격)
        // - config.yml 상점과 같은 shops 맵을 공유하므로 /상점, Citizens NPC 연동 모두 별도 처리 없이 그대로 동작함.
        AdminShopStore adminShopStore;
        try {
            AdminShopSchemaInitializer.initialize(core.dataSource());
            AdminShopRepository adminShopRepository = new AdminShopRepository(core.dataSource());
            adminShopStore = new AdminShopStore(this, adminShopRepository, executor, shops, rotationManager);
            adminShopStore.loadIntoCache();
        } catch (Exception e) {
            getLogger().severe("인게임 상점 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        var shopCreateCommand = new ShopCreateCommand(adminShopStore, shops);
        bindCommand("상점생성", shopCreateCommand, shopCreateCommand);
        var shopDeleteCommand = new ShopDeleteCommand(adminShopStore, shops);
        bindCommand("상점제거", shopDeleteCommand, shopDeleteCommand);
        var shopPageAddCommand = new ShopPageAddCommand(adminShopStore);
        bindCommand("상점페이지추가", shopPageAddCommand, shopPageAddCommand);
        var shopPageRemoveCommand = new ShopPageRemoveCommand(adminShopStore);
        bindCommand("상점페이지제거", shopPageRemoveCommand, shopPageRemoveCommand);
        var shopEditCommand = new ShopEditCommand(this, adminShopStore, shops, messages);
        bindCommand("상점수정", shopEditCommand, shopEditCommand);
        bindCommand("상점아이템설정", shopEditCommand, shopEditCommand);
        var shopPriceListener = new ShopPriceAnvilListener(this, adminShopStore);
        getServer().getPluginManager().registerEvents(shopPriceListener, this);
        var shopPriceCommand = new ShopPriceCommand(this, adminShopStore, shopPriceListener, messages);
        bindCommand("상점아이템가격", shopPriceCommand, shopPriceCommand);
        var shopRotationSetCommand = new ShopRotationSetCommand(adminShopStore);
        bindCommand("상점로테이션설정", shopRotationSetCommand, shopRotationSetCommand);
        var shopRotationAddCommand = new ShopRotationAddCommand(this, adminShopStore);
        bindCommand("상점로테이션추가", shopRotationAddCommand, shopRotationAddCommand);
        var shopRotationClearCommand = new ShopRotationClearCommand(adminShopStore);
        bindCommand("상점로테이션제거", shopRotationClearCommand, shopRotationClearCommand);
        var shopImportCommand = new ShopImportCommand(adminShopStore, shops);
        bindCommand("상점가져오기", shopImportCommand, shopImportCommand);

        var npcShopCommand = getCommand("상점");
        if (npcShopCommand != null) {
            var executor2 = new NPCShopCommand(this, core, messages, shops, rotationManager);
            npcShopCommand.setExecutor(executor2);
            npcShopCommand.setTabCompleter(executor2);
        }

        if (getServer().getPluginManager().isPluginEnabled("Citizens")) {
            Map<Integer, String> npcShops = ShopConfigLoader.loadCitizensNpcShops(this);
            getServer().getPluginManager().registerEvents(new CitizensShopListener(this, core, messages, npcShops, shops, rotationManager), this);
            getLogger().info("Citizens NPC 상점 연동을 등록했습니다. (매핑: " + npcShops + ")");
        }

        // /상점이동 - 상점가 좌표는 서버 하나(기본 lobby)에만 있음. 다른 서버에서 실행하면
        // 그 서버로 먼저 보내고, ShopTeleportJoinListener(그 서버에만 등록됨)가 도착 즉시
        // 텔레포트를 마무리함 - Citizens 유무와 무관하게 항상 등록. 같은 리스너가 /상점
        // 메인메뉴에서 상점 NPC로 이동하는 것(ShopNpcTeleporter)도 같이 처리함.
        String thisServerId = getConfig().getString("npc-shop.this-server-id", "lobby");
        String npcServerId = getConfig().getString("npc-shop.npc-server-id", "lobby");
        var shopLocationCommand = new ShopLocationCommand(this, core, messages, npcServerId, thisServerId);
        var shopLocationCmd = getCommand("상점이동");
        if (shopLocationCmd != null) {
            shopLocationCmd.setExecutor(shopLocationCommand);
        }
        if (thisServerId.equals(npcServerId)) {
            getServer().getPluginManager().registerEvents(new ShopTeleportJoinListener(this, core, messages, shopLocationCommand), this);
        }

        // 떠돌이 상인 — Citizens가 있는 서버에서만 (NPC가 필요함). 실패해도 나머지 기능은 그대로.
        if (getConfig().getBoolean("wandering-merchant.enabled", true)) {
            if (getServer().getPluginManager().isPluginEnabled("Citizens")) {
                try {
                    enableWanderingMerchant(core, messages, shops, rotationManager, thisServerId);
                } catch (Exception e) {
                    getLogger().severe("떠돌이 상인 초기화 실패 — 떠돌이 상인을 끕니다: " + e.getMessage());
                }
            } else {
                getLogger().warning("Citizens가 없어 이 서버에서는 떠돌이 상인을 끕니다.");
            }
        }

        // 직접 거래
        TradeManager tradeManager = new TradeManager(this, core, messages);
        getServer().getPluginManager().registerEvents(new TradeChatInputListener(this, core, messages), this);
        getServer().getPluginManager().registerEvents(new TradeQuitListener(tradeManager), this);
        var tradeCommand = getCommand("거래");
        if (tradeCommand != null) {
            var executor2 = new TradeCommand(tradeManager, messages);
            tradeCommand.setExecutor(executor2);
            tradeCommand.setTabCompleter(executor2);
        }

        // 경매장
        try {
            AuctionSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("경매장 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        AuctionRepository auctionRepository = new AuctionRepository(core.dataSource());
        AuctionManager auctionManager = new AuctionManager(this, core, auctionRepository, executor);
        try {
            auctionManager.loadAll();
        } catch (Exception e) {
            getLogger().severe("경매장 데이터 로드 실패: " + e.getMessage());
        }
        double auctionFeePercent = getConfig().getDouble("auction.fee-percent", 5.0);
        int auctionBackgroundOffsetPx = getConfig().getInt("auction.gui-background-offset", -8);
        long[] bidIncrements = getConfig().getLongList("auction.bid-increments").stream().mapToLong(Long::longValue).toArray();
        if (bidIncrements.length == 0) {
            bidIncrements = new long[]{1000, 10000, 100000};
        }
        AuctionContext auctionContext = new AuctionContext(this, core, messages, auctionManager, auctionFeePercent, auctionBackgroundOffsetPx, bidIncrements);

        var auctionCommand = getCommand("경매");
        if (auctionCommand != null) {
            var executor3 = new AuctionCommand(auctionContext);
            auctionCommand.setExecutor(executor3);
            auctionCommand.setTabCompleter(executor3);
        }
        new AuctionExpiryTask(auctionManager, auctionFeePercent).runTaskTimer(this, 20L * 30, 20L * 30);

        // 의뢰 게시판 — 실패해도 나머지 상점 기능은 그대로 켜 둠
        try {
            QuestRepository questRepository = new QuestRepository(core.dataSource(), getLogger());
            questRepository.createTables();
            QuestBoardService.Settings questSettings = new QuestBoardService.Settings(
                    getConfig().getString("quest-board.furniture-id", "workshop_six:quest_board"),
                    getConfig().getInt("quest-board.fee-percent", 5),
                    getConfig().getLong("quest-board.duration-hours", 72L) * 3_600_000L,
                    getConfig().getInt("quest-board.max-open-per-player", 5),
                    getConfig().getInt("quest-board.max-quantity", 100000));
            payoutClaimer = new QuestPayoutClaimer(this, core, messages, questRepository, executor);
            questBoard = new QuestBoardService(this, core, messages, questRepository, payoutClaimer, executor, questSettings);
            getServer().getPluginManager().registerEvents(new QuestBoardListener(this, questBoard, payoutClaimer), this);
            if (getServer().getPluginManager().isPluginEnabled("ItemsAdder")) {
                getServer().getPluginManager().registerEvents(new QuestBoardFurnitureListener(questBoard), this);
            } else {
                getLogger().warning("ItemsAdder가 없어 의뢰 게시판 가구를 쓸 수 없습니다 (/의뢰로 내 의뢰만 확인 가능).");
            }
            bindCommand("의뢰", (sender, command, label, args) -> {
                if (sender instanceof Player player) {
                    questBoard.openMine(player);
                } else {
                    messages.send(sender, "general.player-only");
                }
                return true;
            });
            getServer().getScheduler().runTaskTimer(this, () -> {
                executor.execute(questBoard::expireDue);
                payoutClaimer.claimAllOnline();
            }, 20L * 60, 20L * 60);
        } catch (Exception e) {
            getLogger().severe("의뢰 게시판 초기화 실패 — 의뢰 게시판을 끕니다: " + e.getMessage());
        }

        getLogger().info("YeowoolMarket이 활성화되었습니다.");
    }

    private void enableWanderingMerchant(YeowoolCoreAPI core, MessageManager messages, Map<String, ShopDefinition> shops,
                                         ShopRotationManager rotationManager, String thisServerId) throws java.sql.SQLException {
        EntityType entityType;
        String typeName = getConfig().getString("wandering-merchant.entity-type", "WANDERING_TRADER");
        try {
            entityType = EntityType.valueOf(typeName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            getLogger().warning("wandering-merchant.entity-type '" + typeName + "'을(를) 알 수 없어 WANDERING_TRADER를 씁니다.");
            entityType = EntityType.WANDERING_TRADER;
        }
        Map<String, String> serverNames = new HashMap<>();
        ConfigurationSection namesSection = getConfig().getConfigurationSection("wandering-merchant.server-names");
        if (namesSection != null) {
            for (String key : namesSection.getKeys(false)) {
                serverNames.put(key, namesSection.getString(key, key));
            }
        }
        MerchantService.Settings settings = new MerchantService.Settings(
                thisServerId,
                getConfig().getString("wandering-merchant.shop-id", "wandering_merchant"),
                getConfig().getString("wandering-merchant.npc-name", "&6떠돌이 상인"),
                entityType,
                getConfig().getInt("wandering-merchant.stay-minutes", 30),
                getConfig().getInt("wandering-merchant.interval-min-minutes", 180),
                getConfig().getInt("wandering-merchant.interval-max-minutes", 300),
                serverNames);
        MerchantRepository repository = new MerchantRepository(core.dataSource());
        repository.createTables(System.currentTimeMillis()
                + MerchantRules.nextDelayMillis(new Random(), settings.intervalMinMinutes(), settings.intervalMaxMinutes()));
        this.merchantService = new MerchantService(this, core, messages, repository, settings, shops, rotationManager);
        getServer().getPluginManager().registerEvents(merchantService, this);
        var merchantCommand = new MerchantCommand(this, messages, merchantService, executor);
        bindCommand("떠돌이상인", merchantCommand, merchantCommand);
        MerchantService service = merchantService;
        getServer().getScheduler().runTaskTimer(this, () -> executor.execute(service::tick), 20L * 10, 20L * 60);
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
        if (merchantService != null) {
            merchantService.removeNpc();
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    getLogger().warning("의뢰 게시판/떠돌이 상인 작업이 5초 안에 끝나지 않았습니다.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Main thread, after the executor drained: settle whatever follow-up task Paper cancelled on disable.
        if (questBoard != null) {
            questBoard.settleInFlight();
        }
        if (payoutClaimer != null) {
            payoutClaimer.restoreUnpaid();
        }
    }
}
