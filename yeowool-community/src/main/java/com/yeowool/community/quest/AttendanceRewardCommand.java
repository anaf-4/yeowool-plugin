package com.yeowool.community.quest;

import com.yeowool.core.api.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /출석보상설정} — opens {@link AttendanceRewardMenuGui} (OP-only, see plugin.yml permission). */
public final class AttendanceRewardCommand implements CommandExecutor {

    private final AttendanceRewardStore rewardStore;
    private final AttendanceRewardAmountListener amountListener;
    private final MessageService messages;

    public AttendanceRewardCommand(AttendanceRewardStore rewardStore, AttendanceRewardAmountListener amountListener, MessageService messages) {
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
        new AttendanceRewardMenuGui(rewardStore, amountListener).open(player);
        return true;
    }
}
