package com.yeowool.market.citizens;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
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

/**
 * {@code /상점이동} — teleports the player to the shop district's fixed
 * coordinates ({@code npc-shop.location.*} in config.yml), no NPC lookup
 * involved (unlike per-shop teleports, see {@link ShopNpcTeleporter}).
 *
 * <p>That location only physically exists on one backend server
 * ({@code npc-shop.npc-server-id}, lobby by default). Running this on a
 * different server sends the player there first via Velocity's
 * BungeeCord-compatible "Connect" plugin channel and marks
 * {@link #PENDING_SETTING_KEY} on their {@link com.yeowool.core.api.model.PlayerData};
 * {@link ShopTeleportJoinListener} — registered only on that server — finishes
 * the teleport once they actually join there.
 */
public final class ShopLocationCommand implements CommandExecutor {

    public static final String PENDING_SETTING_KEY = "market.pending-shop-teleport";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final String npcServerId;
    private final String thisServerId;

    public ShopLocationCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages,
                                String npcServerId, String thisServerId) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.npcServerId = npcServerId;
        this.thisServerId = thisServerId;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (!thisServerId.equals(npcServerId)) {
            core.playerData().getOnline(player.getUniqueId()).setSetting(PENDING_SETTING_KEY, "true");
            sendToServer(player, npcServerId);
            return true;
        }
        teleportToDistrict(player);
        return true;
    }

    /** Package-private so {@link ShopTeleportJoinListener} reuses the exact same lookup. */
    void teleportToDistrict(Player player) {
        Location location = districtLocation();
        if (location == null) {
            messages.send(player, "npcshop.location-not-set");
            return;
        }
        player.teleportAsync(location);
        messages.send(player, "npcshop.teleported");
    }

    private Location districtLocation() {
        String worldName = plugin.getConfig().getString("npc-shop.location.world");
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        double x = plugin.getConfig().getDouble("npc-shop.location.x");
        double y = plugin.getConfig().getDouble("npc-shop.location.y");
        double z = plugin.getConfig().getDouble("npc-shop.location.z");
        float yaw = (float) plugin.getConfig().getDouble("npc-shop.location.yaw");
        float pitch = (float) plugin.getConfig().getDouble("npc-shop.location.pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void sendToServer(Player player, String serverName) {
        try {
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteArray);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(plugin, "BungeeCord", byteArray.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("상점 NPC 서버로 이동 요청 전송 실패: " + e.getMessage());
        }
    }
}
