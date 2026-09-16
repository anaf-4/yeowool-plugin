package com.yeowool.quest;

import com.yeowool.core.api.gui.ItemGridEditorGui;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * {@code /퀘스트 생성|대사|대사초기화|목표|보상|목록|정보|삭제} — everything an
 * admin needs to build a quest, matching the reference video's "명령어로
 * 생성, 대사, 조건, 보상 관리" flow. {@code 생성} binds the new quest to
 * whichever Citizens NPC the admin currently has selected (stand near it and
 * run Citizens' own {@code /npc select}, or just right-click it with the
 * stick Citizens gives you — same selector every other Citizens command
 * uses).
 */
public final class QuestAdminCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "yeowool.quest.manage";

    private final JavaPlugin plugin;
    private final QuestManager questManager;
    private final ExecutorService executor;

    public QuestAdminCommand(JavaPlugin plugin, QuestManager questManager, ExecutorService executor) {
        this.plugin = plugin;
        this.questManager = questManager;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§c사용법: /퀘스트 <생성|대사|대사초기화|목표|보상|목록|정보|삭제> ...");
            return true;
        }
        switch (args[0]) {
            case "생성" -> create(sender, args);
            case "대사" -> addDialogue(sender, args);
            case "대사초기화" -> clearDialogue(sender, args);
            case "목표" -> setObjective(sender, args);
            case "보상" -> setReward(sender, args);
            case "목록" -> list(sender);
            case "정보" -> info(sender, args);
            case "삭제" -> delete(sender, args);
            default -> sender.sendMessage("§c사용법: /퀘스트 <생성|대사|대사초기화|목표|보상|목록|정보|삭제> ...");
        }
        return true;
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다. (NPC를 선택한 상태여야 합니다)");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("§c사용법: /퀘스트 생성 <이름> (Citizens NPC를 먼저 선택하세요)");
            return;
        }
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(player);
        if (npc == null) {
            sender.sendMessage("§c선택된 NPC가 없습니다. 먼저 Citizens NPC를 선택하세요 (예: 셀렉터로 우클릭).");
            return;
        }
        String name = args[1];
        executor.execute(() -> {
            try {
                var result = questManager.create(name, npc.getId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (result == QuestManager.CreateResult.ALREADY_EXISTS) {
                        sender.sendMessage("§c이미 존재하는 퀘스트 이름입니다: " + name);
                        return;
                    }
                    sender.sendMessage(Component.text("퀘스트가 생성되었습니다: ", NamedTextColor.GREEN)
                            .append(Component.text(name + " (NPC: " + npc.getName() + ")", NamedTextColor.YELLOW)));
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("퀘스트 생성 실패: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c퀘스트 생성에 실패했습니다."));
            }
        });
    }

    private void addDialogue(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c사용법: /퀘스트 대사 <이름> <대사 텍스트>");
            return;
        }
        String name = args[1];
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        var quest = questManager.find(name);
        if (quest.isEmpty()) {
            sender.sendMessage("§c존재하지 않는 퀘스트입니다: " + name);
            return;
        }
        List<String> lines = new ArrayList<>(quest.get().dialogue());
        lines.add(text);
        executor.execute(() -> {
            try {
                questManager.setDialogue(name, lines);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        Component.text("대사 " + lines.size() + "줄이 추가되었습니다: ", NamedTextColor.GREEN)
                                .append(Component.text(name, NamedTextColor.YELLOW))));
            } catch (SQLException e) {
                plugin.getLogger().severe("퀘스트 대사 추가 실패: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c대사 추가에 실패했습니다."));
            }
        });
    }

    private void clearDialogue(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§c사용법: /퀘스트 대사초기화 <이름>");
            return;
        }
        String name = args[1];
        if (questManager.find(name).isEmpty()) {
            sender.sendMessage("§c존재하지 않는 퀘스트입니다: " + name);
            return;
        }
        executor.execute(() -> {
            try {
                questManager.setDialogue(name, List.of());
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§a대사를 초기화했습니다: " + name));
            } catch (SQLException e) {
                plugin.getLogger().severe("퀘스트 대사 초기화 실패: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c대사 초기화에 실패했습니다."));
            }
        });
    }

    private void setObjective(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /퀘스트 목표 <이름> <처치|수집|없음> [대상] [개수]");
            return;
        }
        String name = args[1];
        if (questManager.find(name).isEmpty()) {
            sender.sendMessage("§c존재하지 않는 퀘스트입니다: " + name);
            return;
        }
        QuestObjectiveType type = switch (args.length >= 3 ? args[2] : "없음") {
            case "처치" -> QuestObjectiveType.KILL;
            case "수집" -> QuestObjectiveType.COLLECT;
            case "없음" -> QuestObjectiveType.NONE;
            default -> null;
        };
        if (type == null) {
            sender.sendMessage("§c목표 종류는 처치, 수집, 없음 중 하나여야 합니다.");
            return;
        }
        String target = null;
        int amount = 0;
        if (type != QuestObjectiveType.NONE) {
            if (args.length != 5) {
                sender.sendMessage("§c사용법: /퀘스트 목표 <이름> <처치|수집> <대상> <개수>");
                return;
            }
            target = args[3].toUpperCase();
            boolean validTarget = type == QuestObjectiveType.KILL ? isValidEnum(EntityType.class, target) : isValidEnum(Material.class, target);
            if (!validTarget) {
                sender.sendMessage("§c대상 ID가 올바르지 않습니다: " + args[3]);
                return;
            }
            try {
                amount = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§c개수는 숫자여야 합니다.");
                return;
            }
            if (amount <= 0) {
                sender.sendMessage("§c개수는 0보다 커야 합니다.");
                return;
            }
        }
        String finalTarget = target;
        int finalAmount = amount;
        executor.execute(() -> {
            try {
                questManager.setObjective(name, type, finalTarget, finalAmount);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§a목표가 설정되었습니다: " + name));
            } catch (SQLException e) {
                plugin.getLogger().severe("퀘스트 목표 설정 실패: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c목표 설정에 실패했습니다."));
            }
        });
    }

    private void setReward(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다. (GUI에 아이템을 넣어 보상을 설정합니다)");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("§c사용법: /퀘스트 보상 <이름>");
            return;
        }
        String name = args[1];
        var quest = questManager.find(name);
        if (quest.isEmpty()) {
            sender.sendMessage("§c존재하지 않는 퀘스트입니다: " + name);
            return;
        }
        new ItemGridEditorGui("퀘스트 보상 설정 (닫으면 저장됩니다)", quest.get().rewardItems(), items -> executor.execute(() -> {
            try {
                questManager.setRewardItems(name, items);
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§a보상이 저장되었습니다: " + name + " (" + items.size() + "종)"));
            } catch (SQLException e) {
                plugin.getLogger().severe("퀘스트 보상 저장 실패: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§c보상 저장에 실패했습니다."));
            }
        })).open(player);
    }

    private void list(CommandSender sender) {
        var quests = questManager.all();
        if (quests.isEmpty()) {
            sender.sendMessage("§7등록된 퀘스트가 없습니다.");
            return;
        }
        sender.sendMessage("§6=== 퀘스트 목록 (" + quests.size() + "개) ===");
        for (Quest quest : quests) {
            sender.sendMessage("§7- " + quest.name() + " (NPC #" + quest.npcId() + ", 대사 " + quest.dialogue().size()
                    + "줄, 목표: " + quest.objectiveType() + ")");
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§c사용법: /퀘스트 정보 <이름>");
            return;
        }
        var quest = questManager.find(args[1]);
        if (quest.isEmpty()) {
            sender.sendMessage("§c존재하지 않는 퀘스트입니다: " + args[1]);
            return;
        }
        Quest q = quest.get();
        sender.sendMessage("§6퀘스트: " + q.name() + " §7(NPC #" + q.npcId() + ")");
        sender.sendMessage("§7대사 " + q.dialogue().size() + "줄, 목표: " + q.objectiveType()
                + (q.objectiveType() == QuestObjectiveType.NONE ? "" : " " + q.objectiveTarget() + " x" + q.objectiveAmount()));
        sender.sendMessage("§7보상 아이템 " + q.rewardItems().size() + "종");
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§c사용법: /퀘스트 삭제 <이름>");
            return;
        }
        String name = args[1];
        executor.execute(() -> {
            try {
                boolean removed = questManager.delete(name);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(removed
                        ? "§a퀘스트를 삭제했습니다: " + name : "§c존재하지 않는 퀘스트입니다: " + name));
            } catch (SQLException e) {
                plugin.getLogger().severe("퀘스트 삭제 실패: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c삭제에 실패했습니다."));
            }
        });
    }

    private boolean isValidEnum(Class<? extends Enum<?>> enumClass, String name) {
        for (Enum<?> constant : enumClass.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("생성", "대사", "대사초기화", "목표", "보상", "목록", "정보", "삭제").stream()
                    .filter(s -> s.startsWith(args[0])).toList();
        }
        if (args.length == 2 && !args[0].equals("생성")) {
            return questManager.all().stream().map(Quest::name).filter(n -> n.startsWith(args[1])).toList();
        }
        if (args.length == 3 && args[0].equals("목표")) {
            return List.of("처치", "수집", "없음").stream().filter(s -> s.startsWith(args[2])).toList();
        }
        return List.of();
    }
}
