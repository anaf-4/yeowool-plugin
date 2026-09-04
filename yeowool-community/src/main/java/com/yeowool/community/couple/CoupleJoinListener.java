package com.yeowool.community.couple;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Notifies the joining player's partner, if currently online — mirrors {@code FriendJoinListener}. */
public final class CoupleJoinListener implements Listener {

    private final CoupleManager coupleManager;

    public CoupleJoinListener(CoupleManager coupleManager) {
        this.coupleManager = coupleManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        coupleManager.partnerOf(joined.getUniqueId()).ifPresent(partnerId -> {
            Player partner = Bukkit.getPlayer(partnerId);
            if (partner != null) {
                partner.sendMessage(Component.text("💑 " + joined.getName() + "님이 접속했습니다.", NamedTextColor.LIGHT_PURPLE));
            }
        });
    }
}
