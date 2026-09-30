package com.yeowool.life.donation;

import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.life.donation.DonationRules.Candidate;
import com.yeowool.life.donation.DonationRules.GoalSpec;
import com.yeowool.life.surprise.SurpriseEventType;
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
import java.util.function.Consumer;

/**
 * Staff editor for the next week's project ({@code /기부관리 예약}). Items on the cursor are only copied as
 * goals, never taken; the name, targets and points come from chat. Closing without 저장 discards the draft.
 */
final class DonationEditGui extends YeowoolGui {

    /** Opens a chat prompt; the GUI reopens itself once the text arrives (null = cancelled, keep the old value). */
    interface Prompter {
        void ask(Player player, String messageKey, Consumer<String> onText);
    }

    /** Writes the reservation; {@code candidate} null deletes it. */
    interface Saver {
        void save(Player player, Candidate candidate);
    }

    private static final int NAME_SLOT = 2;
    private static final int BUFF_SLOT = 6;
    private static final int HELP_SLOT = 13;
    private static final int GOAL_FIRST = 20;
    private static final int MAX_GOALS = 5;
    private static final int DELETE_SLOT = 45;
    private static final int CANCEL_SLOT = 49;
    private static final int SAVE_SLOT = 53;
    private static final int DEFAULT_TARGET = 1000;

    private final MessageService messages;
    private final Prompter prompter;
    private final Saver saver;
    private String name;
    private SurpriseEventType buff;
    private final List<GoalSpec> goals;

    DonationEditGui(MessageService messages, Prompter prompter, Saver saver, Candidate existing) {
        super(54, Component.text("다음 기부 프로젝트 예약", NamedTextColor.DARK_GREEN));
        this.messages = messages;
        this.prompter = prompter;
        this.saver = saver;
        this.name = existing == null ? "" : existing.name();
        this.buff = existing == null ? null : existing.buff();
        this.goals = existing == null ? new ArrayList<>() : new ArrayList<>(existing.goals());
        render();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        ItemStack cursor = event.getCursor();
        boolean holding = cursor != null && !cursor.getType().isAir();
        if (slot == NAME_SLOT) {
            ask(player, "donation.admin.ask-name", text -> name = text.length() > 64 ? text.substring(0, 64) : text);
            return;
        } else if (slot == BUFF_SLOT) {
            SurpriseEventType[] types = SurpriseEventType.values();
            buff = buff == null ? types[0] : buff.ordinal() + 1 < types.length ? types[buff.ordinal() + 1] : null;
        } else if (slot >= GOAL_FIRST && slot < GOAL_FIRST + MAX_GOALS) {
            if (clickGoal(player, slot - GOAL_FIRST, holding ? cursor : null, event.getClick())) {
                return;
            }
        } else if (slot == DELETE_SLOT) {
            player.closeInventory();
            saver.save(player, null);
            return;
        } else if (slot == CANCEL_SLOT) {
            player.closeInventory();
            messages.send(player, "donation.admin.edit-cancelled");
            return;
        } else if (slot == SAVE_SLOT) {
            if (name.isBlank() || goals.isEmpty()) {
                messages.send(player, "donation.admin.edit-incomplete");
                return;
            }
            player.closeInventory();
            saver.save(player, new Candidate(name, buff, List.copyOf(goals)));
            return;
        }
        render();
    }

    /** True if a chat prompt took over (the GUI reopens itself afterwards). */
    private boolean clickGoal(Player player, int index, ItemStack cursor, ClickType click) {
        if (cursor != null) {
            String key = DonationService.keyOf(cursor);
            if (key == null) {
                messages.send(player, "donation.admin.named-item");
                return false;
            }
            ItemStack sample = key.contains(":") ? cursor.clone() : new ItemStack(cursor.getType());
            sample.setAmount(1);
            GoalSpec goal = index < goals.size()
                    ? new GoalSpec(key, sample.serializeAsBytes(), goals.get(index).target(), goals.get(index).points())
                    : new GoalSpec(key, sample.serializeAsBytes(), DEFAULT_TARGET, 1);
            if (index < goals.size()) {
                goals.set(index, goal);
            } else {
                goals.add(goal);
            }
            return false;
        }
        if (index >= goals.size()) {
            return false;
        }
        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            goals.remove(index);
            return false;
        }
        boolean target = !click.isRightClick();
        ask(player, target ? "donation.admin.ask-target" : "donation.admin.ask-points", text -> {
            try {
                int value = Integer.parseInt(text.replace(",", ""));
                if (value < 1) {
                    throw new NumberFormatException();
                }
                GoalSpec old = goals.get(index);
                goals.set(index, new GoalSpec(old.itemKey(), old.display(), target ? value : old.target(), target ? old.points() : value));
            } catch (NumberFormatException e) {
                messages.send(player, "donation.admin.bad-number");
            }
        });
        return true;
    }

    private void ask(Player player, String key, Consumer<String> apply) {
        player.closeInventory();
        prompter.ask(player, key, text -> {
            if (text != null && !text.isBlank()) {
                apply.accept(text.trim());
            }
            render();
            open(player);
        });
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
        getInventory().setItem(NAME_SLOT, item(Material.NAME_TAG, "이름: " + (name.isBlank() ? "(없음)" : name), NamedTextColor.YELLOW,
                List.of(line("클릭: 채팅으로 입력", NamedTextColor.GRAY))));
        getInventory().setItem(BUFF_SLOT, item(Material.EXPERIENCE_BOTTLE, "달성 버프: " + (buff == null ? "자동(순환)"
                        : buff.label() + " " + DonationService.multiplier(DonationRules.buffMultiplier(buff)) + "배"),
                NamedTextColor.AQUA, List.of(line("클릭: 자동 → 토지 경험치 → 작물 수확량 → 보물지도 발견 → 직업 경험치", NamedTextColor.GRAY))));
        getInventory().setItem(HELP_SLOT, item(Material.OAK_SIGN, "아래 칸: 기부 품목 (최대 5개)", NamedTextColor.YELLOW, List.of(
                line("내 인벤토리에서 아이템을 집어 칸 클릭: 품목 추가/바꾸기", NamedTextColor.GRAY),
                line("품목 좌클릭: 목표 수량 입력, 우클릭: 1개당 점수 입력, Q: 삭제", NamedTextColor.GRAY),
                line("아이템은 복사만 되고 가져가지 않습니다", NamedTextColor.GRAY),
                line("바닐라는 새 아이템 그대로만 가능 (이름·설명·인챈트·내구도 X)", NamedTextColor.GRAY))));
        for (int i = 0; i < MAX_GOALS; i++) {
            if (i < goals.size()) {
                GoalSpec goal = goals.get(i);
                ItemStack icon = DonationService.icon(goal.itemKey(), goal.display());
                appendLore(icon, List.of(Component.empty(),
                        line("목표: " + String.format("%,d", goal.target()) + "개", NamedTextColor.AQUA),
                        line("1개당 점수: " + goal.points() + "점", NamedTextColor.AQUA),
                        line(goal.itemKey(), NamedTextColor.DARK_GRAY)));
                getInventory().setItem(GOAL_FIRST + i, icon);
            } else {
                getInventory().setItem(GOAL_FIRST + i, item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "빈 품목 칸", NamedTextColor.GRAY,
                        List.of(line("아이템을 들고 클릭해 추가", NamedTextColor.GRAY))));
            }
        }
        getInventory().setItem(DELETE_SLOT, item(Material.LAVA_BUCKET, "예약 삭제", NamedTextColor.RED,
                List.of(line("다음 프로젝트를 후보 목록에서 무작위로 고르게 합니다", NamedTextColor.GRAY))));
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
