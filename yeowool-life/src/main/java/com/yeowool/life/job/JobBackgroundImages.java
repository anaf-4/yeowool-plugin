package com.yeowool.life.job;

import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;

/**
 * Renders a GUI title as one of the "medival_jobs" ItemsAdder pack's
 * per-job full-inventory background images ({@code medival_jobs_<jobId>},
 * e.g. {@code medival_jobs_farmer}) — same font-image-in-the-title trick
 * {@code yeowool-market}'s {@code ShopBackgroundImages} and {@code
 * yeowool-community}'s {@code AttendanceGui} already use, duplicated here
 * rather than shared across modules since it's a small, self-contained
 * utility and yeowool-life has no existing dependency on either. Falls back
 * to plain text if ItemsAdder isn't installed or the image id doesn't
 * resolve. The pixel offset is pack/image-specific and generally needs a
 * small visual nudge in-game — see {@code jobs.gui-background-offset}.
 */
public final class JobBackgroundImages {

    private JobBackgroundImages() {
    }

    public static Component title(int offsetPx, String imageId, Component fallback) {
        if (imageId == null || imageId.isBlank() || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return fallback;
        }
        String offsetPlaceholder = ":offset_" + offsetPx + ":";
        String imagePlaceholder = ":" + imageId + ":";
        String legacy = FontImageWrapper.replaceFontImages(offsetPlaceholder + imagePlaceholder);
        if (legacy.contains(offsetPlaceholder) || legacy.contains(imagePlaceholder)) {
            return fallback;
        }
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }
}
