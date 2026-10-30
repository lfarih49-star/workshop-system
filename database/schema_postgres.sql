-- Workshop Registration & Certificate Issue System
-- PostgreSQL Auto-Schema for Render & Cloud Deployment

-- 1. USERS TABLE
CREATE TABLE IF NOT EXISTS users (
    user_id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'PARTICIPANT',
    phone VARCHAR(25),
    department VARCHAR(100),
    institution VARCHAR(150),
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_user_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_user_role ON users(role);

-- 2. WORKSHOPS TABLE
CREATE TABLE IF NOT EXISTS workshops (
    workshop_id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL,
    organizer_name VARCHAR(150) NOT NULL,
    organizer_id BIGINT NULL,
    workshop_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    venue VARCHAR(150) NOT NULL,
    capacity INT NOT NULL,
    registration_deadline DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UPCOMING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (organizer_id) REFERENCES users(user_id) ON DELETE SET NULL
);

-- 3. REGISTRATIONS TABLE
CREATE TABLE IF NOT EXISTS registrations (
    registration_id BIGSERIAL PRIMARY KEY,
    participant_id BIGINT NOT NULL,
    workshop_id BIGINT NOT NULL,
    registration_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    registration_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (participant_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (workshop_id) REFERENCES workshops(workshop_id) ON DELETE CASCADE,
    UNIQUE (participant_id, workshop_id)
);

-- 4. ATTENDANCE TABLE
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id BIGSERIAL PRIMARY KEY,
    registration_id BIGINT NOT NULL UNIQUE,
    workshop_id BIGINT NOT NULL,
    participant_id BIGINT NOT NULL,
    attendance_status VARCHAR(20) NOT NULL DEFAULT 'NOT_RECORDED',
    recorded_at TIMESTAMP NULL,
    recorded_by VARCHAR(150),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (registration_id) REFERENCES registrations(registration_id) ON DELETE CASCADE,
    FOREIGN KEY (workshop_id) REFERENCES workshops(workshop_id) ON DELETE CASCADE,
    FOREIGN KEY (participant_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- 5. CERTIFICATES TABLE
CREATE TABLE IF NOT EXISTS certificates (
    certificate_id BIGSERIAL PRIMARY KEY,
    registration_id BIGINT NOT NULL UNIQUE,
    participant_id BIGINT NOT NULL,
    workshop_id BIGINT NOT NULL,
    certificate_number VARCHAR(100) NOT NULL UNIQUE,
    verification_code VARCHAR(50) NOT NULL UNIQUE,
    certificate_status VARCHAR(20) NOT NULL DEFAULT 'GENERATED',
    eligibility_verified BOOLEAN NOT NULL DEFAULT TRUE,
    issue_date DATE NOT NULL DEFAULT CURRENT_DATE,
    file_path VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (registration_id) REFERENCES registrations(registration_id) ON DELETE CASCADE,
    FOREIGN KEY (participant_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (workshop_id) REFERENCES workshops(workshop_id) ON DELETE CASCADE
);

-- 6. AUDIT LOGS TABLE
CREATE TABLE IF NOT EXISTS audit_logs (
    log_id BIGSERIAL PRIMARY KEY,
    actor_email VARCHAR(150) NOT NULL,
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NULL,
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 7. SYNCHRONIZE SERIAL SEQUENCES (Prevents duplicate key errors after manual inserts)
SELECT setval(COALESCE(pg_get_serial_sequence('registrations', 'registration_id'), 'registrations_registration_id_seq'), COALESCE((SELECT MAX(registration_id) FROM registrations), 1), (SELECT COUNT(*) > 0 FROM registrations));
SELECT setval(COALESCE(pg_get_serial_sequence('attendance', 'attendance_id'), 'attendance_attendance_id_seq'), COALESCE((SELECT MAX(attendance_id) FROM attendance), 1), (SELECT COUNT(*) > 0 FROM attendance));
SELECT setval(COALESCE(pg_get_serial_sequence('certificates', 'certificate_id'), 'certificates_certificate_id_seq'), COALESCE((SELECT MAX(certificate_id) FROM certificates), 1), (SELECT COUNT(*) > 0 FROM certificates));
SELECT setval(COALESCE(pg_get_serial_sequence('audit_logs', 'log_id'), 'audit_logs_log_id_seq'), COALESCE((SELECT MAX(log_id) FROM audit_logs), 1), (SELECT COUNT(*) > 0 FROM audit_logs));
SELECT setval(COALESCE(pg_get_serial_sequence('workshops', 'workshop_id'), 'workshops_workshop_id_seq'), COALESCE((SELECT MAX(workshop_id) FROM workshops), 1), (SELECT COUNT(*) > 0 FROM workshops));
SELECT setval(COALESCE(pg_get_serial_sequence('users', 'user_id'), 'users_user_id_seq'), COALESCE((SELECT MAX(user_id) FROM users), 1), (SELECT COUNT(*) > 0 FROM users));
