package com.yeowool.federation;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.message.MessageManager;
import com.yeowool.core.util.ConfigMerger;
import com.yeowool.federation.chat.FederationChatCommand;
import com.yeowool.federation.chat.FederationChatListener;
import com.yeowool.federation.chat.FederationChatService;
import com.yeowool.federation.database.FederationRepository;
import com.yeowool.federation.database.FederationSchemaInitializer;
import com.yeowool.federation.land.LandLookup;
import com.yeowool.core.api.event.LandDeletedEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public final class YeowoolFederation extends JavaPlugin {

    private ExecutorService executor;

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

        var federationCommand = new FederationCommand(this, messages, manager, landLookup, executor);
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
        if (executor != null) {
            executor.shutdown();
        }
    }
}
