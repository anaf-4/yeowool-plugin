package com.yeowool.life.job;

import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared icon+lore builder for {@link JobListGui} and {@link JobSelectGui},
 * so both render a job identically apart from the closing action line.
 * Resolves {@link JobDefinition#customIconId()} via ItemsAdder when present
 * and available, falling back to the plain {@link JobDefinition#icon()}
 * material otherwise — same guarded pattern as yeowool-market's
 * {@code ItemResolver.build}, so this module still loads fine without
 * ItemsAdder installed.
 */
final class JobIconFactory {

    private JobIconFactory() {
    }

    static ItemStack build(JobDefinition job, JobManager jobManager, Optional<PlayerJobData.JobProgress> progress, String actionLine) {
        ItemStack stack = resolveIcon(job);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(job.displayName(), NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        if (!job.description().isBlank()) {
            lore.add(Component.text(job.description(), NamedTextColor.GRAY));
        }
        if (progress.isPresent()) {
            var p = progress.get();
            lore.add(Component.text("레벨: " + p.level(), NamedTextColor.YELLOW));
            lore.add(Component.text("경험치: " + p.xp() + "/" + jobManager.requiredXpForLevel(p.level()), NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("아직 시작하지 않은 직업입니다.", NamedTextColor.DARK_GRAY));
        }
        if (actionLine != null) {
            lore.add(Component.text(actionLine, NamedTextColor.GREEN));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack resolveIcon(JobDefinition job) {
        if (job.customIconId() != null && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance(job.customIconId());
            if (custom != null) {
                return custom.getItemStack();
            }
        }
        return new ItemStack(job.icon());
    }
}
