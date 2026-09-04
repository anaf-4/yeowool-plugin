package com.yeowool.community.quest;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /일일퀘스트} / {@code /주간퀘스트} — each opens {@link QuestBoardGui} directly for its own {@link QuestManager.Period}, no shared selector screen. */
public final class QuestBoardCommand implements CommandExecutor {

    private final QuestContext ctx;
    private final QuestManager.Period period;

    public QuestBoardCommand(QuestContext ctx, QuestManager.Period period) {
        this.ctx = ctx;
        this.period = period;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            ctx.messages().send(sender, "general.player-only");
            return true;
        }
        ctx.questManager().ensureRolled(player, period);
        new QuestBoardGui(ctx, period, player).open(player);
        return true;
    }
}
