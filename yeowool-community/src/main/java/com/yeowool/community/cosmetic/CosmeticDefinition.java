package com.yeowool.community.cosmetic;

/**
 * One purchasable cosmetic (plugin plan 10.2): pure decoration, never
 * combat power or economic advantage. {@code value} is a
 * {@link org.bukkit.Particle} name for {@link Type#PARTICLE}, or a
 * MiniMessage color tag (e.g. {@code "gold"} or {@code "#ff8800"}) for
 * {@link Type#CHAT_COLOR}.
 */
public record CosmeticDefinition(String id, Type type, String display, long price, String value) {

    public enum Type { PARTICLE, CHAT_COLOR }
}
