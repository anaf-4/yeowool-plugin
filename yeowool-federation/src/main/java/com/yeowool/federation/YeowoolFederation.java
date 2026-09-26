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
import com.yeowool.federation.event.Announcement;
import com.yeowool.federation.event.FederationEventCommand;
import com.yeowool.federation.event.FederationEventRepository;
import com.yeowool.federation.event.FederationEventSchedule;
import com.yeowool.federation.event.FederationEventService;
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
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

        Optional<FederationEventSchedule> eventSchedule = Optional.empty();
        if (getConfig().getBoolean("event.auto.enabled", true)) {
            try {
                eventSchedule = Optional.of(new FederationEventSchedule(
                        DayOfWeek.valueOf(getConfig().getString("event.auto.day-of-week", "SATURDAY").trim().toUpperCase(Locale.ROOT)),
                        LocalTime.parse(getConfig().getString("event.auto.start-time", "20:00").trim()),
                        getConfig().getInt("event.auto.duration-minutes", 1440)));
            } catch (IllegalArgumentException | DateTimeException e) {
                getLogger().warning("연합대항 자동 시작 설정을 읽지 못해 자동 시작을 끕니다: " + e.getMessage());
            }
        }
        List<Long> eventRewards = getConfig().getLongList("event.rewards");
        FederationEventService eventService = new FederationEventService(this, messages,
                new FederationEventRepository(core.dataSource()), manager, eventRewards, eventSchedule);
        executor.execute(() -> {
            try {
                eventService.init();
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "연합대항 상태 불러오기 실패", e);
            }
        });
        var eventCommand = getCommand("연합대항");
        if (eventCommand != null) {
            eventCommand.setExecutor(new FederationEventCommand(this, messages, eventService, executor));
        }

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
                try {
                    List<Announcement> announcements = eventService.tick(ZonedDateTime.now());
                    if (!announcements.isEmpty()) {
                        getServer().getScheduler().runTask(this, () -> eventService.broadcast(announcements));
                    }
                } catch (SQLException e) {
                    getLogger().log(Level.SEVERE, "연합대항 주기 처리 실패", e);
                }
            });
        }, 1200L, 1200L);

        List<FederationShop> shops = FederationShop.fromConfig(getConfig().getMapList("shops"));
        getServer().getPluginManager().registerEvents(new FederationShopGate(messages, shops, levelCache), this);

        FederationChatService chatService = new FederationChatService(
                this, core, core.dataSource(), getConfig().getLong("chat.cooldown-ms", 1500L));

        var federationCommand = new FederationCommand(this, core, messages, manager, landLookup, resolver, levelCache, shops, chatService, executor);
        var command = getCommand("연합");
        if (command != null) {
            command.setExecutor(federationCommand);
        }

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
