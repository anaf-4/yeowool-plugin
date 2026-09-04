package com.yeowool.community.quest;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;

/** Bundles every dependency the quest GUI screens need, to avoid threading 7 loose params through the whole chain. */
public record QuestContext(JavaPlugin plugin, YeowoolCoreAPI core, QuestManager questManager, QuestBadgeConfig badgeConfig,
                            QuestLeaderboardQuery leaderboardQuery, ExecutorService executor, MessageService messages) {
}
