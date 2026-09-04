package com.yeowool.admin.coupon;

import com.yeowool.core.api.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@code /쿠폰} — opens a virtual anvil (no real anvil block needed) so
 * players can type a coupon code directly; {@link CouponRedeemListener}'s
 * {@code PrepareAnvilEvent} handler does the actual redemption once typed.
 */
public final class CouponCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final MessageService messages;

    public CouponCommand(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        var view = player.openAnvil(null, true);
        if (view == null || !(view.getTopInventory() instanceof AnvilInventory anvil)) {
            messages.send(player, "coupon.anvil-unavailable");
            return true;
        }
        anvil.setFirstItem(CouponAnvilPlaceholder.create(plugin));
        return true;
    }
}
