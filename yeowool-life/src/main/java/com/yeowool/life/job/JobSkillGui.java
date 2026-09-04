package com.yeowool.life.job;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /직업 스킬} — spend the active job's unspent skill points on its
 * (shared) passive skill tree. Unlike {@link JobListGui}/{@link
 * JobSelectGui} (which show every job at once, so no single background
 * would fit), this screen is always scoped to exactly one job — the active
 * one — so it renders that job's own {@code medival_jobs_<jobId>} full
 * background image behind the title, matching how NPCShopGui/AttendanceGui
 * theme their own single-context screens.
 */
public final class JobSkillGui extends YeowoolGui {

    public JobSkillGui(JobManager jobManager, Player player, String jobId) {
        super(54, JobBackgroundImages.title(
                jobManager.plugin().getConfig().getInt("jobs.gui-background-offset", -46),
                "medival_jobs_" + jobId,
                Component.text("직업 스킬", NamedTextColor.DARK_PURPLE)));

        var jobDef = jobManager.jobs().get(jobId);
        String jobName = jobDef != null ? jobDef.displayName() : jobId;
        var progress = jobManager.getIfLoaded(player.getUniqueId())
                .map(data -> data.progress(jobId))
                .orElse(null);

        setButton(4, GuiButton.display(buildInfoIcon(jobManager, jobName, progress)));

        int slot = 10;
        for (SkillNode node : jobManager.skills().values()) {
            int allocated = progress != null ? progress.allocatedPoints(node.id()) : 0;
            setButton(slot, GuiButton.of(buildSkillIcon(node, allocated), event -> {
                Player clicker = (Player) event.getWhoClicked();
                var result = jobManager.allocateSkillPoint(clicker, jobId, node.id());
                switch (result) {
                    case SUCCESS -> clicker.sendMessage(Component.text(node.displayName() + " 포인트를 사용했습니다.", NamedTextColor.GREEN));
                    case NO_POINTS -> clicker.sendMessage(Component.text("사용 가능한 스킬 포인트가 없습니다.", NamedTextColor.RED));
                    case MAX_POINTS -> clicker.sendMessage(Component.text("이미 최대 포인트입니다.", NamedTextColor.RED));
                    case UNKNOWN_SKILL -> {
                    }
                }
                new JobSkillGui(jobManager, clicker, jobId).open(clicker);
            }));
            slot += 2;
        }
    }

    private ItemStack buildInfoIcon(JobManager jobManager, String jobName, PlayerJobData.JobProgress progress) {
        ItemStack stack = new ItemStack(Material.BOOK);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(jobName, NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        if (progress != null) {
            lore.add(Component.text("레벨: " + progress.level(), NamedTextColor.GRAY));
            lore.add(Component.text("경험치: " + progress.xp() + "/" + jobManager.requiredXpForLevel(progress.level()), NamedTextColor.GRAY));
            lore.add(Component.text("남은 스킬 포인트: " + progress.skillPoints(), NamedTextColor.YELLOW));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildSkillIcon(SkillNode node, int allocated) {
        ItemStack stack = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(node.displayName(), NamedTextColor.AQUA));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("현재: " + allocated + "/" + node.maxPoints(), NamedTextColor.GRAY));
        lore.add(Component.text("효과: " + describeEffect(node), NamedTextColor.GRAY));
        lore.add(Component.text("클릭하여 포인트 1 사용", NamedTextColor.GREEN));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private String describeEffect(SkillNode node) {
        return switch (node.effectType()) {
            case XP_BONUS_PERCENT -> "포인트당 경험치 +" + node.perPointValue() + "%";
            case EXTRA_YIELD_CHANCE_PERCENT -> "포인트당 추가 획득 확률 +" + node.perPointValue() + "%";
            case CRITICAL_XP_CHANCE_PERCENT -> "포인트당 경험치 2배 확률 +" + node.perPointValue() + "%";
            case BONUS_CURRENCY_CHANCE_PERCENT -> "포인트당 부수입 획득 확률 +" + node.perPointValue() + "%";
        };
    }
}
