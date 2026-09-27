package com.yeowool.life.treasure;

import com.yeowool.core.api.gui.ItemGridEditorGui;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/** {@code /보물지도}: held map info for everyone; reward pool editing and grants for staff. */
public final class TreasureCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "yeowool.life.treasure.manage";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final List<String> TIER_LABELS = Arrays.stream(TreasureTier.values()).map(TreasureTier::label).toList();

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final TreasureService service;
    private final Executor executor;

    public TreasureCommand(JavaPlugin plugin, MessageService messages, TreasureService service, Executor executor) {
        this.plugin = plugin;
        this.messages = messages;
        this.service = service;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            info(sender);
            return true;
        }
        if (!sender.hasPermission(ADMIN)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        switch (args[0]) {
            case "보상설정" -> editRewards(sender, args);
            case "지급" -> grant(sender, args);
            default -> messages.send(sender, "treasure.admin-usage");
        }
        return true;
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "treasure.usage");
            return;
        }
        Optional<TreasureMapItem.MapData> held = service.mapItem().read(player.getInventory().getItemInMainHand());
        if (held.isEmpty()) {
            messages.send(player, "treasure.usage");
            if (player.hasPermission(ADMIN)) {
                messages.send(player, "treasure.admin-usage");
            }
            return;
        }
        TreasureMapItem.MapData data = held.get();
        messages.send(player, "treasure.info",
                Placeholder.unparsed("tier", data.tier().label()),
                Placeholder.unparsed("id", String.valueOf(data.id())),
                Placeholder.unparsed("xband", TreasureRules.band(data.x())),
                Placeholder.unparsed("zband", TreasureRules.band(data.z())),
                Placeholder.unparsed("expires", DATE.format(Instant.ofEpochMilli(data.expiresAt()))));
    }

    private void editRewards(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        Optional<TreasureTier> tier = args.length < 2 ? Optional.empty() : TreasureTier.parse(args[1]);
        if (tier.isEmpty()) {
            messages.send(sender, "treasure.unknown-tier");
            return;
        }
        executor.execute(() -> {
            List<ItemStack> existing;
            try {
                existing = service.repository().loadRewards(tier.get());
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "보물 보상 후보 불러오기 실패", e);
                reply(sender, "treasure.error");
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                new ItemGridEditorGui("보물 보상 - " + tier.get().label(), existing, items -> save(player, tier.get(), items)).open(player);
            });
        });
    }

    private void save(Player player, TreasureTier tier, List<ItemStack> items) {
        List<ItemStack> copies = items.stream().map(ItemStack::clone).toList();
        executor.execute(() -> {
            try {
                service.repository().saveRewards(tier, copies);
                reply(player, "treasure.rewards-saved",
                        Placeholder.unparsed("tier", tier.label()),
                        Placeholder.unparsed("count", String.valueOf(copies.size())));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "보물 보상 후보 저장 실패 (" + tier + ")", e);
                reply(player, "treasure.error");
            }
        });
    }

    private void grant(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "treasure.admin-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "treasure.target-offline");
            return;
        }
        Optional<TreasureTier> tier = TreasureTier.parse(args[2]);
        if (tier.isEmpty()) {
            messages.send(sender, "treasure.unknown-tier");
            return;
        }
        service.grant(target, tier.get(), false);
        messages.send(sender, "treasure.granted",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("tier", tier.get().label()));
    }

    private void reply(CommandSender sender, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... placeholders) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, key, placeholders));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("보상설정", "지급").stream().filter(s -> s.startsWith(args[0])).toList();
        }
        if (args.length == 2 && args[0].equals("보상설정")) {
            return TIER_LABELS.stream().filter(s -> s.startsWith(args[1])).toList();
        }
        if (args.length == 2 && args[0].equals("지급")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.startsWith(args[1])).toList();
        }
        if (args.length == 3 && args[0].equals("지급")) {
            return TIER_LABELS.stream().filter(s -> s.startsWith(args[2])).toList();
        }
        return List.of();
    }
}
