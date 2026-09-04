package com.yeowool.core.api.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * One item waiting for a player who was offline (or whose inventory was
 * full) when it was sent — a purchased auction win, a returned unsold
 * listing, anything a plugin can't hand over synchronously. Claimed through
 * {@code /우편함} or auto-delivered on next join via
 * {@link com.yeowool.core.api.service.MailboxService}.
 */
public record MailboxEntry(long id, UUID recipient, ItemStack item, String sourcePlugin, String note, long createdAt) {
}
