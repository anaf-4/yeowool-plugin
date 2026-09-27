package com.yeowool.life.surprise;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum SurpriseEventType {
    LAND_XP("land_xp", "토지 경험치", List.of("경험치")),
    CROP_DROP("crop_drop", "작물 수확량", List.of("작물")),
    TREASURE_DROP("treasure_drop", "보물지도 발견", List.of("보물지도")),
    JOB_XP("job_xp", "직업 경험치", List.of("직업"));

    private final String key;
    private final String label;
    private final List<String> aliases;

    SurpriseEventType(String key, String label, List<String> aliases) {
        this.key = key;
        this.label = label;
        this.aliases = aliases;
    }

    /** DB/config key. */
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public static Optional<SurpriseEventType> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (SurpriseEventType type : values()) {
            if (type.key.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** Command input: config key, the label without spaces (e.g. 토지경험치), an alias (e.g. 작물) or the enum name. */
    public static Optional<SurpriseEventType> parse(String input) {
        String trimmed = input.trim();
        for (SurpriseEventType type : values()) {
            if (type.key.equalsIgnoreCase(trimmed) || type.name().equalsIgnoreCase(trimmed)
                    || type.label.replace(" ", "").equals(trimmed) || type.aliases.contains(trimmed)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
