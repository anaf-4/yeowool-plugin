package com.yeowool.market.casino;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.ToDoubleFunction;
import java.util.logging.Level;
import java.util.random.RandomGenerator;

/**
 * 카지노: chips are a DB balance bought only with 온 and sold only for 온. Every round's bet and payout are
 * settled in one DB transaction on the worker; the main thread only animates and shows the result.
 */
public final class CasinoService implements Listener {

    static final String SOURCE = "YeowoolMarket";
    private static final long PROMPT_TTL_MILLIS = 60_000;

    public enum Game {
        SLOT("slot", "슬롯머신"), ROULETTE("roulette", "룰렛"), DICE("dice", "주사위"), BLACKJACK("blackjack", "블랙잭");

        private final String key;
        private final String label;

        Game(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }

        static String labelOf(String key) {
            for (Game game : values()) {
                if (game.key.equals(key)) {
                    return game.label;
                }
            }
            return key;
        }
    }

    public record Settings(long chipPrice, long dailyBuyLimit, long maxBet, boolean exchangeAnywhere,
                           double announceMultiplier, long announceChips, Map<String, Game> furniture,
                           CasinoRules.Slot slot, double rouletteNumber, double rouletteOutside, double rouletteDozen,
                           double diceHighLow, double diceSeven, double blackjackNatural) {

        /** Throws on a broken slot table so a bad reload keeps the old settings. */
        public static Settings load(ConfigurationSection c) {
            Map<String, Game> furniture = new HashMap<>();
            for (Game game : Game.values()) {
                ConfigurationSection section = c.getConfigurationSection("games." + game.key());
                String id = section == null ? null : section.getString("furniture-id");
                if (id != null && section.getBoolean("enabled", true)) {
                    furniture.put(id, game);
                }
            }
            ConfigurationSection slot = c.getConfigurationSection("games.slot");
            if (slot == null) {
                throw new IllegalArgumentException("casino.games.slot 설정이 없습니다.");
            }
            return new Settings(Math.max(1, c.getLong("chip-price", 1000)), Math.max(0, c.getLong("daily-buy-limit", 500)),
                    Math.max(1, c.getLong("max-bet", 100)), c.getBoolean("exchange-anywhere", true),
                    c.getDouble("announce.min-multiplier", 20), c.getLong("announce.min-chips", 1000), Map.copyOf(furniture),
                    CasinoRules.Slot.fromConfig(slot),
                    c.getDouble("games.roulette.number-payout", 36), c.getDouble("games.roulette.outside-payout", 2),
                    c.getDouble("games.roulette.dozen-payout", 3),
                    c.getDouble("games.dice.high-low-payout", 2), c.getDouble("games.dice.seven-payout", 4),
                    c.getDouble("games.blackjack.blackjack-payout", 2.5));
        }
    }

    /** A settled slot/roulette/dice round. {@code draw} is the game's raw result (reels, number, dice). */
    record Round(Game game, long bet, long payout, long balance, int[] draw) {
    }

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final CasinoRepository repository;
    private final Executor executor;
    private final SecureRandom random = new SecureRandom();
    private volatile Settings settings;
    private final File npcFile;
    private final Set<Integer> npcIds = new LinkedHashSet<>();
    // one money/chip operation per player at a time; released from whichever thread finishes it
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    // main thread only
    private final Map<UUID, BlackjackGui> blackjackHands = new HashMap<>();
    private record Prompt(boolean buying, long createdAt) {
    }

    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();
    private volatile long announcedUntilId;
    private final AtomicBoolean polling = new AtomicBoolean();

    public CasinoService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, CasinoRepository repository,
                         Executor executor, Settings settings, long announcedUntilId) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.executor = executor;
        this.settings = settings;
        this.announcedUntilId = announcedUntilId;
        this.npcFile = new File(plugin.getDataFolder(), "casino-npcs.yml");
        npcIds.addAll(YamlConfiguration.loadConfiguration(npcFile).getIntegerList("npc-ids"));
    }

    public Settings settings() {
        return settings;
    }

    public void reload(Settings settings) {
        this.settings = settings;
    }

    CasinoRepository repository() {
        return repository;
    }

    JavaPlugin plugin() {
        return plugin;
    }

    MessageService messages() {
        return messages;
    }

    RandomGenerator random() {
        return random;
    }

    // ---- NPC (exchange only) ----

    public boolean isCasinoNpc(int npcId) {
        return npcIds.contains(npcId);
    }

    /** Binds or unbinds a Citizens NPC as the chip exchange on this server; returns whether it is now bound. */
    public boolean toggleNpc(int npcId) {
        boolean bound = npcIds.add(npcId) || !npcIds.remove(npcId);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("npc-ids", new ArrayList<>(npcIds));
        try {
            yaml.save(npcFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "casino-npcs.yml 저장 실패", e);
        }
        return bound;
    }

    // ---- 칩 환전 ----

    /** Loads chips and today's purchases off the main thread, then opens the exchange. */
    public void openExchange(Player player) {
        UUID uuid = player.getUniqueId();
        String day = LocalDate.now().toString();
        worker(uuid, false, () -> {
            long chips = repository.balance(uuid);
            long bought = repository.boughtToday(uuid, day);
            runMain(() -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    new ChipExchangeGui(this, chips, core.economyData().getBalance(uuid),
                            CasinoRules.remainingBuy(bought, settings.dailyBuyLimit())).open(online);
                }
            });
        });
    }

    /** Main thread: 온 first, then chips on the worker; if the chips can't be added the 온 goes back through the payout ledger. */
    public void buy(Player player, long amount) {
        UUID uuid = player.getUniqueId();
        Settings s = settings;
        if (amount <= 0 || amount > s.dailyBuyLimit()) {
            messages.send(player, "casino.over-limit-request", Placeholder.unparsed("limit", fmt(s.dailyBuyLimit())));
            return;
        }
        if (!busy.add(uuid)) {
            return;
        }
        long cost = amount * s.chipPrice();
        String reason = "카지노 칩 " + fmt(amount) + "개 구매";
        boolean paid = false;
        try {
            paid = core.economyData().modifyBalance(uuid, -cost, SOURCE, reason);
        } finally {
            if (!paid) {
                busy.remove(uuid);
            }
        }
        if (!paid) {
            messages.send(player, "casino.no-money", Placeholder.unparsed("cost", fmt(cost)));
            return;
        }
        String day = LocalDate.now().toString();
        try {
            executor.execute(() -> {
                try {
                    String key;
                    try {
                        key = repository.buy(uuid, amount, s.dailyBuyLimit(), day) ? "casino.bought" : "casino.over-limit";
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "카지노 칩 구매 실패: " + uuid + " " + amount, e);
                        key = "casino.error";
                    }
                    if (key.equals("casino.bought")) {
                        logSafely(uuid, reason, amount, cost);
                    } else {
                        refund(uuid, cost, reason + " 취소 환불");
                    }
                    String done = key;
                    runMain(() -> {
                        core.payouts().claimNow(uuid);
                        reopenExchange(uuid, done, Placeholder.unparsed("amount", fmt(amount)), Placeholder.unparsed("cost", fmt(cost)),
                                Placeholder.unparsed("limit", fmt(s.dailyBuyLimit())));
                    });
                } finally {
                    busy.remove(uuid);
                }
            });
        } catch (RejectedExecutionException e) {
            busy.remove(uuid);
            core.economyData().modifyBalance(uuid, cost, SOURCE, reason + " 취소 환불");
        }
    }

    /** Main thread: chips leave the DB first (conditional), then the 온 goes through the payout ledger. */
    public void sell(Player player, long amount) {
        UUID uuid = player.getUniqueId();
        if (amount <= 0 || !busy.add(uuid)) {
            return;
        }
        long value = amount * settings.chipPrice();
        String reason = "카지노 칩 " + fmt(amount) + "개 판매";
        worker(uuid, true, () -> {
            String key = "casino.no-chips";
            if (repository.take(uuid, amount).isPresent()) {
                try {
                    core.payouts().enqueue(uuid, value, SOURCE, reason);
                } catch (Exception e) {
                    repository.give(uuid, amount); // nothing was paid — put the chips back
                    throw e;
                }
                key = "casino.sold";
                logSafely(uuid, reason, -amount, value); // after the payout, and can't throw into the refund above
            }
            String done = key;
            runMain(() -> {
                core.payouts().claimNow(uuid);
                reopenExchange(uuid, done, Placeholder.unparsed("amount", fmt(amount)), Placeholder.unparsed("cost", fmt(value)));
            });
        });
    }

    private void logSafely(UUID uuid, String reason, long chips, long on) {
        try {
            core.logs().log(SOURCE, "casino", uuid, reason, Map.of("chips", String.valueOf(chips), "on", String.valueOf(on)));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "카지노 로그 기록 실패: " + uuid + " " + reason, e);
        }
    }

    /** Main thread: closes the GUI and waits for a chat amount (직접입력). */
    void askAmount(Player player, boolean buying) {
        player.closeInventory();
        prompts.put(player.getUniqueId(), new Prompt(buying, System.currentTimeMillis()));
        messages.send(player, buying ? "casino.ask-buy" : "casino.ask-sell");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Prompt prompt = prompts.remove(player.getUniqueId());
        if (prompt == null || System.currentTimeMillis() - prompt.createdAt() > PROMPT_TTL_MILLIS) {
            return;
        }
        event.setCancelled(true);
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim().replace(",", "");
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || raw.equals("취소")) {
                return;
            }
            long amount;
            try {
                amount = Long.parseLong(raw);
            } catch (NumberFormatException e) {
                amount = -1;
            }
            if (amount <= 0 || amount > 1_000_000) {
                messages.send(player, "casino.bad-number");
            } else if (prompt.buying()) {
                buy(player, amount);
            } else {
                sell(player, amount);
            }
        });
    }

    private void reopenExchange(UUID uuid, String key, TagResolver... placeholders) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        messages.send(player, key, placeholders);
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof ChipExchangeGui) {
            openExchange(player);
        }
    }

    private void refund(UUID uuid, long amount, String reason) {
        try {
            core.payouts().enqueue(uuid, amount, SOURCE, reason);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "카지노 환불 실패 — 수동 지급 필요: " + uuid + " " + amount + "온 (" + reason + ")", e);
        }
    }

    // ---- games ----

    /** Opens a game window (or the player's unfinished blackjack hand). */
    public void openGame(Player player, Game game) {
        UUID uuid = player.getUniqueId();
        BlackjackGui hand = blackjackHands.get(uuid);
        if (hand != null) {
            hand.open(player);
            return;
        }
        worker(uuid, false, () -> {
            long chips = repository.balance(uuid);
            runMain(() -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online == null) {
                    return;
                }
                switch (game) {
                    case SLOT -> new SlotGui(this, chips).open(online);
                    case ROULETTE -> new RouletteGui(this, chips).open(online);
                    case DICE -> new DiceGui(this, chips).open(online);
                    case BLACKJACK -> new BlackjackGui(this, online, chips).open(online);
                }
            });
        });
    }

    /** Main thread: true if {@code bet} is allowed under the current max-bet; tells the player otherwise. */
    boolean checkBet(Player player, long bet) {
        long max = settings.maxBet();
        if (bet > 0 && bet <= max) {
            return true;
        }
        messages.send(player, "casino.over-max-bet", Placeholder.unparsed("max", fmt(max)));
        return false;
    }

    /**
     * Main thread: one slot/roulette/dice round. The worker draws the result ({@link SecureRandom}), takes the bet and
     * credits the payout in one transaction; {@code show} gets the settled round on the main thread and must end with
     * {@link #finishRound}. Clicks are ignored until then.
     */
    void play(Player player, Game game, long bet, Function<RandomGenerator, int[]> draw, ToDoubleFunction<int[]> multiplier,
              Function<int[], String> detail, Consumer<Round> show) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        if (!checkBet(player, bet) || !busy.add(uuid)) {
            return;
        }
        try {
            executor.execute(() -> {
                boolean handedOff = false;
                try {
                    int[] result = draw.apply(random);
                    long payout = CasinoRules.payout(bet, multiplier.applyAsDouble(result));
                    OptionalLong balance = repository.settle(uuid, bet, payout, game.key(), bet, detail.apply(result), 0);
                    if (balance.isEmpty()) {
                        runMain(() -> sendIfOnline(uuid, "casino.no-chips-bet"));
                        return;
                    }
                    announceIfBig(name, game, bet, payout);
                    Round round = new Round(game, bet, payout, balance.getAsLong(), result);
                    // the lock stays through the animation; finishRound releases it
                    runMain(() -> {
                        try {
                            show.accept(round);
                        } catch (RuntimeException e) {
                            busy.remove(uuid);
                            throw e;
                        }
                    });
                    handedOff = plugin.isEnabled();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "카지노 게임 처리 실패: " + uuid, e);
                    runMain(() -> sendIfOnline(uuid, "casino.error"));
                } finally {
                    if (!handedOff) {
                        busy.remove(uuid);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            busy.remove(uuid);
        }
    }

    /** Main thread, after the animation: unlocks the player and tells them the outcome. */
    void finishRound(UUID uuid, Game game, long bet, long payout, long balance, String result) {
        busy.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        TagResolver[] tags = {Placeholder.unparsed("game", game.label()), Placeholder.unparsed("result", result),
                Placeholder.unparsed("bet", fmt(bet)), Placeholder.unparsed("payout", fmt(payout)),
                Placeholder.unparsed("balance", fmt(balance))};
        if (payout > 0) {
            messages.send(player, payout > bet ? "casino.round-win" : "casino.round-push", tags);
            if (payout > bet) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
            }
        } else {
            messages.send(player, "casino.round-lose", tags);
        }
    }

    // ---- blackjack (the stake stays taken while the hand is open) ----

    /**
     * Hand-off between an in-flight blackjack take (worker) and a server stop (main thread): whoever claims first owns
     * the outcome. If the stop wins, the worker gives its chips straight back; if the worker won, the stop reads
     * {@link #result} and finishes the hand itself (its main-thread callback is cancelled with the plugin).
     */
    static final class TakeTicket {
        private final AtomicBoolean claimed = new AtomicBoolean();
        private volatile OptionalLong result = OptionalLong.empty();

        /** Main thread at shutdown: null if the take hadn't finished (the worker returns those chips), else its outcome. */
        OptionalLong cancel() {
            return claimed.compareAndSet(false, true) ? null : result;
        }
    }

    /** Main thread: takes {@code chips} for a deal or a double-down; exactly one of the callbacks runs on the main thread. */
    TakeTicket blackjackTake(Player player, long chips, LongConsumer onTaken, Runnable onFailed) {
        UUID uuid = player.getUniqueId();
        TakeTicket ticket = new TakeTicket();
        if (chips <= 0 || !busy.add(uuid)) {
            ticket.claimed.set(true);
            onFailed.run();
            return ticket;
        }
        try {
            executor.execute(() -> {
                try {
                    OptionalLong balance = OptionalLong.empty();
                    boolean error = false;
                    try {
                        balance = repository.take(uuid, chips);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "블랙잭 베팅 실패: " + uuid, e);
                        error = true;
                    }
                    ticket.result = balance;
                    if (!ticket.claimed.compareAndSet(false, true)) {
                        if (balance.isPresent()) { // the hand was already settled without these chips
                            giveBackSafely(uuid, chips, "블랙잭 종료 중 베팅 반환");
                        }
                        return;
                    }
                    OptionalLong taken = balance;
                    String failKey = error ? "casino.error" : "casino.no-chips-bet";
                    runMain(() -> {
                        if (taken.isPresent()) {
                            onTaken.accept(taken.getAsLong());
                        } else {
                            onFailed.run();
                            sendIfOnline(uuid, failKey);
                        }
                    });
                } finally {
                    busy.remove(uuid);
                }
            });
        } catch (RejectedExecutionException e) {
            busy.remove(uuid);
            ticket.claimed.set(true);
            onFailed.run();
        }
        return ticket;
    }

    void blackjackOpened(UUID uuid, BlackjackGui hand) {
        blackjackHands.put(uuid, hand);
    }

    void blackjackClosed(UUID uuid, BlackjackGui hand) {
        blackjackHands.remove(uuid, hand);
    }

    /**
     * Main thread: credits a finished hand on the worker. A winning hand is first written to {@code yw_casino_pending};
     * the credit transaction deletes that row, so a failed credit is replayed by {@link #replayPending} exactly once.
     */
    void blackjackSettle(UUID uuid, String name, long stake, long payout, String detail, String result, LongConsumer onBalance) {
        blackjackHands.remove(uuid);
        Runnable settle = () -> {
            long pendingId;
            try {
                pendingId = payout > 0 ? repository.addPending(uuid, payout, stake, detail) : 0;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "블랙잭 정산 실패 — 수동 지급 필요: " + uuid + " " + payout + "칩 (" + detail + ")", e);
                return;
            }
            long balance;
            try {
                OptionalLong settled = repository.settle(uuid, 0, payout, Game.BLACKJACK.key(), stake, detail, pendingId);
                balance = settled.isPresent() ? settled.getAsLong() : repository.balance(uuid); // empty: a replay already paid it
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "블랙잭 정산 실패" + (pendingId > 0 ? " — 1분 안에 보류 장부에서 다시 지급합니다: " : ": ")
                        + uuid + " " + payout + "칩 (" + detail + ")", e);
                return;
            }
            announceIfBig(name, Game.BLACKJACK, stake, payout);
            runMain(() -> {
                onBalance.accept(balance);
                finishRound(uuid, Game.BLACKJACK, stake, payout, balance, result);
            });
        };
        try {
            executor.execute(settle);
        } catch (RejectedExecutionException e) {
            plugin.getLogger().severe("블랙잭 정산 불가(종료 중) — 수동 지급 필요: " + uuid + " " + payout + "칩 (" + detail + ")");
        }
    }

    /** Worker, on startup and every minute: credits blackjack payouts whose settlement failed. Each row pays once. */
    public void replayPending() {
        try {
            for (CasinoRepository.Pending row : repository.pending()) {
                try {
                    if (repository.settle(row.uuid(), 0, row.chips(), Game.BLACKJACK.key(), row.stake(),
                            row.detail() + " (재정산)", row.id()).isPresent()) {
                        plugin.getLogger().info("블랙잭 보류 정산 완료: " + row.uuid() + " " + row.chips() + "칩");
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "블랙잭 보류 정산 실패 (다음에 다시): " + row.uuid(), e);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "블랙잭 보류 장부 조회 실패", e);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        prompts.remove(uuid);
        BlackjackGui hand = blackjackHands.get(uuid);
        if (hand != null) {
            hand.forceStand();
        }
        busy.remove(uuid);
    }

    /** Main thread, from onDisable before the worker pool shuts down: settles every open hand (queued on the worker). */
    public void shutdown() {
        for (BlackjackGui hand : List.copyOf(blackjackHands.values())) {
            hand.shutdownSettle();
        }
    }

    // ---- big-win announcements (every ~20 s on every server) ----

    private void announceIfBig(String name, Game game, long bet, long payout) {
        Settings s = settings;
        if (!CasinoRules.bigWin(bet, payout, s.announceMultiplier(), s.announceChips())) {
            return;
        }
        try {
            repository.announce(name, game.key(), bet, payout);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "카지노 당첨 공지 기록 실패", e);
        }
    }

    public void pollAnnouncements() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        try {
            List<CasinoRepository.Announcement> rows = repository.announcementsAfter(announcedUntilId);
            if (rows.isEmpty()) {
                return;
            }
            announcedUntilId = rows.get(rows.size() - 1).id();
            runMain(() -> rows.forEach(row -> messages.broadcast("casino.big-win",
                    Placeholder.unparsed("player", row.player()), Placeholder.unparsed("game", Game.labelOf(row.game())),
                    Placeholder.unparsed("payout", fmt(row.payout())),
                    Placeholder.unparsed("multiplier", String.format("%.1f", (double) row.payout() / Math.max(1, row.bet()))))));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "카지노 공지 조회 실패", e);
        } finally {
            polling.set(false);
        }
    }

    // ---- helpers ----

    @FunctionalInterface
    private interface Work {
        void run() throws Exception;
    }

    /**
     * Runs {@code work} on the worker; any failure shows a generic error. With {@code locked} the caller already took
     * the busy lock and it is always released here once the work ends, fails or can't be queued.
     */
    private void worker(UUID uuid, boolean locked, Work work) {
        try {
            executor.execute(() -> {
                try {
                    work.run();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "카지노 처리 실패: " + uuid, e);
                    runMain(() -> sendIfOnline(uuid, "casino.error"));
                } finally {
                    if (locked) {
                        busy.remove(uuid);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            if (locked) {
                busy.remove(uuid);
            }
        }
    }

    private void giveBackSafely(UUID uuid, long chips, String reason) {
        try {
            repository.give(uuid, chips);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "칩 반환 실패 — 수동 지급 필요: " + uuid + " " + chips + "칩 (" + reason + ")", e);
        }
    }

    private void sendIfOnline(UUID uuid, String key) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            messages.send(player, key);
        }
    }

    private void runMain(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    static String fmt(long value) {
        return String.format("%,d", value);
    }
}
