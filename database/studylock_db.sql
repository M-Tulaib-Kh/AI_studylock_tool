-- ============================================================
-- StudyLock — MySQL Database Schema (XAMPP)
-- Run this file in phpMyAdmin or MySQL Workbench
-- ============================================================

CREATE DATABASE IF NOT EXISTS studylock_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE studylock_db;

-- ── users ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  email           VARCHAR(255) NOT NULL UNIQUE,
  password        VARCHAR(255) NOT NULL,
  full_name       VARCHAR(255) NOT NULL DEFAULT '',
  role            ENUM('STUDENT','LECTURER','ADMIN') NOT NULL DEFAULT 'STUDENT',
  department      VARCHAR(255),
  registration_no VARCHAR(100),
  designation     VARCHAR(255),
  semester        INT,
  avatar_url      TEXT,
  is_active       TINYINT(1) NOT NULL DEFAULT 1,
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_email (email),
  INDEX idx_role  (role)
) ENGINE=InnoDB;

-- ── courses ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS courses (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  title       VARCHAR(255) NOT NULL,
  description TEXT,
  subject     VARCHAR(255),
  credit_hours INT NOT NULL DEFAULT 3,
  semester    INT NOT NULL DEFAULT 1,
  lecturer_id BIGINT,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (lecturer_id) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_lecturer (lecturer_id)
) ENGINE=InnoDB;

-- ── lectures ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS lectures (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id   BIGINT,
  title       VARCHAR(255) NOT NULL,
  subject     VARCHAR(255),
  description TEXT,
  file_url    TEXT,
  file_name   VARCHAR(255),
  uploaded_by BIGINT NOT NULL,
  views       INT NOT NULL DEFAULT 0,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (course_id)   REFERENCES courses(id) ON DELETE CASCADE,
  FOREIGN KEY (uploaded_by) REFERENCES users(id)   ON DELETE CASCADE,
  INDEX idx_course   (course_id),
  INDEX idx_uploader (uploaded_by)
) ENGINE=InnoDB;

-- ── assignments ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS assignments (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id    BIGINT,
  title        VARCHAR(255) NOT NULL,
  subject      VARCHAR(255),
  description  TEXT,
  instructions TEXT,
  file_url     TEXT,
  file_name    VARCHAR(255),
  deadline     DATETIME NOT NULL,
  total_marks  INT NOT NULL DEFAULT 100,
  created_by   BIGINT NOT NULL,
  ai_generated TINYINT(1) NOT NULL DEFAULT 0,
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (course_id)  REFERENCES courses(id) ON DELETE CASCADE,
  FOREIGN KEY (created_by) REFERENCES users(id)   ON DELETE CASCADE,
  INDEX idx_course    (course_id),
  INDEX idx_creator   (created_by),
  INDEX idx_deadline  (deadline)
) ENGINE=InnoDB;

-- ── submissions ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS submissions (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  assignment_id   BIGINT NOT NULL,
  student_id      BIGINT NOT NULL,
  file_url        TEXT NOT NULL,
  file_name       VARCHAR(255),
  submitted_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  status          ENUM('SUBMITTED','LATE','GRADED') NOT NULL DEFAULT 'SUBMITTED',
  marks_obtained  INT,
  feedback        TEXT,
  graded_by       BIGINT,
  graded_at       DATETIME,
  UNIQUE KEY uq_submission (assignment_id, student_id),
  FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
  FOREIGN KEY (student_id)    REFERENCES users(id)       ON DELETE CASCADE,
  FOREIGN KEY (graded_by)     REFERENCES users(id)       ON DELETE SET NULL,
  INDEX idx_assignment (assignment_id),
  INDEX idx_student    (student_id)
) ENGINE=InnoDB;

-- ── quizzes ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS quizzes (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id        BIGINT,
  title            VARCHAR(255) NOT NULL,
  subject          VARCHAR(255),
  description      TEXT,
  questions_json   LONGTEXT,
  duration_minutes INT NOT NULL DEFAULT 15,
  total_marks      INT NOT NULL DEFAULT 10,
  deadline         DATETIME,
  created_by       BIGINT NOT NULL,
  ai_generated     TINYINT(1) NOT NULL DEFAULT 0,
  created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (course_id)  REFERENCES courses(id) ON DELETE CASCADE,
  FOREIGN KEY (created_by) REFERENCES users(id)   ON DELETE CASCADE,
  INDEX idx_course  (course_id),
  INDEX idx_creator (created_by)
) ENGINE=InnoDB;

-- ── quiz_attempts ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS quiz_attempts (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  quiz_id      BIGINT NOT NULL,
  student_id   BIGINT NOT NULL,
  answers_json TEXT,
  score        INT NOT NULL DEFAULT 0,
  total_marks  INT NOT NULL DEFAULT 10,
  ai_feedback  TEXT,
  ai_evaluated TINYINT(1) NOT NULL DEFAULT 0,
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (quiz_id)    REFERENCES quizzes(id) ON DELETE CASCADE,
  FOREIGN KEY (student_id) REFERENCES users(id)   ON DELETE CASCADE,
  INDEX idx_quiz    (quiz_id),
  INDEX idx_student (student_id)
) ENGINE=InnoDB;

-- ── study_sessions ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS study_sessions (
  id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id                  BIGINT NOT NULL,
  planned_duration_seconds INT NOT NULL,
  elapsed_seconds          INT NOT NULL DEFAULT 0,
  status                   ENUM('ACTIVE','COMPLETED','FAILED') NOT NULL DEFAULT 'ACTIVE',
  violations               INT NOT NULL DEFAULT 0,
  camera_enabled           TINYINT(1) NOT NULL DEFAULT 0,
  valid_session            TINYINT(1) NOT NULL DEFAULT 0,
  blocked_apps             TEXT,
  started_at               DATETIME DEFAULT CURRENT_TIMESTAMP,
  ended_at                 DATETIME,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_user   (user_id),
  INDEX idx_status (status)
) ENGINE=InnoDB;

-- ── game_state ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS game_state (
  user_id           BIGINT PRIMARY KEY,
  points            INT NOT NULL DEFAULT 0,
  streak            INT NOT NULL DEFAULT 0,
  longest_streak    INT NOT NULL DEFAULT 0,
  last_session_date DATE,
  badges_json       LONGTEXT,
  challenges_json   LONGTEXT,
  updated_at        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ── login_events ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS login_events (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT NOT NULL,
  role         ENUM('STUDENT','LECTURER','ADMIN') NOT NULL,
  email        VARCHAR(255) NOT NULL,
  name         VARCHAR(255) NOT NULL,
  device_info  TEXT,
  logged_in_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_user (user_id),
  INDEX idx_time (logged_in_at)
) ENGINE=InnoDB;

-- ── notifications ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT NOT NULL,
  title      VARCHAR(255) NOT NULL,
  message    TEXT NOT NULL,
  type       VARCHAR(50) NOT NULL DEFAULT 'info',
  is_read    TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_user    (user_id),
  INDEX idx_is_read (is_read)
) ENGINE=InnoDB;

-- ── Default Admin User ────────────────────────────────────────
-- Password: admin123 (BCrypt hash)
INSERT IGNORE INTO users (email, password, full_name, role, is_active)
VALUES (
  'admin@studylock.com',
  '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN9rEflmKKXJDV.QjL2uy',
  'System Admin',
  'ADMIN',
  1
);

-- ── Default Lecturer ─────────────────────────────────────────
-- Password: lecturer123
INSERT IGNORE INTO users (email, password, full_name, role, designation, is_active)
VALUES (
  'lecturer@studylock.com',
  '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uHnmrm/YQC',
  'Dr. Sarah Williams',
  'LECTURER',
  'Senior Lecturer',
  1
);

-- ── Default Student ───────────────────────────────────────────
-- Password: student123
INSERT IGNORE INTO users (email, password, full_name, role, registration_no, is_active)
VALUES (
  'student@studylock.com',
  '$2a$12$TwLeQfYKUcOqVxrT/6T6AeZBF2ZfKjNmf.Jq2Py3u0AjGBg1xLsWe',
  'Ahmed Khan',
  'STUDENT',
  'CS-2021-001',
  1
);

-- ═══════════════════════════════════════════════════
-- NEW TABLES (added v3.0)
-- ═══════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS `attendance` (
  `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
  `student_id`      BIGINT NOT NULL,
  `course_id`       BIGINT NOT NULL,
  `marked_by`       BIGINT,
  `attendance_date` DATE NOT NULL,
  `status`          ENUM('PRESENT','ABSENT','LATE') DEFAULT 'PRESENT',
  `notes`           TEXT,
  `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uq_attendance` (`student_id`, `course_id`, `attendance_date`),
  FOREIGN KEY (`student_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`course_id`)  REFERENCES `courses`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`marked_by`)  REFERENCES `users`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `study_plans` (
  `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
  `student_id`     BIGINT NOT NULL,
  `title`          VARCHAR(255) NOT NULL,
  `plan_json`      LONGTEXT,
  `subject`        TEXT,
  `duration_weeks` INT DEFAULT 4,
  `created_at`     DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`student_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═══════════════════════════════════════════════════
-- NEW TABLES (added v4.0)
-- ═══════════════════════════════════════════════════

-- ── enrollments ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `enrollments` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `student_id`  BIGINT NOT NULL,
  `course_id`   BIGINT NOT NULL,
  `enrolled_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `completed`   TINYINT(1) NOT NULL DEFAULT 0,
  UNIQUE KEY `uq_enrollment` (`student_id`, `course_id`),
  FOREIGN KEY (`student_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`course_id`)  REFERENCES `courses`(`id`) ON DELETE CASCADE,
  INDEX `idx_student` (`student_id`),
  INDEX `idx_course`  (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── topic_progress ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `topic_progress` (
  `id`                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  `student_id`         BIGINT NOT NULL,
  `enrollment_id`      BIGINT NOT NULL,
  `topic_name`         VARCHAR(255) NOT NULL,
  `completed`          TINYINT(1) NOT NULL DEFAULT 0,
  `completed_at`       DATETIME,
  `time_spent_seconds` INT NOT NULL DEFAULT 0,
  UNIQUE KEY `uq_topic` (`student_id`, `enrollment_id`, `topic_name`),
  FOREIGN KEY (`student_id`)    REFERENCES `users`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`enrollment_id`) REFERENCES `enrollments`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═══════════════════════════════════════════════════
-- NEW TABLES (added v4.2 — semester/credit-hour rules)
-- ═══════════════════════════════════════════════════

-- ── enrollment_requests ─────────────────────────────────────
-- Pending admin-approval requests when a student tries to enroll
-- in a course from a later semester than their own.
CREATE TABLE IF NOT EXISTS `enrollment_requests` (
  `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
  `student_id`       BIGINT NOT NULL,
  `course_id`        BIGINT NOT NULL,
  `status`           ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  `reason`           ENUM('FUTURE_SEMESTER','CREDIT_LIMIT_EXCEEDED') NOT NULL DEFAULT 'FUTURE_SEMESTER',
  `student_semester` INT,
  `course_semester`  INT,
  `current_credit_hours`           INT,
  `requested_course_credit_hours`  INT,
  `decided_by`       BIGINT,
  `admin_note`       TEXT,
  `requested_at`     DATETIME DEFAULT CURRENT_TIMESTAMP,
  `decided_at`       DATETIME,
  FOREIGN KEY (`student_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`course_id`)  REFERENCES `courses`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`decided_by`) REFERENCES `users`(`id`) ON DELETE SET NULL,
  INDEX `idx_status` (`status`),
  INDEX `idx_req_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Add default CS courses (with credit hours & semester spread) ──
INSERT IGNORE INTO courses (title, description, subject, credit_hours, semester, created_at) VALUES
('AI & Machine Learning', 'Introduction to artificial intelligence and ML algorithms', 'Computer Science', 3, 6, NOW()),
('Software Engineering', 'Software development lifecycle, design patterns, and best practices', 'Computer Science', 3, 5, NOW()),
('Data Structures & Algorithms', 'Core DSA concepts: arrays, trees, graphs, sorting, searching', 'Computer Science', 4, 3, NOW()),
('Web Development', 'Full-stack web development with HTML, CSS, JavaScript, and frameworks', 'Computer Science', 3, 4, NOW()),
('Game Programming', 'Game development fundamentals, game engines, and 2D/3D programming', 'Computer Science', 3, 6, NOW()),
('Database Management Systems', 'Relational databases, SQL, normalization, and transactions', 'Computer Science', 3, 4, NOW()),
('Operating Systems', 'Process management, memory management, file systems, and concurrency', 'Computer Science', 4, 5, NOW()),
('Computer Networks', 'Network protocols, TCP/IP, routing, security fundamentals', 'Computer Science', 3, 5, NOW()),
('Mobile App Development', 'Android and iOS development, cross-platform frameworks', 'Computer Science', 3, 6, NOW()),
('Cybersecurity', 'Network security, ethical hacking, cryptography, and secure coding', 'Computer Science', 3, 7, NOW());

-- Existing rows from earlier installs won't have credit_hours/semester set by the
-- INSERT IGNORE above (it only applies to brand-new rows). Backfill sane defaults:
UPDATE courses SET credit_hours = 3 WHERE credit_hours IS NULL;
UPDATE courses SET semester = 1 WHERE semester IS NULL;
