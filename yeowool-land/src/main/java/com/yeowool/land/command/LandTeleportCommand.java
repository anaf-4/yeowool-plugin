package com.yeowool.land.command;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.land.LandManager;
import com.yeowool.land.model.ChunkKey;
import com.yeowool.land.model.Land;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.Optional;

/**
 * {@code /토지이동} — teleports the player to their own land (mirrors
 * {@code /상점이동}'s cross-server pattern). Land only physically exists in
 * one backend server's world ({@code land-teleport.land-server-id}, town by
 * default); running this on a different server sends the player there first
 * via Velocity's BungeeCord-compatible "Connect" channel and marks
 * {@link #PENDING_SETTING_KEY} on their {@code PlayerData} — {@link
 * LandTeleportJoinListener}, registered only on the land server, finishes
 * the teleport once they actually join there.
 */
public final class LandTeleportCommand implements CommandExecutor {

    public static final String PENDING_SETTING_KEY = "land.pending-land-teleport";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final LandManager landManager;
    private final MessageService messages;
    private final String landServerId;
    private final String thisServerId;

    public LandTeleportCommand(JavaPlugin plugin, YeowoolCoreAPI core, LandManager landManager, MessageService messages,
                                String landServerId, String thisServerId) {
        this.plugin = plugin;
        this.core = core;
        this.landManager = landManager;
        this.messages = messages;
        this.landServerId = landServerId;
        this.thisServerId = thisServerId;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (!thisServerId.equals(landServerId)) {
            core.playerData().getOnline(player.getUniqueId()).setSetting(PENDING_SETTING_KEY, "true");
            sendToServer(player, landServerId);
            return true;
        }
        teleportToLand(player);
        return true;
    }

    /** Package-private so {@link LandTeleportJoinListener} (on the land server) reuses the exact same lookup. */
    void teleportToLand(Player player) {
        Optional<Land> land = landManager.getLandOwnedBy(player.getUniqueId());
        if (land.isEmpty()) {
            messages.send(player, "land.no-land");
            return;
        }
        Optional<ChunkKey> chunk = land.get().getChunks().stream()
                .min(Comparator.comparingInt(ChunkKey::x).thenComparingInt(ChunkKey::z));
        if (chunk.isEmpty()) {
            messages.send(player, "land.no-land");
            return;
        }
        World world = Bukkit.getWorld(chunk.get().world());
        if (world == null) {
            messages.send(player, "land.no-land");
            return;
        }
        int x = (chunk.get().x() << 4) + 8;
        int z = (chunk.get().z() << 4) + 8;
        int y = world.getHighestBlockYAt(x, z) + 1;
        player.teleportAsync(new Location(world, x + 0.5, y, z + 0.5));
        messages.send(player, "land.teleported");
    }

    private void sendToServer(Player player, String serverName) {
        try {
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteArray);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(plugin, "BungeeCord", byteArray.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("토지 서버로 이동 요청 전송 실패: " + e.getMessage());
        }
    }
}
