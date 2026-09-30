package com.yeowool.life.metals;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.life.metals.MetalConfig.Metal;
import com.yeowool.life.orders.OrderNpcListener;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** {@code /대장간관리 npc|열기|지급 <닉네임> <금속id> <원석|주괴> [개수]|리로드} — staff only. */
final class SmithyCommand implements CommandExecutor, TabCompleter {

    static final String PERMISSION = "yeowool.life.smithy.manage";
    private static final List<String> SUBCOMMANDS = List.of("npc", "열기", "지급", "리로드");
    private static final List<String> FORMS = List.of("원석", "주괴");

    private final MetalService service;
    private final MessageService messages;

    SmithyCommand(MetalService service, MessageService messages) {
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
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
            case "지급" -> give(sender, args);
            case "리로드" -> {
                service.reload();
                messages.send(sender, "metals.admin.reloaded", Placeholder.unparsed("count", String.valueOf(service.metalCount())));
            }
            default -> messages.send(sender, "metals.admin.usage");
        }
        return true;
    }

    private void bindNpc(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            messages.send(player, "metals.admin.no-citizens");
            return;
        }
        Integer npcId = OrderNpcListener.selectedNpcId(player);
        if (npcId == null) {
            messages.send(player, "metals.admin.select-npc");
            return;
        }
        messages.send(player, service.toggleNpc(npcId) ? "metals.admin.npc-bound" : "metals.admin.npc-unbound",
                Placeholder.unparsed("id", String.valueOf(npcId)));
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages.send(sender, "metals.admin.usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "metals.admin.player-offline", Placeholder.unparsed("player", args[1]));
            return;
        }
        Metal metal = service.config().metals().get(args[2]);
        int formIndex = FORMS.indexOf(args[3]);
        if (metal == null || formIndex < 0) {
            messages.send(sender, "metals.admin.unknown-item", Placeholder.unparsed("id", args[2]), Placeholder.unparsed("form", args[3]));
            return;
        }
        int amount;
        try {
            amount = args.length >= 5 ? Integer.parseInt(args[4]) : 1;
        } catch (NumberFormatException e) {
            amount = 0;
        }
        if (amount < 1 || amount > 2304) {
            messages.send(sender, "metals.admin.bad-amount");
            return;
        }
        boolean raw = formIndex == 0;
        ItemStack stack = MetalItems.create(metal.id(), raw ? MetalConfig.RAW : MetalConfig.INGOT, amount);
        if (stack == null) {
            messages.send(sender, "metals.pack-missing");
            return;
        }
        MetalItems.give(target, stack);
        messages.send(sender, "metals.admin.given", Placeholder.unparsed("player", target.getName()),
                Placeholder.component("item", service.name(metal, raw ? "metals.form.raw" : "metals.form.ingot")),
                Placeholder.unparsed("amount", String.valueOf(amount)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0])).toList();
        }
        if (!args[0].equals("지급")) {
            return List.of();
        }
        return switch (args.length) {
            case 2 -> null; // online player names
            case 3 -> service.config().metals().keySet().stream().filter(id -> id.startsWith(args[2])).toList();
            case 4 -> FORMS;
            default -> List.of();
        };
    }
}
