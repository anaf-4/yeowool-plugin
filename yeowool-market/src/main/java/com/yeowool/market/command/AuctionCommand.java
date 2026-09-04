package com.yeowool.market.command;

import com.yeowool.core.api.model.CurrencyType;
import com.yeowool.core.util.TabCompletions;
import com.yeowool.market.auction.AuctionContext;
import com.yeowool.market.auction.AuctionGui;
import com.yeowool.market.auction.AuctionListing;
import com.yeowool.market.auction.MyAuctionsGui;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

/**
 * {@code /경매} opens the browse GUI (v0id AuctionHouse pack reskin);
 * {@code /경매 등록 <시작가> <즉시구매가|0> <시간(분)>} lists the item in the
 * player's main hand; {@code /경매 내경매} opens {@link MyAuctionsGui}.
 */
public final class AuctionCommand implements CommandExecutor, TabCompleter {

    private final AuctionContext ctx;

    public AuctionCommand(AuctionContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            ctx.messages().send(sender, "general.player-only");
            return true;
        }

        if (args.length == 0) {
            new AuctionGui(ctx, 0, null).open(player);
            return true;
        }

        switch (args[0]) {
            case "등록" -> register(player, args);
            case "검색" -> search(player, args);
            case "내경매" -> new MyAuctionsGui(ctx, player, 0).open(player);
            default -> ctx.messages().send(player, "auction.usage");
        }
        return true;
    }

    private void search(Player player, String[] args) {
        if (args.length < 2) {
            ctx.messages().send(player, "auction.search-usage");
            return;
        }
        String keyword = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        new AuctionGui(ctx, 0, keyword).open(player);
    }

    private void register(Player player, String[] args) {
        if (args.length != 4 && args.length != 5) {
            ctx.messages().send(player, "auction.register-usage");
            return;
        }
        long startingBid;
        long buyNowPrice;
        int minutes;
        try {
            startingBid = Long.parseLong(args[1]);
            buyNowPrice = Long.parseLong(args[2]);
            minutes = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            ctx.messages().send(player, "auction.register-usage");
            return;
        }
        if (startingBid <= 0 || buyNowPrice < 0 || minutes <= 0) {
            ctx.messages().send(player, "auction.register-usage");
            return;
        }
        if (buyNowPrice > 0 && buyNowPrice < startingBid) {
            ctx.messages().send(player, "auction.buy-now-below-starting");
            return;
        }
        CurrencyType currency = CurrencyType.ON;
        if (args.length == 5) {
            currency = switch (args[4]) {
                case "온" -> CurrencyType.ON;
                case "캐시" -> CurrencyType.CASH;
                default -> null;
            };
            if (currency == null) {
                ctx.messages().send(player, "auction.invalid-currency");
                return;
            }
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            ctx.messages().send(player, "auction.nothing-to-register");
            return;
        }

        player.getInventory().setItemInMainHand(null);
        AuctionListing listing = ctx.manager().create(player.getUniqueId(), hand, startingBid, buyNowPrice, minutes, currency);
        ctx.messages().send(player, "auction.register-success",
                Placeholder.unparsed("item", listing.item().getType().name()),
                Placeholder.unparsed("price", String.format("%,d", startingBid)),
                Placeholder.unparsed("currency", currency.displayName()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("등록", "검색", "내경매"), args[0]);
        }
        if (args.length == 5 && args[0].equals("등록")) {
            return TabCompletions.filter(List.of("온", "캐시"), args[4]);
        }
        return List.of();
    }
}
