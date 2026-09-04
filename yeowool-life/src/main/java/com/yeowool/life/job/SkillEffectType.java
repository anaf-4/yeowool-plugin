package com.yeowool.life.job;

/**
 * Every skill node is one of these four universal passive effects, applied
 * uniformly regardless of job — all four are handled entirely inside
 * {@link JobManager#grantXp}, so no per-job action listener needs to know
 * about skills at all.
 */
public enum SkillEffectType {
    /** Flat percentage more job XP on every action. */
    XP_BONUS_PERCENT,
    /** Chance of an extra copy of whatever the action would have produced (see each action listener's {@code rollExtraYield} call). */
    EXTRA_YIELD_CHANCE_PERCENT,
    /** Chance to double the job XP earned from one action. */
    CRITICAL_XP_CHANCE_PERCENT,
    /** Chance to also receive a small flat 온 bonus (config's {@code jobs.bonus-currency-per-proc}) on one action. */
    BONUS_CURRENCY_CHANCE_PERCENT
}
