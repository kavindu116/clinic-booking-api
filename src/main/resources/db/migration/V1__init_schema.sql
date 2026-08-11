-- =====================================================================
-- V1: Core schema
-- =====================================================================

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    phone         VARCHAR(20),
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('PATIENT', 'DOCTOR', 'ADMIN'))
);

-- Refresh tokens: plain token eka DB eke thiyanne naa, SHA-256 hash eka witharai.
-- DB leak ekak unath tokens use karanna baa.
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_expires ON refresh_tokens (expires_at);

CREATE TABLE doctors (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT        NOT NULL,
    specialization   VARCHAR(100)  NOT NULL,
    qualifications   VARCHAR(255),
    consultation_fee NUMERIC(10,2) NOT NULL,
    bio              TEXT,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_doctors_user UNIQUE (user_id),
    CONSTRAINT fk_doctors_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_doctors_fee CHECK (consultation_fee >= 0)
);
CREATE INDEX idx_doctors_specialization ON doctors (specialization);
CREATE INDEX idx_doctors_active ON doctors (active);

-- Slots pre-generate karanne naa. Doctor kenekge weekly rule eka meke thiyenawa,
-- actual slots runtime ekedi calculate wenawa. Rows million ganan hadenne naa.
CREATE TABLE availability (
    id                    BIGSERIAL PRIMARY KEY,
    doctor_id             BIGINT   NOT NULL,
    day_of_week           SMALLINT NOT NULL,      -- 1=Monday .. 7=Sunday (ISO-8601)
    start_time            TIME     NOT NULL,
    end_time              TIME     NOT NULL,
    slot_duration_minutes SMALLINT NOT NULL DEFAULT 30,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_availability_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id) ON DELETE CASCADE,
    CONSTRAINT uq_availability_block UNIQUE (doctor_id, day_of_week, start_time),
    CONSTRAINT chk_availability_dow CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_availability_order CHECK (end_time > start_time),
    CONSTRAINT chk_availability_slot CHECK (slot_duration_minutes IN (10, 15, 20, 30, 45, 60))
);
CREATE INDEX idx_availability_doctor ON availability (doctor_id, day_of_week);

CREATE TABLE bookings (
    id         BIGSERIAL PRIMARY KEY,
    patient_id BIGINT       NOT NULL,
    doctor_id  BIGINT       NOT NULL,
    slot_start TIMESTAMPTZ  NOT NULL,
    slot_end   TIMESTAMPTZ  NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    notes      VARCHAR(500),
    version    BIGINT       NOT NULL DEFAULT 0,   -- JPA optimistic locking
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_bookings_patient FOREIGN KEY (patient_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_doctor  FOREIGN KEY (doctor_id)  REFERENCES doctors (id),
    CONSTRAINT chk_bookings_status CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    CONSTRAINT chk_bookings_order  CHECK (slot_end > slot_start)
);

-- ====================  ME PROJECT EKE HEART EKA  ====================
-- Partial unique index. Doctor kenekge ekama slot ekata active booking
-- ekakata wada thiyenna baa. Cancelled ewa count wenne naa, ee nisa
-- cancel karapu slot ekak aayemath book karanna puluwan.
-- Application-level check eka race condition ekakedi fail unath,
-- DB eka last line of defence eka widihata weda karanawa.
-- ===================================================================
CREATE UNIQUE INDEX uq_active_booking_slot
    ON bookings (doctor_id, slot_start)
    WHERE status <> 'CANCELLED';

CREATE INDEX idx_bookings_patient ON bookings (patient_id, slot_start DESC);
CREATE INDEX idx_bookings_doctor_range ON bookings (doctor_id, slot_start);
CREATE INDEX idx_bookings_status ON bookings (status);
