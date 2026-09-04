package com.yeowool.life.fishing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** {@code /낚시대회} — shows whether the daily competition is running and, if so, its live top 3. */
public final class FishingCompetitionCommand implements CommandExecutor {

    private final FishingCompetitionManager competitionManager;

    public FishingCompetitionCommand(FishingCompetitionManager competitionManager) {
        this.competitionManager = competitionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!competitionManager.isActive()) {
            sender.sendMessage(Component.text("현재 진행 중인 낚시 대회가 없습니다. 매일 정해진 시각에 자동으로 시작됩니다.", NamedTextColor.GRAY));
            return true;
        }
        sender.sendMessage(Component.text("현재 낚시 대회가 진행 중입니다!", NamedTextColor.AQUA));
        var top = competitionManager.currentTop(3);
        if (top.isEmpty()) {
            sender.sendMessage(Component.text("아직 기록이 없습니다.", NamedTextColor.GRAY));
            return true;
        }
        String[] medals = {"1위", "2위", "3위"};
        for (int i = 0; i < top.size(); i++) {
            var entry = top.get(i);
            sender.sendMessage(Component.text(medals[i] + " " + entry.playerName() + " - "
                    + entry.speciesName() + " (" + String.format("%.1f", entry.sizeCm()) + "cm)", NamedTextColor.YELLOW));
        }
        return true;
    }
}
