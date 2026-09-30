package com.yeowool.community.vote;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.YearMonth;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/** {@code /추천}: vote link + my status; {@code /추천 순위}: this month's top 10. */
public final class VoteCommand implements TabExecutor {

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final VoteRepository repository;
    private final Executor executor;

    VoteCommand(JavaPlugin plugin, MessageService messages, VoteRepository repository, Executor executor) {
        this.plugin = plugin;
        this.messages = messages;
        this.repository = repository;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length > 0 && args[0].equals("순위")) {
            ranking(player);
        } else {
            status(player);
        }
        return true;
    }

    private void status(Player player) {
        String url = plugin.getConfig().getString("vote.url", "");
        messages.send(player, "vote.link", Placeholder.component("url",
                Component.text(url).clickEvent(ClickEvent.openUrl(url))));
        UUID uuid = player.getUniqueId();
        String today = VoteService.today();
        executor.execute(() -> {
            try {
                boolean votedToday = repository.votedOn(uuid, today);
                int total = repository.totalVotes(uuid);
                var rewards = repository.loadRewards();
                OptionalInt next = VoteRules.nextMilestone(rewards.keySet(), total);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messages.send(player, votedToday ? "vote.status-voted" : "vote.status-not-voted",
                            Placeholder.unparsed("total", String.valueOf(total)));
                    if (next.isPresent()) {
                        int count = next.getAsInt();
                        messages.send(player, "vote.next-milestone",
                                Placeholder.unparsed("count", String.valueOf(count)),
                                Placeholder.unparsed("remaining", String.valueOf(count - total)),
                                Placeholder.unparsed("reward", VoteService.summary(rewards.get(count))));
                    } else {
                        messages.send(player, "vote.no-milestone");
                    }
                });
            } catch (SQLException | RuntimeException e) {
                fail(player, e);
            }
        });
    }

    private void ranking(Player player) {
        YearMonth month = YearMonth.now();
        String[] range = VoteRules.monthRange(month);
        executor.execute(() -> {
            try {
                List<VoteRepository.Ranked> top = repository.topVoters(range[0], range[1], 10);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messages.send(player, "vote.ranking-header", Placeholder.unparsed("month", String.valueOf(month.getMonthValue())));
                    if (top.isEmpty()) {
                        messages.send(player, "vote.ranking-empty");
                    }
                    for (int i = 0; i < top.size(); i++) {
                        messages.send(player, "vote.ranking-line", Placeholder.unparsed("rank", String.valueOf(i + 1)),
                                Placeholder.unparsed("player", top.get(i).name()),
                                Placeholder.unparsed("votes", String.valueOf(top.get(i).votes())));
                    }
                });
            } catch (SQLException | RuntimeException e) {
                fail(player, e);
            }
        });
    }

    private void fail(Player player, Exception e) {
        plugin.getLogger().log(Level.WARNING, "추천 정보 조회 실패", e);
        Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "general.error"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 && "순위".startsWith(args[0]) ? List.of("순위") : List.of();
    }
}
