package com.trix.workshop.repository;

import com.trix.workshop.dto.RegistrationDto;
import com.trix.workshop.entity.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class RegistrationRepository {
    private static final Logger log = LoggerFactory.getLogger(RegistrationRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public RegistrationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Registration> registrationRowMapper = (rs, rowNum) -> new Registration(
            rs.getLong("registration_id"),
            rs.getLong("participant_id"),
            rs.getLong("workshop_id"),
            rs.getTimestamp("registration_date") != null ? rs.getTimestamp("registration_date").toLocalDateTime() : null,
            rs.getString("registration_status"),
            rs.getString("notes"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null
    );

    private final RowMapper<RegistrationDto> registrationDtoRowMapper = (rs, rowNum) -> {
        RegistrationDto dto = new RegistrationDto();
        dto.setRegistrationId(rs.getLong("registration_id"));
        dto.setParticipantId(rs.getLong("participant_id"));
        dto.setParticipantName(rs.getString("full_name"));
        dto.setParticipantEmail(rs.getString("email"));
        dto.setParticipantPhone(rs.getString("phone"));
        dto.setParticipantDepartment(rs.getString("department"));
        dto.setParticipantInstitution(rs.getString("institution"));
        dto.setWorkshopId(rs.getLong("workshop_id"));
        dto.setWorkshopTitle(rs.getString("title"));
        dto.setWorkshopCategory(rs.getString("category"));
        dto.setWorkshopDate(rs.getString("workshop_date"));
        dto.setWorkshopVenue(rs.getString("venue"));
        dto.setRegistrationDate(rs.getTimestamp("registration_date") != null ? rs.getTimestamp("registration_date").toLocalDateTime() : null);
        dto.setRegistrationStatus(rs.getString("registration_status"));
        dto.setNotes(rs.getString("notes"));
        dto.setAttendanceStatus(rs.getString("attendance_status") != null ? rs.getString("attendance_status") : "NOT_RECORDED");
        dto.setCertificateStatus(rs.getString("certificate_status"));
        dto.setCertificateId(rs.getObject("certificate_id") != null ? rs.getLong("certificate_id") : null);
        dto.setCertificateNumber(rs.getString("certificate_number"));
        return dto;
    };

    public Registration save(Registration r) {
        String sql = "INSERT INTO registrations (participant_id, workshop_id, registration_status, notes) " +
                     "VALUES (?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        try {
            executeInsert(sql, r, keyHolder);
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
            // If PostgreSQL sequence desync caused duplicate primary key:
            if (msg.contains("registrations_pkey") || msg.contains("duplicate key") || msg.contains("unique constraint")) {
                log.warn("Sequence or constraint warning during registration: {}. Attempting sequence resync and retry...", ex.getMessage());
                try {
                    jdbcTemplate.execute("SELECT setval(COALESCE(pg_get_serial_sequence('registrations', 'registration_id'), 'registrations_registration_id_seq'), " +
                            "COALESCE((SELECT MAX(registration_id) FROM registrations), 1), (SELECT COUNT(*) > 0 FROM registrations))");
                    executeInsert(sql, r, keyHolder);
                } catch (Exception retryEx) {
                    throw ex; // If retry also fails (e.g. unique constraint on participant_id + workshop_id), throw original
                }
            } else {
                throw ex;
            }
        }

        Number key = extractKey(keyHolder, "registration_id");
        if (key != null) {
            r.setRegistrationId(key.longValue());
        } else {
            try {
                Long id = jdbcTemplate.queryForObject(
                        "SELECT registration_id FROM registrations WHERE participant_id = ? AND workshop_id = ? ORDER BY registration_id DESC LIMIT 1",
                        Long.class, r.getParticipantId(), r.getWorkshopId());
                r.setRegistrationId(id);
            } catch (Exception ignored) {}
        }
        return r;
    }

    private void executeInsert(String sql, Registration r, KeyHolder keyHolder) {
        jdbcTemplate.update(connection -> {
            PreparedStatement ps;
            try {
                ps = connection.prepareStatement(sql, new String[]{"registration_id"});
            } catch (Exception e) {
                ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            }
            ps.setLong(1, r.getParticipantId());
            ps.setLong(2, r.getWorkshopId());
            ps.setString(3, r.getRegistrationStatus() != null ? r.getRegistrationStatus() : "APPROVED");
            ps.setString(4, r.getNotes());
            return ps;
        }, keyHolder);
    }

    private Number extractKey(KeyHolder keyHolder, String column) {
        if (keyHolder == null) return null;
        try {
            if (keyHolder.getKey() != null) return keyHolder.getKey();
        } catch (Exception ignored) {}
        try {
            if (keyHolder.getKeys() != null && !keyHolder.getKeys().isEmpty()) {
                for (java.util.Map.Entry<String, Object> entry : keyHolder.getKeys().entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(column) && entry.getValue() instanceof Number) {
                        return (Number) entry.getValue();
                    }
                }
                for (Object val : keyHolder.getKeys().values()) {
                    if (val instanceof Number) {
                        return (Number) val;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public Optional<Registration> findById(Long id) {
        String sql = "SELECT * FROM registrations WHERE registration_id = ?";
        try {
            Registration r = jdbcTemplate.queryForObject(sql, registrationRowMapper, id);
            return Optional.ofNullable(r);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Registration> findByParticipantAndWorkshop(Long participantId, Long workshopId) {
        String sql = "SELECT * FROM registrations WHERE participant_id = ? AND workshop_id = ?";
        try {
            Registration r = jdbcTemplate.queryForObject(sql, registrationRowMapper, participantId, workshopId);
            return Optional.ofNullable(r);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existsByParticipantAndWorkshop(Long participantId, Long workshopId) {
        String sql = "SELECT COUNT(*) FROM registrations WHERE participant_id = ? AND workshop_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, participantId, workshopId);
        return count != null && count > 0;
    }

    public List<RegistrationDto> findByParticipantId(Long participantId) {
        String sql = "SELECT r.registration_id, r.participant_id, u.full_name, u.email, u.phone, u.department, u.institution, " +
                     "w.workshop_id, w.title, w.category, w.workshop_date, w.venue, " +
                     "r.registration_date, r.registration_status, r.notes, " +
                     "COALESCE(a.attendance_status, 'NOT_RECORDED') as attendance_status, " +
                     "c.certificate_status, c.certificate_id, c.certificate_number " +
                     "FROM registrations r " +
                     "JOIN users u ON r.participant_id = u.user_id " +
                     "JOIN workshops w ON r.workshop_id = w.workshop_id " +
                     "LEFT JOIN attendance a ON r.registration_id = a.registration_id " +
                     "LEFT JOIN certificates c ON r.registration_id = c.registration_id " +
                     "WHERE r.participant_id = ? " +
                     "ORDER BY r.registration_date DESC";
        return jdbcTemplate.query(sql, registrationDtoRowMapper, participantId);
    }

    public List<RegistrationDto> findAll(Long workshopId, String status, String search) {
        StringBuilder sql = new StringBuilder(
                "SELECT r.registration_id, r.participant_id, u.full_name, u.email, u.phone, u.department, u.institution, " +
                "w.workshop_id, w.title, w.category, w.workshop_date, w.venue, " +
                "r.registration_date, r.registration_status, r.notes, " +
                "COALESCE(a.attendance_status, 'NOT_RECORDED') as attendance_status, " +
                "c.certificate_status, c.certificate_id, c.certificate_number " +
                "FROM registrations r " +
                "JOIN users u ON r.participant_id = u.user_id " +
                "JOIN workshops w ON r.workshop_id = w.workshop_id " +
                "LEFT JOIN attendance a ON r.registration_id = a.registration_id " +
                "LEFT JOIN certificates c ON r.registration_id = c.registration_id " +
                "WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();

        if (workshopId != null) {
            sql.append("AND r.workshop_id = ? ");
            params.add(workshopId);
        }

        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
            sql.append("AND r.registration_status = ? ");
            params.add(status.trim().toUpperCase());
        }

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(u.full_name) LIKE ? OR LOWER(u.email) LIKE ? OR LOWER(w.title) LIKE ?) ");
            String q = "%" + search.trim().toLowerCase() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
        }

        sql.append("ORDER BY r.registration_date DESC");
        return jdbcTemplate.query(sql.toString(), registrationDtoRowMapper, params.toArray());
    }

    public void updateStatus(Long registrationId, String status) {
        String sql = "UPDATE registrations SET registration_status = ? WHERE registration_id = ?";
        jdbcTemplate.update(sql, status, registrationId);
    }

    public int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM registrations WHERE registration_status = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, status);
        return count != null ? count : 0;
    }

    public int countByParticipant(Long participantId) {
        String sql = "SELECT COUNT(*) FROM registrations WHERE participant_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, participantId);
        return count != null ? count : 0;
    }

    public int countApprovedByWorkshop(Long workshopId) {
        String sql = "SELECT COUNT(*) FROM registrations WHERE workshop_id = ? AND registration_status = 'APPROVED'";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, workshopId);
        return count != null ? count : 0;
    }
}
