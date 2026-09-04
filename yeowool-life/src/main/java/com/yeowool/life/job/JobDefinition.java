package com.yeowool.life.job;

import org.bukkit.Material;

/**
 * {@code customIconId} (nullable) is an ItemsAdder namespaced item id used
 * as the GUI icon in preference to {@code icon} when ItemsAdder is enabled
 * and the id resolves — see {@link JobIconFactory}. {@code icon} always
 * stays a valid vanilla fallback, so the job screens still render correctly
 * on a server without ItemsAdder or with a missing/renamed custom item.
 */
public record JobDefinition(String id, String displayName, Material icon, String customIconId, String description) {
}
