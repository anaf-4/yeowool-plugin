package com.yeowool.life.job;

import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /직업 선택|정보|목록|스킬} — one active job at a time; switching
 * preserves every job's progress. Bare {@code /직업 선택} opens a picker GUI
 * ({@link JobSelectGui}); {@code /직업 선택 <직업>} still works directly for
 * players who already know the job id. {@code /직업 목록} opens a read-only
 * info GUI ({@link JobListGui}) instead of a plain chat list.
 */
public final class JobCommand implements CommandExecutor, TabCompleter {

    private final JobManager jobManager;

    public JobCommand(JobManager jobManager) {
        this.jobManager = jobManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용할 수 있습니다.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            info(player);
            return true;
        }
        switch (args[0]) {
            case "선택" -> select(player, args);
            case "정보" -> info(player);
            case "목록" -> list(player);
            case "스킬" -> openSkills(player);
            default -> player.sendMessage(Component.text("사용법: /직업 [선택 <직업>|정보|목록|스킬]", NamedTextColor.RED));
        }
        return true;
    }

    private void select(Player player, String[] args) {
        if (args.length == 1) {
            new JobSelectGui(jobManager, player).open(player);
            return;
        }
        if (args.length != 2) {
            player.sendMessage(Component.text("사용법: /직업 선택 [직업]", NamedTextColor.RED));
            return;
        }
        JobDefinition job = jobManager.jobs().get(args[1]);
        if (job == null) {
            player.sendMessage(Component.text("존재하지 않는 직업입니다. /직업 목록 으로 확인하세요.", NamedTextColor.RED));
            return;
        }
        jobManager.setActiveJob(player, args[1]);
        player.sendMessage(Component.text("직업을 " + job.displayName() + "(으)로 설정했습니다.", NamedTextColor.GREEN));
    }

    private void info(Player player) {
        var data = jobManager.getIfLoaded(player.getUniqueId());
        String activeJob = data.map(PlayerJobData::activeJob).orElse(null);
        if (activeJob == null) {
            player.sendMessage(Component.text("현재 선택한 직업이 없습니다. /직업 선택 <직업> 으로 선택하세요.", NamedTextColor.RED));
            return;
        }
        var progress = data.get().progress(activeJob);
        JobDefinition job = jobManager.jobs().get(activeJob);
        String jobName = job != null ? job.displayName() : activeJob;
        player.sendMessage(Component.text("직업: " + jobName + " | 레벨 " + progress.level()
                + " (" + progress.xp() + "/" + jobManager.requiredXpForLevel(progress.level()) + " XP)"
                + " | 스킬 포인트: " + progress.skillPoints(), NamedTextColor.GOLD));
    }

    private void list(Player player) {
        new JobListGui(jobManager, player).open(player);
    }

    private void openSkills(Player player) {
        var data = jobManager.getIfLoaded(player.getUniqueId());
        String activeJob = data.map(PlayerJobData::activeJob).orElse(null);
        if (activeJob == null) {
            player.sendMessage(Component.text("현재 선택한 직업이 없습니다.", NamedTextColor.RED));
            return;
        }
        new JobSkillGui(jobManager, player, activeJob).open(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("선택", "정보", "목록", "스킬"), args[0]);
        }
        if (args.length == 2 && args[0].equals("선택")) {
            return TabCompletions.filter(List.copyOf(jobManager.jobs().keySet()), args[1]);
        }
        return List.of();
    }
}
