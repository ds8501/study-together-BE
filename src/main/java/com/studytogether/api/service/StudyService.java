package com.studytogether.api.service;

import com.studytogether.api.model.dto.request.CreateTopicRequest;
import com.studytogether.api.model.dto.request.LogSessionRequest;
import com.studytogether.api.model.dto.request.UpdateTopicRequest;
import com.studytogether.api.model.dto.response.*;
import com.studytogether.api.model.entity.CategoryEntity;
import com.studytogether.api.model.entity.StudySessionEntity;
import com.studytogether.api.model.entity.StudyStreakEntity;
import com.studytogether.api.model.entity.TopicEntity;
import com.studytogether.api.repository.StudyTogetherRepository;
import com.studytogether.api.util.QueryConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class StudyService {
    private final StudyTogetherRepository db;
    private final TransactionTemplate transactions;
    private final SessionService sessionService;

    public StudyService(StudyTogetherRepository db,
                        TransactionTemplate transactions,
                        SessionService sessionService) {
        this.db = db;
        this.transactions = transactions;
        this.sessionService = sessionService;
    }

    private static String text(String value) {
        if (value == null) return null;
        return value.isBlank() ? null : value.trim();
    }

    private static MapSqlParameterSource params(Object... pairs) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        for (int i = 0; i < pairs.length; i += 2) {
            p.addValue(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return p;
    }

    private static SafeUserResponse safeUser(Map<String, Object> row) {
        return new SafeUserResponse(
                ((Number) row.get("id")).longValue(),
                (String) row.get("name"),
                (String) row.get("email"),
                (String) row.get("avatar")
        );
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Timestamp ts) return ts.toInstant();
        if (value instanceof Instant inst) return inst;
        if (value instanceof java.util.Date date) return date.toInstant();
        return null;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public HealthResponse health() {
        return new HealthResponse("ok");
    }

    public ResponseEntity<WorkspaceResponse> workspace(HttpServletRequest request) {
        Long userId = sessionService.requireUserId(request);
        List<Map<String, Object>> memberships = db.queryForList(
                QueryConstants.WORKSPACE_FOR_USER,
                Map.of("user", userId)
        );
        if (memberships.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "You have not joined a workspace");
        }
        Map<String, Object> membership = memberships.get(0);
        Long workspaceId = ((Number) membership.get("id")).longValue();

        List<Map<String, Object>> memberRows = db.queryForList(
                QueryConstants.MEMBERS_FOR_WORKSPACE,
                Map.of("workspace", workspaceId)
        );
        List<WorkspaceMemberResponse> members = memberRows.stream()
                .map(row -> new WorkspaceMemberResponse(
                        safeUser(row),
                        String.valueOf(row.get("role")),
                        toInstant(row.get("joinedAt"))
                ))
                .toList();

        String inviteCode = "OWNER".equals(membership.get("role")) ? String.valueOf(membership.get("inviteCode")) : null;
        WorkspaceResponse result = new WorkspaceResponse(
                workspaceId,
                String.valueOf(membership.get("name")),
                inviteCode,
                members
        );
        return ResponseEntity.ok(result);
    }

    public ResponseEntity<StudyPlansResponse> studyPlans(HttpServletRequest request) {
        Long userId = sessionService.requireUserId(request);

        List<Map<String, Object>> memberships = db.queryForList(
                QueryConstants.WORKSPACE_ID_FOR_USER,
                Map.of("user", userId)
        );

        if (memberships.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "You have not joined a workspace");
        }

        Long workspaceId = ((Number) memberships.get(0).get("workspaceId")).longValue();

        List<Map<String, Object>> plans = db.queryForList(
                QueryConstants.PLANS_FOR_WORKSPACE,
                Map.of("workspace", workspaceId)
        );

        List<StudyPlanResponse> responsePlans = new ArrayList<>();
        for (Map<String, Object> plan : plans) {
            Long planId = ((Number) plan.get("id")).longValue();
            List<CategoryEntity> categories = db.query(
                    QueryConstants.CATEGORIES_FOR_PLAN,
                    Map.of("plan", planId),
                    CategoryEntity.ROW_MAPPER
            );
            List<CategoryResponse> categoryResponses = categories.stream()
                    .map(CategoryResponse::fromEntity)
                    .toList();
            List<Map<String, Object>> topics = db.queryForList(
                    QueryConstants.TOPICS_FOR_PLAN,
                    Map.of("plan", planId)
            );
            List<TopicResponse> topicData = new ArrayList<>();

            for (Map<String, Object> t : topics) {
                CategoryResponse category = null;
                if (t.get("c_id") != null) {
                    category = new CategoryResponse(
                            ((Number) t.get("c_id")).longValue(),
                            ((Number) t.get("c_plan")).longValue(),
                            (String) t.get("c_name"),
                            (String) t.get("c_description"),
                            (String) t.get("c_icon"),
                            (String) t.get("c_color"),
                            t.get("c_order") != null ? ((Number) t.get("c_order")).intValue() : 0
                    );
                }

                TopicResponse tr = new TopicResponse(
                        ((Number) t.get("id")).longValue(),
                        t.get("categoryId") != null ? ((Number) t.get("categoryId")).longValue() : null,
                        ((Number) t.get("studyPlanId")).longValue(),
                        ((Number) t.get("ownerId")).longValue(),
                        (String) t.get("title"),
                        (String) t.get("description"),
                        t.get("dayNumber") != null ? ((Number) t.get("dayNumber")).intValue() : null,
                        t.get("order") != null ? ((Number) t.get("order")).intValue() : 0,
                        t.get("difficulty") != null ? TopicEntity.Difficulty.valueOf(String.valueOf(t.get("difficulty"))) : TopicEntity.Difficulty.MEDIUM,
                        t.get("priority") != null ? TopicEntity.Priority.valueOf(String.valueOf(t.get("priority"))) : TopicEntity.Priority.MEDIUM,
                        t.get("estimatedHours") != null ? ((Number) t.get("estimatedHours")).doubleValue() : null,
                        t.get("status") != null ? TopicEntity.Status.valueOf(String.valueOf(t.get("status"))) : TopicEntity.Status.NOT_STARTED,
                        toInstant(t.get("createdAt")),
                        toInstant(t.get("updatedAt")),
                        category
                );
                topicData.add(tr);
            }

            PlanOwnerResponse owner = new PlanOwnerResponse(
                    ((Number) plan.get("owner_id")).longValue(),
                    (String) plan.get("owner_name"),
                    (String) plan.get("owner_avatar"),
                    ""
            );

            StudyPlanResponse spr = new StudyPlanResponse(
                    planId,
                    ((Number) plan.get("workspaceId")).longValue(),
                    ((Number) plan.get("ownerId")).longValue(),
                    (String) plan.get("name"),
                    (String) plan.get("description"),
                    toInstant(plan.get("createdAt")),
                    owner,
                    categoryResponses,
                    topicData
            );
            responsePlans.add(spr);
        }

        return ResponseEntity.ok(new StudyPlansResponse(responsePlans));
    }

    public StudyStatsResponse studyStats(HttpServletRequest request) {
        return readStats(sessionService.requireUserId(request));
    }

    public ResponseEntity<LogSessionResponse> logSession(LogSessionRequest request, HttpServletRequest httpRequest) {
        if (request == null) throw bad("Request body is required");
        if (request.topicId() == null) throw bad("topicId is required");
        if (request.durationMinutes() == null || request.durationMinutes() < 1 || request.durationMinutes() > 720) {
            throw bad("Duration must be between 1 and 720");
        }
        String notes = text(request.notes());
        if (notes != null && notes.length() > 2000) {
            throw bad("Notes must be at most 2000 characters");
        }

        Long userId = sessionService.requireUserId(httpRequest);
        Long topicId = request.topicId();
        int minutes = request.durationMinutes();
        String requestedZone = text(request.timeZone());

        List<Map<String, Object>> topics = db.queryForList(
                QueryConstants.TOPIC_CHECK_FOR_SESSION,
                params("user", userId, "topic", topicId)
        );
        if (topics.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found on your personal roadmap");
        }

        String zone = requestedZone;
        if (zone == null) {
            zone = db.queryForOptional(
                    QueryConstants.USER_TIME_ZONE,
                    Map.of("id", userId),
                    (rs, rowNum) -> rs.getString("timeZone")
            ).orElse("Asia/Kolkata");
        }
        if (zone == null || zone.isBlank()) zone = "Asia/Kolkata";
        LocalDate today;
        try {
            today = LocalDate.now(ZoneId.of(zone));
        } catch (Exception e) {
            throw bad("Use a valid time zone");
        }
        final String validZone = zone;
        final LocalDate day = today;

        StudyStreakEntity streak = transactions.execute(status -> {
            Optional<StudyStreakEntity> oldOpt = db.queryForOptional(
                    QueryConstants.STREAK_FOR_USER,
                    Map.of("id", userId),
                    StudyStreakEntity.ROW_MAPPER
            );

            int current = oldOpt.map(StudyStreakEntity::currentStreak).orElse(0);
            int best = oldOpt.map(StudyStreakEntity::bestStreak).orElse(0);
            LocalDate last = oldOpt.map(StudyStreakEntity::lastStudyDate)
                    .map(inst -> inst.atZone(ZoneOffset.UTC).toLocalDate())
                    .orElse(null);

            if (day.equals(last)) {
                if (current < 1) current = 1;
            } else if (day.minusDays(1).equals(last)) {
                current++;
            } else {
                current = 1;
            }
            best = Math.max(best, current);

            db.update(QueryConstants.UPDATE_USER_TIME_ZONE, params("zone", validZone, "id", userId));
            db.queryForObject(
                    QueryConstants.INSERT_STUDY_SESSION,
                    params("user", userId, "topic", topicId, "date", Timestamp.from(day.atStartOfDay(ZoneOffset.UTC).toInstant()), "minutes", minutes, "notes", notes),
                    Long.class
            );
            db.update(
                    QueryConstants.UPSERT_STUDY_STREAK,
                    params("user", userId, "current", current, "best", best, "day", Timestamp.from(day.atStartOfDay(ZoneOffset.UTC).toInstant()))
            );
            return new StudyStreakEntity(userId, current, best, day.atStartOfDay(ZoneOffset.UTC).toInstant(), Instant.now());
        });

        int currentStreak = streak != null ? streak.currentStreak() : 0;
        int bestStreak = streak != null ? streak.bestStreak() : 0;
        StudyStatsResponse stats = readStats(userId);
        return ResponseEntity.status(201).body(new LogSessionResponse(true, currentStreak, bestStreak, stats));
    }

    public ResponseEntity<TopicSingleResponse> addTopic(Long planId, CreateTopicRequest request, HttpServletRequest httpRequest) {
        if (request == null) throw bad("Request body is required");
        Long userId = sessionService.requireUserId(httpRequest);

        String title = text(request.title());
        if (title == null || title.length() > 160) throw bad("title is invalid");
        String description = text(request.description());
        if (description != null && description.length() > 3000) throw bad("Description must be at most 3000 characters");
        String categoryName = text(request.categoryName());
        if (categoryName != null && categoryName.length() > 80) throw bad("Category name is invalid");
        if (request.dayNumber() != null && request.dayNumber() < 1) throw bad("Day must be a positive integer");
        if (request.estimatedHours() != null && (request.estimatedHours() <= 0 || request.estimatedHours() > 1000)) {
            throw bad("Estimated hours must be between 0 and 1000");
        }

        List<Map<String, Object>> plans = db.queryForList(
                QueryConstants.STUDY_PLAN_OWNER_CHECK,
                params("user", userId, "plan", planId)
        );
        if (plans.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Roadmap not found for this account");
        }

        Long categoryId = request.categoryId();
        if (categoryId != null) {
            Integer count = db.queryForObject(
                    QueryConstants.CHECK_CATEGORY_IN_PLAN,
                    params("id", categoryId, "plan", planId),
                    Integer.class
            );
            if (count == null || count == 0) {
                throw bad("Category does not belong to this roadmap");
            }
        }

        if (categoryId == null && categoryName != null && !categoryName.isBlank()) {
            categoryId = db.queryForObject(
                    QueryConstants.UPSERT_CATEGORY,
                    params("plan", planId, "name", categoryName),
                    Long.class
            );
        }

        TopicEntity.Difficulty difficulty = request.difficulty() != null ? request.difficulty() : TopicEntity.Difficulty.MEDIUM;
        TopicEntity.Priority priority = request.priority() != null ? request.priority() : TopicEntity.Priority.MEDIUM;
        TopicEntity.Status status = request.status() != null ? request.status() : TopicEntity.Status.NOT_STARTED;

        Long topicId = db.queryForObject(
                QueryConstants.INSERT_TOPIC,
                params("category", categoryId, "plan", planId, "owner", userId,
                        "title", title,
                        "description", description,
                        "day", request.dayNumber(),
                        "difficulty", difficulty.name(),
                        "priority", priority.name(),
                        "hours", request.estimatedHours(),
                        "status", status.name()),
                Long.class
        );

        TopicEntity topic = db.queryForOptional(
                QueryConstants.TOPIC_BY_ID,
                Map.of("id", topicId),
                TopicEntity.ROW_MAPPER
        ).orElseThrow(() -> bad("Failed to load topic"));

        return ResponseEntity.status(201).body(new TopicSingleResponse(TopicResponse.fromEntity(topic)));
    }

    public ResponseEntity<TopicSingleResponse> updateTopic(Long topicId, UpdateTopicRequest request, HttpServletRequest httpRequest) {
        if (request == null) throw bad("Request body is required");
        Long userId = sessionService.requireUserId(httpRequest);

        String title = text(request.title());
        if (title != null && title.length() > 160) throw bad("title is invalid");
        String description = text(request.description());
        if (description != null && description.length() > 3000) throw bad("Description must be at most 3000 characters");
        if (text(request.categoryName()) != null) throw bad("Changing a topic category requires categoryId");
        if (request.dayNumber() != null && request.dayNumber() < 1) throw bad("Day must be a positive integer");
        if (request.estimatedHours() != null && (request.estimatedHours() <= 0 || request.estimatedHours() > 1000)) {
            throw bad("Estimated hours must be between 0 and 1000");
        }

        TopicEntity old = db.queryForOptional(
                QueryConstants.TOPIC_FOR_UPDATE,
                params("user", userId, "topic", topicId),
                TopicEntity.ROW_MAPPER
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found for this account"));

        Long planId = old.studyPlanId();

        if (request.categoryId() != null) {
            Integer count = db.queryForObject(
                    QueryConstants.CHECK_CATEGORY_IN_PLAN,
                    params("id", request.categoryId(), "plan", planId),
                    Integer.class
            );
            if (count == null || count == 0) {
                throw bad("Category does not belong to this roadmap");
            }
        }

        List<String> sets = new ArrayList<>();
        MapSqlParameterSource values = new MapSqlParameterSource()
                .addValue("id", topicId)
                .addValue("updatedAt", Timestamp.from(Instant.now()));

        if (title != null) {
            sets.add("\"title\"=:title");
            values.addValue("title", title);
        }
        if (description != null) {
            sets.add("\"description\"=:description");
            values.addValue("description", description);
        }
        if (request.categoryId() != null) {
            sets.add("\"categoryId\"=:categoryId");
            values.addValue("categoryId", request.categoryId());
        }
        if (request.dayNumber() != null) {
            sets.add("\"dayNumber\"=:dayNumber");
            values.addValue("dayNumber", request.dayNumber());
        }
        if (request.estimatedHours() != null) {
            sets.add("\"estimatedHours\"=:estimatedHours");
            values.addValue("estimatedHours", request.estimatedHours());
        }
        if (request.order() != null) {
            sets.add("\"order\"=:order");
            values.addValue("order", request.order());
        }
        if (request.difficulty() != null) {
            sets.add("\"difficulty\"=CAST(:difficulty AS \"TopicDifficulty\")");
            values.addValue("difficulty", request.difficulty().name());
        }
        if (request.priority() != null) {
            sets.add("\"priority\"=CAST(:priority AS \"TopicPriority\")");
            values.addValue("priority", request.priority().name());
        }
        if (request.status() != null) {
            sets.add("\"status\"=CAST(:status AS \"TopicStatus\")");
            values.addValue("status", request.status().name());
        }

        if (!sets.isEmpty()) {
            sets.add("\"updatedAt\"=:updatedAt");
            db.update("UPDATE \"Topic\" SET " + String.join(",", sets) + " WHERE \"id\"=:id", values);
        }

        TopicEntity updated = db.queryForOptional(
                QueryConstants.TOPIC_BY_ID,
                Map.of("id", topicId),
                TopicEntity.ROW_MAPPER
        ).orElseThrow(() -> bad("Failed to load topic"));

        return ResponseEntity.ok(new TopicSingleResponse(TopicResponse.fromEntity(updated)));
    }

    public ResponseEntity<Void> deleteTopic(Long topicId, HttpServletRequest request) {
        Long userId = sessionService.requireUserId(request);
        int count = db.update(
                QueryConstants.DELETE_TOPIC,
                params("topic", topicId, "user", userId)
        );
        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found for this account");
        }
        return ResponseEntity.noContent().build();
    }

    public ResponseEntity<RotateInviteResponse> rotateInvite(HttpServletRequest request) {
        Long userId = sessionService.requireUserId(request);
        List<Map<String, Object>> rows = db.queryForList(
                QueryConstants.OWNER_WORKSPACE_ID_FOR_USER,
                Map.of("user", userId)
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the workspace owner can rotate the invite");
        }
        Object workspaceId = rows.get(0).get("workspaceId");
        String inviteCode = sessionService.randomUrl(18);
        db.update(
                QueryConstants.UPDATE_WORKSPACE_INVITE,
                params("code", inviteCode, "id", workspaceId)
        );
        return ResponseEntity.ok(new RotateInviteResponse(inviteCode));
    }

    private StudyStatsResponse readStats(Long userId) {
        String zoneName = db.queryForOptional(
                QueryConstants.USER_TIME_ZONE,
                Map.of("id", userId),
                (rs, rowNum) -> rs.getString("timeZone")
        ).orElse("Asia/Kolkata");

        ZoneId zone;
        try {
            zone = ZoneId.of(zoneName);
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Kolkata");
        }

        LocalDate today = LocalDate.now(zone);
        LocalDate yesterday = today.minusDays(1);
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);

        Optional<StudyStreakEntity> storedOpt = db.queryForOptional(
                QueryConstants.STREAK_FOR_USER,
                Map.of("id", userId),
                StudyStreakEntity.ROW_MAPPER
        );

        int current = storedOpt.map(StudyStreakEntity::currentStreak).orElse(0);
        int best = storedOpt.map(StudyStreakEntity::bestStreak).orElse(0);
        LocalDate last = storedOpt.map(StudyStreakEntity::lastStudyDate)
                .map(inst -> inst.atZone(ZoneOffset.UTC).toLocalDate())
                .orElse(null);

        if (last == null || last.isBefore(yesterday)) {
            current = 0;
            if (storedOpt.isPresent() && storedOpt.get().currentStreak() != 0) {
                db.update(QueryConstants.RESET_CURRENT_STREAK, Map.of("id", userId));
            }
        }

        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1);
        Instant monthStartInstant = monthStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthEndInstant = monthEnd.atStartOfDay(ZoneOffset.UTC).toInstant();

        List<StudySessionEntity> sessions = db.query(
                QueryConstants.MONTHLY_SESSIONS,
                params("user", userId, "start", Timestamp.from(monthStartInstant), "end", Timestamp.from(monthEndInstant)),
                StudySessionEntity.ROW_MAPPER
        );

        Set<LocalDate> active = new HashSet<>();
        for (StudySessionEntity session : sessions) {
            if (session.date() != null) {
                active.add(session.date().atZone(ZoneOffset.UTC).toLocalDate());
            }
        }

        String[] labels = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

        int daysInMonth = today.lengthOfMonth();
        List<MonthDayStatsResponse> month = new ArrayList<>(daysInMonth);
        int totalDaysStudiedThisMonth = 0;

        for (int d = 1; d <= daysInMonth; d++) {
            LocalDate date = monthStart.withDayOfMonth(d);
            boolean isActive = active.contains(date);
            if (isActive) totalDaysStudiedThisMonth++;
            boolean isToday = date.equals(today);
            boolean isFuture = date.isAfter(today);
            month.add(new MonthDayStatsResponse(
                    date.toString(),
                    d,
                    labels[date.getDayOfWeek().getValue() % 7],
                    isActive,
                    isToday,
                    isFuture
            ));
        }

        List<WeekDayStatsResponse> week = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            week.add(new WeekDayStatsResponse(
                    date.toString(),
                    labels[date.getDayOfWeek().getValue() % 7],
                    active.contains(date),
                    date.equals(today)
            ));
        }

        String monthName = today.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
        int year = today.getYear();

        return new StudyStatsResponse(current, best, totalDaysStudiedThisMonth, monthName, year, month, week);
    }
}
