package com.yeowool.community.vote;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * 추천 보상 engine, on every server. Votes arrive only on the lobby ({@link VotifierListener} or
 * {@code /추천보상설정 테스트}); a vote for a name that never joined is kept pending and paid when that
 * name joins any server. Rewards are read fresh from the DB for each payout, so an edit made on one
 * server applies everywhere without a reload. Every server announces new vote rows from the DB.
 */
public final class VoteService implements Listener {

    static final String SOURCE = "YeowoolCommunity";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final VoteRepository repository;
    private final Executor executor;
    private volatile long announcedUntilId;

    VoteService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, VoteRepository repository,
                Executor executor, long announcedUntilId) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.executor = executor;
        this.announcedUntilId = announcedUntilId;
    }

    /** Creates the tables, registers listeners/commands and starts the announcement poll (every server). */
    public static void enable(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, Executor executor) throws SQLException {
        VoteRepository repository = new VoteRepository(core.dataSource());
        repository.initialize();
        VoteService service = new VoteService(plugin, core, messages, repository, executor, repository.maxVoteId());
        var plugins = plugin.getServer().getPluginManager();
        plugins.registerEvents(service, plugin);
        if (plugins.isPluginEnabled("Votifier")) {
            plugins.registerEvents(new VotifierListener(service), plugin); // only here: VotifierEvent must not load elsewhere
            plugin.getLogger().info("NuVotifier 추천 수신을 시작합니다.");
        }
        VoteRewardAmountListener amountListener = new VoteRewardAmountListener(plugin, repository, executor);
        plugins.registerEvents(amountListener, plugin);
        var voteCommand = plugin.getCommand("추천");
        if (voteCommand != null) {
            var executorCmd = new VoteCommand(plugin, messages, repository, executor);
            voteCommand.setExecutor(executorCmd);
            voteCommand.setTabCompleter(executorCmd);
        }
        var adminCommand = plugin.getCommand("추천보상설정");
        if (adminCommand != null) {
            var executorCmd = new VoteAdminCommand(plugin, messages, repository, service, amountListener, executor);
            adminCommand.setExecutor(executorCmd);
            adminCommand.setTabCompleter(executorCmd);
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> executor.execute(service::pollAnnouncements), 20L * 20, 20L * 20);
    }

    static String today() {
        return LocalDate.now().toString();
    }

    boolean enabled() {
        return plugin.getConfig().getBoolean("vote.enabled", true);
    }

    /** Main thread: counts the vote (once per player per day) and pays it, or keeps it pending for an unknown name. */
    void recordVote(String rawName, String service) {
        if (!enabled()) {
            plugin.getLogger().info("추천 무시 (vote.enabled: false): " + rawName + " (" + service + ")");
            return;
        }
        String nameKey = VoteRules.nameKey(rawName);
        if (nameKey == null) {
            plugin.getLogger().warning("추천 무시 — 올바르지 않은 닉네임: '" + rawName + "' (" + service + ")");
            return;
        }
        String name = rawName.trim();
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        UUID cachedUuid = cached == null ? null : cached.getUniqueId();
        String day = today();
        executor.execute(() -> {
            try {
                UUID uuid = repository.findUuidByName(name).orElse(cachedUuid);
                if (!repository.insertVote(uuid, name, nameKey, day, service)) {
                    plugin.getLogger().info("추천 중복 무시 — " + name + "님은 오늘(" + day + ") 이미 추천했습니다. (" + service + ")");
                    return;
                }
                plugin.getLogger().info("추천 수신: " + name + " (" + service + ")"
                        + (uuid == null ? " — 접속 기록 없는 닉네임, 첫 접속 때 지급" : ""));
                if (uuid != null) {
                    reward(uuid, name);
                }
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "추천 처리 실패 — 수동 확인 필요: " + name + " " + day, e);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        String nameKey = VoteRules.nameKey(name);
        if (nameKey == null) {
            return;
        }
        executor.execute(() -> {
            try {
                for (long id : repository.pendingIds(nameKey)) {
                    if (repository.claimPending(id, uuid)) {
                        reward(uuid, name);
                    }
                }
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "대기 중인 추천 보상 지급 실패 — 수동 확인 필요: " + name, e);
            }
        });
    }

    /** Worker: pays the every-vote reward, then any milestone the new total reaches (each once, via the DB). */
    private void reward(UUID uuid, String name) throws SQLException {
        TreeMap<Integer, VoteRepository.Reward> rewards = repository.loadRewards();
        int total = repository.totalVotes(uuid);
        VoteRepository.Reward every = rewards.get(VoteRules.EVERY_VOTE);
        if (every != null) {
            deliver(uuid, every, "추천 보상");
        }
        List<Integer> reached = VoteRules.crossed(rewards.keySet(), total - 1, total);
        for (int threshold : reached) {
            if (repository.markMilestonePaid(uuid, threshold)) {
                deliver(uuid, rewards.get(threshold), "누적 추천 " + threshold + "회 보상");
                plugin.getLogger().info("누적 추천 보상 지급: " + name + " " + threshold + "회");
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                return;
            }
            messages.send(player, "vote.thanks", Placeholder.unparsed("reward", summary(every)),
                    Placeholder.unparsed("total", String.valueOf(total)));
            for (int threshold : reached) {
                messages.send(player, "vote.milestone-reached", Placeholder.unparsed("count", String.valueOf(threshold)),
                        Placeholder.unparsed("reward", summary(rewards.get(threshold))));
            }
        });
    }

    /** Worker: 온 via the payout ledger (any server), 별조각 in the DB, items to inventory or mailbox (main thread). */
    private void deliver(UUID uuid, VoteRepository.Reward reward, String reason) throws SQLException {
        if (reward.on() > 0) {
            core.payouts().enqueue(uuid, reward.on(), SOURCE, reason);
        }
        if (reward.stardust() > 0) {
            core.stardust().grant(uuid, reward.stardust(), SOURCE, reason);
        }
        List<ItemStack> items = reward.items();
        Bukkit.getScheduler().runTask(plugin, () -> {
            items.forEach(item -> core.mailbox().deliverOrStore(uuid, item.clone(), SOURCE, reason));
            core.payouts().claimNow(uuid);
        });
    }

    static String summary(VoteRepository.Reward reward) {
        return reward == null ? "없음" : VoteRules.summary(reward.on(), reward.stardust(), reward.items().size());
    }

    /** Worker, every ~20 s on every server: broadcasts votes counted since the last poll. */
    void pollAnnouncements() {
        try {
            List<VoteRepository.Vote> votes = repository.votesAfter(announcedUntilId);
            if (votes.isEmpty()) {
                return;
            }
            announcedUntilId = votes.getLast().id();
            if (!plugin.getConfig().getBoolean("vote.announce", true)) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> votes.forEach(vote ->
                    messages.broadcast("vote.broadcast", Placeholder.unparsed("player", vote.name()))));
        } catch (SQLException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "추천 공지 조회 실패", e);
        }
    }
}
