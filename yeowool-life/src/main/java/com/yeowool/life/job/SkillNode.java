package com.yeowool.life.job;

/** One purchasable node in a job's skill tree — shared across every job (config.yml's {@code jobs.skills}). */
public record SkillNode(String id, String displayName, int maxPoints, SkillEffectType effectType, double perPointValue) {

    public double valueFor(int points) {
        return points * perPointValue;
    }
}
