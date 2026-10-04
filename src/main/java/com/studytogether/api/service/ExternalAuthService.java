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
public class ExternalAuthService {
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

    public ExternalAuthService(StudyTogetherRepository db, PasswordEncoder passwords, TransactionTemplate transactions,
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



























    public RedirectView googleStart(HttpServletResponse response) {
        if (!googleConfigured()) return new RedirectView(frontendUrl + "/login?authError=google_not_configured");
        String state = randomUrl(32), nonce = randomUrl(32);
        response.addHeader("Set-Cookie", cookie("google_oauth_state", state, 600, true).toString());
        response.addHeader("Set-Cookie", cookie("google_oauth_nonce", nonce, 600, true).toString());
        String authorization = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", googleClientId).queryParam("redirect_uri", googleRedirectUri).queryParam("response_type", "code")
                .queryParam("scope", "openid email profile").queryParam("state", state).queryParam("nonce", nonce)
                .queryParam("prompt", "select_account").build().encode().toUriString();
        return new RedirectView(authorization);
    }

    public RedirectView googleCallback(HttpServletRequest request, HttpServletResponse response) {
        String stateCookie = cookieValue(request, "google_oauth_state"), nonceCookie = cookieValue(request, "google_oauth_nonce");
        String state = request.getParameter("state"), code = request.getParameter("code");
        if (stateCookie == null || nonceCookie == null || state == null || code == null || !MessageDigest.isEqual(stateCookie.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8)) || !googleConfigured())
            return googleFail(response, "google");
        try {
            String form = "code=" + enc(code) + "&client_id=" + enc(googleClientId) + "&client_secret=" + enc(googleClientSecret) + "&redirect_uri=" + enc(googleRedirectUri) + "&grant_type=authorization_code";
            HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token")).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
            HttpResponse<String> tokenResponse = HttpClient.newHttpClient().send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            if (tokenResponse.statusCode() < 200 || tokenResponse.statusCode() >= 300)
                return googleFail(response, "google");
            Map<String, Object> tokens = mapper.readValue(tokenResponse.body(), new TypeReference<>() {
            });
            String idTokenValue = text(tokens.get("id_token"));
            if (idTokenValue == null) return googleFail(response, "google");
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance()).setAudience(List.of(googleClientId)).build();
            GoogleIdToken verified = verifier.verify(idTokenValue);
            if (verified == null) return googleFail(response, "google");
            GoogleIdToken.Payload payload = verified.getPayload();
            if (!nonceCookie.equals(payload.get("nonce")) || !Boolean.TRUE.equals(payload.getEmailVerified()) || payload.getEmail() == null || payload.getSubject() == null)
                return googleFail(response, "google");
            String email = payload.getEmail().trim().toLowerCase();
            Object hostedDomain = payload.get("hd");
            if (!email.endsWith("@gmail.com") && hostedDomain == null) return googleFail(response, "google_email");
            List<Map<String, Object>> existing = db.queryForList("SELECT \"id\" FROM \"User\" WHERE \"email\"=:email", Map.of("email", email));
            String userId;
            if (!existing.isEmpty()) userId = String.valueOf(existing.get(0).get("id"));
            else {
                String displayName = Optional.ofNullable(text(payload.get("name"))).map(String::trim).filter(value -> !value.isEmpty()).map(value -> value.substring(0, Math.min(80, value.length()))).orElse(email.split("@", 2)[0]);
                String avatar = text(payload.get("picture"));
                userId = createGoogleUser(email, displayName, avatar);
            }
            setSession(response, userId);
            response.addHeader("Set-Cookie", cookie("google_oauth_state", "", 0, true).toString());
            response.addHeader("Set-Cookie", cookie("google_oauth_nonce", "", 0, true).toString());
            return new RedirectView(frontendUrl + "/login");
        } catch (Exception e) {
            return googleFail(response, "google");
        }
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

    private boolean exists(String sql, Map<String, ?> values) {
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
