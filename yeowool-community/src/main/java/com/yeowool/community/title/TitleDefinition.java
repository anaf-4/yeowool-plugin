package com.yeowool.community.title;

/**
 * One entry from {@code config.yml}'s {@code titles:} list. A title with no
 * {@code requirement-stat} is admin-only (never auto-unlocks) — plugin plan
 * examples like "[여울의 주민]" fit that case.
 */
public record TitleDefinition(String id, String display, String requirementStat, long requirementValue, long rewardOn) {

    public boolean isAutoUnlock() {
        return requirementStat != null && !requirementStat.isBlank();
    }
}
