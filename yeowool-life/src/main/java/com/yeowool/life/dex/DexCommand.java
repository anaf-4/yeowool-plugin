package com.yeowool.life.dex;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.life.fishing.FishRarity;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /도감} — opens {@link DexMenuGui}, the category picker for 물고기/광물/사냥/작물. */
public final class DexCommand implements CommandExecutor {

    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final List<FishRarity> fishRarities;
    private final List<DexEntry> mining;
    private final List<DexEntry> hunting;
    private final List<DexEntry> farming;
    private final int fishBackgroundOffsetPx;

    public DexCommand(YeowoolCoreAPI core, MessageService messages, List<FishRarity> fishRarities,
                       List<DexEntry> mining, List<DexEntry> hunting, List<DexEntry> farming, int fishBackgroundOffsetPx) {
        this.core = core;
        this.messages = messages;
        this.fishRarities = fishRarities;
        this.mining = mining;
        this.hunting = hunting;
        this.farming = farming;
        this.fishBackgroundOffsetPx = fishBackgroundOffsetPx;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        new DexMenuGui(core, fishRarities, mining, hunting, farming, fishBackgroundOffsetPx).open(player);
        return true;
    }
}
