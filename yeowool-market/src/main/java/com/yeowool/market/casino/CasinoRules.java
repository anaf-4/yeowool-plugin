package com.yeowool.market.casino;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Pure game math — no Bukkit state, so every rule is unit-testable. Multipliers are total return (stake included). */
public final class CasinoRules {

    private CasinoRules() {
    }

    /** Chips owed for {@code bet} at {@code multiplier}, rounded down. */
    public static long payout(long bet, double multiplier) {
        return (long) Math.floor(bet * multiplier);
    }

    /** Chips the player may still buy today. */
    public static long remainingBuy(long boughtToday, long dailyLimit) {
        return Math.max(0, dailyLimit - boughtToday);
    }

    /** Worth a cross-server announcement: at least {@code minMultiplier}× the bet, or {@code minChips} chips won. */
    public static boolean bigWin(long bet, long payout, double minMultiplier, long minChips) {
        return payout > 0 && (payout >= minChips || payout >= bet * minMultiplier);
    }

    // ---- 슬롯머신 ----

    public record Symbol(String name, String material, int weight, double payout) {
    }

    /** 3 reels drawing from weighted symbols; the first symbol (체리) also pays {@code twoFirst} when exactly two show. */
    public record Slot(List<Symbol> symbols, double twoFirst) {

        public Slot {
            if (symbols.isEmpty() || symbols.stream().anyMatch(s -> s.weight() <= 0)) {
                throw new IllegalArgumentException("슬롯 그림이 없거나 가중치가 0 이하입니다.");
            }
            symbols = List.copyOf(symbols);
        }

        public static Slot fromConfig(ConfigurationSection section) {
            List<Symbol> symbols = new ArrayList<>();
            for (Map<?, ?> map : section.getMapList("symbols")) {
                symbols.add(new Symbol(String.valueOf(map.get("name")), String.valueOf(map.get("item")),
                        ((Number) map.get("weight")).intValue(), ((Number) map.get("payout")).doubleValue()));
            }
            return new Slot(symbols, section.getDouble("two-cherries", 2));
        }

        private int totalWeight() {
            return symbols.stream().mapToInt(Symbol::weight).sum();
        }

        public int[] spin(RandomGenerator random) {
            int[] reels = new int[3];
            for (int i = 0; i < 3; i++) {
                int roll = random.nextInt(totalWeight());
                int index = 0;
                while (roll >= symbols.get(index).weight()) {
                    roll -= symbols.get(index++).weight();
                }
                reels[i] = index;
            }
            return reels;
        }

        public double multiplier(int[] reels) {
            if (reels[0] == reels[1] && reels[1] == reels[2]) {
                return symbols.get(reels[0]).payout();
            }
            int first = 0;
            for (int reel : reels) {
                first += reel == 0 ? 1 : 0;
            }
            return first == 2 ? twoFirst : 0;
        }

        /** Exact return-to-player of this table. */
        public double expectedRtp() {
            double total = totalWeight();
            double rtp = 0;
            for (Symbol symbol : symbols) {
                rtp += Math.pow(symbol.weight() / total, 3) * symbol.payout();
            }
            double p = symbols.get(0).weight() / total;
            return rtp + 3 * p * p * (1 - p) * twoFirst;
        }
    }

    // ---- 룰렛 (유럽식 0~36) ----

    private static final Set<Integer> RED_NUMBERS = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    public enum RouletteBet {
        NUMBER("숫자"), RED("빨강"), BLACK("검정"), ODD("홀"), EVEN("짝"), LOW("1~18"), HIGH("19~36"),
        DOZEN1("1구간(1~12)"), DOZEN2("2구간(13~24)"), DOZEN3("3구간(25~36)");

        private final String label;

        RouletteBet(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public boolean isDozen() {
            return this == DOZEN1 || this == DOZEN2 || this == DOZEN3;
        }

        /** Does {@code result} win this bet? {@code number} only matters for {@link #NUMBER}. 0 loses every outside bet. */
        public boolean wins(int result, int number) {
            if (this == NUMBER) {
                return result == number;
            }
            if (result == 0) {
                return false;
            }
            return switch (this) {
                case RED -> isRed(result);
                case BLACK -> !isRed(result);
                case ODD -> result % 2 == 1;
                case EVEN -> result % 2 == 0;
                case LOW -> result <= 18;
                case HIGH -> result >= 19;
                case DOZEN1 -> result <= 12;
                case DOZEN2 -> result >= 13 && result <= 24;
                case DOZEN3 -> result >= 25;
                case NUMBER -> false;
            };
        }
    }

    public static boolean isRed(int number) {
        return RED_NUMBERS.contains(number);
    }

    // ---- 주사위 하이/로우 (2개 합) ----

    public enum DiceBet {
        LOW("로우(2~6)"), SEVEN("7"), HIGH("하이(8~12)");

        private final String label;

        DiceBet(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public boolean wins(int sum) {
            return switch (this) {
                case LOW -> sum <= 6;
                case SEVEN -> sum == 7;
                case HIGH -> sum >= 8;
            };
        }
    }

    // ---- 블랙잭 (카드 0~51: 무늬 = card / 13, 숫자 = card % 13 + 1, 1 = A, 11~13 = J/Q/K) ----

    public static int rank(int card) {
        return card % 13 + 1;
    }

    /** Best total: aces count 11 while that doesn't bust. */
    public static int handValue(List<Integer> cards) {
        int total = 0;
        boolean ace = false;
        for (int card : cards) {
            int rank = rank(card);
            total += Math.min(rank, 10);
            ace |= rank == 1;
        }
        return ace && total + 10 <= 21 ? total + 10 : total;
    }

    public static boolean isBlackjack(List<Integer> cards) {
        return cards.size() == 2 && handValue(cards) == 21;
    }

    /** The dealer draws below 17 and stands on every 17 (soft 17 too). */
    public static boolean dealerHits(List<Integer> dealer) {
        return handValue(dealer) < 17;
    }

    /** Chips returned for a finished hand with {@code stake} on the table (doubled stake included). */
    public static long blackjackPayout(List<Integer> player, List<Integer> dealer, long stake, double naturalMultiplier) {
        int mine = handValue(player);
        if (mine > 21) {
            return 0;
        }
        boolean myNatural = isBlackjack(player);
        boolean theirNatural = isBlackjack(dealer);
        if (myNatural || theirNatural) {
            return myNatural && theirNatural ? stake : myNatural ? payout(stake, naturalMultiplier) : 0;
        }
        int theirs = handValue(dealer);
        if (theirs > 21 || mine > theirs) {
            return stake * 2;
        }
        return mine == theirs ? stake : 0;
    }
}
