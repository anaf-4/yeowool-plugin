package com.yeowool.enhance;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * {@code /강화설정} — with no argument (or a bare level number), opens
 * {@link EnhanceSettingsGui} / {@link EnhanceSettingsDetailGui} so staff can
 * tune the per-level enhance cost one +N강 at a time without touching
 * config.yml. The old text form ({@code /강화설정 <레벨> 온 <금액>} /
 * {@code /강화설정 <레벨> 재료 <개수>}, "재료" taking whatever's in the admin's
 * main hand) still works too — useful from console/RCON, where a GUI can't
 * open.
 */
public final class EnhanceSettingsCommand implements CommandExecutor, TabCompleter {

    private final EnhanceCostManager costs;
    private final EnhanceConfig config;
    private final MessageService messages;
    private final EnhanceService service;
    private final EnhanceSettingsAnvilListener anvil;

    public EnhanceSettingsCommand(EnhanceCostManager costs, EnhanceConfig config, MessageService messages,
                                   EnhanceService service, EnhanceSettingsAnvilListener anvil) {
        this.costs = costs;
        this.config = config;
        this.messages = messages;
        this.service = service;
        this.anvil = anvil;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "general.player-only");
                return true;
            }
            new EnhanceSettingsGui(service, costs, anvil, 0).open(player);
            return true;
        }
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "general.player-only");
                return true;
            }
            Integer level = tryParseLevel(args[0]);
            if (level == null) {
                messages.send(sender, "enhance.settings-usage");
                return true;
            }
            new EnhanceSettingsDetailGui(service, costs, anvil, level).open(player);
            return true;
        }
        int level;
        try {
            level = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            messages.send(sender, "enhance.settings-usage");
            return true;
        }
        if (level < 0 || level >= config.maxLevel()) {
            messages.send(sender, "enhance.settings-invalid-level", Placeholder.unparsed("max", String.valueOf(config.maxLevel() - 1)));
            return true;
        }

        switch (args[1]) {
            case "온" -> setCurrency(sender, level, args);
            case "재료" -> setMaterial(sender, level, args);
            case "정보" -> info(sender, level);
            default -> messages.send(sender, "enhance.settings-usage");
        }
        return true;
    }

    private Integer tryParseLevel(String text) {
        try {
            int level = Integer.parseInt(text);
            return level >= 0 && level < config.maxLevel() ? level : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void setCurrency(CommandSender sender, int level, String[] args) {
        if (args.length != 3) {
            messages.send(sender, "enhance.settings-usage");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            messages.send(sender, "enhance.settings-usage");
            return;
        }
        if (amount < 0) {
            messages.send(sender, "enhance.settings-usage");
            return;
        }
        costs.setCurrency(level, amount);
        messages.send(sender, "enhance.settings-currency-set",
                Placeholder.unparsed("level", String.valueOf(level)), Placeholder.unparsed("amount", String.format("%,d", amount)));
    }

    private void setMaterial(CommandSender sender, int level, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        if (args.length != 3) {
            messages.send(sender, "enhance.settings-usage");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            messages.send(sender, "enhance.settings-usage");
            return;
        }
        if (amount <= 0) {
            messages.send(sender, "enhance.settings-usage");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            messages.send(sender, "enhance.hand-empty");
            return;
        }
        String materialId = resolveId(hand);
        costs.setMaterial(level, materialId, amount);
        messages.send(sender, "enhance.settings-material-set",
                Placeholder.unparsed("level", String.valueOf(level)),
                Placeholder.unparsed("material", EnhanceMaterialResolver.displayName(materialId)),
                Placeholder.unparsed("amount", String.valueOf(amount)));
    }

    private String resolveId(ItemStack hand) {
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.byItemStack(hand);
            if (custom != null) {
                return custom.getNamespacedID();
            }
        }
        return hand.getType().name();
    }

    private void info(CommandSender sender, int level) {
        var cost = costs.costFor(level);
        messages.send(sender, "enhance.settings-info",
                Placeholder.unparsed("level", String.valueOf(level)),
                Placeholder.unparsed("amount", String.format("%,d", cost.currency())),
                Placeholder.unparsed("material", EnhanceMaterialResolver.displayName(cost.materialId())),
                Placeholder.unparsed("material-amount", String.valueOf(cost.materialAmount())));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2) {
            return TabCompletions.filter(List.of("온", "재료", "정보"), args[1]);
        }
        return List.of();
    }
}
