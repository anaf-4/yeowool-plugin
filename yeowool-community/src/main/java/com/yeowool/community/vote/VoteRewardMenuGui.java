package com.yeowool.community.vote;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Map;

/** {@code /추천보상설정}'s hub: the every-vote reward on top, one icon per 누적 milestone below. */
final class VoteRewardMenuGui extends YeowoolGui {

    private static final int EVERY_SLOT = 4;
    private static final int HELP_SLOT = 8;
    private static final int FIRST_MILESTONE_SLOT = 18;

    VoteRewardMenuGui(VoteAdminCommand admin, Map<Integer, VoteRepository.Reward> rewards) {
        super(54, Component.text("추천 보상 설정", NamedTextColor.DARK_GREEN));

        int slot = FIRST_MILESTONE_SLOT;
        for (VoteRepository.Reward reward : rewards.values()) {
            int threshold = reward.threshold();
            boolean every = threshold == VoteRules.EVERY_VOTE;
            if (!every && slot >= 54) {
                continue;
            }
            int target = every ? EVERY_SLOT : slot++;
            setButton(target, GuiButton.of(
                    icon(every ? Material.EMERALD : Material.NETHER_STAR,
                            VoteRewardEditorGui.label(threshold),
                            "보상: " + VoteService.summary(reward), "클릭하여 수정"),
                    event -> admin.openEditor((Player) event.getWhoClicked(), threshold)));
        }
        setButton(HELP_SLOT, GuiButton.display(icon(Material.BOOK, "누적 보상 추가/삭제",
                "/추천보상설정 누적추가 <횟수>", "/추천보상설정 누적삭제 <횟수>")));
    }

    static ItemStack icon(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(Arrays.stream(lore)
                .map(line -> (Component) Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                .toList());
        stack.setItemMeta(meta);
        return stack;
    }
}
