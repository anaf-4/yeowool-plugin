package com.yeowool.life.fishing.customfishing;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;
import net.momirealms.customfishing.api.mechanic.MechanicType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * {@code /미끼 빼기} — CustomFishing은 미끼를 어딘가에 "장착"해두는 별도 상태를 갖지 않고,
 * 낚싯대를 든 손의 반대쪽 손(보조 손)에 들려 있는 미끼 아이템을 캐스팅 순간마다 그때그때
 * 확인해서 쓴다({@code FishingGears.defaultFishingGearsConsumers} 참고). 그래서 "장착된
 * 미끼"란 실질적으로 보조 손에 들고 있는 미끼를 뜻하고, 빼는 것도 그 아이템을 보조 손에서
 * 인벤토리로 되돌리는 것으로 충분함.
 */
public final class BaitUnequipCommand implements CommandExecutor {

    private final MessageService messages;

    public BaitUnequipCommand(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length != 1 || !args[0].equals("빼기")) {
            player.sendMessage(Component.text("사용법: /미끼 빼기", NamedTextColor.RED));
            return true;
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        String itemId = BukkitCustomFishingPlugin.getInstance().getItemManager().getItemID(offHand);
        List<MechanicType> types = MechanicType.getTypeByID(itemId);
        if (types == null || !types.contains(MechanicType.BAIT)) {
            player.sendMessage(Component.text("현재 장착된 미끼가 없습니다.", NamedTextColor.RED));
            return true;
        }

        player.getInventory().setItemInOffHand(null);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(offHand);
        leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(Component.text("미끼를 뺐습니다.", NamedTextColor.GREEN));
        return true;
    }
}
