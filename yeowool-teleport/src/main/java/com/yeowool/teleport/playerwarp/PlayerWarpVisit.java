package com.yeowool.teleport.playerwarp;

import com.yeowool.teleport.model.PlayerWarp;
import com.yeowool.teleport.util.PlayerWarpCurrency;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/** Shared "visit this warp" flow — used by the browse/my/saved GUIs alike. Admission (if any) is charged only once the confirm screen is accepted. */
public final class PlayerWarpVisit {

    private PlayerWarpVisit() {
    }

    public static void begin(PlayerWarpContext ctx, Player player, PlayerWarp warp) {
        if (warp.price() > 0 && !warp.owner().equals(player.getUniqueId())) {
            new PlayerWarpConfirmGui(ctx, warp).open(player);
            return;
        }
        teleport(ctx, player, warp, false);
    }

    /** Called directly by {@link PlayerWarpConfirmGui} on accept, after the fee (if any) has already been charged. */
    public static void teleport(PlayerWarpContext ctx, Player player, PlayerWarp warp, boolean chargeAdmission) {
        var location = warp.toLocation();
        if (location == null) {
            ctx.messages().send(player, "warp.world-not-found");
            return;
        }
        long cooldown = ctx.teleportService().remainingCooldownSeconds(player);
        if (cooldown > 0) {
            ctx.messages().send(player, "general.cooldown", Placeholder.unparsed("seconds", String.valueOf(cooldown)));
            return;
        }
        if (ctx.config().checkSafeTeleport()) {
            var safe = PlayerWarpSafety.findSafe(location);
            if (safe.isEmpty()) {
                ctx.messages().send(player, "playerwarp.unsafe-destination");
                return;
            }
            location = safe.get();
        }
        if (chargeAdmission && warp.price() > 0) {
            if (!PlayerWarpCurrency.has(ctx, player.getUniqueId(), warp.price())) {
                ctx.messages().send(player, "playerwarp.insufficient-funds", Placeholder.unparsed("cost", String.format("%,d", warp.price())));
                return;
            }
            PlayerWarpCurrency.pay(ctx, player.getUniqueId(), warp.owner(), warp.price(), "플레이어 워프 입장료 (" + warp.effectiveDisplayName() + ")");
        }
        ctx.warps().put(warp.withVisits(warp.visits() + 1));
        ctx.teleportService().requestTeleport(player, location);
    }
}
