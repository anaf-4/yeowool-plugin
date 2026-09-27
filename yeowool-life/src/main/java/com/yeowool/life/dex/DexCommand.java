package com.yeowool.life.dex;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.life.fishing.FishRarity;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** {@code /도감} — opens {@link DexMenuGui}, the category picker for 물고기/광물/사냥/작물. */
public final class DexCommand implements CommandExecutor {

    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final Supplier<List<FishRarity>> fishRarities;
    private final List<DexEntry> mining;
    private final List<DexEntry> hunting;
    private final List<DexEntry> farming;
    private final int fishBackgroundOffsetPx;
    private final DexRewardService rewards;

    /**
     * {@code fishRarities} is re-read on every open (it merges CustomFishing's loot table, which may
     * not be loaded yet at enable time and can change on {@code /cfishing reload}); {@code rewards}
     * is null when dex rewards are disabled.
     */
    public DexCommand(YeowoolCoreAPI core, MessageService messages, Supplier<List<FishRarity>> fishRarities,
                      List<DexEntry> mining, List<DexEntry> hunting, List<DexEntry> farming, int fishBackgroundOffsetPx,
                      DexRewardService rewards) {
        this.core = core;
        this.messages = messages;
        this.fishRarities = fishRarities;
        this.mining = mining;
        this.hunting = hunting;
        this.farming = farming;
        this.fishBackgroundOffsetPx = fishBackgroundOffsetPx;
        this.rewards = rewards;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        Map<String, DexRewardService.Progress> progress = rewards == null ? Map.of() : rewards.progressFor(player);
        new DexMenuGui(core, fishRarities.get(), mining, hunting, farming, fishBackgroundOffsetPx, progress).open(player);
        return true;
    }
}
