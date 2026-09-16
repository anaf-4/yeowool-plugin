package com.yeowool.quest;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.function.Consumer;

/** Shown when an NPC has more than one quest still available for this player — pick one to start its dialogue. */
public final class QuestListGui extends YeowoolGui {

    public QuestListGui(List<Quest> quests, Consumer<Quest> onPick) {
        super(Math.max(9, ((quests.size() + 8) / 9) * 9), Component.text("퀘스트 목록", NamedTextColor.GOLD));

        int slot = 0;
        for (Quest quest : quests) {
            setButton(slot++, GuiButton.of(icon(quest), event -> {
                ((Player) event.getWhoClicked()).closeInventory();
                onPick.accept(quest);
            }));
        }
    }

    private ItemStack icon(Quest quest) {
        ItemStack stack = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(quest.name(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("클릭하여 대화 시작", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }
}
