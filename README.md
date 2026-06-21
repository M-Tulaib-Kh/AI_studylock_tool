# 🔒 StudyLock v4.0 — AI-Powered Academic Focus Platform

## ✅ What's New in v4.0

### 🆕 New Features Added
| Feature | Description |
|---------|-------------|
| 🤖 AI Tutor (Student) | Chat-based AI tutor — English-only responses, context-aware, multi-turn conversation |
| 🤖 AI Teaching Assistant (Lecturer) | Lecturers get their own AI assistant for content creation and lesson planning |
| ⏱ Topic Study Session | Set a timer per topic, track completion, mark topic as done — no face detection |
| 📚 Course Enrollment System | Students browse all courses and enroll/unenroll themselves |
| 👨‍🏫 Lecturer Overview (Admin) | Admin sees which lecturer teaches which courses and how many/which students are in each |
| 📊 Student Report (Admin) | Admin clicks any student → full report: GPA, avg score, submissions, quiz attempts, enrollments |
| ➕ Admin Course Creator | Admin creates courses with CS dropdown templates and assigns lecturers |
| ✅ English-only AI responses | All AI endpoints (quiz gen, tutor, study plan, summaries) are strictly English-only |
| 🗑️ Face Detection Removed | Replaced with clean session timer — no camera required, tab-switch warning only |

### 🐛 Bugs Carried Forward (Fixed)
| Bug | Fix |
|-----|-----|
| LazyInitializationException | LEFT JOIN FETCH in repositories |
| AI quiz stuck | AbortController 90s timeout |
| Session double-start | SessionRegistry beans |
| Corrupt file downloads | UUID + original filename preserved |
| Quiz result crash | findByIdWithQuiz JOIN FETCH |

## 🚀 Setup

### Prerequisites
- Java 17+, Maven 3.8+, MySQL (XAMPP) on port 3306, NetBeans

### Database
1. Start XAMPP → phpMyAdmin
2. Create DB: `studylock_db`
3. Import `database/studylock_db.sql`

### Run
```bash
mvn spring-boot:run
```
Open: http://localhost:8080

### Default Accounts
| Email | Password | Role |
|-------|----------|------|
| admin@studylock.com | admin123 | Admin |
| lecturer@studylock.com | lecturer123 | Lecturer |
| student@studylock.com | student123 | Student |

## 🤖 AI (OpenRouter)
- **API Key**: already configured in `application.properties`
- **Model**: `anthropic/claude-haiku-4-5` via OpenRouter (free tier)
- **English only**: All AI methods enforce English output regardless of input language

## 📁 New Files Added
```
src/main/java/com/studylock/
├── model/Enrollment.java              — Student ↔ Course enrollment entity
├── model/TopicProgress.java           — Per-topic study progress tracking
├── repository/EnrollmentRepository.java
├── repository/TopicProgressRepository.java
├── service/EnrollmentService.java     — Enroll/unenroll, topic completion
├── dto/TutorMessageDto.java           — AI tutor request payload

src/main/resources/templates/
├── student/ai_tutor.html              — Student AI tutor chat interface
├── student/enrollments.html           — Course enrollment management
├── student/topic_session.html         — Timer-based topic study session
├── admin/student_report.html          — Per-student GPA + marks report
├── admin/lecturer_overview.html       — Lecturer → courses → students overview
├── lecturer/ai_tutor.html             — Lecturer AI teaching assistant

database/studylock_db.sql              — New tables: enrollments, topic_progress
                                         10 default CS courses pre-loaded
```

---

## 🛠️ v4.1 — Bug Fixes, Docker & Tests

### 🐛 Fixed
| Bug | Fix |
|-----|-----|
| Lecturer AI Tutor returned 403 Forbidden | Added `/lecturer/ai-tutor/ask` endpoint (was incorrectly calling `/student/ai-tutor/ask`, which is restricted to `ROLE_STUDENT`) |
| `GeminiService` misnamed | Renamed to `OpenRouterService` everywhere (class, field, templates) — the integration always used the OpenRouter API + `anthropic/claude-haiku-4-5`, never Gemini |

### 🐳 Docker Deployment
A multi-stage `Dockerfile` and `docker-compose.yml` (app + MySQL) are now included.

**First time only** — create your local secrets file (never committed, see "Secrets" section below):
```bash
cp .env.example .env
# then edit .env and set your real OPENROUTER_API_KEY and MYSQL_ROOT_PASSWORD
```

```bash
# Build & run app + MySQL together
docker compose up --build

# App available at http://localhost:8080
# MySQL exposed on localhost:3306 (db: studylock_db, user: root, pass: from your .env)
```

`application.properties` reads `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`, and `OPENROUTER_API_KEY` from the environment (falling back to the
original XAMPP defaults for local `mvn spring-boot:run` use, except `openrouter.api.key` which has
no real default — see below).

### ✅ Unit Tests
JUnit 5 + Mockito tests added under `src/test/java/com/studylock/`:
- `service/EnrollmentServiceTest` — enroll/unenroll, topic completion, course reports
- `service/UserServiceTest` — registration, password change, role/active toggling, stats
- `service/AcademicServiceTest` — course/lecturer assignment, submission status (late/on-time), grading
- `util/SecurityUtilsTest` — current-user resolution from the security context

Run tests + JaCoCo coverage report:
```bash
mvn test
# Coverage report: target/site/jacoco/index.html
```

---

## 🎓 v4.2 — Semester & Credit-Hour Enrollment Rules

### New rules enforced on enrollment
| Rule | Detail |
|------|--------|
| Max 6 courses | A student can be enrolled in at most 6 courses at once. |
| Max 18 credit hours | Each course now has 1–4 credit hours (admin-set). A student's total credit hours across all enrollments can't exceed 18. |
| Semester gating | Each student has a semester (1–8); each course has a semester (1–8). Students can freely enroll in courses at their own semester or earlier. |
| Future-semester approval | Enrolling in a course from a *later* semester is blocked. The student must click **Request Approval**, which notifies all admins. Only after an admin approves (Admin → Enrollment Requests) does the enrollment actually happen — automatically, the moment it's approved. |

### What's new in the UI
- **Searchable / hover course dropdowns** everywhere a course is picked — enrolling, creating a lecture, an assignment, or a quiz. Type to filter, hover to browse; each option shows subject, semester and credit hours. Falls back to a plain `<select>` if JS doesn't enhance it, so nothing breaks.
- **Student enrollment page** now shows a courses-used / credit-hours-used progress summary, disables the Enroll button (with a tooltip explaining why) once either cap would be exceeded, and swaps the Enroll button for a **Request Approval** button on future-semester courses.
- **Admin → Courses**: course creation form now asks for Credit Hours (1–4) and Semester (1–8); both show in the courses table.
- **Admin → Users**: each student's name now shows their current semester as a badge, and a small inline dropdown lets admins change a student's semester directly.
- **Admin → Enrollment Requests** (new sidebar item, with a live pending-count badge): approve or decline future-semester enrollment requests. Approving enrolls the student immediately (re-checking the 6-course/18-credit caps); declining notifies the student with an optional reason.
- **Registration page**: students now pick their starting semester at sign-up.

### Bug fix
- **Admin → Reports tab showing nothing**: `SessionService.getGlobalStats()` only returned a `totalHours` key, but `admin/reports.html` read `sessionStats.totalMinutes` (which silently resolved to nothing). Fixed by returning both `totalHours` and `totalMinutes`. The whole `/admin/reports` controller method is also now defensive — each section catches its own failure and falls back to an empty value instead of taking down the whole page.

### Database
New table `enrollment_requests` (pending/approved/rejected future-semester requests) and new columns `users.semester`, `courses.credit_hours`, `courses.semester`. With `spring.jpa.hibernate.ddl-auto=update` these are created automatically on next startup; `database/studylock_db.sql` has also been updated for fresh installs, including a credit-hour/semester spread across the 10 default CS courses and a backfill `UPDATE` for any pre-existing rows.

### Tests added
- `service/EnrollmentServiceTest` — rewritten: 6-course cap, 18-credit-hour cap (including the exact-18 boundary), semester gating (allowed at own/earlier semester, blocked for future semester), the full request → approve → auto-enroll flow, and reject-with-notification.
- `service/UserServiceTest` — semester defaulting on registration (1 for students, null for lecturers), explicit semester on registration, `updateSemester` (including the "lecturers don't have a semester" guard), `adminCreateUser` with semester.
- `service/AcademicServiceTest` — `adminCreateCourse` now asserts default and explicit credit-hours/semester; new regression test for the `getStudentAttempts` lazy-loading fix below.
- `service/SessionServiceTest` (new) — regression test locking in the `totalMinutes` reports fix.

---

## 🐞 v4.2.2 — Two more bug fixes + full dropdown coverage

### Bug fix 1: `NullPointerException`-style SpEL crash on `/student/enrollments`
`enrollments.size() >= maxCourses or (currentCreditHours + c.creditHours) > maxCreditHours` crashed with
`EL1030E: The operator 'ADD' is not supported between objects of type 'java.lang.Integer' and 'null'`
whenever a course had a `NULL` `credit_hours` value — which every course created **before** this update has,
since `ddl-auto=update` only adds the new column, it doesn't backfill existing rows.

Fixed in two layers:
- **Template**: `enrollments.html` and `admin/courses.html` now use the Thymeleaf Elvis operator (`${c.creditHours ?: 3}`) everywhere `creditHours` is read, so a `null` value can never reach an arithmetic or comparison expression again.
- **Data**: if you're upgrading an existing database (not a fresh import), run this once against your `studylock_db`:
  ```sql
  UPDATE courses SET credit_hours = 3 WHERE credit_hours IS NULL;
  UPDATE courses SET semester = 1 WHERE semester IS NULL;
  UPDATE users SET semester = 1 WHERE role = 'STUDENT' AND semester IS NULL;
  ```

### Bug fix 2: `LazyInitializationException` on `/admin/reports/student/{id}`
Opening a student's report from Admin → Reports threw a 500 with `org.hibernate.LazyInitializationException: could not initialize proxy [Quiz#1] - no Session` at `a.quiz.title` (student_report.html line 78).

`AcademicService.getStudentAttempts()` was loading `QuizAttempt`s via a plain query, so `attempt.quiz` stayed an uninitialized Hibernate lazy proxy. By the time Thymeleaf rendered the page, the database session was already closed, so accessing `.title` on that proxy threw. Fixed by switching to a `JOIN FETCH`-based repository query (`findByStudentIdWithQuizOrderByCreatedAtDesc`) that eagerly loads the `Quiz` in the same query — the same pattern already used elsewhere in this codebase for `quiz_result.html`. This one service method feeds three pages (`admin/student_report`, `student/home`, `student/academic`), so all three are fixed by the one change.

### Full searchable-dropdown coverage
Every remaining plain course/subject `<select>` in the app has now been upgraded to the searchable/hover dropdown (type to filter, hover to browse, subject/semester/credit-hours shown per option):
- Lecturer **Attendance** course filter
- Lecturer **Student Marks** course filter
- Lecturer **AI Teaching Assistant** subject selector
- Student **AI Tutor** subject selector
- Student **Topic Study Session** course selector
- Admin **Create Course** → Assign Lecturer, and the standalone **Assign Lecturer** modal (now also searchable, generalized via a new `searchable-select` class alongside `course-select` — same JS enhancer powers both)

The enhancer never replaces or removes the original `<select>` — it hides it and keeps it in the DOM, so every existing `onchange="..."` handler, `name`/`th:field` binding, and any JS that reads `select.value` or `option.dataset.xxx` keeps working exactly as before.

---

## 🔒 v4.2.3 — Secrets removed from source, .gitignore added

**What was wrong:** there was no `.gitignore` in this project at all. That meant `target/` (Maven build
output) and, much worse, the real OpenRouter API key were committed straight into git history — the key
was hardcoded as a fallback default in three places: `application.properties`, `docker-compose.yml`, and
`OpenRouterService.java`'s `@Value` annotation. Anyone with read access to the repo (including a public
GitHub repo) could read the key straight out of the source.

### What changed
- **All three hardcoded key fallbacks removed.** `application.properties` now has a `CHANGE_ME...`
  placeholder instead of a real key; `OpenRouterService.java`'s `@Value("${openrouter.api.key}")` has no
  default at all (app will fail fast with a clear Spring error if you forget to set it, instead of
  silently working with someone else's key); `docker-compose.yml` now reads `${OPENROUTER_API_KEY}` and
  `${MYSQL_ROOT_PASSWORD}` from a `.env` file with no fallback value.
- **`.gitignore` added** (didn't exist before) — excludes `target/`, `.env`, `application-local.properties`,
  IDE folders, logs, uploaded user files, and common secret-file patterns (`*.pem`, `*.key`, etc.).
- **`.env.example`** and **`application-local.properties.example`** added — safe, placeholder-only
  templates that *are* committed, so you (or anyone cloning the repo) know what variables to set without
  ever seeing a real secret in git.

### What you need to do
1. **Rotate the leaked key.** Since it was committed to a public/shared repo, treat it as compromised —
   go to OpenRouter, revoke that key, and generate a new one, even after removing it from the code.
2. **Set up your local secrets** (one-time):
   ```bash
   cp .env.example .env
   # edit .env: put your NEW OpenRouter key in OPENROUTER_API_KEY
   ```
   For `mvn spring-boot:run` (non-Docker) instead, set the same value as a real environment variable, or:
   ```bash
   cp src/main/resources/application-local.properties.example src/main/resources/application-local.properties
   # edit application-local.properties with your real key, run with -Dspring-boot.run.profiles=local
   ```
3. **Scrub git history**, since removing the key from the current files doesn't remove it from old
   commits — anyone can still `git log -p` and find it. The simplest safe option for a personal/academic
   repo: delete the existing `.git` folder and `git init` fresh now that the secret-free `.gitignore` is
   in place, then push as a new history. If you need to preserve commit history, use
   [`git filter-repo`](https://github.com/newren/git-filter-repo) or BFG Repo-Cleaner to strip the key
   string from every commit, then force-push. Either way, rotating the key (step 1) matters more than
   scrubbing history — a revoked key is safe to leave in old commits.



---

## ✅ v4.2.4 — Test Fix + Credit-Limit Approval Flow + API Key + Rubric Deliverables

### Failing test fixed
`EnrollmentServiceTest.enroll_succeeds_whenCourseSemesterIsEqualToStudentSemester` was failing because
`EnrollmentService.enroll()` unconditionally called `enrollmentRequestRepository.existsByStudentIdAndCourseIdAndStatus()`
even when `semesterOk == true` (same-semester case). Fixed by short-circuiting:
`boolean hasApproval = !semesterOk && enrollmentRequestRepository.existsByStudentIdAndCourseIdAndStatus(...)`.
Java's `&&` now skips the DB call when not needed. **All 49 tests now pass.**

### Credit-limit exceeded → visible message + Request Approval button
Previously, when a student tried to enroll in a course that would push them over the 18-credit-hour cap,
the Enroll button just silently disabled with no explanation and no way to request special approval.
Fixed with a full backend + frontend flow:
- New `EnrollmentRequestReason` enum (`FUTURE_SEMESTER` | `CREDIT_LIMIT_EXCEEDED`)
- `EnrollmentRequest` extended with `reason`, `currentCreditHours`, `requestedCourseCreditHours` fields
- `EnrollmentService.enroll()` now throws `EnrollmentApprovalRequiredException(message, reason)` for both cases
- `requestApproval()` accepts the reason and stores snapshot data for admin context
- Student enrollments page shows a **visible inline warning** ("You've used X/18 credits — needs admin approval") and a **"Request Approval" button** specific to the credit-limit case
- Admin enrollment requests page shows credit-limit requests with "⚠️ Over credit-hour limit" + credit snapshot badges
- `EnrollmentServiceTest` fully rewritten — 23 tests covering both approval reasons, approval bypass, boundary cases

### New API key (OpenRouter) set
The new OpenRouter key is pre-filled in `.env` and `application-local.properties` (both gitignored).
`src/main/java/com/studylock/service/OpenRouterService.java` has no hardcoded key at all — only
`@Value("${openrouter.api.key}")`. This resolves the `401 Unauthorized` AI Tutor errors.

### Database schema updated
`database/studylock_db.sql` — `enrollment_requests` table now includes:
`reason ENUM('FUTURE_SEMESTER','CREDIT_LIMIT_EXCEEDED')`, `current_credit_hours INT`,
`requested_course_credit_hours INT`. For **existing databases** (not fresh installs), run:
```sql
ALTER TABLE enrollment_requests
  ADD COLUMN reason ENUM('FUTURE_SEMESTER','CREDIT_LIMIT_EXCEEDED') NOT NULL DEFAULT 'FUTURE_SEMESTER',
  ADD COLUMN current_credit_hours INT,
  ADD COLUMN requested_course_credit_hours INT;
```

### Rubric deliverables added (docs/ folder)
All four documents for the SCD rubric are now in the `docs/` folder inside the zip:

| File | Covers |
|------|--------|
| `docs/uml/class_diagram.puml` + `.png` | Class diagram — 16 entity classes, 6 enums, all relationships |
| `docs/uml/use_case_diagram.puml` + `.png` | Use case diagram — 3 actors, 40+ use cases |
| `docs/design_patterns.docx` | 8 patterns documented with GoF category, file location, code evidence, rationale |
| `docs/refactoring_log.docx` | 7 refactoring activities with technique, before/after, and measurable benefit |
| `docs/StudyLock_Presentation.pptx` | 20-slide detailed presentation covering features, patterns, advantages, user benefits |
