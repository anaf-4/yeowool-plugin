package com.yeowool.federation;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.message.MessageManager;
import com.yeowool.federation.database.FederationRepository;
import com.yeowool.federation.database.FederationSchemaInitializer;
import com.yeowool.federation.land.LandLookup;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class YeowoolFederation extends JavaPlugin {

    private ExecutorService executor;

    @Override
    public void onEnable() {
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
        FederationManager manager = new FederationManager(repository);

        var federationCommand = new FederationCommand(this, messages, manager, landLookup, executor);
        var command = getCommand("연합");
        if (command != null) {
            command.setExecutor(federationCommand);
        }
    }

    @Override
    public void onDisable() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
