package com.yeowool.admin.backup;

import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Section 11.3 (YeowoolBackup), scoped to a lightweight per-table CSV export
 * rather than a full {@code mysqldump} (which would need that binary
 * available on the host and isn't guaranteed). Good enough to recover core
 * data by hand; a full binary dump is still recommended at the
 * infrastructure level for disaster recovery.
 */
public final class BackupService {

    private static final List<String> CORE_TABLES = List.of(
            "yw_players", "yw_player_lands", "yw_player_settings", "yw_player_statistics", "yw_logs"
    );
    private static final List<String> OPTIONAL_TABLES = List.of(
            "yw_lands", "yw_land_chunks", "yw_land_members", "yw_market_listings"
    );
    private static final Pattern BACKUP_FOLDER_NAME = Pattern.compile("^\\d{8}-\\d{6}$");

    private final JavaPlugin plugin;
    private final DataSource dataSource;

    public BackupService(JavaPlugin plugin, DataSource dataSource) {
        this.plugin = plugin;
        this.dataSource = dataSource;
    }

    /**
     * Runs synchronously — callers must invoke this off the main thread.
     * Returns the backup directory on success.
     */
    public Path runBackup() throws IOException, SQLException {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now());
        Path dir = plugin.getDataFolder().toPath().resolve("backups").resolve(timestamp);
        Files.createDirectories(dir);

        try (Connection connection = dataSource.getConnection()) {
            for (String table : CORE_TABLES) {
                exportTable(connection, table, dir);
            }
            for (String table : OPTIONAL_TABLES) {
                try {
                    exportTable(connection, table, dir);
                } catch (SQLException e) {
                    plugin.getLogger().info("백업 건너뜀 (테이블 없음): " + table);
                }
            }
        }
        return dir;
    }

    private void exportTable(Connection connection, String table, Path dir) throws SQLException, IOException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM " + table)) {
            ResultSetMetaData meta = rs.getMetaData();
            int columns = meta.getColumnCount();

            try (PrintWriter writer = new PrintWriter(new FileWriter(dir.resolve(table + ".csv").toFile()))) {
                for (int i = 1; i <= columns; i++) {
                    writer.print(escape(meta.getColumnName(i)));
                    writer.print(i < columns ? "," : "\n");
                }
                while (rs.next()) {
                    for (int i = 1; i <= columns; i++) {
                        writer.print(escape(rs.getString(i)));
                        writer.print(i < columns ? "," : "\n");
                    }
                }
            }
        }
    }

    /** Lists backup folder names (newest first) for tab completion / picking a restore target. */
    public List<String> listBackups() throws IOException {
        Path backupsDir = plugin.getDataFolder().toPath().resolve("backups");
        if (!Files.isDirectory(backupsDir)) {
            return List.of();
        }
        try (var stream = Files.list(backupsDir)) {
            return stream.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> BACKUP_FOLDER_NAME.matcher(name).matches())
                    .sorted(Collections.reverseOrder())
                    .toList();
        }
    }

    /**
     * Restores every known table from a backup folder's CSVs via
     * {@code REPLACE INTO}, so rows are upserted by primary key instead of
     * wiping tables first. Runs synchronously — callers must invoke this off
     * the main thread. {@code folderName} is restricted to the
     * {@code yyyyMMdd-HHmmss} pattern {@link #runBackup()} produces, both to
     * reject path traversal and to avoid touching directories this service
     * didn't create.
     */
    public void restore(String folderName) throws IOException, SQLException {
        if (!BACKUP_FOLDER_NAME.matcher(folderName).matches()) {
            throw new IOException("올바르지 않은 백업 폴더 이름입니다: " + folderName);
        }
        Path dir = plugin.getDataFolder().toPath().resolve("backups").resolve(folderName);
        if (!Files.isDirectory(dir)) {
            throw new IOException("백업 폴더를 찾을 수 없습니다: " + folderName);
        }

        List<String> knownTables = new ArrayList<>(CORE_TABLES);
        knownTables.addAll(OPTIONAL_TABLES);

        try (Connection connection = dataSource.getConnection()) {
            for (String table : knownTables) {
                Path file = dir.resolve(table + ".csv");
                if (Files.isRegularFile(file)) {
                    restoreTable(connection, table, file);
                }
            }
        }
    }

    private void restoreTable(Connection connection, String table, Path file) throws IOException, SQLException {
        List<String> lines = Files.readAllLines(file);
        if (lines.size() <= 1) {
            return;
        }
        List<String> columns = parseCsvLine(lines.get(0));
        String placeholders = String.join(",", Collections.nCopies(columns.size(), "?"));
        String columnList = columns.stream().map(c -> "`" + c + "`").collect(Collectors.joining(","));
        String sql = "REPLACE INTO " + table + " (" + columnList + ") VALUES (" + placeholders + ")";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int batched = 0;
            for (int i = 1; i < lines.size(); i++) {
                List<String> values = parseCsvLine(lines.get(i));
                for (int c = 0; c < values.size(); c++) {
                    String value = values.get(c);
                    if (value.isEmpty()) {
                        statement.setNull(c + 1, Types.VARCHAR);
                    } else {
                        statement.setString(c + 1, value);
                    }
                }
                statement.addBatch();
                if (++batched % 500 == 0) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
        }
        plugin.getLogger().info("복구 완료: " + table + " (" + (lines.size() - 1) + "행)");
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
