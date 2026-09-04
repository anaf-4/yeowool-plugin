package com.yeowool.admin.command;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * {@code /캐시지급 <닉네임> <금액>} — 관리자가 캐시(프리미엄 화폐)를 즉시 지급/차감하는
 * 명령어. 콘솔에서도 실행 가능해서, 추후 결제 대행사 웹훅이 서버 콘솔로 명령을
 * 흘려보내는 방식(Tebex류)의 캐시 충전 연동 시 이 명령어를 그대로 훅으로 쓸 수 있다.
 * {@link AdminCommand#grant}(온 지급)와 같은 패턴이지만 캐시는 잔액이 음수가 될 수
 * 있는 차감 요청을 실패로 보고해야 하므로 {@code modifyCashBalance}의 반환값을 확인한다.
 */
public final class CashGrantCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;

    public CashGrantCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 2) {
            messages.send(sender, "cash.usage");
            return true;
        }
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            messages.send(sender, "general.invalid-amount");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId() == null) {
            messages.send(sender, "general.player-not-found");
            return true;
        }
        String targetName = args[0];
        core.playerData().load(target.getUniqueId(), targetName).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            boolean success = core.economyData().modifyCashBalance(target.getUniqueId(), amount, "YeowoolAdmin",
                    "관리자 " + (amount >= 0 ? "지급" : "차감") + " (" + sender.getName() + ")");
            if (!success) {
                messages.send(sender, "cash.insufficient", Placeholder.unparsed("target", targetName));
                return;
            }
            messages.send(sender, "cash.success",
                    Placeholder.unparsed("target", targetName),
                    Placeholder.unparsed("amount", String.valueOf(amount)),
                    Placeholder.unparsed("action", amount >= 0 ? "지급" : "차감"));

            // 콘솔(웹사이트 RCON 포함)에서 지급하든 관리자가 직접 명령어를 치든, 받는
            // 쪽에는 항상 "관리진"이라는 일반화된 표현으로 안내한다 - 구체적으로 누가/
            // 어디서 지급했는지는 받는 사람이 알 필요 없음.
            Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
            if (onlineTarget != null) {
                messages.send(onlineTarget, amount >= 0 ? "cash.received" : "cash.deducted",
                        Placeholder.unparsed("amount", String.format("%,d", Math.abs(amount))));
            }
        }));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filteredOnlinePlayerNames(args[0]);
        }
        return List.of();
    }
}
