package com.yeowool.market.casino;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.function.IntConsumer;

/** Shared 54-slot game window: bet buttons in the bottom row, chip balance in the corner, a few-tick animation helper. */
abstract class BetGui extends YeowoolGui {

    private static final long[] BETS = {1, 5, 10, 50, 100};
    private static final int FIRST_BET_SLOT = 45;
    private static final int MAX_BET_SLOT = 50;
    static final int BALANCE_SLOT = 53;

    protected final CasinoService service;
    protected long bet;
    protected long balance;

    BetGui(CasinoService service, String title, long balance) {
        super(54, Component.text(title, NamedTextColor.DARK_GREEN));
        this.service = service;
        this.balance = balance;
        this.bet = Math.min(BETS[0], service.settings().maxBet());
    }

    /** Bet row + balance. {@code betsEnabled} false greys the row out (e.g. during a blackjack hand). */
    protected void renderBetRow(boolean betsEnabled) {
        long max = service.settings().maxBet();
        boolean maxListed = false;
        for (int i = 0; i < BETS.length; i++) {
            long amount = BETS[i];
            maxListed |= amount == max;
            if (amount > max) {
                clearButton(FIRST_BET_SLOT + i);
            } else {
                setBetButton(FIRST_BET_SLOT + i, amount, betsEnabled);
            }
        }
        if (maxListed) {
            clearButton(MAX_BET_SLOT);
        } else {
            setBetButton(MAX_BET_SLOT, max, betsEnabled);
        }
        boolean exchange = service.settings().exchangeAnywhere();
        ItemStack chips = item(Material.SUNFLOWER, "보유 칩: " + CasinoService.fmt(balance) + "개", NamedTextColor.GOLD,
                exchange ? List.of("클릭: 칩 환전 (/카지노)") : List.of("환전은 카지노 환전 NPC에서"));
        setButton(BALANCE_SLOT, GuiButton.of(chips, event -> {
            if (exchange) {
                service.openExchange((Player) event.getWhoClicked());
            }
        }));
    }

    private void setBetButton(int slot, long amount, boolean enabled) {
        boolean selected = amount == bet;
        ItemStack icon = item(!enabled ? Material.GRAY_DYE : selected ? Material.GOLD_BLOCK : Material.GOLD_NUGGET,
                (selected ? "▶ " : "") + "베팅 " + CasinoService.fmt(amount) + "칩",
                selected ? NamedTextColor.YELLOW : NamedTextColor.GRAY, List.of(selected ? "선택됨" : "클릭: 베팅 금액 선택"));
        icon.setAmount((int) Math.min(64, amount));
        setButton(slot, GuiButton.of(icon, event -> {
            if (enabled) {
                bet = amount;
                renderBetRow(true);
            }
        }));
    }

    /** Runs {@code frame} for 0..frames-1 every {@code period} ticks, then {@code done} — even if the window was closed. */
    protected void animate(int frames, long period, IntConsumer frame, Runnable done) {
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick < frames) {
                    frame.accept(tick++);
                    return;
                }
                cancel();
                done.run();
            }
        }.runTaskTimer(service.plugin(), 0L, period);
    }

    protected void finish(Player player, CasinoService.Round round, String result) {
        balance = round.balance();
        renderBetRow(true);
        service.finishRound(player.getUniqueId(), round.game(), round.bet(), round.payout(), round.balance(), result);
    }

    static ItemStack item(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> (Component) Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    static Material material(String name, Material fallback) {
        Material material = Material.matchMaterial(name);
        return material == null || !material.isItem() ? fallback : material;
    }

    static String multiplier(double value) {
        return value == Math.floor(value) ? "×" + (long) value : "×" + value;
    }
}
