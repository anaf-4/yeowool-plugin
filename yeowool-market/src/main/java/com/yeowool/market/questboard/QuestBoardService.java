package com.yeowool.market.questboard;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * The quest board's flows: opening the GUIs, the two-step chat prompt that
 * registers a request, delivering, cancelling and expiring. Money only ever
 * leaves a wallet here (registration, main thread, player online); money
 * owed to anyone goes through {@link QuestRepository}'s payout ledger and
 * {@link QuestPayoutClaimer}.
 */
public final class QuestBoardService {

    public record Settings(String furnitureId, int feePercent, long durationMillis, int maxOpenPerPlayer, int maxQuantity) {
    }

    /** A player partway through the chat prompts; quantity 0 means we're still asking for the quantity. */
    private record PendingInput(ItemStack sample, int quantity) {
    }

    @FunctionalInterface
    private interface SqlCall<T> {
        T call() throws SQLException;
    }

    static final int PAGE_SIZE = 45;
    private static final String SOURCE = "YeowoolMarket";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final QuestRepository repository;
    private final QuestPayoutClaimer claimer;
    private final Executor executor;
    private final Settings settings;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    public QuestBoardService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, QuestRepository repository,
                             QuestPayoutClaimer claimer, Executor executor, Settings settings) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.claimer = claimer;
        this.executor = executor;
        this.settings = settings;
    }

    public Settings settings() {
        return settings;
    }

    // ---- GUIs (main thread) ----

    public void openBoard(Player player, int page) {
        long now = System.currentTimeMillis();
        async(player.getUniqueId(), () -> repository.listOpen(now, page * PAGE_SIZE, PAGE_SIZE + 1), requests -> {
            if (!player.isOnline()) {
                return;
            }
            boolean hasNext = requests.size() > PAGE_SIZE;
            new QuestBoardGui(this, hasNext ? requests.subList(0, PAGE_SIZE) : requests, page, hasNext).open(player);
        });
    }

    public void openMine(Player player) {
        async(player.getUniqueId(), () -> repository.listByRequester(player.getUniqueId(), MyQuestsGui.SIZE), requests -> {
            if (player.isOnline()) {
                new MyQuestsGui(this, requests).open(player);
            }
        });
    }

    // ---- registering (main thread unless noted) ----

    public void beginRegister(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            messages.send(player, "questboard.hold-item");
            return;
        }
        pending.put(player.getUniqueId(), new PendingInput(hand.asOne(), 0));
        player.closeInventory();
        askQuantity(player);
    }

    /** Chat thread. True when the message answered one of our prompts — the caller then cancels the chat event. */
    public boolean consumeChat(Player player, String raw) {
        PendingInput input = pending.remove(player.getUniqueId());
        if (input == null) {
            return false;
        }
        Bukkit.getScheduler().runTask(plugin, () -> handleInput(player, input, raw.trim()));
        return true;
    }

    public void forget(UUID uuid) {
        pending.remove(uuid);
    }

    private void handleInput(Player player, PendingInput input, String raw) {
        if (!player.isOnline()) {
            return;
        }
        if (raw.equals("취소")) {
            messages.send(player, "questboard.input-cancelled");
            return;
        }
        long value;
        try {
            value = Long.parseLong(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            value = -1;
        }
        UUID uuid = player.getUniqueId();
        if (input.quantity() == 0) {
            if (!QuestBoardRules.validQuantity(value, settings.maxQuantity())) {
                pending.put(uuid, input);
                messages.send(player, "questboard.invalid-number");
                askQuantity(player);
                return;
            }
            pending.put(uuid, new PendingInput(input.sample(), (int) value));
            messages.send(player, "questboard.ask-reward");
            return;
        }
        if (value < 1) {
            pending.put(uuid, input);
            messages.send(player, "questboard.invalid-number");
            messages.send(player, "questboard.ask-reward");
            return;
        }
        register(player, input.sample(), input.quantity(), value);
    }

    private void askQuantity(Player player) {
        messages.send(player, "questboard.ask-quantity", Placeholder.unparsed("max", String.format("%,d", settings.maxQuantity())));
    }

    private void register(Player player, ItemStack sample, int quantity, long rewardPerItem) {
        long total = QuestBoardRules.totalReward(quantity, rewardPerItem);
        long cost = QuestBoardRules.upfrontCost(quantity, rewardPerItem, settings.feePercent());
        if (total < 0 || cost < 0) {
            messages.send(player, "questboard.too-expensive");
            return;
        }
        long fee = cost - total;
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        String label = itemLabel(sample);
        async(uuid, () -> repository.countOpen(uuid), open -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) {
                return;
            }
            // ponytail: the count and the insert aren't atomic, so two instant registrations can exceed the cap by one — fine for a soft limit.
            if (open >= settings.maxOpenPerPlayer()) {
                messages.send(online, "questboard.too-many-open", Placeholder.unparsed("max", String.valueOf(settings.maxOpenPerPlayer())));
                return;
            }
            if (!core.economyData().modifyBalance(uuid, -cost, SOURCE, "의뢰 등록 (" + label + " " + quantity + "개)")) {
                messages.send(online, "questboard.insufficient-funds",
                        Placeholder.unparsed("total", String.format("%,d", total)),
                        Placeholder.unparsed("fee", String.format("%,d", fee)));
                return;
            }
            long now = System.currentTimeMillis();
            executor.execute(() -> {
                try {
                    long id = repository.insert(uuid, name, sample, label, quantity, rewardPerItem, now, now + settings.durationMillis());
                    sync(uuid, p -> messages.send(p, "questboard.registered",
                            Placeholder.unparsed("id", String.valueOf(id)),
                            Placeholder.unparsed("item", label),
                            Placeholder.unparsed("quantity", String.format("%,d", quantity)),
                            Placeholder.unparsed("reward", String.format("%,d", rewardPerItem)),
                            Placeholder.unparsed("fee", String.format("%,d", fee))));
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "의뢰 등록 실패 (" + uuid + ", " + cost + "온 환불 예정)", e);
                    try {
                        repository.insertPayout(uuid, cost, "의뢰 등록 실패 환불");
                    } catch (SQLException e2) {
                        plugin.getLogger().log(Level.SEVERE, "의뢰 등록 환불 장부 기록 실패 — 수동 환불 필요: " + uuid + " " + cost + "온", e2);
                    }
                    sync(uuid, p -> {
                        messages.send(p, "questboard.register-failed");
                        claimer.claim(uuid);
                    });
                }
            });
        });
    }

    // ---- delivering (main thread) ----

    public void deliver(Player player, QuestRequest request, int page) {
        UUID uuid = player.getUniqueId();
        if (request.requester().equals(uuid)) {
            messages.send(player, "questboard.own-request");
            return;
        }
        async(uuid, () -> repository.find(request.id()), found -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) {
                return;
            }
            if (found.isEmpty() || !found.get().isOpen()) {
                messages.send(online, "questboard.not-found");
                openBoard(online, page);
                return;
            }
            QuestRequest fresh = found.get();
            int amount = QuestBoardRules.deliverable(fresh.remaining(), countSimilar(online, fresh.sample()));
            if (amount == 0) {
                messages.send(online, "questboard.no-matching-items");
                return;
            }
            int leftover = online.getInventory().removeItem(fresh.sample().asQuantity(amount)).values().stream()
                    .mapToInt(ItemStack::getAmount).sum();
            int removed = amount - leftover;
            if (removed <= 0) {
                messages.send(online, "questboard.no-matching-items");
                return;
            }
            long now = System.currentTimeMillis();
            executor.execute(() -> {
                long reward;
                try {
                    reward = repository.deliver(fresh.id(), uuid, removed, now);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "의뢰 납품 실패 (#" + fresh.id() + ", " + uuid + ") — 아이템 반환", e);
                    reward = -1;
                }
                long result = reward;
                Bukkit.getScheduler().runTask(plugin, () -> finishDelivery(uuid, fresh, removed, result, page));
            });
        });
    }

    private void finishDelivery(UUID deliverer, QuestRequest request, int amount, long reward, int page) {
        Player player = Bukkit.getPlayer(deliverer);
        if (reward < 0) {
            giveStacks(deliverer, request.sample(), amount, "의뢰 #" + request.id() + " 납품 반환");
            if (player != null) {
                messages.send(player, "questboard.deliver-failed");
            }
            return;
        }
        giveStacks(request.requester(), request.sample(), amount, "의뢰 #" + request.id() + " 납품품");
        if (player != null) {
            messages.send(player, "questboard.delivered",
                    Placeholder.unparsed("item", request.itemLabel()),
                    Placeholder.unparsed("amount", String.format("%,d", amount)),
                    Placeholder.unparsed("reward", String.format("%,d", reward)));
            claimer.claim(deliverer);
            openBoard(player, page);
        }
    }

    /** Hands {@code amount} copies of {@code sample} to {@code recipient} in max-size stacks (inventory if online here and it fits, otherwise mailbox). */
    private void giveStacks(UUID recipient, ItemStack sample, int amount, String note) {
        int max = sample.getMaxStackSize();
        for (int left = amount; left > 0; left -= max) {
            core.mailbox().deliverOrStore(recipient, sample.asQuantity(Math.min(max, left)), SOURCE, note);
        }
    }

    private static int countSimilar(Player player, ItemStack sample) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.isSimilar(sample)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    // ---- cancel / expire ----

    /** Main thread. */
    public void cancel(Player player, long requestId) {
        UUID uuid = player.getUniqueId();
        async(uuid, () -> repository.cancel(requestId, uuid), refund -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) {
                return;
            }
            if (refund < 0) {
                messages.send(online, "questboard.not-found");
            } else {
                messages.send(online, "questboard.cancelled",
                        Placeholder.unparsed("id", String.valueOf(requestId)),
                        Placeholder.unparsed("refund", String.format("%,d", refund)));
                claimer.claim(uuid);
            }
            openMine(online);
        });
    }

    /** Executor thread, every minute on every server — the conditional close in the repository makes each refund happen once. */
    public void expireDue() {
        try {
            repository.expireDue(System.currentTimeMillis());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "만료된 의뢰 처리 실패", e);
        }
    }

    // ---- helpers ----

    static String itemLabel(ItemStack sample) {
        ItemMeta meta = sample.getItemMeta();
        String label = meta != null && meta.hasDisplayName() && meta.displayName() != null
                ? PlainTextComponentSerializer.plainText().serialize(meta.displayName())
                : sample.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return label.length() > 64 ? label.substring(0, 64) : label;
    }

    /** Runs {@code call} on the executor, then {@code then} on the main thread; SQL errors are logged and reported to the player. */
    private <T> void async(UUID uuid, SqlCall<T> call, Consumer<T> then) {
        executor.execute(() -> {
            T result;
            try {
                result = call.call();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "의뢰 게시판 DB 작업 실패 (" + uuid + ")", e);
                sync(uuid, p -> messages.send(p, "questboard.error"));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> then.accept(result));
        });
    }

    private void sync(UUID uuid, Consumer<Player> action) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                action.accept(player);
            }
        });
    }
}
