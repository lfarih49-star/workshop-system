package com.trix.workshop.repository;

import com.trix.workshop.dto.CertificateDto;
import com.trix.workshop.dto.CertificateEligibilityDto;
import com.trix.workshop.entity.Certificate;
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
public class CertificateRepository {
    private static final Logger log = LoggerFactory.getLogger(CertificateRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public CertificateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Certificate> certificateRowMapper = (rs, rowNum) -> new Certificate(
            rs.getLong("certificate_id"),
            rs.getLong("registration_id"),
            rs.getLong("participant_id"),
            rs.getLong("workshop_id"),
            rs.getString("certificate_number"),
            rs.getString("verification_code"),
            rs.getString("certificate_status"),
            rs.getBoolean("eligibility_verified"),
            rs.getDate("issue_date").toLocalDate(),
            rs.getString("file_path"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null
    );

    private final RowMapper<CertificateDto> certificateDtoRowMapper = (rs, rowNum) -> {
        CertificateDto dto = new CertificateDto();
        dto.setCertificateId(rs.getLong("certificate_id"));
        dto.setRegistrationId(rs.getLong("registration_id"));
        dto.setParticipantId(rs.getLong("participant_id"));
        dto.setParticipantName(rs.getString("full_name"));
        dto.setParticipantEmail(rs.getString("email"));
        dto.setWorkshopId(rs.getLong("workshop_id"));
        dto.setWorkshopTitle(rs.getString("title"));
        dto.setWorkshopDate(rs.getString("workshop_date"));
        dto.setVenue(rs.getString("venue"));
        dto.setCertificateNumber(rs.getString("certificate_number"));
        dto.setVerificationCode(rs.getString("verification_code"));
        dto.setCertificateStatus(rs.getString("certificate_status"));
        dto.setEligibilityVerified(rs.getBoolean("eligibility_verified"));
        dto.setIssueDate(rs.getDate("issue_date").toLocalDate());
        dto.setDownloadUrl("/api/certificates/" + dto.getCertificateId() + "/download");
        dto.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return dto;
    };

    public Certificate save(Certificate c) {
        String sql = "INSERT INTO certificates (registration_id, participant_id, workshop_id, " +
                     "certificate_number, verification_code, certificate_status, eligibility_verified, " +
                     "issue_date, file_path) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        try {
            executeInsert(sql, c, keyHolder);
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
            if (msg.contains("certificates_pkey") || msg.contains("duplicate key")) {
                log.warn("Certificate sequence collision detected. Resyncing certificates sequence...");
                try {
                    jdbcTemplate.execute("SELECT setval(COALESCE(pg_get_serial_sequence('certificates', 'certificate_id'), 'certificates_certificate_id_seq'), " +
                            "COALESCE((SELECT MAX(certificate_id) FROM certificates), 1), (SELECT COUNT(*) > 0 FROM certificates))");
                    executeInsert(sql, c, keyHolder);
                } catch (Exception retryEx) {
                    throw ex;
                }
            } else {
                throw ex;
            }
        }

        Number key = extractKey(keyHolder, "certificate_id");
        if (key != null) {
            c.setCertificateId(key.longValue());
        } else {
            try {
                Long id = jdbcTemplate.queryForObject(
                        "SELECT certificate_id FROM certificates WHERE certificate_number = ?",
                        Long.class, c.getCertificateNumber());
                c.setCertificateId(id);
            } catch (Exception ignored) {}
        }
        return c;
    }

    private void executeInsert(String sql, Certificate c, KeyHolder keyHolder) {
        jdbcTemplate.update(connection -> {
            PreparedStatement ps;
            try {
                ps = connection.prepareStatement(sql, new String[]{"certificate_id"});
            } catch (Exception e) {
                ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            }
            ps.setLong(1, c.getRegistrationId());
            ps.setLong(2, c.getParticipantId());
            ps.setLong(3, c.getWorkshopId());
            ps.setString(4, c.getCertificateNumber());
            ps.setString(5, c.getVerificationCode());
            ps.setString(6, c.getCertificateStatus() != null ? c.getCertificateStatus() : "GENERATED");
            ps.setBoolean(7, c.getEligibilityVerified() != null ? c.getEligibilityVerified() : true);
            ps.setDate(8, Date.valueOf(c.getIssueDate()));
            ps.setString(9, c.getFilePath());
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

    public Optional<CertificateDto> findDtoById(Long certificateId) {
        String sql = "SELECT c.*, u.full_name, u.email, w.title, w.workshop_date, w.venue " +
                     "FROM certificates c " +
                     "JOIN users u ON c.participant_id = u.user_id " +
                     "JOIN workshops w ON c.workshop_id = w.workshop_id " +
                     "WHERE c.certificate_id = ?";
        try {
            CertificateDto dto = jdbcTemplate.queryForObject(sql, certificateDtoRowMapper, certificateId);
            return Optional.ofNullable(dto);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Certificate> findById(Long certificateId) {
        String sql = "SELECT * FROM certificates WHERE certificate_id = ?";
        try {
            Certificate c = jdbcTemplate.queryForObject(sql, certificateRowMapper, certificateId);
            return Optional.ofNullable(c);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Certificate> findByRegistrationId(Long registrationId) {
        String sql = "SELECT * FROM certificates WHERE registration_id = ?";
        try {
            Certificate c = jdbcTemplate.queryForObject(sql, certificateRowMapper, registrationId);
            return Optional.ofNullable(c);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<CertificateDto> findByVerificationCode(String verificationCode) {
        String sql = "SELECT c.*, u.full_name, u.email, w.title, w.workshop_date, w.venue " +
                     "FROM certificates c " +
                     "JOIN users u ON c.participant_id = u.user_id " +
                     "JOIN workshops w ON c.workshop_id = w.workshop_id " +
                     "WHERE c.verification_code = ? OR c.certificate_number = ?";
        try {
            CertificateDto dto = jdbcTemplate.queryForObject(sql, certificateDtoRowMapper, verificationCode.trim(), verificationCode.trim());
            return Optional.ofNullable(dto);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<CertificateDto> findByParticipantId(Long participantId) {
        String sql = "SELECT c.*, u.full_name, u.email, w.title, w.workshop_date, w.venue " +
                     "FROM certificates c " +
                     "JOIN users u ON c.participant_id = u.user_id " +
                     "JOIN workshops w ON c.workshop_id = w.workshop_id " +
                     "WHERE c.participant_id = ? " +
                     "ORDER BY c.issue_date DESC";
        return jdbcTemplate.query(sql, certificateDtoRowMapper, participantId);
    }

    public List<CertificateDto> findAll(Long workshopId, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT c.*, u.full_name, u.email, w.title, w.workshop_date, w.venue " +
                "FROM certificates c " +
                "JOIN users u ON c.participant_id = u.user_id " +
                "JOIN workshops w ON c.workshop_id = w.workshop_id " +
                "WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();

        if (workshopId != null) {
            sql.append("AND c.workshop_id = ? ");
            params.add(workshopId);
        }

        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
            sql.append("AND c.certificate_status = ? ");
            params.add(status.trim().toUpperCase());
        }

        sql.append("ORDER BY c.created_at DESC");
        return jdbcTemplate.query(sql.toString(), certificateDtoRowMapper, params.toArray());
    }

    public List<CertificateEligibilityDto> findEligibilityByWorkshop(Long workshopId) {
        String sql = "SELECT r.registration_id, r.participant_id, u.full_name, u.email, u.department, u.institution, " +
                     "r.workshop_id, w.title, r.registration_status, " +
                     "COALESCE(a.attendance_status, 'NOT_RECORDED') as attendance_status, " +
                     "c.certificate_id, c.certificate_number, c.verification_code, c.certificate_status " +
                     "FROM registrations r " +
                     "JOIN users u ON r.participant_id = u.user_id " +
                     "JOIN workshops w ON r.workshop_id = w.workshop_id " +
                     "LEFT JOIN attendance a ON r.registration_id = a.registration_id " +
                     "LEFT JOIN certificates c ON r.registration_id = c.registration_id " +
                     "WHERE r.workshop_id = ? " +
                     "ORDER BY u.full_name ASC";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            CertificateEligibilityDto dto = new CertificateEligibilityDto();
            dto.setRegistrationId(rs.getLong("registration_id"));
            dto.setParticipantId(rs.getLong("participant_id"));
            dto.setParticipantName(rs.getString("full_name"));
            dto.setParticipantEmail(rs.getString("email"));
            dto.setDepartment(rs.getString("department"));
            dto.setInstitution(rs.getString("institution"));
            dto.setWorkshopId(rs.getLong("workshop_id"));
            dto.setWorkshopTitle(rs.getString("title"));

            String regStatus = rs.getString("registration_status");
            String attStatus = rs.getString("attendance_status");
            Long certId = rs.getObject("certificate_id") != null ? rs.getLong("certificate_id") : null;
            String certStatus = rs.getString("certificate_status");

            dto.setRegistrationStatus(regStatus);
            dto.setAttendanceStatus(attStatus);
            dto.setCertificateId(certId);
            dto.setCertificateNumber(rs.getString("certificate_number"));
            dto.setVerificationCode(rs.getString("verification_code"));

            // Compute eligibility status & reason
            if (certId != null) {
                dto.setEligibilityStatus(certStatus != null ? certStatus : "GENERATED");
                dto.setReason("Certificate has been generated successfully.");
            } else if (!"APPROVED".equalsIgnoreCase(regStatus)) {
                dto.setEligibilityStatus("NOT_ELIGIBLE");
                dto.setReason("Registration is " + regStatus + " (must be APPROVED).");
            } else if ("NOT_RECORDED".equalsIgnoreCase(attStatus)) {
                dto.setEligibilityStatus("AWAITING_ATTENDANCE");
                dto.setReason("Workshop attendance has not been recorded yet.");
            } else if ("ABSENT".equalsIgnoreCase(attStatus)) {
                dto.setEligibilityStatus("NOT_ELIGIBLE");
                dto.setReason("Participant was marked Absent for this workshop.");
            } else if ("PRESENT".equalsIgnoreCase(attStatus)) {
                dto.setEligibilityStatus("ELIGIBLE");
                dto.setReason("Requirements met: Registration Approved and Attendance is Present.");
            } else {
                dto.setEligibilityStatus("NOT_ELIGIBLE");
                dto.setReason("Eligibility requirements not satisfied.");
            }

            return dto;
        }, workshopId);
    }

    public void updateStatus(Long certificateId, String status) {
        String sql = "UPDATE certificates SET certificate_status = ? WHERE certificate_id = ?";
        jdbcTemplate.update(sql, status, certificateId);
    }

    public void updateFilePath(Long certificateId, String filePath) {
        String sql = "UPDATE certificates SET file_path = ? WHERE certificate_id = ?";
        jdbcTemplate.update(sql, filePath, certificateId);
    }

    public int countAll() {
        String sql = "SELECT COUNT(*) FROM certificates";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    public int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM certificates WHERE certificate_status = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, status);
        return count != null ? count : 0;
    }

    public int countByParticipant(Long participantId) {
        String sql = "SELECT COUNT(*) FROM certificates WHERE participant_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, participantId);
        return count != null ? count : 0;
    }
}
