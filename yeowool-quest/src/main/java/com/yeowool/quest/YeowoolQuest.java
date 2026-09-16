package com.yeowool.quest;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.util.ConfigMerger;
import com.yeowool.quest.database.QuestProgressRepository;
import com.yeowool.quest.database.QuestRepository;
import com.yeowool.quest.database.QuestSchemaInitializer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Citizens NPC quest giver: right-click offers whichever quests that NPC has
 * that the player hasn't completed, dialogue plays through a BetterHud popup
 * (see {@link QuestDialogueService}'s doc for the exact integration seam —
 * BetterHud's own popup files still need to be built by hand, this plugin
 * only triggers/drives them), and accept grants either an instant reward
 * (dialogue-only quests) or starts tracking a kill/collect objective.
 */
public final class YeowoolQuest extends JavaPlugin {

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
            getLogger().severe("Citizens가 설치되어 있지 않습니다. YeowoolQuest는 Citizens 없이 동작할 수 없습니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ConfigMerger.mergeDefaults(this, "config.yml");
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "YeowoolQuest-Worker");
            thread.setDaemon(true);
            return thread;
        });

        try {
            QuestSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("퀘스트 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        QuestRepository questRepository = new QuestRepository(core.dataSource());
        QuestProgressRepository progressRepository = new QuestProgressRepository(core.dataSource());
        QuestManager questManager = new QuestManager(this, questRepository, progressRepository, executor);
        try {
            questManager.loadAll();
        } catch (Exception e) {
            getLogger().severe("퀘스트 로드 실패: " + e.getMessage());
        }
        for (var player : Bukkit.getOnlinePlayers()) {
            questManager.loadPlayer(player.getUniqueId());
        }
        getServer().getPluginManager().registerEvents(new QuestJoinListener(questManager), this);

        boolean betterHudEnabled = Bukkit.getPluginManager().isPluginEnabled("BetterHud");
        if (!betterHudEnabled) {
            getLogger().warning("BetterHud가 설치되어 있지 않습니다 — 대사/수락 UI가 채팅으로만 표시됩니다. "
                    + "BetterHud 설치 후 config.yml의 betterhud.dialogue-popup / decision-popup 이름으로 팝업을 만들어주세요.");
        }
        QuestDialogueService dialogueService = new QuestDialogueService(questManager,
                getConfig().getString("betterhud.dialogue-popup", "yeowool_quest_dialogue"),
                getConfig().getString("betterhud.decision-popup", "yeowool_quest_decision"));

        getServer().getPluginManager().registerEvents(new QuestNpcListener(questManager, dialogueService), this);
        getServer().getPluginManager().registerEvents(new QuestKillListener(questManager), this);
        getServer().getPluginManager().registerEvents(new QuestCollectListener(questManager), this);

        bindCommand("퀘스트", new QuestAdminCommand(this, questManager, executor));
        bindCommand("퀘스트수락", new QuestAcceptCommand(questManager));
        bindCommand("퀘스트거절", new QuestDeclineCommand(questManager));
        bindCommand("퀘스트대사다음", new QuestNextLineCommand(questManager, dialogueService));

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            var expansion = new QuestPlaceholderExpansion(questManager);
            expansion.register();
            dialogueService.setPlaceholders(expansion);
            getLogger().info("PlaceholderAPI 확장을 등록했습니다. (%yeowool_quest_...%)");
        }

        getLogger().info("YeowoolQuest가 활성화되었습니다." + (betterHudEnabled ? "" : " (BetterHud 없이 채팅 폴백 모드)"));
    }

    private void bindCommand(String name, org.bukkit.command.CommandExecutor executorImpl) {
        var command = getCommand(name);
        if (command == null) {
            return;
        }
        command.setExecutor(executorImpl);
        if (executorImpl instanceof org.bukkit.command.TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    @Override
    public void onDisable() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
