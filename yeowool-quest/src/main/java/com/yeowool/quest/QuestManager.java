package com.yeowool.quest;

import com.yeowool.quest.database.QuestProgressRepository;
import com.yeowool.quest.database.QuestRepository;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Owns both quest definitions (small, fully cached — same trade-off {@code
 * CouponManager} makes) and per-player progress (loaded on join / dropped on
 * quit — same trade-off {@code HomeManager} makes, since kill/collect
 * listeners need a synchronous, main-thread-safe read on every relevant
 * event and can't afford a DB round trip).
 *
 * <p>The quest-definition mutators ({@link #create}, {@link #setDialogue},
 * etc.) are plain blocking JDBC calls — admin-only, rare, and every existing
 * "admin creates/edits a thing" command in this codebase already wraps its
 * manager call in its own {@code executor.execute(...)}, so this doesn't
 * need to duplicate that. {@link #recordProgress} and friends are the hot
 * path (gameplay events) and update the cache first, persisting async.
 */
public final class QuestManager {

    private final JavaPlugin plugin;
    private final QuestRepository questRepository;
    private final QuestProgressRepository progressRepository;
    private final ExecutorService executor;

    private final ConcurrentHashMap<Long, Quest> quests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Map<Long, QuestProgress>> progressByPlayer = new ConcurrentHashMap<>();

    public QuestManager(JavaPlugin plugin, QuestRepository questRepository, QuestProgressRepository progressRepository, ExecutorService executor) {
        this.plugin = plugin;
        this.questRepository = questRepository;
        this.progressRepository = progressRepository;
        this.executor = executor;
    }

    public void loadAll() throws SQLException {
        quests.putAll(questRepository.loadAll());
        plugin.getLogger().info("퀘스트 " + quests.size() + "개를 불러왔습니다.");
    }

    public void loadPlayer(UUID uuid) {
        executor.execute(() -> {
            try {
                Map<Long, QuestProgress> loaded = progressRepository.loadForPlayer(uuid);
                progressByPlayer.put(uuid, new ConcurrentHashMap<>(loaded));
            } catch (SQLException e) {
                plugin.getLogger().severe("퀘스트 진행도 로드 실패 (" + uuid + "): " + e.getMessage());
            }
        });
    }

    public void unloadPlayer(UUID uuid) {
        progressByPlayer.remove(uuid);
    }

    // ---- quest definitions ----

    public Collection<Quest> all() {
        return quests.values();
    }

    public Optional<Quest> find(String name) {
        return quests.values().stream().filter(q -> q.name().equalsIgnoreCase(name)).findFirst();
    }

    public List<Quest> byNpc(int npcId) {
        return quests.values().stream().filter(q -> q.npcId() == npcId).toList();
    }

    public enum CreateResult { SUCCESS, ALREADY_EXISTS }

    /** Blocking — call off the main thread. */
    public CreateResult create(String name, int npcId) throws SQLException {
        if (find(name).isPresent()) {
            return CreateResult.ALREADY_EXISTS;
        }
        long id = questRepository.insertQuest(name, npcId, System.currentTimeMillis());
        quests.put(id, new Quest(id, name, npcId, new java.util.ArrayList<>(), QuestObjectiveType.NONE, null, 0, List.of(), System.currentTimeMillis()));
        return CreateResult.SUCCESS;
    }

    /** Blocking — call off the main thread. */
    public boolean delete(String name) throws SQLException {
        var quest = find(name);
        if (quest.isEmpty()) {
            return false;
        }
        questRepository.deleteQuest(quest.get().id());
        quests.remove(quest.get().id());
        return true;
    }

    /** Blocking — call off the main thread. */
    public boolean setDialogue(String name, List<String> lines) throws SQLException {
        var quest = find(name);
        if (quest.isEmpty()) {
            return false;
        }
        questRepository.replaceDialogue(quest.get().id(), lines);
        quests.put(quest.get().id(), quest.get().withDialogue(new java.util.ArrayList<>(lines)));
        return true;
    }

    /** Blocking — call off the main thread. */
    public boolean setObjective(String name, QuestObjectiveType type, String target, int amount) throws SQLException {
        var quest = find(name);
        if (quest.isEmpty()) {
            return false;
        }
        questRepository.updateObjective(quest.get().id(), type, target, amount);
        quests.put(quest.get().id(), quest.get().withObjective(type, target, amount));
        return true;
    }

    /** Blocking — call off the main thread. */
    public boolean setRewardItems(String name, List<ItemStack> items) throws SQLException {
        var quest = find(name);
        if (quest.isEmpty()) {
            return false;
        }
        questRepository.updateRewardItems(quest.get().id(), items);
        quests.put(quest.get().id(), quest.get().withRewardItems(items));
        return true;
    }

    // ---- player progress (hot path — cache first, persist async) ----

    public Optional<QuestProgress> progress(UUID uuid, long questId) {
        var map = progressByPlayer.get(uuid);
        return map == null ? Optional.empty() : Optional.ofNullable(map.get(questId));
    }

    /** True if this player has never completed it (so the NPC still offers it). */
    public boolean isAvailable(UUID uuid, Quest quest) {
        return progress(uuid, quest.id()).map(p -> p.state() != QuestState.COMPLETED).orElse(true);
    }

    public void save(QuestProgress progress) {
        progressByPlayer.computeIfAbsent(progress.uuid(), k -> new ConcurrentHashMap<>()).put(progress.questId(), progress);
        executor.execute(() -> {
            try {
                progressRepository.upsert(progress);
            } catch (SQLException e) {
                plugin.getLogger().severe("퀘스트 진행도 저장 실패 (" + progress.uuid() + "/" + progress.questId() + "): " + e.getMessage());
            }
        });
    }

    /** +{@code delta} to an ACCEPTED quest's kill/collect count. Returns the new count, or -1 if this player isn't currently working on it. */
    public int incrementObjective(UUID uuid, Quest quest, int delta) {
        var current = progress(uuid, quest.id());
        if (current.isEmpty() || current.get().state() != QuestState.ACCEPTED) {
            return -1;
        }
        int newProgress = current.get().progress() + delta;
        save(current.get().withProgress(newProgress));
        return newProgress;
    }

    /** "거절" — forgets progress entirely so the NPC offers it fresh next time. */
    public void reset(UUID uuid, long questId) {
        var map = progressByPlayer.get(uuid);
        if (map != null) {
            map.remove(questId);
        }
        executor.execute(() -> {
            try {
                progressRepository.delete(uuid, questId);
            } catch (SQLException e) {
                plugin.getLogger().severe("퀘스트 진행도 삭제 실패 (" + uuid + "/" + questId + "): " + e.getMessage());
            }
        });
    }
}
