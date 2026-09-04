package com.yeowool.analytics.command;

import com.yeowool.analytics.MetricsCollector;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;

/**
 * {@code /여울통계} — text report covering plugin plan 11.2's four
 * categories (서버/경제/토지/생활), run off the main thread since it's a
 * handful of aggregate SQL queries.
 */
public final class StatsCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final MetricsCollector collector;
    private final ExecutorService executor;

    public StatsCommand(JavaPlugin plugin, MessageService messages, MetricsCollector collector, ExecutorService executor) {
        this.plugin = plugin;
        this.messages = messages;
        this.collector = collector;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        messages.send(sender, "stats.collecting");

        double tps = Bukkit.getServer().getTPS()[0];
        int online = Bukkit.getOnlinePlayers().size();

        executor.execute(() -> {
            Component report = messages.resolveRaw("stats.header")
                    .appendNewline()
                    .append(messages.resolveRaw("stats.server-line",
                            Placeholder.unparsed("tps", String.format("%.2f", tps)),
                            Placeholder.unparsed("online", String.valueOf(online))));

            try {
                var economy = collector.economySummary();
                report = report.appendNewline().append(messages.resolveRaw("stats.economy-line",
                        Placeholder.unparsed("total_on", String.format("%,d", economy.totalOn())),
                        Placeholder.unparsed("avg_on", String.format("%.0f", economy.averageOn())),
                        Placeholder.unparsed("total_bank", String.format("%,d", economy.totalBank())),
                        Placeholder.unparsed("player_count", String.valueOf(economy.playerCount()))));
            } catch (Exception e) {
                report = report.appendNewline().append(messages.resolveRaw("stats.economy-error"));
            }

            try {
                var land = collector.landSummary();
                report = report.appendNewline().append(messages.resolveRaw("stats.land-line",
                        Placeholder.unparsed("land_count", String.valueOf(land.landCount())),
                        Placeholder.unparsed("total_chunks", String.valueOf(land.totalChunks())),
                        Placeholder.unparsed("avg_level", String.format("%.1f", land.averageLevel()))));
            } catch (Exception e) {
                report = report.appendNewline().append(messages.resolveRaw("stats.land-error"));
            }

            try {
                var life = collector.lifeStatistics();
                if (life.isEmpty()) {
                    report = report.appendNewline().append(messages.resolveRaw("stats.life-empty"));
                } else {
                    for (var entry : life.entrySet()) {
                        report = report.appendNewline().append(messages.resolveRaw("stats.life-line",
                                Placeholder.unparsed("label", entry.getKey()),
                                Placeholder.unparsed("value", String.format("%,d", entry.getValue()))));
                    }
                }
            } catch (Exception e) {
                report = report.appendNewline().append(messages.resolveRaw("stats.life-error"));
            }

            Component finalReport = report;
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(finalReport));
        });
        return true;
    }
}
