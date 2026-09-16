package com.yeowool.admin.cashpackage;

import com.yeowool.core.api.gui.ItemGridEditorGui;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** {@code /패키지관리 목록}, {@code /패키지관리 <이름> 정보|삭제|수정}. */
public final class CashPackageManageCommand implements CommandExecutor, TabCompleter {

    private final MessageService messages;
    private final CashPackageManager packageManager;

    public CashPackageManageCommand(MessageService messages, CashPackageManager packageManager) {
        this.messages = messages;
        this.packageManager = packageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equals("목록")) {
            list(sender);
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "cashpackage.manage-usage");
            return true;
        }
        String name = args[0];
        switch (args[1]) {
            case "정보" -> info(sender, name);
            case "삭제" -> remove(sender, name);
            case "수정" -> editItems(sender, name);
            default -> messages.send(sender, "cashpackage.manage-sub-usage");
        }
        return true;
    }

    private void list(CommandSender sender) {
        var packages = packageManager.all();
        if (packages.isEmpty()) {
            messages.send(sender, "cashpackage.manage-list-empty");
            return;
        }
        messages.send(sender, "cashpackage.manage-list-header", Placeholder.unparsed("count", String.valueOf(packages.size())));
        for (var pkg : packages) {
            messages.send(sender, "cashpackage.manage-list-line",
                    Placeholder.unparsed("name", pkg.name()),
                    Placeholder.unparsed("item", describeItems(pkg.items())));
        }
    }

    private void info(CommandSender sender, String name) {
        var pkg = packageManager.find(name);
        if (pkg.isEmpty()) {
            messages.send(sender, "cashpackage.not-found", Placeholder.unparsed("name", name));
            return;
        }
        messages.send(sender, "cashpackage.manage-info",
                Placeholder.unparsed("name", pkg.get().name()),
                Placeholder.unparsed("item", describeItems(pkg.get().items())));
    }

    private void remove(CommandSender sender, String name) {
        if (!packageManager.delete(name)) {
            messages.send(sender, "cashpackage.not-found", Placeholder.unparsed("name", name));
            return;
        }
        messages.send(sender, "cashpackage.manage-remove-success", Placeholder.unparsed("name", name));
    }

    private void editItems(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "cashpackage.manage-edit-player-only");
            return;
        }
        var pkg = packageManager.find(name);
        if (pkg.isEmpty()) {
            messages.send(sender, "cashpackage.not-found", Placeholder.unparsed("name", name));
            return;
        }
        new ItemGridEditorGui("패키지 구성 설정 (닫으면 저장됩니다)", pkg.get().items(), newItems -> {
            if (newItems.isEmpty()) {
                messages.send(player, "cashpackage.manage-edit-no-item");
                return;
            }
            packageManager.updateItems(name, newItems);
            messages.send(player, "cashpackage.manage-edit-success",
                    Placeholder.unparsed("name", name),
                    Placeholder.unparsed("item", describeItems(newItems)));
        }).open(player);
    }

    private static String describeItems(List<ItemStack> items) {
        if (items.size() == 1) {
            var item = items.get(0);
            return item.getType() + " x" + item.getAmount();
        }
        return items.size() + "종";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> names = packageManager.all().stream().map(CashPackage::name).toList();
        if (args.length == 1) {
            List<String> options = new ArrayList<>(names);
            options.add("목록");
            return TabCompletions.filter(options, args[0]);
        }
        if (args.length == 2 && !args[0].equals("목록")) {
            return TabCompletions.filter(List.of("정보", "삭제", "수정"), args[1]);
        }
        return List.of();
    }
}
