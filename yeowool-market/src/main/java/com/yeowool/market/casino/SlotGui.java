package com.yeowool.market.casino;

import com.yeowool.core.api.gui.GuiButton;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 슬롯머신: three reels in the middle row, lever below. */
final class SlotGui extends BetGui {

    private static final int[] REELS = {20, 22, 24};
    private static final int LEVER_SLOT = 40;
    private static final int[] STOP_FRAME = {8, 12, 16};

    private final CasinoRules.Slot slot;

    SlotGui(CasinoService service, long balance) {
        super(service, "슬롯머신", balance);
        this.slot = service.settings().slot();
        ItemStack frame = item(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY, List.of());
        for (int i = 9; i < 36; i++) {
            getInventory().setItem(i, frame);
        }
        List<String> table = new ArrayList<>();
        for (CasinoRules.Symbol symbol : slot.symbols()) {
            table.add(symbol.name() + " 3개: " + multiplier(symbol.payout()));
        }
        table.add(slot.symbols().get(0).name() + " 2개: " + multiplier(slot.twoFirst()));
        setButton(4, GuiButton.display(item(Material.BOOK, "배당표", NamedTextColor.YELLOW, table)));
        for (int i = 0; i < REELS.length; i++) {
            showSymbol(i, slot.symbols().size() - 1);
        }
        setButton(LEVER_SLOT, GuiButton.of(item(Material.LEVER, "돌리기", NamedTextColor.GREEN, List.of("선택한 베팅 금액으로 돌립니다")),
                event -> spin((Player) event.getWhoClicked())));
        renderBetRow(true);
    }

    private void spin(Player player) {
        service.play(player, CasinoService.Game.SLOT, bet, slot::spin, slot::multiplier, this::describe, round ->
                animate(STOP_FRAME[2] + 1, 2L, frame -> {
                    for (int i = 0; i < REELS.length; i++) {
                        showSymbol(i, frame >= STOP_FRAME[i] ? round.draw()[i]
                                : ThreadLocalRandom.current().nextInt(slot.symbols().size()));
                    }
                }, () -> finish(player, round, describe(round.draw()))));
    }

    private void showSymbol(int reel, int index) {
        CasinoRules.Symbol symbol = slot.symbols().get(index);
        getInventory().setItem(REELS[reel], item(material(symbol.material(), Material.PAPER), symbol.name(), NamedTextColor.WHITE, List.of()));
    }

    private String describe(int[] reels) {
        return slot.symbols().get(reels[0]).name() + " | " + slot.symbols().get(reels[1]).name() + " | " + slot.symbols().get(reels[2]).name();
    }
}
