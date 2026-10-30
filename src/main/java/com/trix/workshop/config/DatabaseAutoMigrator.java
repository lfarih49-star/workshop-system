package com.trix.workshop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@Order(1)
public class DatabaseAutoMigrator implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DatabaseAutoMigrator.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseAutoMigrator(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try (Connection con = dataSource.getConnection()) {
            String dbProduct = con.getMetaData().getDatabaseProductName().toLowerCase();
            log.info("DatabaseAutoMigrator: Connected to database [{}]", dbProduct);

            boolean allTablesExist = false;
            try {
                jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class);
                jdbcTemplate.queryForObject("SELECT count(*) FROM workshops", Integer.class);
                jdbcTemplate.queryForObject("SELECT count(*) FROM registrations", Integer.class);
                jdbcTemplate.queryForObject("SELECT count(*) FROM attendance", Integer.class);
                jdbcTemplate.queryForObject("SELECT count(*) FROM certificates", Integer.class);
                allTablesExist = true;
            } catch (Exception e) {
                allTablesExist = false;
            }

            if (!allTablesExist) {
                log.info("DatabaseAutoMigrator: Missing tables detected. Executing schema script for [{}]...", dbProduct);
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                populator.setContinueOnError(true);

                if (dbProduct.contains("postgres")) {
                    populator.addScript(new ClassPathResource("schema-postgres.sql"));
                } else {
                    populator.addScript(new ClassPathResource("schema.sql"));
                }

                populator.execute(dataSource);
                log.info("DatabaseAutoMigrator: Schema script executed successfully!");
            } else {
                log.info("DatabaseAutoMigrator: All core tables verified.");
            }

            // Ensure workshops and certificates have new certificate columns for premium templates & verification
            try {
                jdbcTemplate.execute("ALTER TABLE workshops ADD COLUMN IF NOT EXISTS certificate_template VARCHAR(50) DEFAULT 'classic_gold';");
            } catch (Exception e) {
                try { jdbcTemplate.execute("ALTER TABLE workshops ADD COLUMN certificate_template VARCHAR(50) DEFAULT 'classic_gold';"); } catch (Exception ignored) {}
            }

            try {
                jdbcTemplate.execute("ALTER TABLE certificates ADD COLUMN IF NOT EXISTS certificate_code VARCHAR(30);");
            } catch (Exception e) {
                try { jdbcTemplate.execute("ALTER TABLE certificates ADD COLUMN certificate_code VARCHAR(30);"); } catch (Exception ignored) {}
            }

            try {
                jdbcTemplate.execute("ALTER TABLE certificates ADD COLUMN IF NOT EXISTS template_id VARCHAR(30) DEFAULT 'classic_gold';");
            } catch (Exception e) {
                try { jdbcTemplate.execute("ALTER TABLE certificates ADD COLUMN template_id VARCHAR(30) DEFAULT 'classic_gold';"); } catch (Exception ignored) {}
            }

            try {
                jdbcTemplate.execute("ALTER TABLE certificates ADD COLUMN IF NOT EXISTS certificate_type VARCHAR(30) DEFAULT 'Participation';");
            } catch (Exception e) {
                try { jdbcTemplate.execute("ALTER TABLE certificates ADD COLUMN certificate_type VARCHAR(30) DEFAULT 'Participation';"); } catch (Exception ignored) {}
            }

            try {
                jdbcTemplate.execute("ALTER TABLE certificates ADD COLUMN IF NOT EXISTS issued_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP;");
            } catch (Exception e) {
                try { jdbcTemplate.execute("ALTER TABLE certificates ADD COLUMN issued_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP;"); } catch (Exception ignored) {}
            }

            try {
                jdbcTemplate.execute("ALTER TABLE certificates ADD COLUMN IF NOT EXISTS issued_by BIGINT NULL;");
            } catch (Exception e) {
                try { jdbcTemplate.execute("ALTER TABLE certificates ADD COLUMN issued_by BIGINT NULL;"); } catch (Exception ignored) {}
            }

            // For PostgreSQL: Ensure any older schema versions have updated column definitions
            if (dbProduct.contains("postgres")) {
                try {
                    jdbcTemplate.execute("ALTER TABLE certificates ADD COLUMN IF NOT EXISTS eligibility_verified BOOLEAN NOT NULL DEFAULT TRUE;");
                    jdbcTemplate.execute("ALTER TABLE certificates ADD COLUMN IF NOT EXISTS issue_date DATE NOT NULL DEFAULT CURRENT_DATE;");
                    jdbcTemplate.execute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS actor_email VARCHAR(150);");
                    jdbcTemplate.execute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS action VARCHAR(50);");
                    jdbcTemplate.execute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS entity_type VARCHAR(50);");
                    log.info("DatabaseAutoMigrator: PostgreSQL schema column migrations verified.");
                } catch (Exception e) {
                    log.warn("DatabaseAutoMigrator: Schema column migration note: {}", e.getMessage());
                }

                // Resynchronize all PostgreSQL sequences to avoid duplicate key errors after manual data imports
                syncPostgresSequences();
            }
        } catch (Exception e) {
            log.error("DatabaseAutoMigrator error: ", e);
        }
    }

    public void syncPostgresSequences() {
        try (Connection con = dataSource.getConnection()) {
            String dbProduct = con.getMetaData().getDatabaseProductName().toLowerCase();
            if (!dbProduct.contains("postgres")) {
                return;
            }
        } catch (Exception e) {
            return;
        }

        String[][] tablesAndCols = {
                {"registrations", "registration_id", "registrations_registration_id_seq"},
                {"attendance", "attendance_id", "attendance_attendance_id_seq"},
                {"certificates", "certificate_id", "certificates_certificate_id_seq"},
                {"audit_logs", "log_id", "audit_logs_log_id_seq"},
                {"workshops", "workshop_id", "workshops_workshop_id_seq"},
                {"users", "user_id", "users_user_id_seq"}
        };

        for (String[] tc : tablesAndCols) {
            String table = tc[0];
            String col = tc[1];
            String seq = tc[2];
            try {
                Long maxId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(" + col + "), 0) FROM " + table, Long.class);
                if (maxId == null) maxId = 0L;

                boolean hasRows = maxId > 0;
                long setvalArg = hasRows ? maxId : 1L;

                // Sync sequence: if has rows, set to maxId and is_called=true (nextval will be maxId+1)
                // if empty, set to 1 and is_called=false (nextval will be 1)
                try {
                    String sql = "SELECT setval(COALESCE(pg_get_serial_sequence('" + table + "', '" + col + "'), '" + seq + "'), " +
                                 setvalArg + ", " + hasRows + ")";
                    jdbcTemplate.execute(sql);
                    log.info("DatabaseAutoMigrator: Resynced sequence for {}.{} (max={}, nextval={})",
                            table, col, maxId, (hasRows ? maxId + 1 : 1));
                } catch (Exception e1) {
                    try {
                        String fallbackSql = "SELECT setval('" + seq + "', " + setvalArg + ", " + hasRows + ")";
                        jdbcTemplate.execute(fallbackSql);
                        log.info("DatabaseAutoMigrator: Fallback resynced sequence {} (max={})", seq, maxId);
                    } catch (Exception e2) {
                        log.warn("DatabaseAutoMigrator: Could not sync sequence for {}: {}", table, e2.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("DatabaseAutoMigrator: Error querying max ID for {}: {}", table, e.getMessage());
            }
        }
    }
}
