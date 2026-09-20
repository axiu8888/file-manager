package com.kiftd.service;

import com.kiftd.config.KiftdProperties;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

/**
 * 在应用内定时备份：有文件变动才导出。容器里走 JDBC，不调用 docker/pg_dump。
 */
@Service
@Slf4j
public class DbBackupService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final List<String> TABLES = List.of(
            "account",
            "folder",
            "file_node",
            "properties",
            "file_chain",
            "download_key"
    );

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final KiftdProperties props;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DbBackupService(DataSource dataSource, JdbcTemplate jdbc, KiftdProperties props) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backupOnStartup() {
        Thread.ofVirtual().name("kiftd-db-backup-startup").start(() -> {
            try {
                ensureTodayBackup();
            } catch (Exception e) {
                log.error("启动时数据库备份失败", e);
            }
        });
    }

    @Scheduled(cron = "${kiftd.backup.cron:0 30 22 * * ?}", zone = "${kiftd.backup.zone:Asia/Shanghai}")
    public void scheduledBackup() {
        try {
            runIfEnabled(false);
        } catch (Exception e) {
            log.error("数据库备份失败", e);
        }
    }

    private void ensureTodayBackup() throws Exception {
        KiftdProperties.Backup cfg = props.backupOrDefault();
        if (!cfg.enabled()) {
            return;
        }
        Path today = todayBackupFile(cfg);
        if (Files.isRegularFile(today)) {
            log.info("当日备份已存在，跳过启动补备 {}", today);
            return;
        }
        log.info("当日尚无备份，启动后补备一次");
        runBackup(cfg, true);
    }

    private void runIfEnabled(boolean force) throws Exception {
        KiftdProperties.Backup cfg = props.backupOrDefault();
        if (!cfg.enabled()) {
            return;
        }
        runBackup(cfg, force);
    }

    public Path runBackup(KiftdProperties.Backup cfg) throws Exception {
        return runBackup(cfg, false);
    }

    public Path runBackup(KiftdProperties.Backup cfg, boolean force) throws Exception {
        if (!running.compareAndSet(false, true)) {
            log.info("备份已在进行，跳过");
            return null;
        }
        Path tmp = null;
        try {
            Path dir = Path.of(cfg.dir()).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String fingerprint = fingerprint();
            Path state = dir.resolve(".last-fingerprint");
            if (!force
                    && Files.isRegularFile(state)
                    && fingerprint.equals(Files.readString(state, StandardCharsets.UTF_8).trim())) {
                log.info("文件无变动，跳过数据库导出");
                return null;
            }
            ZoneId zone = ZoneId.of(cfg.zone());
            String day = LocalDate.now(zone).format(DAY);
            Path out = dir.resolve("kiftd-" + day + ".sql.gz");
            tmp = dir.resolve("kiftd-" + day + ".sql.gz.tmp");
            dump(tmp);
            try {
                Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
            Files.writeString(state, fingerprint, StandardCharsets.UTF_8);
            log.info("数据库已导出 {} ({})", out, Files.size(out));
            cleanup(dir, cfg.keepDays(), zone);
            return out;
        } finally {
            if (tmp != null) {
                Files.deleteIfExists(tmp);
            }
            running.set(false);
        }
    }

    private static Path todayBackupFile(KiftdProperties.Backup cfg) {
        ZoneId zone = ZoneId.of(cfg.zone());
        String day = LocalDate.now(zone).format(DAY);
        return Path.of(cfg.dir()).toAbsolutePath().normalize().resolve("kiftd-" + day + ".sql.gz");
    }

    private String fingerprint() {
        String sql = """
                SELECT md5(COALESCE(string_agg(h, '' ORDER BY h), ''))
                FROM (
                  SELECT md5(file_id || chr(31) || file_name || chr(31) || file_parent_folder || chr(31) || file_path) AS h
                    FROM file_node
                  UNION ALL
                  SELECT md5(folder_id || chr(31) || folder_name || chr(31) || COALESCE(folder_parent, ''))
                    FROM folder
                ) q
                """;
        String fp = jdbc.queryForObject(sql, String.class);
        return fp == null ? "" : fp;
    }

    private void dump(Path target) throws Exception {
        Files.createDirectories(target.getParent());
        try (Connection conn = dataSource.getConnection();
             OutputStream out = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(target)))) {
            CopyManager copy = conn.unwrap(PGConnection.class).getCopyAPI();
            write(out, "-- kiftd jdbc backup\n");
            write(out, "BEGIN;\n");
            write(out, "SET session_replication_role = replica;\n");
            write(out, "TRUNCATE download_key, file_chain, file_node, folder, account, properties CASCADE;\n");
            out.flush();
            for (String table : TABLES) {
                if (!tableExists(conn, table)) {
                    continue;
                }
                write(out, "COPY public." + table + " FROM stdin;\n");
                out.flush();
                copy.copyOut("COPY public." + table + " TO STDOUT", out);
                write(out, "\\.\n");
                out.flush();
            }
            write(out, "SET session_replication_role = DEFAULT;\n");
            write(out, "COMMIT;\n");
        }
    }

    private static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean tableExists(Connection conn, String table) throws Exception {
        try (var ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?")) {
            ps.setString(1, table);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void cleanup(Path dir, int keepDays, ZoneId zone) throws IOException {
        if (keepDays <= 0) {
            return;
        }
        LocalDate cutoff = LocalDate.now(zone).minusDays(keepDays);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "kiftd-*.sql.gz")) {
            for (Path f : stream) {
                String name = f.getFileName().toString();
                if (name.length() < 19) {
                    continue;
                }
                try {
                    LocalDate d = LocalDate.parse(name.substring(6, 14), DAY);
                    if (d.isBefore(cutoff)) {
                        Files.deleteIfExists(f);
                    }
                } catch (Exception ignored) {
                    // 文件名不符合 kiftd-yyyyMMdd.sql.gz 则跳过
                }
            }
        }
    }
}
