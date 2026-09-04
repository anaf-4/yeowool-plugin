package com.yeowool.core.api.service;

import org.bukkit.entity.Player;

/**
 * Named sound cues configured once in {@code config.yml} under {@code sounds:}
 * so every Yeowool plugin plays consistent feedback (success/error/click/etc.)
 * instead of hardcoding {@link org.bukkit.Sound} + volume/pitch everywhere.
 */
public interface SoundService {

    /**
     * Plays the sound registered under {@code key} to the player. Does
     * nothing (and logs a warning once) if the key is not configured.
     */
    void play(Player player, String key);
}
