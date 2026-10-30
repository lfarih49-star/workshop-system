package com.trix.workshop.repository;

import com.trix.workshop.entity.Workshop;
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
public class WorkshopRepository {
    private static final Logger log = LoggerFactory.getLogger(WorkshopRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public WorkshopRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Workshop> workshopRowMapper = (rs, rowNum) -> new Workshop(
            rs.getLong("workshop_id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("category"),
            rs.getString("organizer_name"),
            rs.getObject("organizer_id") != null ? rs.getLong("organizer_id") : null,
            rs.getDate("workshop_date").toLocalDate(),
            rs.getTime("start_time").toLocalTime(),
            rs.getTime("end_time").toLocalTime(),
            rs.getString("venue"),
            rs.getInt("capacity"),
            rs.getDate("registration_deadline").toLocalDate(),
            rs.getString("status"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null
    );

    public Workshop save(Workshop w) {
        String sql = "INSERT INTO workshops (title, description, category, organizer_name, organizer_id, " +
                     "workshop_date, start_time, end_time, venue, capacity, registration_deadline, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        try {
            executeInsert(sql, w, keyHolder);
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
            if (msg.contains("workshops_pkey") || msg.contains("duplicate key")) {
                log.warn("Workshop sequence collision. Resyncing workshops sequence...");
                try {
                    jdbcTemplate.execute("SELECT setval(COALESCE(pg_get_serial_sequence('workshops', 'workshop_id'), 'workshops_workshop_id_seq'), " +
                            "COALESCE((SELECT MAX(workshop_id) FROM workshops), 1), (SELECT COUNT(*) > 0 FROM workshops))");
                    executeInsert(sql, w, keyHolder);
                } catch (Exception retryEx) {
                    throw ex;
                }
            } else {
                throw ex;
            }
        }

        Number key = extractKey(keyHolder, "workshop_id");
        if (key != null) {
            w.setWorkshopId(key.longValue());
        } else {
            try {
                Long id = jdbcTemplate.queryForObject(
                        "SELECT workshop_id FROM workshops WHERE title = ? ORDER BY workshop_id DESC LIMIT 1",
                        Long.class, w.getTitle());
                w.setWorkshopId(id);
            } catch (Exception ignored) {}
        }
        return w;
    }

    private void executeInsert(String sql, Workshop w, KeyHolder keyHolder) {
        jdbcTemplate.update(connection -> {
            PreparedStatement ps;
            try {
                ps = connection.prepareStatement(sql, new String[]{"workshop_id"});
            } catch (Exception e) {
                ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            }
            ps.setString(1, w.getTitle());
            ps.setString(2, w.getDescription());
            ps.setString(3, w.getCategory());
            ps.setString(4, w.getOrganizerName());
            if (w.getOrganizerId() != null) {
                ps.setLong(5, w.getOrganizerId());
            } else {
                ps.setNull(5, Types.BIGINT);
            }
            ps.setDate(6, Date.valueOf(w.getWorkshopDate()));
            ps.setTime(7, Time.valueOf(w.getStartTime()));
            ps.setTime(8, Time.valueOf(w.getEndTime()));
            ps.setString(9, w.getVenue());
            ps.setInt(10, w.getCapacity());
            ps.setDate(11, Date.valueOf(w.getRegistrationDeadline()));
            ps.setString(12, w.getStatus() != null ? w.getStatus() : "UPCOMING");
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

    public void update(Workshop w) {
        String sql = "UPDATE workshops SET title = ?, description = ?, category = ?, organizer_name = ?, " +
                     "workshop_date = ?, start_time = ?, end_time = ?, venue = ?, capacity = ?, " +
                     "registration_deadline = ?, status = ? WHERE workshop_id = ?";
        jdbcTemplate.update(sql,
                w.getTitle(),
                w.getDescription(),
                w.getCategory(),
                w.getOrganizerName(),
                Date.valueOf(w.getWorkshopDate()),
                Time.valueOf(w.getStartTime()),
                Time.valueOf(w.getEndTime()),
                w.getVenue(),
                w.getCapacity(),
                Date.valueOf(w.getRegistrationDeadline()),
                w.getStatus(),
                w.getWorkshopId());
    }

    public boolean delete(Long workshopId) {
        String sql = "DELETE FROM workshops WHERE workshop_id = ?";
        return jdbcTemplate.update(sql, workshopId) > 0;
    }

    public Optional<Workshop> findById(Long workshopId) {
        String sql = "SELECT * FROM workshops WHERE workshop_id = ?";
        try {
            Workshop w = jdbcTemplate.queryForObject(sql, workshopRowMapper, workshopId);
            return Optional.ofNullable(w);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Workshop> findAll(String category, String status, String search) {
        StringBuilder sql = new StringBuilder("SELECT * FROM workshops WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("ALL")) {
            sql.append("AND category = ? ");
            params.add(category.trim());
        }

        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
            sql.append("AND status = ? ");
            params.add(status.trim().toUpperCase());
        }

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(title) LIKE ? OR LOWER(venue) LIKE ? OR LOWER(organizer_name) LIKE ?) ");
            String q = "%" + search.trim().toLowerCase() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
        }

        sql.append("ORDER BY workshop_date DESC, start_time ASC");
        return jdbcTemplate.query(sql.toString(), workshopRowMapper, params.toArray());
    }

    public int getRegisteredCount(Long workshopId) {
        String sql = "SELECT COUNT(*) FROM registrations WHERE workshop_id = ? AND registration_status IN ('APPROVED', 'PENDING')";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, workshopId);
        return count != null ? count : 0;
    }

    public int countAll() {
        String sql = "SELECT COUNT(*) FROM workshops";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    public int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM workshops WHERE status = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, status);
        return count != null ? count : 0;
    }

    public List<Workshop> findUpcoming(int limit) {
        String sql = "SELECT * FROM workshops WHERE status = 'UPCOMING' AND workshop_date >= CURRENT_DATE ORDER BY workshop_date ASC, start_time ASC LIMIT ?";
        return jdbcTemplate.query(sql, workshopRowMapper, limit);
    }
}
