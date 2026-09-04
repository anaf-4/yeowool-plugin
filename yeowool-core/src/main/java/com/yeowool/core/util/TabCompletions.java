package com.yeowool.core.util;

import org.bukkit.Bukkit;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Small shared helpers so every Yeowool command's {@code TabCompleter} looks
 * the same instead of each plugin re-implementing prefix filtering.
 */
public final class TabCompletions {

    private TabCompletions() {
    }

    /** Case-insensitive "starts with" filter, the standard Bukkit tab-complete behavior. */
    public static List<String> filter(List<String> candidates, String partial) {
        String lower = partial.toLowerCase();
        return candidates.stream()
                .filter(candidate -> candidate.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }

    public static List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(org.bukkit.entity.Player::getName).collect(Collectors.toList());
    }

    public static List<String> filteredOnlinePlayerNames(String partial) {
        return filter(onlinePlayerNames(), partial);
    }
}
