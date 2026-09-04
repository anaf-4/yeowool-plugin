package com.yeowool.market.trade;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * State machine for one direct player-to-player trade (plugin plan 7.3):
 * item↔item, item↔온, 온↔온, with a final mutual confirm before anything
 * moves. Both sides must be online for the whole trade; disconnecting
 * cancels it (see {@link TradeManager}).
 */
public final class TradeSession {

    private static final Logger LOGGER = Logger.getLogger(TradeSession.class.getName());

    private final TradeManager manager;
    private final YeowoolCoreAPI core;
    private final MessageService messages;

    private final UUID playerA;
    private final UUID playerB;

    private ItemStack[] itemsA = new ItemStack[9];
    private ItemStack[] itemsB = new ItemStack[9];
    private long moneyA = 0;
    private long moneyB = 0;
    private boolean confirmedA = false;
    private boolean confirmedB = false;

    private TradeGui guiA;
    private TradeGui guiB;
    private boolean finished = false;

    public TradeSession(TradeManager manager, YeowoolCoreAPI core, MessageService messages, UUID playerA, UUID playerB) {
        this.manager = manager;
        this.core = core;
        this.messages = messages;
        this.playerA = playerA;
        this.playerB = playerB;
    }

    public boolean isFinished() {
        return finished;
    }

    public void openFor(Player a, Player b) {
        this.guiA = new TradeGui(this, playerA);
        this.guiB = new TradeGui(this, playerB);
        guiA.open(a);
        guiB.open(b);
    }

    public UUID other(UUID uuid) {
        return uuid.equals(playerA) ? playerB : playerA;
    }

    private boolean isA(UUID uuid) {
        return uuid.equals(playerA);
    }

    public ItemStack[] itemsFor(UUID uuid) {
        return isA(uuid) ? itemsA : itemsB;
    }

    public long moneyFor(UUID uuid) {
        return isA(uuid) ? moneyA : moneyB;
    }

    public boolean isConfirmed(UUID uuid) {
        return isA(uuid) ? confirmedA : confirmedB;
    }

    public TradeGui guiFor(UUID uuid) {
        return isA(uuid) ? guiA : guiB;
    }

    public void onItemsChanged(UUID uuid, ItemStack[] newItems) {
        if (isA(uuid)) {
            itemsA = newItems;
        } else {
            itemsB = newItems;
        }
        resetConfirmations();
        refreshBoth();
    }

    public void onMoneyOffered(UUID uuid, long amount) {
        if (isA(uuid)) {
            moneyA = amount;
        } else {
            moneyB = amount;
        }
        resetConfirmations();
        refreshBoth();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            messages.send(player, "trade.money-set", Placeholder.unparsed("amount", String.format("%,d", amount)));
        }
    }

    private void resetConfirmations() {
        confirmedA = false;
        confirmedB = false;
    }

    public void confirm(UUID uuid) {
        if (isA(uuid)) {
            confirmedA = true;
        } else {
            confirmedB = true;
        }
        if (confirmedA && confirmedB) {
            execute();
        } else {
            refreshBoth();
            Player otherPlayer = Bukkit.getPlayer(other(uuid));
            if (otherPlayer != null) {
                messages.send(otherPlayer, "trade.other-confirmed");
            }
        }
    }

    private void execute() {
        if (finished) {
            return;
        }
        Player a = Bukkit.getPlayer(playerA);
        Player b = Bukkit.getPlayer(playerB);
        if (a == null || b == null) {
            cancel("trade.partner-offline");
            return;
        }
        if (!core.economyData().hasBalance(playerA, moneyA) || !core.economyData().hasBalance(playerB, moneyB)) {
            cancel("trade.insufficient-funds-abort");
            return;
        }

        finished = true;

        // hasBalance was just re-checked above with nothing able to interleave before these
        // calls (single-threaded main-thread execution), so a false return here should be
        // unreachable in practice - checked anyway rather than assuming, since silently ignoring
        // the return value would let money vanish undetected if that guarantee ever breaks.
        if (moneyA > 0 && !transfer(playerA, playerB, moneyA, "거래: " + b.getName() + " 에게", "거래: " + a.getName() + " 로부터")) {
            LOGGER.severe("거래 정산 실패 (" + playerA + " -> " + playerB + ", " + moneyA + "온): 잔액 이체가 갑자기 실패했습니다.");
            abortAfterStart(a, b);
            return;
        }
        if (moneyB > 0 && !transfer(playerB, playerA, moneyB, "거래: " + a.getName() + " 에게", "거래: " + b.getName() + " 로부터")) {
            LOGGER.severe("거래 정산 실패 (" + playerB + " -> " + playerA + ", " + moneyB + "온): 잔액 이체가 갑자기 실패했습니다.");
            abortAfterStart(a, b);
            return;
        }

        giveItems(b, itemsA);
        giveItems(a, itemsB);

        a.closeInventory();
        b.closeInventory();
        messages.send(a, "trade.success");
        messages.send(b, "trade.success");
        core.sounds().play(a, "success");
        core.sounds().play(b, "success");

        manager.endSession(this);
    }

    /** Deducts from {@code from} and credits {@code to}; returns {@code false} (and leaves {@code from} untouched) if the deduction itself failed. */
    private boolean transfer(UUID from, UUID to, long amount, String deductReason, String creditReason) {
        if (!core.economyData().modifyBalance(from, -amount, "YeowoolMarket", deductReason)) {
            return false;
        }
        if (!core.economyData().modifyBalance(to, amount, "YeowoolMarket", creditReason)) {
            // Crediting the counterpart somehow failed after the deduction already went through -
            // refund immediately rather than leaving the money destroyed.
            core.economyData().modifyBalance(from, amount, "YeowoolMarket", "거래 실패로 인한 환불");
            return false;
        }
        return true;
    }

    /** Mirrors {@link #cancel}'s cleanup (return offered items, close both GUIs, end the session) for a failure discovered after {@code finished} was already set. */
    private void abortAfterStart(Player a, Player b) {
        returnItems(playerA, itemsA);
        returnItems(playerB, itemsB);
        a.closeInventory();
        b.closeInventory();
        messages.send(a, "trade.settlement-failed");
        messages.send(b, "trade.settlement-failed");
        manager.endSession(this);
    }

    private void giveItems(Player receiver, ItemStack[] items) {
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            var leftover = receiver.getInventory().addItem(item);
            leftover.values().forEach(extra -> receiver.getWorld().dropItemNaturally(receiver.getLocation(), extra));
        }
    }

    public void cancel(String reasonKey) {
        if (finished) {
            return;
        }
        finished = true;

        returnItems(playerA, itemsA);
        returnItems(playerB, itemsB);

        Player a = Bukkit.getPlayer(playerA);
        Player b = Bukkit.getPlayer(playerB);
        if (a != null) {
            a.closeInventory();
            messages.send(a, reasonKey);
        }
        if (b != null) {
            b.closeInventory();
            messages.send(b, reasonKey);
        }

        manager.endSession(this);
    }

    private void returnItems(UUID owner, ItemStack[] items) {
        Player player = Bukkit.getPlayer(owner);
        if (player == null) {
            return;
        }
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            var leftover = player.getInventory().addItem(item);
            leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
        }
    }

    private void refreshBoth() {
        if (guiA != null) {
            guiA.refresh();
        }
        if (guiB != null) {
            guiB.refresh();
        }
    }
}
