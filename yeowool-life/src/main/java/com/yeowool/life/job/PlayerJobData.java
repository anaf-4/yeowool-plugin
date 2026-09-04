package com.yeowool.life.job;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One player's job progress, cached in memory by {@link JobManager} while
 * they're online. Progress for every job they've ever played is kept (not
 * just the active one) so switching jobs never loses history — only the
 * currently-active job earns new XP.
 */
public final class PlayerJobData {

    public static final class JobProgress {
        private volatile int level = 1;
        private volatile long xp = 0;
        private volatile int skillPoints = 0;
        private final Map<String, Integer> skillAllocations = new ConcurrentHashMap<>();

        public int level() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }

        public long xp() {
            return xp;
        }

        public void setXp(long xp) {
            this.xp = xp;
        }

        public int skillPoints() {
            return skillPoints;
        }

        public void setSkillPoints(int skillPoints) {
            this.skillPoints = skillPoints;
        }

        public int allocatedPoints(String skillId) {
            return skillAllocations.getOrDefault(skillId, 0);
        }

        public void setAllocatedPoints(String skillId, int points) {
            skillAllocations.put(skillId, points);
        }
    }

    private volatile String activeJob;
    private final Map<String, JobProgress> progressByJob = new ConcurrentHashMap<>();

    public String activeJob() {
        return activeJob;
    }

    public void setActiveJob(String jobId) {
        this.activeJob = jobId;
    }

    public JobProgress progress(String jobId) {
        return progressByJob.computeIfAbsent(jobId, id -> new JobProgress());
    }

    /** Read-only lookup that never creates an entry — for display contexts (GUIs) that shouldn't fabricate "level 1, 0 xp" for a job the player never touched. */
    public Optional<JobProgress> peekProgress(String jobId) {
        return Optional.ofNullable(progressByJob.get(jobId));
    }
}
