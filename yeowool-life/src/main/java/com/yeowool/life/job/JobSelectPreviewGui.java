package com.yeowool.life.job;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Shown when a player clicks a job in {@link JobSelectGui} instead of
 * selecting it immediately — a real hover-to-preview isn't possible (Bukkit
 * has no "cursor is over this slot" event, and a GUI's title/background is
 * fixed the moment it opens), so this is the closest equivalent: a
 * click-to-preview step that renders the job's own {@code medival_jobs_*}
 * background before committing, with an explicit confirm/back choice.
 */
public final class JobSelectPreviewGui extends YeowoolGui {

    private static final int SLOT_INFO = 4;
    private static final int SLOT_CONFIRM = 41;
    private static final int SLOT_BACK = 37;

    public JobSelectPreviewGui(JobManager jobManager, Player player, JobDefinition job) {
        super(54, JobBackgroundImages.title(
                jobManager.plugin().getConfig().getInt("jobs.gui-background-offset", -46),
                "medival_jobs_" + job.id(),
                Component.text(job.displayName(), NamedTextColor.DARK_PURPLE)));

        var data = jobManager.getIfLoaded(player.getUniqueId());
        var progress = data.flatMap(d -> d.peekProgress(job.id()));

        setButton(SLOT_INFO, GuiButton.display(JobIconFactory.build(job, jobManager, progress, null)));

        setButton(SLOT_CONFIRM, GuiButton.of(confirmIcon(job), event -> {
            Player clicker = (Player) event.getWhoClicked();
            jobManager.setActiveJob(clicker, job.id());
            clicker.sendMessage(Component.text("직업을 " + job.displayName() + "(으)로 설정했습니다.", NamedTextColor.GREEN));
            clicker.closeInventory();
        }));

        setButton(SLOT_BACK, GuiButton.of(backIcon(), event ->
                new JobSelectGui(jobManager, (Player) event.getWhoClicked()).open((Player) event.getWhoClicked())));
    }

    private ItemStack confirmIcon(JobDefinition job) {
        ItemStack stack = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("YES", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(job.displayName() + "(으)로 직업을 설정합니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack backIcon() {
        ItemStack stack = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("NO", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("목록으로 돌아갑니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
