package com.yeowool.core.api.service;

import com.yeowool.core.api.model.MailboxEntry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Item delivery for cases where handing an {@link ItemStack} straight to a
 * player's inventory isn't possible — they're offline, or online but full.
 * Any plugin needing to pay out an item asynchronously (auction winners,
 * unsold listings, mail-in rewards) should go through here instead of
 * dropping it on the ground or just logging a warning. All methods must be
 * called from the main server thread; DB work happens on a background
 * executor internally.
 */
public interface MailboxService {

    /**
     * Delivers straight into {@code recipient}'s inventory if they're online
     * and it fits; otherwise (offline, or no room) queues it in the mailbox
     * for later claim via {@code /우편함}.
     */
    void deliverOrStore(UUID recipient, ItemStack item, String sourcePlugin, String note);

    CompletableFuture<List<MailboxEntry>> loadPending(UUID recipient);

    /**
     * Removes and returns one pending entry's item if it belongs to
     * {@code recipient} — the caller is responsible for actually giving the
     * item to the player (e.g. adding it to their inventory) once the future
     * completes.
     */
    CompletableFuture<Optional<ItemStack>> claim(UUID recipient, long entryId);

    /**
     * Tries to hand every pending item straight to an online player's
     * inventory; whatever doesn't fit stays queued. Returns how many entries
     * were delivered.
     */
    CompletableFuture<Integer> tryDeliverAll(Player player);
}
