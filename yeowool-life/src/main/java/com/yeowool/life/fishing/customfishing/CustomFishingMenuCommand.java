package com.yeowool.life.fishing.customfishing;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.life.fishing.FishAdminGui;
import com.yeowool.life.fishing.FishRarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /커스텀물고기 열기} (yeowool.admin) — CustomFishing에 등록된 물고기와 낚싯대,
 * 미끼를 전부 한 GUI에서 지급받을 수 있게 한다({@link FishAdminGui} 재사용, 클릭하면
 * 즉시 지급). 우리 자체 물고기(config.yml 등급)는 여기 포함하지 않음 — 오직 CustomFishing
 * 쪽 컨텐츠만 대상({@code /물고기지급}이 우리 물고기+CustomFishing 물고기를 섞어 보여주는
 * 것과 역할이 다름).
 *
 * <p>목록은 명령어를 칠 때마다 새로 만든다({@link CustomFishingBridge#buildRarity()} 등을
 * 플러그인 활성화 시점에 한 번만 캐싱해두지 않음) — CustomFishing의 물고기 loot 등록은
 * 그쪽 플러그인 활성화 시점에 항상 끝나있다는 보장이 없어서(실제로 비어있는 채로 캐싱된
 * 적이 있었음), 매번 그 시점의 실제 상태를 그대로 읽는 편이 안전함.
 */
public final class CustomFishingMenuCommand implements CommandExecutor {

    private final MessageService messages;
    private final int backgroundOffsetPx;

    public CustomFishingMenuCommand(MessageService messages, int backgroundOffsetPx) {
        this.messages = messages;
        this.backgroundOffsetPx = backgroundOffsetPx;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("yeowool.admin")) {
            messages.send(player, "general.no-permission");
            return true;
        }
        if (args.length != 1 || !args[0].equals("열기")) {
            player.sendMessage(Component.text("사용법: /커스텀물고기 열기", NamedTextColor.RED));
            return true;
        }
        List<FishRarity> rarities = List.of(
                CustomFishingBridge.buildRarity(), CustomFishingBridge.buildRodRarity(), CustomFishingBridge.buildBaitRarity());
        new FishAdminGui(player, rarities, 0, backgroundOffsetPx).open(player);
        return true;
    }
}
