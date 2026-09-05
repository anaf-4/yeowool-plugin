package com.yeowool.life.farming.custom;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 커스텀 작물 수확 시 롤하는 품질 등급 — 일반/은별/금별 3단계로, 등급이 높을수록
 * {@link CustomFarmingListener}가 지급하는 수확 소득에 곱해지는 배율만 다릅니다
 * (드롭되는 아이템 자체는 ItemsAdder가 만들어서 여기선 손대지 않음).
 */
public record CustomFarmingQualityConfig(double silverChancePercent, double goldChancePercent,
                                          double silverMultiplier, double goldMultiplier) {

    public enum Grade { NORMAL, SILVER, GOLD }

    /** 금별 → 은별 → 일반 순으로 확률을 굴립니다 (희귀한 것부터 먼저 확인). */
    public Grade roll() {
        double roll = ThreadLocalRandom.current().nextDouble(100);
        if (roll < goldChancePercent) {
            return Grade.GOLD;
        }
        if (roll < goldChancePercent + silverChancePercent) {
            return Grade.SILVER;
        }
        return Grade.NORMAL;
    }

    public double multiplierFor(Grade grade) {
        return switch (grade) {
            case GOLD -> goldMultiplier;
            case SILVER -> silverMultiplier;
            case NORMAL -> 1.0;
        };
    }
}
