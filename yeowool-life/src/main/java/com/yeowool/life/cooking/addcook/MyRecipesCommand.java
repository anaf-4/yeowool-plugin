package com.yeowool.life.cooking.addcook;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /레시피} — opens {@link MyRecipesGui} with every recipe the player currently owns
 * (holds the LuckPerms permission {@code addcook.recipe.<id>} for). See
 * {@link AddCookRecipeIndex}'s class doc for how "owning" a recipe actually works in AddCook.
 */
public final class MyRecipesCommand implements CommandExecutor {

    private final MessageService messages;

    public MyRecipesCommand(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }

        List<AddCookRecipeIndex.RecipeEntry> owned = new ArrayList<>();
        for (AddCookRecipeIndex.RecipeEntry recipe : AddCookRecipeIndex.load()) {
            if (player.hasPermission(recipe.permission())) {
                owned.add(recipe);
            }
        }

        if (owned.isEmpty()) {
            player.sendMessage(Component.text("아직 보유한 레시피가 없습니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            return true;
        }
        new MyRecipesGui(player, owned, 0).open(player);
        return true;
    }
}
