package com.yeowool.market.casino;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.market.casino.CasinoRules.DiceBet;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 주사위 하이/로우: two dice in the middle, 로우 / 7 / 하이 buttons below. */
final class DiceGui extends BetGui {

    private static final int[] DICE = {21, 23};
    private static final int[] BUTTONS = {38, 40, 42};
    private static final int ROLL_FRAMES = 10;

    DiceGui(CasinoService service, long balance) {
        super(service, "주사위", balance);
        CasinoService.Settings s = service.settings();
        DiceBet[] bets = DiceBet.values();
        for (int i = 0; i < bets.length; i++) {
            DiceBet pick = bets[i];
            Material material = pick == DiceBet.SEVEN ? Material.GOLD_BLOCK : pick == DiceBet.LOW ? Material.BLUE_WOOL : Material.RED_WOOL;
            setButton(BUTTONS[i], GuiButton.of(item(material, pick.label(), NamedTextColor.YELLOW,
                    List.of("두 주사위 합 기준, 배당 " + multiplier(multiplierOf(pick, s)),
                            pick == DiceBet.SEVEN ? "합이 정확히 7" : "7이 나오면 짐", "클릭: 이 베팅으로 굴리기")),
                    event -> roll((Player) event.getWhoClicked(), pick)));
        }
        showDice(1, 1);
        renderBetRow(true);
    }

    private void roll(Player player, DiceBet pick) {
        double win = multiplierOf(pick, service.settings());
        service.play(player, CasinoService.Game.DICE, bet,
                random -> new int[]{random.nextInt(6) + 1, random.nextInt(6) + 1},
                draw -> pick.wins(draw[0] + draw[1]) ? win : 0,
                draw -> draw[0] + "+" + draw[1] + "=" + (draw[0] + draw[1]) + " ← " + pick.label(),
                round -> animate(ROLL_FRAMES + 1, 2L, frame -> {
                    if (frame < ROLL_FRAMES) {
                        showDice(ThreadLocalRandom.current().nextInt(6) + 1, ThreadLocalRandom.current().nextInt(6) + 1);
                    } else {
                        showDice(round.draw()[0], round.draw()[1]);
                    }
                }, () -> finish(player, round, round.draw()[0] + " + " + round.draw()[1] + " = "
                        + (round.draw()[0] + round.draw()[1]) + " (베팅: " + pick.label() + ")")));
    }

    private void showDice(int first, int second) {
        int[] values = {first, second};
        for (int i = 0; i < DICE.length; i++) {
            ItemStack die = item(Material.QUARTZ_BLOCK, "주사위 " + values[i], NamedTextColor.WHITE, List.of());
            die.setAmount(values[i]);
            getInventory().setItem(DICE[i], die);
        }
    }

    private static double multiplierOf(DiceBet pick, CasinoService.Settings s) {
        return pick == DiceBet.SEVEN ? s.diceSeven() : s.diceHighLow();
    }
}
