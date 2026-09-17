package com.yeowool.raid;

import com.yeowool.core.api.gui.ItemGridEditorGui;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public final class RaidAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "생성", "npc설정", "몹설정", "티켓설정", "인원설정", "제한시간설정", "부활횟수설정",
            "보상설정", "인스턴스설정", "인스턴스개수설정", "목록", "정보", "삭제", "새로고침");
    private static final List<String> SLOT_FIELDS = List.of("입장", "보스스폰", "퇴장", "경계1", "경계2");

    private final JavaPlugin plugin;
    private final RaidManager raidManager;
    private final ExecutorService executor;

    public RaidAdminCommand(JavaPlugin plugin, RaidManager raidManager, ExecutorService executor) {
        this.plugin = plugin;
        this.raidManager = raidManager;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§c사용법: /레이드 <생성|몹설정|티켓설정|인원설정|제한시간설정|부활횟수설정|보상설정|인스턴스설정|인스턴스개수설정|목록|정보|삭제|새로고침>");
            return true;
        }
        switch (args[0]) {
            case "생성" -> handleCreate(sender, args);
            case "npc설정" -> handleSetNpc(sender, args);
            case "몹설정" -> handleSetMythicMob(sender, args);
            case "티켓설정" -> handleSetTicket(sender, args);
            case "인원설정" -> handleSetPartySize(sender, args);
            case "제한시간설정" -> handleSetTimeLimit(sender, args);
            case "부활횟수설정" -> handleSetSharedLives(sender, args);
            case "보상설정" -> handleSetRewards(sender, args);
            case "인스턴스설정" -> handleSetInstance(sender, args);
            case "인스턴스개수설정" -> handleSetInstanceCount(sender, args);
            case "목록" -> handleList(sender);
            case "정보" -> handleInfo(sender, args);
            case "삭제" -> handleDelete(sender, args);
            case "새로고침" -> handleReload(sender);
            default -> sender.sendMessage("§c알 수 없는 하위 명령어입니다.");
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§c사용법: /레이드 생성 <이름>");
            return;
        }
        String name = args[1];
        executor.execute(() -> {
            try {
                var result = raidManager.create(name);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        result == RaidManager.CreateResult.SUCCESS
                                ? "§a레이드 '" + name + "'를 생성했습니다."
                                : "§c이미 존재하는 레이드입니다: " + name));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("레이드 생성 실패: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c레이드 생성 중 오류가 발생했습니다."));
            }
        });
    }

    private void handleSetNpc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("§c사용법: /레이드 npc설정 <이름> (Citizens에서 NPC를 먼저 선택하세요)");
            return;
        }
        String name = args[1];
        var selected = net.citizensnpcs.api.CitizensAPI.getDefaultNPCSelector().getSelected(player);
        if (selected == null) {
            sender.sendMessage("§cCitizens에서 NPC를 먼저 선택해주세요 (/npc select).");
            return;
        }
        int npcId = selected.getId();
        executor.execute(() -> {
            try {
                boolean updated = raidManager.setNpcId(name, npcId);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        updated ? "§aNPC를 연결했습니다: " + selected.getName() : "§c존재하지 않는 레이드입니다: " + name));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("NPC 설정 실패: " + e.getMessage());
            }
        });
    }

    private void handleSetMythicMob(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§c사용법: /레이드 몹설정 <이름> <mythicMobId>");
            return;
        }
        String name = args[1];
        String mythicMobId = args[2];
        executor.execute(() -> {
            try {
                boolean updated = raidManager.setMythicMob(name, mythicMobId);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        updated ? "§a몹을 설정했습니다: " + mythicMobId : "§c존재하지 않는 레이드입니다: " + name));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("몹 설정 실패: " + e.getMessage());
            }
        });
    }

    private void handleSetTicket(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage("§c사용법: /레이드 티켓설정 <이름> <아이템즈어더ID> <수량>");
            return;
        }
        String name = args[1];
        String ticketItemId = args[2];
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c수량은 숫자여야 합니다.");
            return;
        }
        int finalAmount = amount;
        executor.execute(() -> {
            try {
                boolean updated = raidManager.setTicket(name, ticketItemId, finalAmount);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        updated ? "§a입장권을 설정했습니다." : "§c존재하지 않는 레이드입니다: " + name));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("티켓 설정 실패: " + e.getMessage());
            }
        });
    }

    private void handleSetPartySize(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage("§c사용법: /레이드 인원설정 <이름> <최소> <최대>");
            return;
        }
        String name = args[1];
        int min, max;
        try {
            min = Integer.parseInt(args[2]);
            max = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c최소/최대는 숫자여야 합니다.");
            return;
        }
        int finalMin = min;
        int finalMax = max;
        executor.execute(() -> {
            try {
                boolean updated = raidManager.setPartySize(name, finalMin, finalMax);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        updated ? "§a인원을 설정했습니다." : "§c존재하지 않는 레이드입니다: " + name));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("인원 설정 실패: " + e.getMessage());
            }
        });
    }

    private void handleSetTimeLimit(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§c사용법: /레이드 제한시간설정 <이름> <초>");
            return;
        }
        String name = args[1];
        int seconds;
        try {
            seconds = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c초는 숫자여야 합니다.");
            return;
        }
        int finalSeconds = seconds;
        executor.execute(() -> {
            try {
                boolean updated = raidManager.setTimeLimit(name, finalSeconds);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        updated ? "§a제한시간을 설정했습니다." : "§c존재하지 않는 레이드입니다: " + name));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("제한시간 설정 실패: " + e.getMessage());
            }
        });
    }

    private void handleSetSharedLives(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§c사용법: /레이드 부활횟수설정 <이름> <횟수>");
            return;
        }
        String name = args[1];
        int lives;
        try {
            lives = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c횟수는 숫자여야 합니다.");
            return;
        }
        int finalLives = lives;
        executor.execute(() -> {
            try {
                boolean updated = raidManager.setSharedLives(name, finalLives);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        updated ? "§a부활 횟수를 설정했습니다." : "§c존재하지 않는 레이드입니다: " + name));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("부활 횟수 설정 실패: " + e.getMessage());
            }
        });
    }

    private void handleSetInstanceCount(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§c사용법: /레이드 인스턴스개수설정 <이름> <개수>");
            return;
        }
        String name = args[1];
        int count;
        try {
            count = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c개수는 숫자여야 합니다.");
            return;
        }
        int finalCount = count;
        executor.execute(() -> {
            try {
                boolean updated = raidManager.setInstanceCount(name, finalCount);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        updated ? "§a인스턴스 개수를 설정했습니다." : "§c먼저 진행 중인 레이드가 모두 끝난 후 다시 시도해주세요."));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("인스턴스 개수 설정 실패: " + e.getMessage());
            }
        });
    }

    private void handleReload(CommandSender sender) {
        executor.execute(() -> {
            try {
                raidManager.loadAll();
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§a레이드 데이터를 새로고침했습니다."));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("레이드 새로고침 실패: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c새로고침 중 오류가 발생했습니다."));
            }
        });
    }

    private void handleSetRewards(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("§c사용법: /레이드 보상설정 <이름>");
            return;
        }
        String name = args[1];
        var raid = raidManager.find(name);
        if (raid.isEmpty()) {
            sender.sendMessage("§c존재하지 않는 레이드입니다: " + name);
            return;
        }
        new ItemGridEditorGui("레이드 보상 - " + name, raid.get().rewardItems(), items -> executor.execute(() -> {
            try {
                raidManager.setRewardItems(name, items);
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("보상 설정 실패: " + e.getMessage());
            }
        })).open(player);
    }

    private void handleSetInstance(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }
        if (args.length != 4) {
            sender.sendMessage("§c사용법: /레이드 인스턴스설정 <이름> <슬롯번호> <입장|보스스폰|퇴장|경계1|경계2>");
            return;
        }
        var raid = raidManager.find(args[1]);
        if (raid.isEmpty()) {
            sender.sendMessage("§c존재하지 않는 레이드입니다: " + args[1]);
            return;
        }
        int slotIndex;
        try {
            slotIndex = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c슬롯 번호는 숫자여야 합니다.");
            return;
        }
        String field = switch (args[3]) {
            case "입장" -> "entry";
            case "보스스폰" -> "boss_spawn";
            case "퇴장" -> "exit";
            case "경계1" -> "bound_min";
            case "경계2" -> "bound_max";
            default -> null;
        };
        if (field == null) {
            sender.sendMessage("§c위치 종류는 입장/보스스폰/퇴장/경계1/경계2 중 하나여야 합니다.");
            return;
        }
        Location location = player.getLocation();
        long raidId = raid.get().id();
        String finalField = field;
        executor.execute(() -> {
            try {
                raidManager.setInstanceSlotLocation(raidId, slotIndex, finalField, location);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§a" + args[3] + " 위치를 저장했습니다."));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("인스턴스 좌표 저장 실패: " + e.getMessage());
            }
        });
    }

    private void handleList(CommandSender sender) {
        if (raidManager.all().isEmpty()) {
            sender.sendMessage("§7등록된 레이드가 없습니다.");
            return;
        }
        sender.sendMessage("§6등록된 레이드 목록:");
        for (RaidDefinition raid : raidManager.all()) {
            sender.sendMessage("§7- " + raid.name());
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§c사용법: /레이드 정보 <이름>");
            return;
        }
        var raid = raidManager.find(args[1]);
        if (raid.isEmpty()) {
            sender.sendMessage("§c존재하지 않는 레이드입니다: " + args[1]);
            return;
        }
        RaidDefinition r = raid.get();
        sender.sendMessage("§6[" + r.name() + "] §7NPC ID: " + (r.npcId() < 0 ? "미설정" : r.npcId())
                + " / 몹: " + r.mythicMobId()
                + " / 티켓: " + r.ticketItemId() + " x" + r.ticketAmount()
                + " / 인원: " + r.minPartySize() + "~" + r.maxPartySize()
                + " / 제한시간: " + r.timeLimitSeconds() + "초"
                + " / 부활: " + r.sharedLives() + "회"
                + " / 인스턴스: " + r.instanceCount() + "개"
                + " / 보상: " + r.rewardItems().size() + "종");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§c사용법: /레이드 삭제 <이름>");
            return;
        }
        String name = args[1];
        executor.execute(() -> {
            try {
                boolean deleted = raidManager.delete(name);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        deleted ? "§a레이드를 삭제했습니다: " + name : "§c존재하지 않는 레이드입니다: " + name));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("레이드 삭제 실패: " + e.getMessage());
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0])) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && !args[0].equals("생성")) {
            for (RaidDefinition raid : raidManager.all()) {
                if (raid.name().startsWith(args[1])) {
                    completions.add(raid.name());
                }
            }
        } else if (args.length == 4 && args[0].equals("인스턴스설정")) {
            for (String field : SLOT_FIELDS) {
                if (field.startsWith(args[3])) {
                    completions.add(field);
                }
            }
        }
        return completions;
    }
}
