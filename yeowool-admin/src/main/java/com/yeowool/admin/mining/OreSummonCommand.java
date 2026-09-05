package com.yeowool.admin.mining;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /광석소환 <종류> [개수]} — OP가 W6 Custom Mining &amp; Ores 팩의 위장
 * 채굴 몹을 자기 위치에 바로 소환. 랜덤 스폰으로 자연 발생하는 것과 완전히
 * 같은 MythicMobs 몹이라 캐면 똑같이 광석이 드롭됨 — {@code /mm mobs spawn}을
 * 그대로 위임 실행할 뿐, 별도 드랍 로직은 없음(타운/야생에만 해당 몹이
 * 등록되어 있으므로 로비에서는 자동으로 동작하지 않음).
 */
public final class OreSummonCommand implements CommandExecutor, TabCompleter {

    private static final Map<String, String> ORE_TYPES = new LinkedHashMap<>();

    static {
        ORE_TYPES.put("석탄", "coal_ore");
        ORE_TYPES.put("구리", "copper_ore");
        ORE_TYPES.put("철", "iron_ore");
        ORE_TYPES.put("금", "gold_ore");
        ORE_TYPES.put("다이아", "diamond_ore");
        ORE_TYPES.put("에메랄드", "emerald_ore");
        ORE_TYPES.put("청금석", "lapis_ore");
        ORE_TYPES.put("레드스톤", "redstone_ore");
        ORE_TYPES.put("자수정", "amethyst_ore");
        ORE_TYPES.put("쿼츠", "quartz_ore");
        ORE_TYPES.put("네더라이트", "netherite_ore");
    }

    private static final int MAX_AMOUNT = 10;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용할 수 있습니다.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text(
                    "사용법: /광석소환 <" + String.join("/", ORE_TYPES.keySet()) + "> [개수, 최대 " + MAX_AMOUNT + "]",
                    NamedTextColor.YELLOW));
            return true;
        }

        String mobId = ORE_TYPES.get(args[0]);
        if (mobId == null) {
            player.sendMessage(Component.text("알 수 없는 광물 종류입니다: " + args[0], NamedTextColor.RED));
            return true;
        }

        int amount = 1;
        if (args.length > 1) {
            try {
                amount = Math.max(1, Math.min(MAX_AMOUNT, Integer.parseInt(args[1])));
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("개수는 숫자로 입력해주세요.", NamedTextColor.RED));
                return true;
            }
        }

        Bukkit.dispatchCommand(player, "mm mobs spawn " + mobId + " " + amount);
        player.sendMessage(Component.text(args[0] + " 광석 " + amount + "개를 소환했습니다.", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return ORE_TYPES.keySet().stream().filter(k -> k.startsWith(args[0])).toList();
        }
        if (args.length == 2) {
            return List.of("1", "3", "5");
        }
        return List.of();
    }
}
