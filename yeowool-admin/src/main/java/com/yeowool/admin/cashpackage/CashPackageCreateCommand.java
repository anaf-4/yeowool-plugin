package com.yeowool.admin.cashpackage;

import com.yeowool.core.api.gui.ItemGridEditorGui;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * {@code /패키지생성 <이름>} — opens a 54-slot {@link ItemGridEditorGui}; whatever
 * is in it when the admin closes it becomes the package's contents. Deliver it
 * later with {@code /패키지지급 <닉네임> <이름>} — the same "console-triggerable
 * command as a Tebex delivery hook" role {@code /캐시지급} plays for currency-only
 * packages, just for a whole item bundle (e.g. a weapon+armor+tool set) instead.
 */
public final class CashPackageCreateCommand implements CommandExecutor {

    private final MessageService messages;
    private final CashPackageManager packageManager;

    public CashPackageCreateCommand(MessageService messages, CashPackageManager packageManager) {
        this.messages = messages;
        this.packageManager = packageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "cashpackage.create-player-only");
            return true;
        }
        if (args.length != 1) {
            messages.send(sender, "cashpackage.create-usage");
            return true;
        }
        String name = args[0];
        if (packageManager.find(name).isPresent()) {
            messages.send(sender, "cashpackage.already-exists", Placeholder.unparsed("name", name));
            return true;
        }

        new ItemGridEditorGui("패키지 구성 설정 (닫으면 저장됩니다)", List.of(), items -> {
            if (items.isEmpty()) {
                messages.send(player, "cashpackage.create-no-item");
                return;
            }
            var result = packageManager.create(name, items);
            if (result == CashPackageManager.CreateResult.ALREADY_EXISTS) {
                giveBack(player, items);
                messages.send(player, "cashpackage.already-exists", Placeholder.unparsed("name", name));
                return;
            }
            messages.send(player, "cashpackage.create-success",
                    Placeholder.unparsed("name", name),
                    Placeholder.unparsed("count", String.valueOf(items.size())));
        }).open(player);
        return true;
    }

    private static void giveBack(Player player, List<ItemStack> items) {
        var leftover = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
    }
}
