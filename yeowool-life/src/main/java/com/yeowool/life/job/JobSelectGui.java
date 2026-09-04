package com.yeowool.life.job;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** {@code /직업 선택} — click a job to preview its background+details in {@link JobSelectPreviewGui}, which is where it's actually confirmed. */
public final class JobSelectGui extends YeowoolGui {

    private static final int[] ICON_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    public JobSelectGui(JobManager jobManager, Player player) {
        super(45, Component.text("직업 선택", NamedTextColor.DARK_PURPLE));

        var data = jobManager.getIfLoaded(player.getUniqueId());
        int i = 0;
        for (JobDefinition job : jobManager.jobs().values()) {
            if (i >= ICON_SLOTS.length) {
                break;
            }
            var progress = data.flatMap(d -> d.peekProgress(job.id()));
            setButton(ICON_SLOTS[i], GuiButton.of(JobIconFactory.build(job, jobManager, progress, "클릭하여 미리보기"), event ->
                    new JobSelectPreviewGui(jobManager, (Player) event.getWhoClicked(), job).open((Player) event.getWhoClicked())));
            i++;
        }
    }
}
