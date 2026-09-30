package com.yeowool.market.casino;

import com.yeowool.core.api.gui.GuiButton;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.yeowool.market.casino.CasinoRules.handValue;

/**
 * 블랙잭 against the dealer. The stake is taken from the DB when the hand is dealt (and again on a double-down) and the
 * payout is credited when it ends. Closing the window or leaving mid-hand stands.
 */
final class BlackjackGui extends BetGui {

    private enum Phase { IDLE, WAITING, PLAYING }

    private static final String[] SUITS = {"♠", "♥", "♦", "♣"};
    private static final String[] RANKS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
    private static final int DEALER_INFO = 4;
    private static final int DEALER_FIRST = 10;
    private static final int PLAYER_INFO = 22;
    private static final int PLAYER_FIRST = 28;
    private static final int MAX_SHOWN = 7;
    private static final int HIT_SLOT = 38;
    private static final int MAIN_SLOT = 40;
    private static final int DOUBLE_SLOT = 42;

    private final UUID uuid;
    private final String name;
    private final List<Integer> deck = new ArrayList<>();
    private final List<Integer> mine = new ArrayList<>();
    private final List<Integer> dealer = new ArrayList<>();
    private Phase phase = Phase.IDLE;
    private boolean left;
    private long baseBet;
    private long stake;

    BlackjackGui(CasinoService service, Player player, long balance) {
        super(service, "블랙잭", balance);
        this.uuid = player.getUniqueId();
        this.name = player.getName();
        render();
    }

    private void render() {
        for (int slot : new int[]{DEALER_INFO, PLAYER_INFO, HIT_SLOT, MAIN_SLOT, DOUBLE_SLOT}) {
            clearButton(slot);
        }
        for (int i = 0; i < MAX_SHOWN; i++) {
            clearButton(DEALER_FIRST + i);
            clearButton(PLAYER_FIRST + i);
        }
        boolean hideHole = phase != Phase.IDLE;
        for (int i = 0; i < Math.min(dealer.size(), MAX_SHOWN); i++) {
            setButton(DEALER_FIRST + i, GuiButton.display(hideHole && i == 1
                    ? item(Material.BLACK_STAINED_GLASS_PANE, "?", NamedTextColor.DARK_GRAY, List.of())
                    : card(dealer.get(i))));
        }
        for (int i = 0; i < Math.min(mine.size(), MAX_SHOWN); i++) {
            setButton(PLAYER_FIRST + i, GuiButton.display(card(mine.get(i))));
        }
        if (!dealer.isEmpty()) {
            String dealerValue = hideHole ? String.valueOf(handValue(dealer.subList(0, 1))) + " + ?" : String.valueOf(handValue(dealer));
            setButton(DEALER_INFO, GuiButton.display(item(Material.SKELETON_SKULL, "딜러: " + dealerValue, NamedTextColor.WHITE,
                    List.of("딜러는 17 이상이면 멈춥니다"))));
            setButton(PLAYER_INFO, GuiButton.display(item(Material.PLAYER_HEAD, "나: " + handValue(mine), NamedTextColor.WHITE,
                    List.of("걸린 칩: " + CasinoService.fmt(stake) + "개"))));
        }
        switch (phase) {
            case IDLE -> setButton(MAIN_SLOT, GuiButton.of(item(Material.EMERALD_BLOCK, "딜 (" + CasinoService.fmt(bet) + "칩)",
                    NamedTextColor.GREEN, List.of("블랙잭 " + multiplier(service.settings().blackjackNatural()) + ", 승리 ×2, 무승부 환급",
                            "창을 닫거나 나가면 스탠드로 처리됩니다")), event -> deal((Player) event.getWhoClicked())));
            case WAITING -> setButton(MAIN_SLOT, GuiButton.display(item(Material.CLOCK, "처리 중...", NamedTextColor.GRAY, List.of())));
            case PLAYING -> {
                setButton(HIT_SLOT, GuiButton.of(item(Material.LIME_WOOL, "히트", NamedTextColor.GREEN, List.of("카드 한 장 더")),
                        event -> hit()));
                setButton(MAIN_SLOT, GuiButton.of(item(Material.RED_WOOL, "스탠드", NamedTextColor.RED, List.of("멈추고 딜러 차례")),
                        event -> stand()));
                if (mine.size() == 2) {
                    setButton(DOUBLE_SLOT, GuiButton.of(item(Material.GOLD_BLOCK, "더블다운", NamedTextColor.GOLD,
                            List.of("베팅 " + CasinoService.fmt(baseBet) + "칩 추가, 카드 한 장만 받고 스탠드")),
                            event -> doubleDown((Player) event.getWhoClicked())));
                }
            }
        }
        renderBetRow(phase == Phase.IDLE);
    }

    private void deal(Player player) {
        if (phase != Phase.IDLE) {
            return;
        }
        phase = Phase.WAITING;
        left = false;
        baseBet = bet;
        render();
        service.blackjackTake(player, baseBet, this::start, () -> {
            phase = Phase.IDLE;
            render();
        });
    }

    private void start(long newBalance) {
        balance = newBalance;
        stake = baseBet;
        deck.clear();
        for (int card = 0; card < 52; card++) {
            deck.add(card);
        }
        Collections.shuffle(deck, service.random());
        mine.clear();
        dealer.clear();
        mine.add(draw());
        dealer.add(draw());
        mine.add(draw());
        dealer.add(draw());
        phase = Phase.PLAYING;
        service.blackjackOpened(uuid, this);
        if (CasinoRules.isBlackjack(mine) || CasinoRules.isBlackjack(dealer)) {
            finish();
        } else if (left || Bukkit.getPlayer(uuid) == null) {
            stand();
        } else {
            render();
        }
    }

    private void hit() {
        if (phase != Phase.PLAYING) {
            return;
        }
        mine.add(draw());
        int value = handValue(mine);
        if (value > 21) {
            finish();
        } else if (value == 21) {
            stand();
        } else {
            render();
        }
    }

    private void stand() {
        if (phase != Phase.PLAYING) {
            return;
        }
        while (CasinoRules.dealerHits(dealer)) {
            dealer.add(draw());
        }
        finish();
    }

    private void doubleDown(Player player) {
        if (phase != Phase.PLAYING || mine.size() != 2) {
            return;
        }
        phase = Phase.WAITING;
        render();
        service.blackjackTake(player, baseBet, newBalance -> {
            balance = newBalance;
            stake += baseBet;
            phase = Phase.PLAYING;
            mine.add(draw());
            if (handValue(mine) > 21) {
                finish();
            } else {
                stand();
            }
        }, () -> {
            phase = Phase.PLAYING;
            if (left) {
                stand();
            } else {
                render();
            }
        });
    }

    private void finish() {
        double natural = service.settings().blackjackNatural();
        long payout = CasinoRules.blackjackPayout(mine, dealer, stake, natural);
        phase = Phase.IDLE;
        String outcome = handValue(mine) > 21 ? "버스트" : payout > stake
                ? (CasinoRules.isBlackjack(mine) ? "블랙잭!" : "승리") : payout == stake ? "무승부" : "패배";
        String result = "딜러 " + valueText(dealer) + " vs 나 " + valueText(mine) + " — " + outcome;
        String detail = "나 " + cards(mine) + " / 딜러 " + cards(dealer) + (stake > baseBet ? " (더블)" : "");
        service.blackjackSettle(uuid, name, stake, payout, detail, result, newBalance -> {
            balance = newBalance;
            if (phase == Phase.IDLE) {
                render();
            }
        });
        render();
    }

    /** Leaving or closing mid-hand stands; a pending deal/double stands once the DB answers. */
    void forceStand() {
        if (phase == Phase.PLAYING) {
            stand();
        } else if (phase == Phase.WAITING) {
            left = true;
        }
    }

    @Override
    public void onClose(Player player) {
        forceStand();
    }

    private int draw() {
        return deck.remove(deck.size() - 1);
    }

    private static String valueText(List<Integer> cards) {
        return CasinoRules.isBlackjack(cards) ? "블랙잭" : String.valueOf(handValue(cards));
    }

    private static String label(int card) {
        return SUITS[card / 13] + RANKS[card % 13];
    }

    private static String cards(List<Integer> cards) {
        return cards.stream().map(BlackjackGui::label).collect(Collectors.joining(","));
    }

    private static ItemStack card(int card) {
        boolean red = card / 13 == 1 || card / 13 == 2;
        return item(Material.PAPER, label(card), red ? NamedTextColor.RED : NamedTextColor.WHITE, List.of());
    }
}
