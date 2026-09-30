package com.yeowool.enhance;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.message.MessageManager;
import com.yeowool.core.util.ConfigMerger;
import com.yeowool.enchant.EnchantCommand;
import com.yeowool.enchant.EnchantConfig;
import com.yeowool.enchant.EnchantItems;
import com.yeowool.enchant.EnchantService;
import com.yeowool.enhance.database.EnhanceCostRepository;
import com.yeowool.enhance.database.EnhanceCostSchemaInitializer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 장비 강화 시스템: {@code /강화} GUI에서 무기/방어구/도구를 강화하고, 30강부터는 초월석으로 초월(1차: 네더라이트, 10강에서 2차, 20강에서 3차)합니다. 9강까지는
 * "일반" 등급, 10강 성공 시 "희귀", 15강 성공 시 "에픽"으로 등급이 자동으로
 * 올라가며(그 이상은 {@code config.yml}의 {@code enhance.tiers}에 계속 추가
 * 가능), 강화 수치가 오를수록 공격력/방어력도 함께 오릅니다. 단계별 필요
 * 온/재료 개수는 {@code /강화설정}으로 서버 재시작 없이 바로 조정 가능합니다.
 */
public final class YeowoolEnhance extends JavaPlugin {

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
        MessageService messages = new MessageManager(this);
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "YeowoolEnhance-Worker");
            thread.setDaemon(true);
            return thread;
        });

        EnhanceConfig config = EnhanceConfig.load(getConfig());
        EnhanceItemData itemData = new EnhanceItemData(this, config);

        try {
            EnhanceCostSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("강화 비용 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        EnhanceCostRepository costRepository = new EnhanceCostRepository(core.dataSource());
        EnhanceCostManager costManager = new EnhanceCostManager(this, costRepository, executor);
        try {
            costManager.loadAll(config.maxLevel());
        } catch (Exception e) {
            getLogger().severe("강화 비용 데이터 로드 실패: " + e.getMessage());
        }

        EnhanceService service = new EnhanceService(core, config, itemData, costManager,
                getConfig().getBoolean("enhance.boosters.allow-transcend", true));

        int backgroundOffsetPx = getConfig().getInt("enhance.gui-background-offset", -8);
        var command = getCommand("강화");
        if (command != null) {
            command.setExecutor(new EnhanceCommand(service, messages, backgroundOffsetPx));
        }
        var settingsAnvil = new EnhanceSettingsAnvilListener(this, service, costManager);
        getServer().getPluginManager().registerEvents(settingsAnvil, this);
        var settingsCommand = getCommand("강화설정");
        if (settingsCommand != null) {
            var executorCmd = new EnhanceSettingsCommand(costManager, config, messages, service, settingsAnvil);
            settingsCommand.setExecutor(executorCmd);
            settingsCommand.setTabCompleter(executorCmd);
        }
        getServer().getPluginManager().registerEvents(new EnhanceRepairListener(this, itemData), this);

        var stoneCommand = getCommand("초월석");
        if (stoneCommand != null) {
            var stoneExecutor = new TranscendStoneCommand(core, messages, config);
            stoneCommand.setExecutor(stoneExecutor);
            stoneCommand.setTabCompleter(stoneExecutor);
        }

        // 인챈트강화 (별도 시스템 — /강화의 +N강과 완전히 무관, AdvancedEnchantments 방식 인챈트북 구매/분해/조합, 적용은 일반 모루)
        EnchantConfig enchantConfig = EnchantConfig.load(getConfig());
        EnchantItems enchantItems = new EnchantItems(this, enchantConfig);
        EnchantService enchantService = new EnchantService(core, enchantConfig, enchantItems);
        var enchantCommand = getCommand("인챈트강화");
        if (enchantCommand != null) {
            enchantCommand.setExecutor(new EnchantCommand(enchantService, messages));
        }

        getLogger().info("YeowoolEnhance가 활성화되었습니다.");
    }

    @Override
    public void onDisable() {
        // hands 보조 재료 칸 items back before the plugin (and its GUIs) go away — GuiListener fires EnhanceGui.onClose
        for (var online : Bukkit.getOnlinePlayers()) {
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof EnhanceGui) {
                online.closeInventory();
            }
        }
        if (executor != null) {
            executor.shutdown();
        }
    }
}
