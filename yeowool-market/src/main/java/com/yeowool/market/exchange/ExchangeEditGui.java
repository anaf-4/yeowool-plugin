package com.yeowool.market.exchange;

import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.market.exchange.ExchangeRepository.Entry;
import com.yeowool.market.exchange.ExchangeRepository.Limit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Staff editor for one 교환소 entry (new when {@code id} is null). Nothing the admin clicks with is taken:
 * items on the cursor are only copied as the reward or a cost. Closing without 저장 discards the draft.
 */
final class ExchangeEditGui extends YeowoolGui {

    /** Opens a chat prompt; the GUI reopens itself once the value arrives (negative = cancelled, keep the old one). */
    interface Prompter {
        void ask(Player player, String messageKey, LongConsumer onValue);
    }

    /** Writes the draft; {@code id} is null for a new entry. */
    interface Saver {
        void save(Player player, Integer id, ItemStack reward, List<ItemStack> costStacks, long money, long stardust, Limit limit, int limitCount);
    }

    private static final int REWARD_SLOT = 4;
    private static final int COST_FIRST = 18;
    private static final int COST_LAST = 44;
    private static final int MONEY_SLOT = 45;
    private static final int STARDUST_SLOT = 46;
    private static final int LIMIT_TYPE_SLOT = 47;
    private static final int LIMIT_COUNT_SLOT = 48;
    private static final int CANCEL_SLOT = 51;
    private static final int SAVE_SLOT = 53;
    private static final int MAX_COST_AMOUNT = 2304; // a full player inventory of 64-stacks

    private final MessageService messages;
    private final Prompter prompter;
    private final Saver saver;
    private final Integer id;
    private ItemStack reward;
    private final List<ExchangeService.Cost> costs;
    private long money;
    private long stardust;
    private Limit limit;
    private int limitCount;

    ExchangeEditGui(MessageService messages, Prompter prompter, Saver saver, Integer id, ItemStack reward,
                    List<ExchangeService.Cost> costs, long money, long stardust, Limit limit, int limitCount) {
        super(54, Component.text(id == null ? "교환 항목 추가" : "교환 항목 #" + id + " 수정", NamedTextColor.DARK_PURPLE));
        this.messages = messages;
        this.prompter = prompter;
        this.saver = saver;
        this.id = id;
        this.reward = reward.clone();
        this.costs = new ArrayList<>(costs);
        this.money = money;
        this.stardust = stardust;
        this.limit = limit;
        this.limitCount = Math.max(1, limitCount);
        render();
    }

    static ExchangeEditGui forEntry(MessageService messages, Prompter prompter, Saver saver, Entry entry) {
        return new ExchangeEditGui(messages, prompter, saver, entry.id(), entry.reward(), ExchangeService.mergedCosts(entry),
                entry.costMoney(), entry.costStardust(), entry.limit(), entry.limitCount());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        ItemStack cursor = event.getCursor();
        boolean holding = cursor != null && !cursor.getType().isAir();
        int step = stepOf(event.getClick());
        if (slot == REWARD_SLOT) {
            if (holding) {
                reward = cursor.clone();
            } else if (step != 0) {
                reward.setAmount(Math.clamp(reward.getAmount() + step, 1, reward.getMaxStackSize()));
            }
        } else if (slot >= COST_FIRST && slot <= COST_LAST) {
            clickCost(slot - COST_FIRST, holding ? cursor : null, event.getClick(), step);
        } else if (slot == MONEY_SLOT || slot == STARDUST_SLOT) {
            boolean isMoney = slot == MONEY_SLOT;
            if (event.getClick().isRightClick()) {
                if (isMoney) {
                    money = 0;
                } else {
                    stardust = 0;
                }
            } else {
                player.closeInventory();
                prompter.ask(player, isMoney ? "exchange.ask-money" : "exchange.ask-stardust", value -> {
                    if (value >= 0 && isMoney) {
                        money = value;
                    } else if (value >= 0) {
                        stardust = value;
                    }
                    render();
                    open(player);
                });
                return;
            }
        } else if (slot == LIMIT_TYPE_SLOT) {
            limit = Limit.values()[(limit.ordinal() + 1) % Limit.values().length];
        } else if (slot == LIMIT_COUNT_SLOT && step != 0) {
            limitCount = Math.clamp(limitCount + step, 1, 9999);
        } else if (slot == CANCEL_SLOT) {
            player.closeInventory();
            messages.send(player, "exchange.edit-cancelled");
            return;
        } else if (slot == SAVE_SLOT) {
            save(player);
            return;
        }
        render();
    }

    private void clickCost(int index, ItemStack cursor, ClickType click, int step) {
        if (cursor != null) {
            for (int i = 0; i < costs.size(); i++) {
                if (costs.get(i).sample().isSimilar(cursor)) {
                    costs.set(i, withAmount(costs.get(i), costs.get(i).amount() + cursor.getAmount()));
                    return;
                }
            }
            if (costs.size() <= COST_LAST - COST_FIRST) {
                ItemStack sample = cursor.clone();
                sample.setAmount(1);
                costs.add(new ExchangeService.Cost(sample, cursor.getAmount()));
            }
            return;
        }
        if (index >= costs.size()) {
            return;
        }
        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            costs.remove(index);
            return;
        }
        int amount = costs.get(index).amount() + step;
        if (amount <= 0) {
            costs.remove(index);
        } else {
            costs.set(index, withAmount(costs.get(index), amount));
        }
    }

    private static ExchangeService.Cost withAmount(ExchangeService.Cost cost, int amount) {
        return new ExchangeService.Cost(cost.sample(), Math.min(amount, MAX_COST_AMOUNT));
    }

    private static int stepOf(ClickType click) {
        return switch (click) {
            case LEFT -> 1;
            case RIGHT -> -1;
            case SHIFT_LEFT -> 10;
            case SHIFT_RIGHT -> -10;
            default -> 0;
        };
    }

    private void save(Player player) {
        if (costs.isEmpty() && money == 0 && stardust == 0 && limit == Limit.NONE) {
            messages.send(player, "exchange.free-unlimited");
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (ExchangeService.Cost cost : costs) {
            int left = cost.amount();
            while (left > 0) {
                ItemStack stack = cost.sample().clone();
                stack.setAmount(Math.min(left, stack.getMaxStackSize()));
                left -= stack.getAmount();
                stacks.add(stack);
            }
        }
        player.closeInventory();
        saver.save(player, id, reward.clone(), stacks, money, stardust, limit, limit == Limit.NONE ? 0 : limitCount);
    }

    private void render() {
        getInventory().clear();
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY, List.of());
        for (int slot = 0; slot < 18; slot++) {
            getInventory().setItem(slot, filler);
        }
        for (int slot = 45; slot < 54; slot++) {
            getInventory().setItem(slot, filler);
        }

        ItemStack rewardIcon = reward.clone();
        appendLore(rewardIcon, List.of(Component.empty(),
                line("[보상] 커서에 아이템을 들고 클릭: 보상 바꾸기", NamedTextColor.YELLOW),
                line("좌/우클릭 ±1, Shift ±10 — 보상 개수", NamedTextColor.GRAY)));
        getInventory().setItem(REWARD_SLOT, rewardIcon);
        getInventory().setItem(13, item(Material.OAK_SIGN, "아래 칸: 필요한 재료", NamedTextColor.YELLOW, List.of(
                line("내 인벤토리에서 아이템을 집어 빈 칸 클릭: 재료 추가(든 개수만큼)", NamedTextColor.GRAY),
                line("재료 좌/우클릭 ±1, Shift ±10, Q: 삭제", NamedTextColor.GRAY),
                line("아이템은 복사만 되고 가져가지 않습니다", NamedTextColor.GRAY))));

        for (int i = 0; i < costs.size(); i++) {
            ExchangeService.Cost cost = costs.get(i);
            ItemStack icon = cost.sample().clone();
            icon.setAmount(Math.min(cost.amount(), icon.getMaxStackSize()));
            appendLore(icon, List.of(Component.empty(), line("필요 개수: " + cost.amount() + "개", NamedTextColor.AQUA)));
            getInventory().setItem(COST_FIRST + i, icon);
        }

        getInventory().setItem(MONEY_SLOT, item(Material.GOLD_INGOT, "온: " + (money == 0 ? "없음" : String.format("%,d", money)),
                NamedTextColor.GOLD, List.of(line("좌클릭: 채팅으로 입력, 우클릭: 없음", NamedTextColor.GRAY))));
        getInventory().setItem(STARDUST_SLOT, item(Material.NETHER_STAR, "별조각: " + (stardust == 0 ? "없음" : String.format("%,d", stardust)),
                NamedTextColor.LIGHT_PURPLE, List.of(line("좌클릭: 채팅으로 입력, 우클릭: 없음", NamedTextColor.GRAY))));
        getInventory().setItem(LIMIT_TYPE_SLOT, item(Material.CLOCK, "교환 제한: " + limit.label(), NamedTextColor.AQUA,
                List.of(line("클릭: 없음 → 일일 → 주간", NamedTextColor.GRAY))));
        if (limit != Limit.NONE) {
            getInventory().setItem(LIMIT_COUNT_SLOT, item(Material.PAPER, limit.label() + " " + limitCount + "회까지", NamedTextColor.AQUA,
                    List.of(line("좌/우클릭 ±1, Shift ±10", NamedTextColor.GRAY))));
        }
        getInventory().setItem(CANCEL_SLOT, item(Material.BARRIER, "취소", NamedTextColor.RED, List.of()));
        getInventory().setItem(SAVE_SLOT, item(Material.EMERALD, "저장", NamedTextColor.GREEN, List.of()));
    }

    private static ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(line(name, color));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static void appendLore(ItemStack item, List<Component> extra) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.addAll(extra);
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
