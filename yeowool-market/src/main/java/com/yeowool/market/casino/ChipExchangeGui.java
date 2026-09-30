package com.yeowool.market.casino;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

import static com.yeowool.market.casino.BetGui.item;
import static com.yeowool.market.casino.CasinoService.fmt;

/** 칩 환전: buy with 온 on the left, sell for 온 on the right, balance in the middle. Never 캐시. */
final class ChipExchangeGui extends YeowoolGui {

    private static final long[] AMOUNTS = {1, 10, 100};
    private static final Material[] BUY_ICONS = {Material.GOLD_NUGGET, Material.GOLD_INGOT, Material.GOLD_BLOCK};
    private static final Material[] SELL_ICONS = {Material.EMERALD, Material.EMERALD_BLOCK, Material.EMERALD_BLOCK};

    ChipExchangeGui(CasinoService service, long chips, long wallet, long remainingBuy) {
        super(27, Component.text("카지노 칩 환전", NamedTextColor.DARK_GREEN));
        CasinoService.Settings s = service.settings();
        long price = s.chipPrice();
        for (int i = 0; i < AMOUNTS.length; i++) {
            long amount = AMOUNTS[i];
            setButton(9 + i, GuiButton.of(item(BUY_ICONS[i], "칩 " + amount + "개 구매", NamedTextColor.YELLOW,
                    List.of("가격: " + fmt(amount * price) + "온")), event -> service.buy((Player) event.getWhoClicked(), amount)));
            setButton(14 + i, GuiButton.of(item(SELL_ICONS[i], "칩 " + amount + "개 판매", NamedTextColor.GREEN,
                    List.of("받는 온: " + fmt(amount * price) + "온")), event -> service.sell((Player) event.getWhoClicked(), amount)));
        }
        setButton(12, GuiButton.of(item(Material.OAK_SIGN, "구매 수량 직접 입력", NamedTextColor.YELLOW, List.of("채팅으로 개수 입력")),
                event -> service.askAmount((Player) event.getWhoClicked(), true)));
        setButton(17, GuiButton.of(item(Material.OAK_SIGN, "판매 수량 직접 입력", NamedTextColor.GREEN, List.of("채팅으로 개수 입력")),
                event -> service.askAmount((Player) event.getWhoClicked(), false)));
        setButton(13, GuiButton.display(item(Material.SUNFLOWER, "보유 칩: " + fmt(chips) + "개", NamedTextColor.GOLD, List.of(
                "1칩 = " + fmt(price) + "온",
                "지갑: " + fmt(wallet) + "온",
                "오늘 더 살 수 있는 칩: " + fmt(remainingBuy) + " / " + fmt(s.dailyBuyLimit()) + "개",
                "칩은 온으로만 사고, 온으로만 팔 수 있습니다",
                "게임: 카지노의 슬롯머신·룰렛·주사위·블랙잭 테이블 우클릭"))));
    }
}
