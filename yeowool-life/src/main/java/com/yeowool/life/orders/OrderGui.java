package com.yeowool.life.orders;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.DurationFormat;
import com.yeowool.life.orders.OrderRepository.GroupOrder;
import com.yeowool.life.orders.OrderRepository.Order;
import com.yeowool.life.orders.OrderRules.Difficulty;
import com.yeowool.life.orders.OrderRules.FameLevel;
import com.yeowool.life.orders.OrderRules.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The order window (식당 / 수산시장, specs §5). 교체 is two clicks: arm it with the 교체 button, then click the order to replace. */
public final class OrderGui extends YeowoolGui {

    private static final int FAME_SLOT = 0;
    private static final int INFO_SLOT = 4;
    private static final int REROLL_SLOT = 8;
    private static final int[] ORDER_SLOTS = {11, 13, 15};
    private static final int GROUP_SLOT = 31;
    private static final int CLOSE_SLOT = 49;
    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private final OrderService service;
    private final MessageService messages;
    private final OrderService.View view;
    private final Player viewer;
    private boolean pendingReroll;

    OrderGui(OrderService service, MessageService messages, OrderService.View view, Player viewer) {
        super(54, messages.resolveRaw(service.key("gui.title")));
        this.service = service;
        this.messages = messages;
        this.view = view;
        this.viewer = viewer;
        render();
    }

    OrderService service() {
        return service;
    }

    OrderService.View view() {
        return view;
    }

    /** Click-spam guard feedback: the clicked slot shows "처리 중" until the window refreshes. */
    void showBusy(int slot) {
        setButton(slot, GuiButton.display(item(new ItemStack(Material.CLOCK), line(service.key("gui.busy")), List.of())));
    }

    private void render() {
        Settings settings = service.rules().settings();
        double fameMultiplier = service.rules().level(view.fame()).multiplier();
        setButton(FAME_SLOT, GuiButton.display(fameIcon()));
        setButton(INFO_SLOT, GuiButton.display(infoIcon(settings)));
        setButton(REROLL_SLOT, GuiButton.of(rerollIcon(settings), event -> {
            pendingReroll = !pendingReroll;
            render();
        }));
        if (view.orders().isEmpty()) {
            setButton(ORDER_SLOTS[1], GuiButton.display(item(new ItemStack(Material.WRITABLE_BOOK),
                    line(service.key("gui.no-recipes-name")), List.of(line(service.key("gui.no-recipes"))))));
        }
        for (int i = 0; i < view.orders().size() && i < ORDER_SLOTS.length; i++) {
            Order order = view.orders().get(i);
            int slot = ORDER_SLOTS[i];
            setButton(slot, GuiButton.of(orderIcon(order, settings, fameMultiplier), event -> {
                Player player = (Player) event.getWhoClicked();
                if (pendingReroll) {
                    pendingReroll = false;
                    render();
                    service.reroll(player, this, order, slot);
                } else {
                    service.deliver(player, this, order, slot);
                }
            }));
            if (order.vip() && !order.completed()) {
                ItemStack border = item(new ItemStack(service.catalog().vipBorder()), line(service.key("gui.vip-border")), List.of());
                for (int offset : new int[]{-1, 1, 8, 9, 10}) {
                    setButton(slot + offset, GuiButton.display(border));
                }
            }
        }
        setButton(GROUP_SLOT, GuiButton.of(groupIcon(), event -> service.deliverGroup((Player) event.getWhoClicked(), this, GROUP_SLOT)));
        setButton(CLOSE_SLOT, GuiButton.of(item(new ItemStack(Material.BARRIER), line(service.key("gui.close")), List.of()),
                event -> event.getWhoClicked().closeInventory()));
    }

    private ItemStack orderIcon(Order order, Settings settings, double fameMultiplier) {
        ItemStack base = service.catalog().icon(order.itemId(), viewer);
        List<Component> lore = new ArrayList<>();
        if (base == null) {
            lore.add(line(service.key("gui.order-suspended")));
            if (pendingReroll) {
                lore.add(line(service.key("gui.order-reroll-pick")));
            }
            return item(new ItemStack(Material.BARRIER), line(service.key("gui.order-name-suspended")), lore);
        }
        TagResolver name = service.itemName(order.itemId());
        Difficulty difficulty = service.difficultyOf(order);
        long money = service.rules().moneyPerItem(order.itemId(), difficulty);
        Component title;
        if (order.completed()) {
            title = line(service.key("gui.order-name-done"), name);
        } else if (order.vip()) {
            title = line(service.key("gui.order-name-vip"), name);
        } else {
            title = line(service.key("gui.order-name"), name);
        }
        lore.add(line(service.key("gui.order-difficulty"), Placeholder.unparsed("difficulty", difficulty.label())));
        lore.add(line(service.key("gui.order-progress"),
                Placeholder.unparsed("delivered", String.valueOf(order.delivered())),
                Placeholder.unparsed("required", String.valueOf(order.required()))));
        String fame = multiplier(fameMultiplier);
        if (order.vip()) {
            service.catalog().conditionLore(order, this, lore);
            lore.add(line(service.key("gui.order-money-vip"),
                    Placeholder.unparsed("money", money(service.rules().itemMoney(money, 1, true, fameMultiplier))),
                    Placeholder.unparsed("fame", fame)));
            lore.add(line(service.key("gui.order-reward"),
                    Placeholder.unparsed("stardust", String.valueOf(settings.vip().stardust())),
                    Placeholder.unparsed("fame", String.valueOf(settings.vip().fame()))));
        } else {
            lore.add(line(service.key("gui.order-money"),
                    Placeholder.unparsed("money", money(service.rules().itemMoney(money,
                            settings.itemMultipliers().get(OrderRules.NORMAL), false, fameMultiplier))),
                    Placeholder.unparsed("fame", fame)));
            lore.add(line(service.key("gui.order-quality"),
                    Placeholder.unparsed("silver", multiplier(settings.itemMultipliers().get(OrderRules.SILVER))),
                    Placeholder.unparsed("golden", multiplier(settings.itemMultipliers().get(OrderRules.GOLDEN)))));
            service.catalog().conditionLore(order, this, lore);
            OrderRules.Tier tier = service.rules().tier(difficulty);
            lore.add(line(service.key("gui.order-reward"),
                    Placeholder.unparsed("stardust", String.valueOf(tier.stardust())),
                    Placeholder.unparsed("fame", String.valueOf(tier.fame()))));
        }
        lore.add(Component.empty());
        if (order.completed()) {
            lore.add(line(service.key("gui.order-done")));
        } else if (pendingReroll) {
            lore.add(line(service.key("gui.order-reroll-pick")));
        } else {
            lore.add(line(service.key("gui.order-click")));
        }
        ItemStack icon = item(base, title, lore);
        if (order.completed()) {
            glint(icon);
        }
        return icon;
    }

    private ItemStack fameIcon() {
        FameLevel level = service.rules().level(view.fame());
        List<Component> lore = new ArrayList<>();
        lore.add(line(service.key("gui.fame-points"), Placeholder.unparsed("fame", String.format("%,d", view.fame()))));
        lore.add(line(service.key("gui.fame-multiplier"), Placeholder.unparsed("multiplier", multiplier(level.multiplier()))));
        Optional<FameLevel> next = service.rules().nextLevel(view.fame());
        lore.add(next.isPresent()
                ? line(service.key("gui.fame-next"), Placeholder.unparsed("next", next.get().name()),
                Placeholder.unparsed("remaining", String.format("%,d", next.get().fame() - view.fame())))
                : line(service.key("gui.fame-max")));
        return item(new ItemStack(Material.GOLDEN_APPLE), line(service.key("gui.fame-name"), Placeholder.unparsed("level", level.name())), lore);
    }

    private ItemStack infoIcon(Settings settings) {
        long done = view.orders().stream().filter(Order::completed).count();
        List<Component> lore = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            lore.add(line(service.key("gui.info-" + i)));
        }
        lore.add(Component.empty());
        lore.add(view.daily().bonusPaid() ? line(service.key("gui.info-bonus-paid"))
                : line(service.key("gui.info-bonus"),
                Placeholder.unparsed("done", String.valueOf(done)),
                Placeholder.unparsed("total", String.valueOf(view.orders().size())),
                Placeholder.unparsed("money", money(settings.allDoneMoney())),
                Placeholder.unparsed("stardust", String.valueOf(settings.allDoneStardust()))));
        lore.add(line(service.key("gui.info-cap"), Placeholder.unparsed("cap", String.valueOf(settings.stardustDailyCap()))));
        return item(new ItemStack(Material.BOOK), line(service.key("gui.info-name")), lore);
    }

    private ItemStack rerollIcon(Settings settings) {
        List<Component> lore = new ArrayList<>();
        lore.add(line(service.key("gui.reroll-left"),
                Placeholder.unparsed("left", view.daily().rerolled() ? "0" : "1"),
                Placeholder.unparsed("cost", money(settings.rerollCost()))));
        lore.add(line(service.key("gui.reroll-how")));
        if (pendingReroll) {
            lore.add(Component.empty());
            lore.add(line(service.key("gui.reroll-pending")));
        }
        ItemStack icon = item(new ItemStack(Material.HOPPER), line(service.key("gui.reroll-name")), lore);
        if (pendingReroll) {
            glint(icon);
        }
        return icon;
    }

    private ItemStack groupIcon() {
        GroupOrder group = view.group();
        if (group == null) {
            Optional<ZonedDateTime> next = service.rules().nextGroupStart(ZonedDateTime.now());
            List<Component> lore = List.of(next.isPresent()
                    ? line(service.key("gui.group-next"), Placeholder.unparsed("time",
                    next.get().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN) + " " + HOUR_MINUTE.format(next.get())))
                    : line(service.key("gui.group-no-schedule")));
            return item(new ItemStack(Material.CLOCK), line(service.key("gui.group-idle-name")), lore);
        }
        OrderRules.Group settings = service.rules().settings().group();
        ItemStack base = service.catalog().icon(group.itemId(), viewer);
        long money = service.rules().moneyPerItem(group.itemId(), service.groupDifficulty(group.itemId()).orElse(Difficulty.EASY));
        List<Component> lore = new ArrayList<>();
        lore.add(line(service.key("gui.group-progress"),
                Placeholder.unparsed("progress", String.format("%,d", group.progress())),
                Placeholder.unparsed("target", String.format("%,d", group.target()))));
        lore.add(line(service.key("gui.group-remaining"),
                Placeholder.unparsed("remaining", DurationFormat.humanize(group.endsAt() - System.currentTimeMillis()))));
        lore.add(line(service.key("gui.group-mine"), Placeholder.unparsed("mine", String.format("%,d", view.myContribution()))));
        lore.add(line(service.key("gui.group-money"), Placeholder.unparsed("money", money(service.rules().groupItemMoney(money,
                service.rules().settings().itemMultipliers().get(OrderRules.NORMAL))))));
        lore.add(line(service.key("gui.group-reward"),
                Placeholder.unparsed("min", String.valueOf(settings.minContribution())),
                Placeholder.unparsed("stardust", String.valueOf(settings.participationStardust()))));
        lore.add(Component.empty());
        lore.add(line(service.key(base == null ? "gui.order-suspended" : "gui.group-click")));
        return item(base == null ? new ItemStack(Material.BARRIER) : base,
                line(service.key("gui.group-name"), service.itemName(group.itemId())), lore);
    }

    /** One non-italic lore/name line from the messages file. */
    public Component line(String key, TagResolver... placeholders) {
        return messages.resolveRaw(key, placeholders).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static ItemStack item(ItemStack stack, Component name, List<Component> lore) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void glint(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            stack.setItemMeta(meta);
        }
    }

    private static String money(long amount) {
        return String.format("%,d", amount);
    }

    public static String multiplier(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
