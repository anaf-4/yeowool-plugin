package com.yeowool.life.job;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** {@code /직업 목록} — read-only info display of every job: description + this player's progress, if they've ever played it. */
public final class JobListGui extends YeowoolGui {

    private static final int[] ICON_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    public JobListGui(JobManager jobManager, Player player) {
        super(45, Component.text("직업 목록", NamedTextColor.DARK_PURPLE));

        var data = jobManager.getIfLoaded(player.getUniqueId());
        int i = 0;
        for (JobDefinition job : jobManager.jobs().values()) {
            if (i >= ICON_SLOTS.length) {
                break;
            }
            var progress = data.flatMap(d -> d.peekProgress(job.id()));
            setButton(ICON_SLOTS[i], GuiButton.display(JobIconFactory.build(job, jobManager, progress, null)));
            i++;
        }
    }
}
