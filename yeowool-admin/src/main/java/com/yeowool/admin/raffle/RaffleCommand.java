package com.yeowool.admin.raffle;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/**
 * {@code /추첨 <당첨 인원 수>} — 손에 든 아이템이 상품. 접속 중인 플레이어 중
 * OP를 제외한 전원이 대상 풀(기획서 요청: "OP를 가진 플레이어를 제외"). 아이템
 * 자체는 소모되지 않음 — {@code /쿠폰생성}이 손에 든 아이템을 "등록"만 하고
 * 소모하지 않는 것과 같은 규칙.
 */
public final class RaffleCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final RaffleManager manager;

    public RaffleCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, RaffleManager manager) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length != 1) {
            messages.send(player, "raffle.usage");
            return true;
        }
        int winnerCount;
        try {
            winnerCount = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            messages.send(player, "raffle.usage");
            return true;
        }
        if (winnerCount <= 0) {
            messages.send(player, "raffle.usage");
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            messages.send(player, "raffle.hand-empty");
            return true;
        }

        List<UUID> eligiblePool = Bukkit.getOnlinePlayers().stream()
                .filter(online -> !online.isOp())
                .map(Player::getUniqueId)
                .toList();
        if (eligiblePool.size() < winnerCount) {
            messages.send(player, "raffle.not-enough-players",
                    Placeholder.unparsed("eligible", String.valueOf(eligiblePool.size())),
                    Placeholder.unparsed("requested", String.valueOf(winnerCount)));
            return true;
        }

        ItemStack prizeTemplate = hand.clone();
        prizeTemplate.setAmount(1);
        String itemId = RaffleItemIdentity.identify(hand);
        String displayName = RaffleItemIdentity.displayName(hand);

        messages.send(player, "raffle.started",
                Placeholder.unparsed("item", displayName), Placeholder.unparsed("count", String.valueOf(winnerCount)));
        new RaffleSession(plugin, core, messages, manager, itemId, prizeTemplate, displayName, eligiblePool, winnerCount).start();
        return true;
    }
}
