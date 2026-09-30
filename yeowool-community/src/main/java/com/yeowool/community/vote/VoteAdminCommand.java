package com.yeowool.community.vote;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * {@code /추천보상설정} (permission {@code yeowool.community.vote.manage}): no args opens
 * {@link VoteRewardMenuGui}; {@code 누적추가|누적삭제 <횟수>}, {@code 테스트 <닉네임>}, {@code 리로드}.
 * Also the GUIs' way to (re)open each other and persist edits — reads/writes go through the worker.
 */
public final class VoteAdminCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("누적추가", "누적삭제", "테스트", "리로드");

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final VoteRepository repository;
    private final VoteService service;
    private final VoteRewardAmountListener amountListener;
    private final Executor executor;

    VoteAdminCommand(JavaPlugin plugin, MessageService messages, VoteRepository repository, VoteService service,
                     VoteRewardAmountListener amountListener, Executor executor) {
        this.plugin = plugin;
        this.messages = messages;
        this.repository = repository;
        this.service = service;
        this.amountListener = amountListener;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                openMenu(player);
            } else {
                messages.send(sender, "general.player-only");
            }
            return true;
        }
        switch (args[0]) {
            case "누적추가", "누적삭제" -> {
                Integer count = args.length == 2 ? parseCount(args[1]) : null;
                if (count == null) {
                    messages.send(sender, "vote.admin.usage");
                } else if (args[0].equals("누적추가")) {
                    addMilestone(sender, count);
                } else {
                    deleteMilestone(sender, count);
                }
            }
            case "테스트" -> {
                if (args.length != 2) {
                    messages.send(sender, "vote.admin.usage");
                } else if (!service.enabled()) {
                    messages.send(sender, "vote.admin.disabled");
                } else {
                    service.recordVote(args[1], "test");
                    messages.send(sender, "vote.admin.test-sent", Placeholder.unparsed("player", args[1]));
                }
            }
            case "리로드" -> {
                plugin.reloadConfig();
                messages.reload();
                messages.send(sender, "vote.admin.reloaded");
            }
            default -> messages.send(sender, "vote.admin.usage");
        }
        return true;
    }

    private static Integer parseCount(String text) {
        try {
            int count = Integer.parseInt(text);
            return count > 0 ? count : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    void openMenu(Player admin) {
        async(admin, () -> {
            var rewards = repository.loadRewards();
            main(() -> new VoteRewardMenuGui(this, rewards).open(admin));
        });
    }

    void openEditor(Player admin, int threshold) {
        async(admin, () -> {
            VoteRepository.Reward reward = repository.loadRewards().get(threshold);
            main(() -> {
                if (reward == null) {
                    messages.send(admin, "vote.admin.not-found", Placeholder.unparsed("count", String.valueOf(threshold)));
                } else if (admin.isOnline()) {
                    new VoteRewardEditorGui(this, amountListener, reward).open(admin);
                }
            });
        });
    }

    void saveItems(Player admin, int threshold, List<ItemStack> items) {
        async(admin, () -> {
            boolean saved = repository.saveItems(threshold, items);
            main(() -> admin.sendMessage(saved
                    ? Component.text(VoteRewardEditorGui.label(threshold) + " 아이템을 저장했습니다. (" + items.size() + "개)", NamedTextColor.GREEN)
                    : Component.text(VoteRewardEditorGui.label(threshold) + "이(가) 그 사이 삭제되어 아이템을 저장하지 못했습니다.", NamedTextColor.RED)));
        });
    }

    private void addMilestone(CommandSender sender, int count) {
        async(sender, () -> {
            boolean added = repository.addMilestone(count);
            main(() -> {
                messages.send(sender, added ? "vote.admin.added" : "vote.admin.exists", Placeholder.unparsed("count", String.valueOf(count)));
                if (added && sender instanceof Player player) {
                    openEditor(player, count);
                }
            });
        });
    }

    void deleteMilestone(CommandSender sender, int count) {
        async(sender, () -> {
            boolean deleted = repository.deleteMilestone(count);
            main(() -> messages.send(sender, deleted ? "vote.admin.deleted" : "vote.admin.not-found",
                    Placeholder.unparsed("count", String.valueOf(count))));
        });
    }

    @FunctionalInterface
    private interface SqlWork {
        void run() throws SQLException;
    }

    private void async(CommandSender sender, SqlWork work) {
        executor.execute(() -> {
            try {
                work.run();
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "추천 보상 설정 처리 실패", e);
                main(() -> messages.send(sender, "general.error"));
            }
        });
    }

    private void main(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(sub -> sub.startsWith(args[0])).toList();
        }
        if (args.length == 2 && args[0].equals("테스트")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}
