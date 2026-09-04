package com.yeowool.life.job.repository;

import com.yeowool.life.job.PlayerJobData;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Blocking JDBC access to {@code yw_job_progress}/{@code yw_job_skills}/{@code yw_job_active}. Must only be called off the main thread. */
public final class JobRepository {

    private final DataSource dataSource;

    public JobRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public PlayerJobData load(UUID uuid) throws SQLException {
        PlayerJobData data = new PlayerJobData();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT job, level, xp, skill_points FROM yw_job_progress WHERE uuid = ?")) {
                select.setString(1, uuid.toString());
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        var progress = data.progress(rs.getString("job"));
                        progress.setLevel(rs.getInt("level"));
                        progress.setXp(rs.getLong("xp"));
                        progress.setSkillPoints(rs.getInt("skill_points"));
                    }
                }
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT job, skill_id, points FROM yw_job_skills WHERE uuid = ?")) {
                select.setString(1, uuid.toString());
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        data.progress(rs.getString("job")).setAllocatedPoints(rs.getString("skill_id"), rs.getInt("points"));
                    }
                }
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT job FROM yw_job_active WHERE uuid = ?")) {
                select.setString(1, uuid.toString());
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        data.setActiveJob(rs.getString("job"));
                    }
                }
            }
        }
        return data;
    }

    public void saveProgress(UUID uuid, String job, PlayerJobData.JobProgress progress) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO yw_job_progress (uuid, job, level, xp, skill_points) VALUES (?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE level = VALUES(level), xp = VALUES(xp), skill_points = VALUES(skill_points)")) {
            upsert.setString(1, uuid.toString());
            upsert.setString(2, job);
            upsert.setInt(3, progress.level());
            upsert.setLong(4, progress.xp());
            upsert.setInt(5, progress.skillPoints());
            upsert.executeUpdate();
        }
    }

    public void saveSkillPoint(UUID uuid, String job, String skillId, int points) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO yw_job_skills (uuid, job, skill_id, points) VALUES (?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE points = VALUES(points)")) {
            upsert.setString(1, uuid.toString());
            upsert.setString(2, job);
            upsert.setString(3, skillId);
            upsert.setInt(4, points);
            upsert.executeUpdate();
        }
    }

    public void saveActiveJob(UUID uuid, String job) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO yw_job_active (uuid, job) VALUES (?, ?) ON DUPLICATE KEY UPDATE job = VALUES(job)")) {
            upsert.setString(1, uuid.toString());
            upsert.setString(2, job);
            upsert.executeUpdate();
        }
    }
}
