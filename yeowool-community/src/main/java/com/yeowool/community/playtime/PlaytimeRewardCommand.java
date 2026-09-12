package com.yeowool.community.playtime;

import com.yeowool.core.api.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /플레이타임보상설정} — opens {@link PlaytimeRewardMenuGui} (OP-only, see plugin.yml permission). */
public final class PlaytimeRewardCommand implements CommandExecutor {

    private final PlaytimeRewardStore rewardStore;
    private final PlaytimeRewardAmountListener amountListener;
    private final MessageService messages;

    public PlaytimeRewardCommand(PlaytimeRewardStore rewardStore, PlaytimeRewardAmountListener amountListener, MessageService messages) {
        this.rewardStore = rewardStore;
        this.amountListener = amountListener;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        new PlaytimeRewardMenuGui(rewardStore, amountListener).open(player);
        return true;
    }
}
