package com.yeowool.federation;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.message.MessageManager;
import com.yeowool.core.util.ConfigMerger;
import com.yeowool.federation.activity.ActivityTracker;
import com.yeowool.federation.chat.FederationChatCommand;
import com.yeowool.federation.chat.FederationChatListener;
import com.yeowool.federation.chat.FederationChatService;
import com.yeowool.federation.database.FederationRepository;
import com.yeowool.federation.database.FederationSchemaInitializer;
import com.yeowool.federation.land.LandLookup;
import com.yeowool.federation.land.PlayerFederationResolver;
import com.yeowool.federation.shop.FederationLevelCache;
import com.yeowool.federation.shop.FederationShop;
import com.yeowool.federation.shop.FederationShopGate;
import com.yeowool.core.api.event.LandDeletedEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public final class YeowoolFederation extends JavaPlugin {

    private ExecutorService executor;
    private ActivityTracker activityTracker;

    @Override
    public void onEnable() {
        ConfigMerger.mergeDefaults(this, "config.yml");
        YeowoolCoreAPI core = Bukkit.getServicesManager().load(YeowoolCoreAPI.class);
        if (core == null) {
            getLogger().severe("YeowoolCore를 찾을 수 없습니다. 비활성화합니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            FederationSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("데이터베이스 초기화에 실패했습니다. 서버를 비활성화합니다: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "YeowoolFederation-DB");
            thread.setDaemon(true);
            return thread;
        });

        MessageService messages = new MessageManager(this);

        FederationRepository repository = new FederationRepository(core.dataSource());
        LandLookup landLookup = new LandLookup(core.dataSource());
        FederationLevelConfig levelConfig = new FederationLevelConfig(
                getConfig().getLong("level-up.activity-per-level", 20000L),
                getConfig().getLong("level-up.cost-per-level", 30000L),
                getConfig().getInt("member-cap.base", 3),
                getConfig().getInt("member-cap.per-level", 1));
        FederationManager manager = new FederationManager(repository, levelConfig);

        PlayerFederationResolver resolver = new PlayerFederationResolver(core.dataSource());
        this.activityTracker = new ActivityTracker(this, manager, resolver);
        FederationLevelCache levelCache = new FederationLevelCache(this, manager, resolver, executor);
        getServer().getPluginManager().registerEvents(activityTracker, this);
        getServer().getPluginManager().registerEvents(levelCache, this);
        getServer().getScheduler().runTaskTimer(this, () -> {
            List<UUID> online = getServer().getOnlinePlayers().stream().map(Player::getUniqueId).toList();
            executor.execute(() -> {
                activityTracker.flush();
                for (UUID playerUuid : online) {
                    try {
                        levelCache.refresh(playerUuid);
                    } catch (SQLException e) {
                        getLogger().log(Level.SEVERE, "연합 레벨 캐시 갱신 실패 (" + playerUuid + ")", e);
                    }
                }
            });
        }, 1200L, 1200L);

        List<FederationShop> shops = FederationShop.fromConfig(getConfig().getMapList("shops"));
        getServer().getPluginManager().registerEvents(new FederationShopGate(messages, shops, levelCache), this);

        var federationCommand = new FederationCommand(this, core, messages, manager, landLookup, resolver, levelCache, shops, executor);
        var command = getCommand("연합");
        if (command != null) {
            command.setExecutor(federationCommand);
        }

        FederationChatService chatService = new FederationChatService(
                this, core, core.dataSource(), getConfig().getLong("chat.cooldown-ms", 1500L));
        FederationChatCommand chatCommand = new FederationChatCommand(this, core, messages, chatService, executor);
        var chatCommandEntry = getCommand("연합채팅");
        if (chatCommandEntry != null) {
            chatCommandEntry.setExecutor(chatCommand);
        }
        getServer().getPluginManager().registerEvents(
                new FederationChatListener(this, core, messages, chatService, chatCommand), this);

        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onLandDeleted(LandDeletedEvent event) {
                executor.execute(() -> {
                    try {
                        manager.handleLandDeleted(event.getLandId());
                    } catch (SQLException e) {
                        getLogger().log(Level.SEVERE, "삭제된 토지의 연합 정리 실패 (" + event.getLandId() + ")", e);
                    }
                });
            }
        }, this);
    }

    @Override
    public void onDisable() {
        if (activityTracker != null) {
            activityTracker.flush();
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    getLogger().warning("연합 작업이 5초 안에 끝나지 않았습니다 — 일부 작업이 중단되었을 수 있습니다.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
