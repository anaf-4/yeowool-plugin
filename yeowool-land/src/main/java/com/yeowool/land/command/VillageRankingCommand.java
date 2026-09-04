package com.yeowool.land.command;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.land.LandManager;
import com.yeowool.land.model.Land;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Comparator;
import java.util.List;

/**
 * {@code /마을랭킹} — every land ranked by its owner's land level (ties
 * broken by land XP), so a claim reads as a "village" with its own
 * identity (see {@code /토지 이름}) rather than just an anonymous chunk
 * claim. All in-memory ({@link LandManager#all()}), no DB round trip.
 */
public final class VillageRankingCommand implements CommandExecutor {

    private static final int TOP_N = 10;

    private final YeowoolCoreAPI core;
    private final LandManager landManager;
    private final MessageService messages;

    public VillageRankingCommand(YeowoolCoreAPI core, LandManager landManager, MessageService messages) {
        this.core = core;
        this.landManager = landManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<Land> top = landManager.all().stream()
                .sorted(Comparator
                        .comparingInt((Land land) -> core.landStats().getLandLevel(land.getOwner())).reversed()
                        .thenComparing(Comparator.comparingLong((Land land) -> core.landStats().getLandXp(land.getOwner())).reversed()))
                .limit(TOP_N)
                .toList();

        if (top.isEmpty()) {
            messages.send(sender, "land.village-ranking-empty");
            return true;
        }

        messages.send(sender, "land.village-ranking-header", Placeholder.unparsed("count", String.valueOf(top.size())));
        int rank = 1;
        for (Land land : top) {
            String ownerName = Bukkit.getOfflinePlayer(land.getOwner()).getName();
            messages.send(sender, "land.village-ranking-line",
                    Placeholder.unparsed("rank", String.valueOf(rank++)),
                    Placeholder.unparsed("name", LandCommand.displayName(land)),
                    Placeholder.unparsed("owner", ownerName != null ? ownerName : "알 수 없음"),
                    Placeholder.unparsed("level", String.valueOf(core.landStats().getLandLevel(land.getOwner()))),
                    Placeholder.unparsed("members", String.valueOf(land.getMembers().size())),
                    Placeholder.unparsed("chunks", String.valueOf(land.getChunkCount())));
        }
        return true;
    }
}
