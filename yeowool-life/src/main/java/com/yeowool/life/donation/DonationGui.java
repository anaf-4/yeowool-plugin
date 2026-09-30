package com.yeowool.life.donation;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.DurationFormat;
import com.yeowool.life.donation.DonationRepository.Goal;
import com.yeowool.life.donation.DonationRepository.Project;
import com.yeowool.life.surprise.SurpriseEventType;
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
import java.util.Map;

/** The {@code /기부} window: the project, each goal's progress (click to donate), my score and running buffs. */
final class DonationGui extends YeowoolGui {

    private static final int BUFF_SLOT = 0;
    private static final int INFO_SLOT = 4;
    private static final int CENTER_SLOT = 22;
    private static final int RANK_SLOT = 48;
    private static final int CLOSE_SLOT = 49;
    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private final DonationService service;
    private final MessageService messages;
    private final DonationService.View view;

    DonationGui(DonationService service, MessageService messages, DonationService.View view) {
        super(54, messages.resolveRaw("donation.gui.title"));
        this.service = service;
        this.messages = messages;
        this.view = view;
        render();
    }

    DonationService.View view() {
        return view;
    }

    void showBusy(int slot) {
        setButton(slot, GuiButton.display(item(new ItemStack(Material.CLOCK), line("donation.gui.busy"), List.of())));
    }

    private void render() {
        if (!view.buffs().isEmpty()) {
            setButton(BUFF_SLOT, GuiButton.display(buffIcon()));
        }
        Project project = view.project();
        if (project == null) {
            ZonedDateTime next = DonationRules.nextPeriodStart(ZonedDateTime.now(), service.settings().startDay());
            setButton(CENTER_SLOT, GuiButton.display(item(new ItemStack(Material.CLOCK), line("donation.gui.none-name"),
                    List.of(line("donation.gui.none-next", Placeholder.unparsed("time",
                            next.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN) + " " + HOUR_MINUTE.format(next)))))));
        } else {
            setButton(INFO_SLOT, GuiButton.display(infoIcon(project)));
            List<Goal> goals = project.goals();
            int first = CENTER_SLOT - (goals.size() - 1) / 2;
            for (int i = 0; i < goals.size(); i++) {
                Goal goal = goals.get(i);
                int slot = first + i;
                setButton(slot, GuiButton.of(goalIcon(goal), event -> service.donate((Player) event.getWhoClicked(), this, goal,
                        event.getClick().isShiftClick() || event.getClick().isRightClick(), slot)));
            }
        }
        setButton(RANK_SLOT, GuiButton.of(item(new ItemStack(Material.GOLDEN_HELMET), line("donation.gui.rank"), List.of()), event -> {
            event.getWhoClicked().closeInventory();
            service.ranking(event.getWhoClicked());
        }));
        setButton(CLOSE_SLOT, GuiButton.of(item(new ItemStack(Material.BARRIER), line("donation.gui.close"), List.of()),
                event -> event.getWhoClicked().closeInventory()));
    }

    private ItemStack infoIcon(Project project) {
        DonationRules.Settings settings = service.settings();
        int percent = DonationRules.overallPercent(project.goals());
        List<Component> lore = new ArrayList<>();
        lore.add(line("donation.gui.info-progress", Placeholder.unparsed("bar", DonationRules.bar(percent, 100, 20)),
                Placeholder.unparsed("percent", String.valueOf(percent))));
        lore.add(line("donation.gui.info-remaining",
                Placeholder.unparsed("remaining", DurationFormat.humanize(Math.max(0, project.endsAt() - System.currentTimeMillis())))));
        lore.add(line("donation.gui.info-mine", Placeholder.unparsed("points", String.format("%,d", view.myPoints()))));
        lore.add(Component.empty());
        lore.add(line("donation.gui.info-buff", Placeholder.unparsed("buff", project.buff().label()),
                Placeholder.unparsed("multiplier", DonationService.multiplier(DonationRules.buffMultiplier(project.buff()))),
                Placeholder.unparsed("hours", String.valueOf(settings.buffHours()))));
        lore.add(line("donation.gui.info-reward", Placeholder.unparsed("stardust", String.valueOf(settings.participationStardust())),
                Placeholder.unparsed("min", String.valueOf(settings.minScore()))));
        List<Long> ranks = settings.rankStardust();
        lore.add(line("donation.gui.info-rank",
                Placeholder.unparsed("first", String.valueOf(ranks.size() > 0 ? ranks.get(0) : 0)),
                Placeholder.unparsed("second", String.valueOf(ranks.size() > 1 ? ranks.get(1) : 0)),
                Placeholder.unparsed("third", String.valueOf(ranks.size() > 2 ? ranks.get(2) : 0))));
        lore.add(line("donation.gui.info-money", Placeholder.unparsed("money", String.valueOf(settings.moneyPerPoint()))));
        return item(new ItemStack(Material.BEACON), line("donation.gui.info-name", Placeholder.unparsed("project", project.name())), lore);
    }

    private ItemStack goalIcon(Goal goal) {
        boolean done = goal.progress() >= goal.target();
        TagResolver name = Placeholder.component("item", DonationService.itemName(goal.itemKey()));
        List<Component> lore = new ArrayList<>();
        lore.add(line("donation.gui.goal-bar", Placeholder.unparsed("bar", DonationRules.bar(goal.progress(), goal.target(), 20))));
        lore.add(line("donation.gui.goal-progress",
                Placeholder.unparsed("progress", String.format("%,d", goal.progress())),
                Placeholder.unparsed("target", String.format("%,d", goal.target()))));
        lore.add(line("donation.gui.goal-points", Placeholder.unparsed("points", String.valueOf(goal.points())),
                Placeholder.unparsed("money", String.format("%,d", goal.points() * service.settings().moneyPerPoint()))));
        lore.add(Component.empty());
        lore.add(line(done ? "donation.gui.goal-done" : "donation.gui.goal-click"));
        ItemStack icon = item(DonationService.icon(goal.itemKey(), goal.display()),
                line(done ? "donation.gui.goal-name-done" : "donation.gui.goal-name", name), lore);
        if (done) {
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setEnchantmentGlintOverride(true);
                icon.setItemMeta(meta);
            }
        }
        return icon;
    }

    private ItemStack buffIcon() {
        List<Component> lore = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<SurpriseEventType, Long> buff : view.buffs().entrySet()) {
            lore.add(line("donation.gui.buff-line", Placeholder.unparsed("buff", buff.getKey().label()),
                    Placeholder.unparsed("multiplier", DonationService.multiplier(DonationRules.buffMultiplier(buff.getKey()))),
                    Placeholder.unparsed("remaining", DurationFormat.humanize(Math.max(0, buff.getValue() - now)))));
        }
        return item(new ItemStack(Material.EXPERIENCE_BOTTLE), line("donation.gui.buff-name"), lore);
    }

    private Component line(String key, TagResolver... placeholders) {
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
}
