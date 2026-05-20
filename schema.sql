-- Nexura PostgreSQL Database Schema

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    age INT,
    emergency_caregiver_name VARCHAR(100),
    caregiver_phone_number VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Health Logs Table
CREATE TABLE IF NOT EXISTS health_logs (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    heart_rate INT,
    blood_pressure VARCHAR(20), -- format: "SYS/DIA" (e.g. "120/80")
    oxygen_level INT,
    body_temperature DOUBLE PRECISION, -- Celsius
    stress_level INT CHECK (stress_level BETWEEN 1 AND 10),
    mood VARCHAR(50),
    fatigue_level VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Sleep Logs Table
CREATE TABLE IF NOT EXISTS sleep_logs (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    sleep_start_time TIMESTAMP NOT NULL,
    wake_time TIMESTAMP NOT NULL,
    duration_hours DOUBLE PRECISION NOT NULL,
    sleep_quality INT CHECK (sleep_quality BETWEEN 1 AND 5),
    interruptions_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Medications Table
CREATE TABLE IF NOT EXISTS medications (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    dosage VARCHAR(50) NOT NULL,
    scheduled_time VARCHAR(20) NOT NULL, -- format "HH:MM"
    frequency VARCHAR(50) NOT NULL, -- e.g. "DAILY", "TWICE_A_DAY"
    notes TEXT,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Medication Adherence Table
CREATE TABLE IF NOT EXISTS medication_adherence (
    id SERIAL PRIMARY KEY,
    medication_id INT REFERENCES medications(id) ON DELETE CASCADE,
    taken_at TIMESTAMP,
    scheduled_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL, -- 'TAKEN', 'MISSED', 'SNOOZED'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. Reminders Table
CREATE TABLE IF NOT EXISTS reminders (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    medication_id INT REFERENCES medications(id) ON DELETE CASCADE,
    scheduled_time TIMESTAMP NOT NULL,
    actual_time TIMESTAMP,
    status VARCHAR(20) NOT NULL, -- 'PENDING', 'SNOOZED', 'TAKEN', 'MISSED'
    delay_minutes INT DEFAULT 0,
    adaptive_interval_minutes INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 7. Alerts Table
CREATE TABLE IF NOT EXISTS alerts (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,
    category VARCHAR(50) NOT NULL, -- 'MEDICATION_MISSED', 'SLEEP_DETERIORATION', 'ABNORMAL_VITALS', 'CRITICAL_EMERGENCY', 'AI_WARNING'
    priority VARCHAR(20) NOT NULL, -- 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 8. AI Analysis Table
CREATE TABLE IF NOT EXISTS ai_analysis (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    risk_level VARCHAR(20) NOT NULL, -- 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    key_findings TEXT,
    recommendations TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 9. Chatbot Messages Table
CREATE TABLE IF NOT EXISTS chatbot_messages (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    ai_response TEXT NOT NULL,
    language VARCHAR(50) NOT NULL,
    voice_used BOOLEAN NOT NULL DEFAULT FALSE,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 10. Emergency Events Table
CREATE TABLE IF NOT EXISTS emergency_events (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    trigger_reason TEXT NOT NULL,
    escalated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'ESCALATED' -- 'ESCALATED', 'RESOLVED'
);

-- 11. Medical Reports Table
CREATE TABLE IF NOT EXISTS medical_reports (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 12. Report Analysis Table
CREATE TABLE IF NOT EXISTS report_analysis (
    id SERIAL PRIMARY KEY,
    report_id INT REFERENCES medical_reports(id) ON DELETE CASCADE,
    extracted_text TEXT,
    ai_summary TEXT,
    risk_score VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance tuning
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_health_logs_user_id ON health_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_sleep_logs_user_id ON sleep_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_medications_user_id ON medications(user_id);
CREATE INDEX IF NOT EXISTS idx_medication_adherence_medication_id ON medication_adherence(medication_id);
CREATE INDEX IF NOT EXISTS idx_reminders_user_id ON reminders(user_id);
CREATE INDEX IF NOT EXISTS idx_alerts_user_id ON alerts(user_id);
CREATE INDEX IF NOT EXISTS idx_ai_analysis_user_id ON ai_analysis(user_id);
CREATE INDEX IF NOT EXISTS idx_chatbot_messages_user_id ON chatbot_messages(user_id);
CREATE INDEX IF NOT EXISTS idx_emergency_events_user_id ON emergency_events(user_id);
CREATE INDEX IF NOT EXISTS idx_medical_reports_user_id ON medical_reports(user_id);
CREATE INDEX IF NOT EXISTS idx_report_analysis_report_id ON report_analysis(report_id);
