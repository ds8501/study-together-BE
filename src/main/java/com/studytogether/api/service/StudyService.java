package com.studytogether.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.studytogether.api.repository.StudyTogetherRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class StudyService {
    private static final String SESSION_COOKIE = "study_session";
    private static final long SESSION_TTL_SECONDS = 14L * 24 * 60 * 60;
    private static final long SESSION_TTL_MILLIS = SESSION_TTL_SECONDS * 1000;
    private final StudyTogetherRepository db;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();
    private final String authSecret;
    private final String frontendUrl;
    private final String googleClientId;
    private final String googleClientSecret;
    private final String googleRedirectUri;
    private final boolean production;

    public StudyService(StudyTogetherRepository db, PasswordEncoder passwords, TransactionTemplate transactions,
                                ObjectMapper mapper, @Value("${AUTH_SECRET:}") String authSecret,
                                @Value("${FRONTEND_URL:${FRONTEND_ORIGIN:http://localhost:3000}}") String frontendUrl,
                                @Value("${GOOGLE_CLIENT_ID:}") String googleClientId,
                                @Value("${GOOGLE_CLIENT_SECRET:}") String googleClientSecret,
                                @Value("${GOOGLE_REDIRECT_URI:}") String googleRedirectUri,
                                @Value("${NODE_ENV:development}") String nodeEnv) {
        this.db = db;
        this.passwords = passwords;
        this.transactions = transactions;
        this.mapper = mapper;
        if (authSecret == null || authSecret.length() < 32)
            throw new IllegalStateException("AUTH_SECRET must be set to at least 32 characters");
        this.authSecret = authSecret;
        this.frontendUrl = trimSlash(frontendUrl);
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
        this.googleRedirectUri = googleRedirectUri;
        this.production = "production".equalsIgnoreCase(nodeEnv);
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    private static void addUpdate(Map<String, Object> body, MapSqlParameterSource values, List<String> sets, String input, String column, boolean ignored) {
        if (body.containsKey(input)) {
            sets.add("\"" + column + "\"=:" + input);
            values.addValue(input, body.get(input));
        }
    }

    private static Map<String, Object> categoryMap(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.get("c_id"));
        m.put("studyPlanId", row.get("c_plan"));
        m.put("name", row.get("c_name"));
        m.put("description", row.get("c_description"));
        m.put("icon", row.get("c_icon"));
        m.put("color", row.get("c_color"));
        m.put("order", row.get("c_order"));
        return m;
    }

    private static Map<String, Object> withoutPrefix(Map<String, Object> row, String prefix) {
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach((key, value) -> {
            if (!key.startsWith("c_")) result.put(key, value);
        });
        return result;
    }

    private static String requiredText(Map<String, Object> body, String key, int min, int max) {
        String value = text(body.get(key));
        if (value == null || value.trim().length() < min || value.length() > max) throw bad(key + " is invalid");
        return value.trim();
    }

    private static String email(String raw) {
        String value = raw.trim().toLowerCase();
        if (!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw bad("Enter a valid email address");
        return value;
    }

    private static String text(Object value) {
        if (value == null) return null;
        String string = String.valueOf(value);
        return string.isBlank() ? null : string;
    }

    private static String enumOr(Object value, String fallback, List<String> accepted) {
        if (value == null) return fallback;
        String result = String.valueOf(value);
        if (!accepted.contains(result)) throw bad("Invalid value: " + result);
        return result;
    }

    private static int integer(Object value, String message, int min, int max) {
        if (!(value instanceof Number n) || n.doubleValue() != n.intValue() || n.intValue() < min || n.intValue() > max)
            throw bad(message);
        return n.intValue();
    }

    private static double number(Object value, String message) {
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) throw bad(message);
        return n.doubleValue();
    }

    private static int asInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static LocalDate asLocalDate(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        if (value instanceof java.util.Date date) return date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        if (value instanceof LocalDate date) return date;
        return null;
    }

    private static String cuid() {
        return "c" + Long.toString(System.currentTimeMillis(), 36) + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimSlash(String value) {
        if (value == null || value.isBlank()) return "http://localhost:3000";
        return value.replaceAll("/+$", "");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static MapSqlParameterSource params(Object... pairs) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        for (int i = 0; i < pairs.length; i += 2) p.addValue(String.valueOf(pairs[i]), pairs[i + 1]);
        return p;
    }

    private static Map<String, Object> safeUser(Map<String, Object> row) {
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("id", row.get("id"));
        u.put("name", row.get("name"));
        u.put("email", row.get("email"));
        u.put("avatar", row.get("avatar"));
        return u;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public Map<String, String> health() {
        return Map.of("status", "ok");
    }









    public ResponseEntity<?> workspace(HttpServletRequest request) {
        String userId = requireUserId(request);
        List<Map<String, Object>> memberships = db.queryForList("SELECT wm.\"role\", w.\"id\",w.\"name\",w.\"inviteCode\" FROM \"WorkspaceMember\" wm JOIN \"Workspace\" w ON w.\"id\"=wm.\"workspaceId\" WHERE wm.\"userId\"=:user ORDER BY wm.\"joinedAt\" LIMIT 1", Map.of("user", userId));
        if (memberships.isEmpty())
            return ResponseEntity.status(404).body(Map.of("error", "You have not joined a workspace"));
        Map<String, Object> membership = memberships.get(0);
        List<Map<String, Object>> memberRows = db.queryForList("SELECT u.\"id\",u.\"name\",u.\"email\",u.\"avatar\",wm.\"role\",wm.\"joinedAt\" FROM \"WorkspaceMember\" wm JOIN \"User\" u ON u.\"id\"=wm.\"userId\" WHERE wm.\"workspaceId\"=:workspace ORDER BY wm.\"joinedAt\"", Map.of("workspace", membership.get("id")));
        List<Map<String, Object>> members = memberRows.stream().map(row -> Map.of("user", safeUser(row), "role", row.get("role"), "joinedAt", row.get("joinedAt"))).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", membership.get("id"));
        result.put("name", membership.get("name"));
        if ("OWNER".equals(membership.get("role"))) result.put("inviteCode", membership.get("inviteCode"));
        result.put("members", members);
        return ResponseEntity.ok(result);
    }

    public ResponseEntity<?> studyPlans(HttpServletRequest request) {
        String userId = requireUserId(request);

        List<Map<String, Object>> memberships =
                db.queryForList("SELECT \"workspaceId\" FROM \"WorkspaceMember\" WHERE \"userId\"=:user ORDER BY \"joinedAt\" LIMIT 1", Map.of("user", userId));

        if (memberships.isEmpty())
            return ResponseEntity.status(404).body(Map.of("error", "You have not joined a workspace"));

        Object workspaceId = memberships.get(0).get("workspaceId");

        List<Map<String, Object>> plans =
                db.queryForList("SELECT p.*, u.\"id\" AS owner_id,u.\"name\" AS owner_name,u.\"avatar\" AS owner_avatar FROM \"StudyPlan\" p JOIN \"User\" u ON u.\"id\"=p.\"ownerId\" WHERE p.\"workspaceId\"=:workspace ORDER BY p.\"createdAt\"", Map.of("workspace", workspaceId));

        List<Map<String, Object>> responsePlans = new ArrayList<>();
        for (Map<String, Object> plan : plans) {
            String planId = String.valueOf(plan.get("id"));
            List<Map<String, Object>> categories =
                    db.queryForList("SELECT * FROM \"Category\" WHERE \"studyPlanId\"=:plan ORDER BY \"order\"", Map.of("plan", planId));
            List<Map<String, Object>> topics =
                    db.queryForList("SELECT t.*, c.\"id\" AS c_id,c.\"studyPlanId\" AS c_plan,c.\"name\" AS c_name,c.\"description\" AS c_description,c.\"icon\" AS c_icon,c.\"color\" AS c_color,c.\"order\" AS c_order FROM \"Topic\" t LEFT JOIN \"Category\" c ON c.\"id\"=t.\"categoryId\" AND c.\"studyPlanId\"=t.\"studyPlanId\" WHERE t.\"studyPlanId\"=:plan ORDER BY t.\"dayNumber\" NULLS LAST,t.\"order\"", Map.of("plan", planId));
            List<Map<String, Object>> topicData = new ArrayList<>();

            for (Map<String, Object> topic : topics) {
                Map<String, Object> output = withoutPrefix(topic, "t.");
                Object categoryId = topic.get("c_id");
                if (categoryId != null) output.put("category", categoryMap(topic));
                else output.put("category", null);
                topicData.add(output);
            }
            Map<String, Object> owner = new LinkedHashMap<>();
            owner.put("id", plan.get("owner_id"));
            owner.put("name", plan.get("owner_name"));
            owner.put("avatar", plan.get("owner_avatar"));
            owner.put("email", "");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", plan.get("id"));
            result.put("workspaceId", plan.get("workspaceId"));
            result.put("ownerId", plan.get("ownerId"));
            result.put("name", plan.get("name"));
            result.put("description", plan.get("description"));
            result.put("createdAt", plan.get("createdAt"));
            result.put("owner", owner);
            result.put("categories", categories);
            result.put("topics", topicData);
            responsePlans.add(result);
        }

        return ResponseEntity.ok(Map.of("plans", responsePlans));
    }

    public Map<String, Object> studyStats(HttpServletRequest request) {
        return readStats(requireUserId(request));
    }

    public ResponseEntity<?> logSession(Map<String, Object> body, HttpServletRequest request) {
        String userId = requireUserId(request);
        String topicId = requiredText(body, "topicId", 1, 100);
        int minutes = integer(body.get("durationMinutes"), "Duration must be between 1 and 720", 1, 720);
        String notes = text(body.get("notes"));
        if (notes != null && notes.length() > 2000) throw bad("Notes must be at most 2000 characters");
        String requestedZone = text(body.get("timeZone"));
        List<Map<String, Object>> topics = db.queryForList("SELECT t.\"id\" FROM \"Topic\" t JOIN \"StudyPlan\" p ON p.\"id\"=t.\"studyPlanId\" AND p.\"ownerId\"=t.\"ownerId\" JOIN \"WorkspaceMember\" wm ON wm.\"workspaceId\"=p.\"workspaceId\" AND wm.\"userId\"=:user WHERE t.\"id\"=:topic AND t.\"ownerId\"=:user", params("user", userId, "topic", topicId));
        if (topics.isEmpty())
            return ResponseEntity.status(404).body(Map.of("error", "Topic not found on your personal roadmap"));
        String zone = requestedZone;
        if (zone == null)
            zone = db.queryForObject("SELECT \"timeZone\" FROM \"User\" WHERE \"id\"=:id", Map.of("id", userId), String.class);
        if (zone == null || zone.isBlank()) zone = "Asia/Kolkata";
        LocalDate today;
        try {
            today = LocalDate.now(ZoneId.of(zone));
        } catch (Exception e) {
            throw bad("Use a valid time zone");
        }
        final String validZone = zone;
        final LocalDate day = today;
        Map<String, Object> streak = transactions.execute(status -> {
            Map<String, Object> old = db.queryForList("SELECT \"currentStreak\",\"bestStreak\",\"lastStudyDate\" FROM \"StudyStreak\" WHERE \"userId\"=:id", Map.of("id", userId)).stream().findFirst().orElse(Map.of());
            int current = asInt(old.get("currentStreak"));
            int best = asInt(old.get("bestStreak"));
            LocalDate last = asLocalDate(old.get("lastStudyDate"));
            if (day.equals(last)) {
                if (current < 1) current = 1;
            } else if (day.minusDays(1).equals(last)) current++;
            else current = 1;
            best = Math.max(best, current);
            db.update("UPDATE \"User\" SET \"timeZone\"=:zone WHERE \"id\"=:id", params("zone", validZone, "id", userId));
            db.update("INSERT INTO \"StudySession\" (\"id\",\"userId\",\"topicId\",\"date\",\"durationMinutes\",\"notes\") VALUES (:id,:user,:topic,:date,:minutes,:notes)", params("id", cuid(), "user", userId, "topic", topicId, "date", Timestamp.from(day.atStartOfDay(ZoneOffset.UTC).toInstant()), "minutes", minutes, "notes", notes));
            db.update("INSERT INTO \"StudyStreak\" (\"userId\",\"currentStreak\",\"bestStreak\",\"lastStudyDate\",\"updatedAt\") VALUES (:user,:current,:best,:day,CURRENT_TIMESTAMP) ON CONFLICT (\"userId\") DO UPDATE SET \"currentStreak\"=EXCLUDED.\"currentStreak\",\"bestStreak\"=EXCLUDED.\"bestStreak\",\"lastStudyDate\"=EXCLUDED.\"lastStudyDate\",\"updatedAt\"=CURRENT_TIMESTAMP", params("user", userId, "current", current, "best", best, "day", Timestamp.from(day.atStartOfDay(ZoneOffset.UTC).toInstant())));
            return Map.of("currentStreak", current, "bestStreak", best);
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.putAll(streak);
        result.put("stats", readStats(userId));
        return ResponseEntity.status(201).body(result);
    }

    public ResponseEntity<?> addTopic(String planId, Map<String, Object> body, HttpServletRequest request) {
        String userId = requireUserId(request);
        validateTopic(body, false);
        List<Map<String, Object>> plans = db.queryForList("SELECT p.\"id\" FROM \"StudyPlan\" p JOIN \"WorkspaceMember\" wm ON wm.\"workspaceId\"=p.\"workspaceId\" AND wm.\"userId\"=:user WHERE p.\"id\"=:plan AND p.\"ownerId\"=:user", params("user", userId, "plan", planId));
        if (plans.isEmpty())
            return ResponseEntity.status(404).body(Map.of("error", "Roadmap not found for this account"));
        String categoryId = text(body.get("categoryId"));
        String categoryName = text(body.get("categoryName"));
        if (categoryId != null && !exists("SELECT COUNT(*) FROM \"Category\" WHERE \"id\"=:id AND \"studyPlanId\"=:plan", params("id", categoryId, "plan", planId)))
            throw bad("Category does not belong to this roadmap");
        if (categoryId == null && categoryName != null && !categoryName.isBlank()) {
            List<Map<String, Object>> categories = db.queryForList("INSERT INTO \"Category\" (\"id\",\"studyPlanId\",\"name\",\"order\") VALUES (:id,:plan,:name,(SELECT COUNT(*) FROM \"Category\" WHERE \"studyPlanId\"=:plan)) ON CONFLICT (\"studyPlanId\",\"name\") DO UPDATE SET \"name\"=EXCLUDED.\"name\" RETURNING \"id\"", params("id", cuid(), "plan", planId, "name", categoryName.trim()));
            categoryId = String.valueOf(categories.get(0).get("id"));
        }
        String id = cuid();
        db.update("INSERT INTO \"Topic\" (\"id\",\"categoryId\",\"studyPlanId\",\"ownerId\",\"title\",\"description\",\"dayNumber\",\"order\",\"difficulty\",\"priority\",\"estimatedHours\",\"status\",\"createdAt\",\"updatedAt\") VALUES (:id,:category,:plan,:owner,:title,:description,:day,(SELECT COUNT(*) FROM \"Topic\" WHERE \"studyPlanId\"=:plan),CAST(:difficulty AS \"TopicDifficulty\"),CAST(:priority AS \"TopicPriority\"),:hours,CAST(:status AS \"TopicStatus\"),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", params("id", id, "category", categoryId, "plan", planId, "owner", userId, "title", text(body.get("title")).trim(), "description", text(body.get("description")), "day", body.get("dayNumber"), "difficulty", enumOr(body.get("difficulty"), "MEDIUM", List.of("EASY", "MEDIUM", "HARD")), "priority", enumOr(body.get("priority"), "MEDIUM", List.of("LOW", "MEDIUM", "HIGH")), "hours", body.get("estimatedHours"), "status", enumOr(body.get("status"), "NOT_STARTED", List.of("NOT_STARTED", "IN_PROGRESS", "COMPLETED"))));
        return ResponseEntity.status(201).body(Map.of("topic", db.queryForMap("SELECT * FROM \"Topic\" WHERE \"id\"=:id", Map.of("id", id))));
    }

    public ResponseEntity<?> updateTopic(String topicId, Map<String, Object> body, HttpServletRequest request) {
        String userId = requireUserId(request);
        validateTopic(body, true);
        List<Map<String, Object>> found = db.queryForList("SELECT t.* FROM \"Topic\" t JOIN \"StudyPlan\" p ON p.\"id\"=t.\"studyPlanId\" AND p.\"ownerId\"=t.\"ownerId\" JOIN \"WorkspaceMember\" wm ON wm.\"workspaceId\"=p.\"workspaceId\" AND wm.\"userId\"=:user WHERE t.\"id\"=:topic AND t.\"ownerId\"=:user", params("user", userId, "topic", topicId));
        if (found.isEmpty())
            return ResponseEntity.status(404).body(Map.of("error", "Topic not found for this account"));
        Map<String, Object> old = found.get(0);
        String planId = String.valueOf(old.get("studyPlanId"));
        if (body.containsKey("categoryName") && text(body.get("categoryName")) != null)
            throw bad("Changing a topic category requires categoryId");
        if (body.containsKey("categoryId") && body.get("categoryId") != null && !exists("SELECT COUNT(*) FROM \"Category\" WHERE \"id\"=:id AND \"studyPlanId\"=:plan", params("id", body.get("categoryId"), "plan", planId)))
            throw bad("Category does not belong to this roadmap");
        List<String> sets = new ArrayList<>();
        MapSqlParameterSource values = new MapSqlParameterSource().addValue("id", topicId).addValue("updatedAt", Timestamp.from(Instant.now()));
        addUpdate(body, values, sets, "title", "title", false);
        addUpdate(body, values, sets, "description", "description", false);
        addUpdate(body, values, sets, "categoryId", "categoryId", false);
        addUpdate(body, values, sets, "dayNumber", "dayNumber", false);
        addUpdate(body, values, sets, "estimatedHours", "estimatedHours", false);
        addUpdate(body, values, sets, "order", "order", false);
        if (body.containsKey("difficulty")) {
            sets.add("\"difficulty\"=CAST(:difficulty AS \"TopicDifficulty\")");
            values.addValue("difficulty", enumOr(body.get("difficulty"), "MEDIUM", List.of("EASY", "MEDIUM", "HARD")));
        }
        if (body.containsKey("priority")) {
            sets.add("\"priority\"=CAST(:priority AS \"TopicPriority\")");
            values.addValue("priority", enumOr(body.get("priority"), "MEDIUM", List.of("LOW", "MEDIUM", "HIGH")));
        }
        if (body.containsKey("status")) {
            sets.add("\"status\"=CAST(:status AS \"TopicStatus\")");
            values.addValue("status", enumOr(body.get("status"), "NOT_STARTED", List.of("NOT_STARTED", "IN_PROGRESS", "COMPLETED")));
        }
        if (!sets.isEmpty()) {
            sets.add("\"updatedAt\"=:updatedAt");
            db.update("UPDATE \"Topic\" SET " + String.join(",", sets) + " WHERE \"id\"=:id", values);
        }
        return ResponseEntity.ok(Map.of("topic", db.queryForMap("SELECT * FROM \"Topic\" WHERE \"id\"=:id", Map.of("id", topicId))));
    }

    public ResponseEntity<?> deleteTopic(String topicId, HttpServletRequest request) {
        String userId = requireUserId(request);
        int count = db.update("DELETE FROM \"Topic\" t USING \"StudyPlan\" p, \"WorkspaceMember\" wm WHERE t.\"id\"=:topic AND t.\"ownerId\"=:user AND p.\"id\"=t.\"studyPlanId\" AND p.\"ownerId\"=t.\"ownerId\" AND wm.\"workspaceId\"=p.\"workspaceId\" AND wm.\"userId\"=:user", params("topic", topicId, "user", userId));
        if (count == 0) return ResponseEntity.status(404).body(Map.of("error", "Topic not found for this account"));
        return ResponseEntity.noContent().build();
    }

    public ResponseEntity<?> rotateInvite(HttpServletRequest request) {
        String userId = requireUserId(request);
        List<Map<String, Object>> rows = db.queryForList("SELECT \"workspaceId\" FROM \"WorkspaceMember\" WHERE \"userId\"=:user AND \"role\"='OWNER' LIMIT 1", Map.of("user", userId));
        if (rows.isEmpty())
            return ResponseEntity.status(403).body(Map.of("error", "Only the workspace owner can rotate the invite"));
        String inviteCode = randomUrl(18);
        db.update("UPDATE \"Workspace\" SET \"inviteCode\"=:code WHERE \"id\"=:id", params("code", inviteCode, "id", rows.get(0).get("workspaceId")));
        return ResponseEntity.ok(Map.of("inviteCode", inviteCode));
    }





    private String createGoogleUser(String email, String name, String avatar) {
        return transactions.execute(status -> {
            String id = cuid();
            db.update("INSERT INTO \"User\" (\"id\",\"name\",\"email\",\"passwordHash\",\"avatar\",\"createdAt\") VALUES (:id,:name,:email,:hash,:avatar,CURRENT_TIMESTAMP)", params("id", id, "name", name, "email", email, "hash", passwords.encode(randomUrl(48)), "avatar", avatar));
            String workspace = cuid();
            db.update("INSERT INTO \"Workspace\" (\"id\",\"name\",\"inviteCode\",\"createdAt\") VALUES (:id,'Study Room',:invite,CURRENT_TIMESTAMP)", params("id", workspace, "invite", cuid()));
            db.update("INSERT INTO \"WorkspaceMember\" (\"id\",\"workspaceId\",\"userId\",\"role\",\"joinedAt\") VALUES (:id,:workspace,:user,'OWNER',CURRENT_TIMESTAMP)", params("id", cuid(), "workspace", workspace, "user", id));
            db.update("INSERT INTO \"StudyPlan\" (\"id\",\"workspaceId\",\"ownerId\",\"name\",\"createdAt\") VALUES (:id,:workspace,:owner,:name,CURRENT_TIMESTAMP)", params("id", cuid(), "workspace", workspace, "owner", id, "name", name + "’s roadmap"));
            return id;
        });
    }

    private RedirectView googleFail(HttpServletResponse response, String reason) {
        response.addHeader("Set-Cookie", cookie("google_oauth_state", "", 0, true).toString());
        response.addHeader("Set-Cookie", cookie("google_oauth_nonce", "", 0, true).toString());
        return new RedirectView(frontendUrl + "/login?authError=" + enc(reason));
    }

    private Map<String, Object> readStats(String userId) {
        String zoneName = db.queryForList("SELECT \"timeZone\" FROM \"User\" WHERE \"id\"=:id", Map.of("id", userId)).stream().findFirst().map(row -> String.valueOf(row.get("timeZone"))).orElse("Asia/Kolkata");
        ZoneId zone;
        try {
            zone = ZoneId.of(zoneName);
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Kolkata");
        }
        LocalDate today = LocalDate.now(zone);
        LocalDate yesterday = today.minusDays(1);
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        Map<String, Object> stored = db.queryForList("SELECT \"currentStreak\",\"bestStreak\",\"lastStudyDate\" FROM \"StudyStreak\" WHERE \"userId\"=:id", Map.of("id", userId)).stream().findFirst().orElse(Map.of());
        LocalDate last = asLocalDate(stored.get("lastStudyDate"));
        int current = asInt(stored.get("currentStreak"));
        int best = asInt(stored.get("bestStreak"));
        if (last == null || last.isBefore(yesterday)) {
            current = 0;
            if (!stored.isEmpty() && asInt(stored.get("currentStreak")) != 0)
                db.update("UPDATE \"StudyStreak\" SET \"currentStreak\"=0,\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"userId\"=:id", Map.of("id", userId));
        }
        Instant start = weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = weekStart.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<Map<String, Object>> sessions = db.queryForList("SELECT \"date\" FROM \"StudySession\" WHERE \"userId\"=:user AND \"date\">=:start AND \"date\"<:end", params("user", userId, "start", Timestamp.from(start), "end", Timestamp.from(end)));
        java.util.Set<LocalDate> active = new java.util.HashSet<>();
        for (Map<String, Object> row : sessions) {
            LocalDate date = asLocalDate(row.get("date"));
            if (date != null) active.add(date);
        }
        String[] labels = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        List<Map<String, Object>> week = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            week.add(Map.of("key", date.toString(), "label", labels[date.getDayOfWeek().getValue() % 7], "active", active.contains(date), "today", date.equals(today)));
        }
        return Map.of("currentStreak", current, "bestStreak", best, "week", week);
    }

    private String requireUserId(HttpServletRequest request) {
        String value = cookieValue(request, SESSION_COOKIE);
        if (value == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue");
        String[] token = value.split("\\.", 2);
        if (token.length != 2) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        try {
            String expected = sign(token[0]);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token[1].getBytes(StandardCharsets.UTF_8)))
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
            String data = new String(Base64.getUrlDecoder().decode(token[0]), StandardCharsets.UTF_8);
            int separator = data.lastIndexOf(':');
            if (separator < 1 || Long.parseLong(data.substring(separator + 1)) < System.currentTimeMillis())
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired");
            return data.substring(0, separator);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
    }

    private void setSession(HttpServletResponse response, String userId) {
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString((userId + ":" + (System.currentTimeMillis() + SESSION_TTL_MILLIS)).getBytes(StandardCharsets.UTF_8));
        response.addHeader("Set-Cookie", cookie(SESSION_COOKIE, id + "." + sign(id), SESSION_TTL_SECONDS, true).toString());
    }

    private ResponseCookie cookie(String name, String value, long maxAge, boolean httpOnly) {
        return ResponseCookie.from(name, value).httpOnly(httpOnly).secure(production).sameSite("Lax").path("/").maxAge(maxAge).build();
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(authSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormatHelper.hex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void validateTopic(Map<String, Object> body, boolean partial) {
        if (!partial || body.containsKey("title")) requiredText(body, "title", 1, 160);
        if (text(body.get("description")) != null && text(body.get("description")).length() > 3000)
            throw bad("Description must be at most 3000 characters");
        if (body.containsKey("categoryName") && text(body.get("categoryName")) != null && (text(body.get("categoryName")).trim().isEmpty() || text(body.get("categoryName")).length() > 80))
            throw bad("Category name is invalid");
        if (body.containsKey("dayNumber") && body.get("dayNumber") != null)
            integer(body.get("dayNumber"), "Day must be a positive integer", 1, Integer.MAX_VALUE);
        if (body.containsKey("estimatedHours") && body.get("estimatedHours") != null) {
            double hours = number(body.get("estimatedHours"), "Estimated hours must be positive");
            if (hours <= 0 || hours > 1000) throw bad("Estimated hours must be between 0 and 1000");
        }
        if (body.containsKey("difficulty")) enumOr(body.get("difficulty"), "MEDIUM", List.of("EASY", "MEDIUM", "HARD"));
        if (body.containsKey("priority")) enumOr(body.get("priority"), "MEDIUM", List.of("LOW", "MEDIUM", "HIGH"));
        if (body.containsKey("status"))
            enumOr(body.get("status"), "NOT_STARTED", List.of("NOT_STARTED", "IN_PROGRESS", "COMPLETED"));
    }

    private boolean exists(String sql, MapSqlParameterSource values) {
        Integer count = db.queryForObject(sql, values, Integer.class);
        return count != null && count > 0;
    }

    private boolean googleConfigured() {
        return !blank(googleClientId) && !blank(googleClientSecret) && !blank(googleRedirectUri);
    }

    private String randomUrl(int bytes) {
        byte[] data = new byte[bytes];
        random.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static final class HexFormatHelper {
        private static String hex(byte[] bytes) {
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) result.append(String.format("%02x", b));
            return result.toString();
        }
    }
}
