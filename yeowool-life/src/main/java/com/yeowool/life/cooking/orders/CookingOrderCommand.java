package com.yeowool.life.cooking.orders;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.life.cooking.addcook.AddCookRecipeIndex.RecipeEntry;
import com.yeowool.life.cooking.orders.CookingOrderRules.Difficulty;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/** {@code /요리주문관리 npc|열기|단체시작|단체종료|초기화|명성} — staff only (spec §8). */
public final class CookingOrderCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "yeowool.life.cooking.manage";
    private static final List<String> SUBCOMMANDS = List.of("npc", "열기", "단체시작", "단체종료", "초기화", "명성");

    @FunctionalInterface
    private interface SqlTask {
        void run() throws SQLException;
    }

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final CookingOrderService service;
    private final Executor executor;
    private final Random random = new Random();

    public CookingOrderCommand(JavaPlugin plugin, MessageService messages, CookingOrderService service, Executor executor) {
        this.plugin = plugin;
        this.messages = messages;
        this.service = service;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        switch (args.length == 0 ? "" : args[0]) {
            case "npc", "NPC" -> bindNpc(sender);
            case "열기" -> {
                if (sender instanceof Player player) {
                    service.open(player);
                } else {
                    messages.send(sender, "general.player-only");
                }
            }
            case "단체시작" -> startGroup(sender, args);
            case "단체종료" -> async(sender, () -> service.reply(sender,
                    service.endGroup() ? "cooking-orders.admin.group-ended" : "cooking-orders.admin.group-none"));
            case "초기화" -> {
                OfflinePlayer target = target(sender, args);
                if (target != null) {
                    UUID uuid = target.getUniqueId();
                    async(sender, () -> {
                        service.repository().resetDay(uuid, CookingOrderService.today());
                        service.reply(sender, "cooking-orders.admin.reset", Placeholder.unparsed("player", String.valueOf(target.getName())));
                    });
                }
            }
            case "명성" -> {
                OfflinePlayer target = target(sender, args);
                Integer fame = target == null ? null : number(sender, args, 2);
                if (fame != null) {
                    UUID uuid = target.getUniqueId();
                    int value = Math.max(0, fame);
                    async(sender, () -> {
                        service.repository().setFame(uuid, value);
                        service.reply(sender, "cooking-orders.admin.fame-set", Placeholder.unparsed("player", String.valueOf(target.getName())),
                                Placeholder.unparsed("fame", String.valueOf(value)));
                    });
                }
            }
            default -> messages.send(sender, "cooking-orders.admin.usage");
        }
        return true;
    }

    /** {@code 단체시작 [레시피ID] [수량] [시간]} — omitted parts: a random 쉬움/보통 recipe, its default target, the configured duration. */
    private void startGroup(CommandSender sender, String[] args) {
        CookingOrderRules rules = service.rules();
        String recipeId;
        Difficulty difficulty;
        if (args.length >= 2) {
            RecipeEntry recipe = service.recipe(args[1]);
            if (recipe == null) {
                messages.send(sender, "cooking-orders.admin.unknown-recipe", Placeholder.unparsed("id", args[1]));
                return;
            }
            recipeId = recipe.id();
            difficulty = service.groupDifficulty(recipeId).orElse(Difficulty.EASY);
        } else {
            recipeId = rules.pickGroupRecipe(random, service.allRecipes()).map(CookingOrderRules.Recipe::id).orElse(null);
            if (recipeId == null) {
                messages.send(sender, "cooking-orders.admin.no-group-recipe");
                return;
            }
            difficulty = service.groupDifficulty(recipeId).orElse(Difficulty.EASY);
        }
        Integer target = args.length >= 3 ? number(sender, args, 2) : Integer.valueOf(rules.groupTarget(difficulty));
        Integer hours = args.length >= 4 ? number(sender, args, 3) : Integer.valueOf(rules.settings().group().durationHours());
        if (target == null || hours == null) {
            return;
        }
        if (target < 1 || hours < 1) {
            messages.send(sender, "cooking-orders.admin.bad-number");
            return;
        }
        long now = System.currentTimeMillis();
        async(sender, () -> service.reply(sender,
                service.startGroup(recipeId, target, now, now + hours * 3_600_000L)
                        ? "cooking-orders.admin.group-started" : "cooking-orders.admin.group-already"));
    }

    private void bindNpc(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            messages.send(player, "cooking-orders.admin.no-citizens");
            return;
        }
        Integer npcId = CookingNpcListener.selectedNpcId(player);
        if (npcId == null) {
            messages.send(player, "cooking-orders.admin.select-npc");
            return;
        }
        boolean bound = service.toggleNpc(npcId);
        messages.send(player, bound ? "cooking-orders.admin.npc-bound" : "cooking-orders.admin.npc-unbound",
                Placeholder.unparsed("id", String.valueOf(npcId)));
    }

    private OfflinePlayer target(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "cooking-orders.admin.usage");
            return null;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(args[1]);
        }
        if (target == null) {
            messages.send(sender, "cooking-orders.admin.player-not-found", Placeholder.unparsed("player", args[1]));
        }
        return target;
    }

    private Integer number(CommandSender sender, String[] args, int index) {
        try {
            return Integer.parseInt(args[index]);
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            messages.send(sender, "cooking-orders.admin.bad-number");
            return null;
        }
    }

    private void async(CommandSender sender, SqlTask task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "요리 주문 관리 명령 처리 실패", e);
                service.reply(sender, "cooking-orders.error");
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            return List.of();
        }
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0])).toList();
        }
        if (args.length == 2 && (args[0].equals("초기화") || args[0].equals("명성"))) {
            return null; // online player names
        }
        return List.of();
    }
}
