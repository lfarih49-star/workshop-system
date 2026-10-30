package com.trix.workshop.repository;

import com.trix.workshop.dto.AttendanceSummaryDto;
import com.trix.workshop.entity.Attendance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AttendanceRepository {
    private static final Logger log = LoggerFactory.getLogger(AttendanceRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public AttendanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Attendance> attendanceRowMapper = (rs, rowNum) -> {
        Attendance a = new Attendance();
        a.setAttendanceId(rs.getLong("attendance_id"));
        a.setRegistrationId(rs.getLong("registration_id"));
        a.setWorkshopId(rs.getLong("workshop_id"));
        a.setParticipantId(rs.getLong("participant_id"));
        a.setAttendanceStatus(rs.getString("attendance_status"));
        a.setRecordedAt(rs.getTimestamp("recorded_at") != null ? rs.getTimestamp("recorded_at").toLocalDateTime() : null);
        a.setRecordedBy(rs.getString("recorded_by"));
        a.setUpdatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        a.setParticipantName(rs.getString("full_name"));
        a.setParticipantEmail(rs.getString("email"));
        a.setWorkshopTitle(rs.getString("title"));
        return a;
    };

    public void saveOrUpdate(Long registrationId, Long workshopId, Long participantId, String status, String recordedBy) {
        if (registrationId == null) {
            log.warn("saveOrUpdate skipped: registrationId is null");
            return;
        }

        Integer count = 0;
        try {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM attendance WHERE registration_id = ?", Integer.class, registrationId);
        } catch (Exception ignored) {}

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        if (count != null && count > 0) {
            String updateSql = "UPDATE attendance SET attendance_status = ?, recorded_at = ?, recorded_by = ? WHERE registration_id = ?";
            jdbcTemplate.update(updateSql, status, now, recordedBy, registrationId);
        } else {
            String insertSql = "INSERT INTO attendance (registration_id, workshop_id, participant_id, attendance_status, recorded_at, recorded_by) VALUES (?, ?, ?, ?, ?, ?)";
            try {
                jdbcTemplate.update(insertSql, registrationId, workshopId, participantId, status, now, recordedBy);
            } catch (Exception e) {
                // If sequence collision on PostgreSQL:
                try {
                    jdbcTemplate.execute("SELECT setval(COALESCE(pg_get_serial_sequence('attendance', 'attendance_id'), 'attendance_attendance_id_seq'), " +
                            "COALESCE((SELECT MAX(attendance_id) FROM attendance), 1), (SELECT COUNT(*) > 0 FROM attendance))");
                    jdbcTemplate.update(insertSql, registrationId, workshopId, participantId, status, now, recordedBy);
                } catch (Exception ignored) {
                    log.warn("Attendance record init warning: {}", e.getMessage());
                }
            }
        }
    }

    public List<Attendance> findByWorkshopId(Long workshopId) {
        String sql = "SELECT a.attendance_id, r.registration_id, r.workshop_id, r.participant_id, " +
                     "COALESCE(a.attendance_status, 'NOT_RECORDED') as attendance_status, " +
                     "a.recorded_at, a.recorded_by, a.updated_at, " +
                     "u.full_name, u.email, w.title " +
                     "FROM registrations r " +
                     "JOIN users u ON r.participant_id = u.user_id " +
                     "JOIN workshops w ON r.workshop_id = w.workshop_id " +
                     "LEFT JOIN attendance a ON r.registration_id = a.registration_id " +
                     "WHERE r.workshop_id = ? AND r.registration_status = 'APPROVED' " +
                     "ORDER BY u.full_name ASC";
        return jdbcTemplate.query(sql, attendanceRowMapper, workshopId);
    }

    public Optional<Attendance> findByRegistrationId(Long registrationId) {
        String sql = "SELECT a.attendance_id, a.registration_id, a.workshop_id, a.participant_id, " +
                     "a.attendance_status, a.recorded_at, a.recorded_by, a.updated_at, " +
                     "u.full_name, u.email, w.title " +
                     "FROM attendance a " +
                     "JOIN users u ON a.participant_id = u.user_id " +
                     "JOIN workshops w ON a.workshop_id = w.workshop_id " +
                     "WHERE a.registration_id = ?";
        try {
            Attendance a = jdbcTemplate.queryForObject(sql, attendanceRowMapper, registrationId);
            return Optional.ofNullable(a);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public AttendanceSummaryDto getSummaryByWorkshopId(Long workshopId) {
        String sql = "SELECT " +
                     "COUNT(r.registration_id) as total_approved, " +
                     "SUM(CASE WHEN a.attendance_status = 'PRESENT' THEN 1 ELSE 0 END) as present_cnt, " +
                     "SUM(CASE WHEN a.attendance_status = 'ABSENT' THEN 1 ELSE 0 END) as absent_cnt " +
                     "FROM registrations r " +
                     "LEFT JOIN attendance a ON r.registration_id = a.registration_id " +
                     "WHERE r.workshop_id = ? AND r.registration_status = 'APPROVED'";

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            AttendanceSummaryDto summary = new AttendanceSummaryDto();
            summary.setWorkshopId(workshopId);
            int total = rs.getInt("total_approved");
            int present = rs.getInt("present_cnt");
            int absent = rs.getInt("absent_cnt");
            int notRecorded = total - (present + absent);

            summary.setApprovedRegistrations(total);
            summary.setPresentCount(present);
            summary.setAbsentCount(absent);
            summary.setNotRecordedCount(Math.max(0, notRecorded));

            double pct = total > 0 ? ((double) present / total) * 100.0 : 0.0;
            summary.setAttendancePercentage(Math.round(pct * 10.0) / 10.0);
            return summary;
        }, workshopId);
    }

    public int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM attendance WHERE attendance_status = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, status);
        return count != null ? count : 0;
    }

    public int countPresentByParticipant(Long participantId) {
        String sql = "SELECT COUNT(*) FROM attendance WHERE participant_id = ? AND attendance_status = 'PRESENT'";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, participantId);
        return count != null ? count : 0;
    }
}
