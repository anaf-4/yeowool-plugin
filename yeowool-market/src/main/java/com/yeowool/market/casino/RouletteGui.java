package com.yeowool.market.casino;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.market.casino.CasinoRules.RouletteBet;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** 룰렛: numbers 1~36 fill the top four rows, 0 sits at slot 51, outside bets in row five. Clicking a bet spins. */
final class RouletteGui extends BetGui {

    private static final int ZERO_SLOT = 51;
    private static final int OUTSIDE_FIRST = 36;
    private static final int STEPS = 24;

    RouletteGui(CasinoService service, long balance) {
        super(service, "룰렛", balance);
        render();
    }

    private void render() {
        CasinoService.Settings s = service.settings();
        for (int number = 0; number <= 36; number++) {
            int n = number;
            setButton(slotOf(n), GuiButton.of(numberIcon(n, false, s),
                    event -> spin((Player) event.getWhoClicked(), RouletteBet.NUMBER, n)));
        }
        RouletteBet[] outside = {RouletteBet.RED, RouletteBet.BLACK, RouletteBet.ODD, RouletteBet.EVEN, RouletteBet.LOW,
                RouletteBet.HIGH, RouletteBet.DOZEN1, RouletteBet.DOZEN2, RouletteBet.DOZEN3};
        for (int i = 0; i < outside.length; i++) {
            RouletteBet bet = outside[i];
            Material material = switch (bet) {
                case RED -> Material.RED_WOOL;
                case BLACK -> Material.BLACK_WOOL;
                case DOZEN1, DOZEN2, DOZEN3 -> Material.CYAN_WOOL;
                default -> Material.WHITE_WOOL;
            };
            setButton(OUTSIDE_FIRST + i, GuiButton.of(item(material, bet.label(), NamedTextColor.YELLOW,
                    List.of("배당 " + multiplier(multiplierOf(bet, s)), "0이 나오면 짐", "클릭: 이 베팅으로 돌리기")),
                    event -> spin((Player) event.getWhoClicked(), bet, -1)));
        }
        renderBetRow(true);
    }

    private void spin(Player player, RouletteBet pick, int number) {
        CasinoService.Settings s = service.settings();
        double win = multiplierOf(pick, s);
        String label = pick == RouletteBet.NUMBER ? String.valueOf(number) : pick.label();
        service.play(player, CasinoService.Game.ROULETTE, bet, random -> new int[]{random.nextInt(37)},
                draw -> pick.wins(draw[0], number) ? win : 0,
                draw -> describe(draw[0]) + " ← " + label, round -> {
                    render();
                    int result = round.draw()[0];
                    animate(STEPS, 2L, step -> {
                        // the highlight walks the numbers and lands on the result on the last step
                        int shown = Math.floorMod(result - (STEPS - 1 - step), 37);
                        int previous = Math.floorMod(shown - 1, 37);
                        getInventory().setItem(slotOf(previous), numberIcon(previous, false, s));
                        getInventory().setItem(slotOf(shown), numberIcon(shown, true, s));
                    }, () -> finish(player, round, describe(result) + " (베팅: " + label + ")"));
                });
    }

    private static int slotOf(int number) {
        return number == 0 ? ZERO_SLOT : number - 1;
    }

    private static double multiplierOf(RouletteBet bet, CasinoService.Settings s) {
        return bet == RouletteBet.NUMBER ? s.rouletteNumber() : bet.isDozen() ? s.rouletteDozen() : s.rouletteOutside();
    }

    private static String describe(int number) {
        return number == 0 ? "0 (초록)" : number + (CasinoRules.isRed(number) ? " (빨강)" : " (검정)");
    }

    private static ItemStack numberIcon(int number, boolean highlighted, CasinoService.Settings s) {
        Material material = highlighted ? Material.GOLD_BLOCK
                : number == 0 ? Material.LIME_CONCRETE : CasinoRules.isRed(number) ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
        ItemStack icon = item(material, (highlighted ? "▶ " : "") + describe(number),
                number == 0 ? NamedTextColor.GREEN : CasinoRules.isRed(number) ? NamedTextColor.RED : NamedTextColor.DARK_GRAY,
                List.of("배당 " + multiplier(s.rouletteNumber()), "클릭: 이 숫자에 베팅하고 돌리기"));
        icon.setAmount(Math.max(1, number));
        return icon;
    }
}
