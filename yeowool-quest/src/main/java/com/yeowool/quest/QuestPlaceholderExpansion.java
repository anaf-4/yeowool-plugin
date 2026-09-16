package com.yeowool.quest;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.OfflinePlayer;

/**
 * {@code %yeowool_quest_...%} — the data a BetterHud popup config needs to
 * actually render the dialogue/decision box (name, current line, progress).
 * Resolves against whichever quest this player most recently interacted
 * with (tracked in-memory per online player — see {@link #lastQuestId}),
 * since a popup placeholder has no way to say "for quest X" itself.
 */
public final class QuestPlaceholderExpansion extends PlaceholderExpansion {

    private final QuestManager questManager;
    private final java.util.Map<java.util.UUID, Long> lastQuestId = new java.util.concurrent.ConcurrentHashMap<>();

    public QuestPlaceholderExpansion(QuestManager questManager) {
        this.questManager = questManager;
    }

    /** Called by {@link QuestDialogueService} whenever a popup is shown, so the placeholders below know which quest to describe. */
    void track(java.util.UUID uuid, long questId) {
        lastQuestId.put(uuid, questId);
    }

    @Override
    public String getIdentifier() {
        return "yeowool_quest";
    }

    @Override
    public String getAuthor() {
        return "Yeowool";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return null;
        }
        Long questId = lastQuestId.get(player.getUniqueId());
        Quest quest = questId == null ? null : questManager.all().stream().filter(q -> q.id() == questId).findFirst().orElse(null);
        if (quest == null) {
            return switch (params) {
                case "name", "npc_name", "line" -> "";
                default -> "0";
            };
        }
        var progress = questManager.progress(player.getUniqueId(), quest.id());
        int dialogueIndex = progress.map(QuestProgress::dialogueIndex).orElse(0);
        return switch (params) {
            case "name" -> quest.name();
            case "npc_name" -> {
                var npc = CitizensAPI.getNPCRegistry().getById(quest.npcId());
                yield npc == null ? "" : npc.getName();
            }
            case "line" -> dialogueIndex < quest.dialogue().size() ? quest.dialogue().get(dialogueIndex) : "";
            case "line_index" -> String.valueOf(dialogueIndex + 1);
            case "line_total" -> String.valueOf(quest.dialogue().size());
            case "is_last_line" -> String.valueOf(dialogueIndex >= quest.dialogue().size() - 1);
            case "objective_progress" -> String.valueOf(progress.map(QuestProgress::progress).orElse(0));
            case "objective_amount" -> String.valueOf(quest.objectiveAmount());
            default -> null;
        };
    }
}
