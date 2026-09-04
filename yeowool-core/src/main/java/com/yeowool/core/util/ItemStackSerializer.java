package com.yeowool.core.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Round-trips an {@link ItemStack} (including NBT — enchants, custom names,
 * PDC tags, etc.) through Bukkit's own object serialization so any plugin
 * can store an arbitrary item as a single TEXT column (player-shop listings,
 * the starter kit editor, ...).
 */
public final class ItemStackSerializer {

    private ItemStackSerializer() {
    }

    public static String serialize(ItemStack item) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(byteStream)) {
            out.writeObject(item);
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("아이템 직렬화 실패", e);
        }
    }

    public static ItemStack deserialize(String data) {
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream in = new BukkitObjectInputStream(byteStream)) {
            return (ItemStack) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("아이템 역직렬화 실패", e);
        }
    }
}
