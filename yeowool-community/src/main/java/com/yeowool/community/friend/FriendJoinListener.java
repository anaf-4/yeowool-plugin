package com.yeowool.community.friend;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Notifies whichever of the joining player's friends are currently online. */
public final class FriendJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final FriendManager friendManager;

    public FriendJoinListener(JavaPlugin plugin, FriendManager friendManager) {
        this.plugin = plugin;
        this.friendManager = friendManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        friendManager.friendsOf(joined.getUniqueId()).thenAccept(friends ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (var friendUuid : friends) {
                        Player friend = Bukkit.getPlayer(friendUuid);
                        if (friend != null) {
                            friend.sendMessage(Component.text(joined.getName() + "님이 접속했습니다.", NamedTextColor.GREEN));
                        }
                    }
                }));
    }
}
