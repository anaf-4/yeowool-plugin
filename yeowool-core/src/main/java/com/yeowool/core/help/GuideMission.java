package com.yeowool.core.help;

/** One numbered onboarding mission shown in {@link GuideGui}, managed at runtime via {@code /길라잡이 추가|제거|수정}. */
public record GuideMission(int number, String title, String action) {
}
