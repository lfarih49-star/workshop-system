package com.trix.workshop.repository;

import com.trix.workshop.entity.AuditLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AuditLogRepository {
    private final JdbcTemplate jdbcTemplate;

    public AuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<AuditLog> auditLogRowMapper = (rs, rowNum) -> new AuditLog(
            rs.getLong("log_id"),
            rs.getString("actor_email"),
            rs.getString("action"),
            rs.getString("entity_type"),
            rs.getObject("entity_id") != null ? rs.getLong("entity_id") : null,
            rs.getString("details"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null
    );

    public void save(AuditLog log) {
        String sql = "INSERT INTO audit_logs (actor_email, action, entity_type, entity_id, details) VALUES (?, ?, ?, ?, ?)";
        try {
            jdbcTemplate.update(sql, log.getActorEmail(), log.getAction(), log.getEntityType(), log.getEntityId(), log.getDetails());
        } catch (Exception e) {
            try {
                jdbcTemplate.execute("SELECT setval(COALESCE(pg_get_serial_sequence('audit_logs', 'log_id'), 'audit_logs_log_id_seq'), " +
                        "COALESCE((SELECT MAX(log_id) FROM audit_logs), 1), (SELECT COUNT(*) > 0 FROM audit_logs))");
                jdbcTemplate.update(sql, log.getActorEmail(), log.getAction(), log.getEntityType(), log.getEntityId(), log.getDetails());
            } catch (Exception ignored) {}
        }
    }

    public List<AuditLog> findRecent(int limit) {
        String sql = "SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, auditLogRowMapper, limit);
    }
}
