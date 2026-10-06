package com.studytogether.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.studytogether.api.model.entity.UserEntity;
import com.studytogether.api.repository.StudyTogetherRepository;
import com.studytogether.api.util.QueryConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ExternalAuthService {
    private static final Logger log = LoggerFactory.getLogger(ExternalAuthService.class);

    private final StudyTogetherRepository db;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;
    private final SessionService sessionService;
    private final String frontendUrl;
    private final String googleClientId;
    private final String googleClientSecret;
    private final String googleRedirectUri;

    public ExternalAuthService(StudyTogetherRepository db,
                               PasswordEncoder passwords,
                               TransactionTemplate transactions,
                               ObjectMapper mapper,
                               SessionService sessionService,
                               @Value("${FRONTEND_URL:${FRONTEND_ORIGIN:http://localhost:3000}}") String frontendUrl,
                               @Value("${GOOGLE_CLIENT_ID:}") String googleClientId,
                               @Value("${GOOGLE_CLIENT_SECRET:}") String googleClientSecret,
                               @Value("${GOOGLE_REDIRECT_URI:}") String googleRedirectUri) {
        this.db = db;
        this.passwords = passwords;
        this.transactions = transactions;
        this.mapper = mapper;
        this.sessionService = sessionService;
        this.frontendUrl = trimSlash(cleanEnv(frontendUrl));
        this.googleClientId = cleanEnv(googleClientId);
        this.googleClientSecret = cleanEnv(googleClientSecret);
        this.googleRedirectUri = cleanEnv(googleRedirectUri);

        log.info("ExternalAuthService initialized. Google configured: {}, redirectUri: '{}', frontendUrl: '{}'",
                googleConfigured(), this.googleRedirectUri, this.frontendUrl);
    }

    private static String cleanEnv(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String trimSlash(String value) {
        if (value == null || value.isBlank()) return "http://localhost:3000";
        return value.replaceAll("/+$", "");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String text(Object value) {
        if (value == null) return null;
        String string = String.valueOf(value);
        return string.isBlank() ? null : string;
    }

    private static MapSqlParameterSource params(Object... pairs) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        for (int i = 0; i < pairs.length; i += 2) {
            p.addValue(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return p;
    }

    private boolean googleConfigured() {
        return googleClientId != null && !googleClientId.isBlank()
                && googleClientSecret != null && !googleClientSecret.isBlank()
                && googleRedirectUri != null && !googleRedirectUri.isBlank();
    }

    public RedirectView googleStart(HttpServletRequest request, HttpServletResponse response) {
        if (!googleConfigured()) {
            log.warn("Google OAuth requested but not configured: clientId present={}, clientSecret present={}, redirectUri={}",
                    googleClientId != null, googleClientSecret != null, googleRedirectUri);
            return new RedirectView(frontendUrl + "/login?authError=google_not_configured");
        }

        String host = request != null ? request.getHeader("Host") : null;
        if (host != null && googleRedirectUri != null) {
            boolean reqIsLocalhost = host.startsWith("localhost");
            boolean redIs127 = googleRedirectUri.contains("127.0.0.1");
            boolean reqIs127 = host.startsWith("127.0.0.1");
            boolean redIsLocalhost = googleRedirectUri.contains("localhost");
            if ((reqIsLocalhost && redIs127) || (reqIs127 && redIsLocalhost)) {
                log.warn("OAuth started on host '{}' while GOOGLE_REDIRECT_URI is '{}'. Browser cookies set on localhost are not sent to 127.0.0.1 and vice versa.", host, googleRedirectUri);
            }
        }

        String nonce = sessionService.randomUrl(24);
        long expiresAt = System.currentTimeMillis() + 600_000L; // 10 minutes
        String statePayload = expiresAt + ":" + nonce + ":" + sessionService.randomUrl(16);
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(statePayload.getBytes(StandardCharsets.UTF_8));
        String state = encodedPayload + "." + sessionService.sign(encodedPayload);

        response.addHeader("Set-Cookie", sessionService.cookie("google_oauth_state", state, 600, true).toString());
        response.addHeader("Set-Cookie", sessionService.cookie("google_oauth_nonce", nonce, 600, true).toString());

        String authorization = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", googleClientId)
                .queryParam("redirect_uri", googleRedirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("prompt", "select_account")
                .build().encode().toUriString();

        log.info("Redirecting user to Google OAuth authorization endpoint with redirect_uri: {}", googleRedirectUri);
        return new RedirectView(authorization);
    }

    public RedirectView googleCallback(HttpServletRequest request, HttpServletResponse response) {
        String state = request.getParameter("state");
        String code = request.getParameter("code");
        String oauthError = request.getParameter("error");
        String errorDescription = request.getParameter("error_description");

        if (oauthError != null) {
            log.error("Google OAuth returned error: {} - {}", oauthError, errorDescription);
            return googleFail(response, "google");
        }

        if (state == null || code == null) {
            log.error("Google OAuth callback missing required parameters: state={}, code present={}", state != null, code != null);
            return googleFail(response, "google");
        }

        if (!googleConfigured()) {
            log.error("Google OAuth callback received but Google auth is not configured");
            return googleFail(response, "google_not_configured");
        }

        String stateCookie = sessionService.cookieValue(request, "google_oauth_state");
        String nonceCookie = sessionService.cookieValue(request, "google_oauth_nonce");

        String verifiedNonce = null;
        if (state.contains(".")) {
            String[] parts = state.split("\\.", 2);
            try {
                String expectedSig = sessionService.sign(parts[0]);
                if (MessageDigest.isEqual(expectedSig.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
                    String decoded = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                    String[] segments = decoded.split(":");
                    if (segments.length >= 2) {
                        long expiresAt = Long.parseLong(segments[0]);
                        if (System.currentTimeMillis() <= expiresAt) {
                            verifiedNonce = segments[1];
                        } else {
                            log.error("Google OAuth callback: state token expired (expiresAt={}, now={})", expiresAt, System.currentTimeMillis());
                            return googleFail(response, "google");
                        }
                    }
                } else {
                    log.error("Google OAuth callback: state token HMAC signature mismatch");
                    return googleFail(response, "google");
                }
            } catch (Exception e) {
                log.error("Google OAuth callback: failed to decode/verify state token: {}", e.getMessage());
                return googleFail(response, "google");
            }
        }

        if (stateCookie != null) {
            if (!MessageDigest.isEqual(stateCookie.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
                log.error("Google OAuth callback: state cookie does not match state parameter");
                return googleFail(response, "google");
            }
        } else {
            if (verifiedNonce == null) {
                log.error("Google OAuth callback: state cookie missing and state parameter could not be cryptographically verified");
                return googleFail(response, "google");
            }
            log.info("Google OAuth callback: state cookie missing (likely localhost/127.0.0.1 domain mismatch or cross-origin restrictions), but HMAC-signed state is valid");
        }

        try {
            String form = "code=" + enc(code) + "&client_id=" + enc(googleClientId) + "&client_secret=" + enc(googleClientSecret) + "&redirect_uri=" + enc(googleRedirectUri) + "&grant_type=authorization_code";
            HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> tokenResponse = HttpClient.newHttpClient().send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            if (tokenResponse.statusCode() < 200 || tokenResponse.statusCode() >= 300) {
                log.error("Google token exchange failed (HTTP {}): {}", tokenResponse.statusCode(), tokenResponse.body());
                return googleFail(response, "google");
            }

            Map<String, Object> tokens = mapper.readValue(tokenResponse.body(), new TypeReference<>() {});
            String idTokenValue = text(tokens.get("id_token"));
            if (idTokenValue == null) {
                log.error("Google token response did not contain id_token: {}", tokenResponse.body());
                return googleFail(response, "google");
            }

            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(List.of(googleClientId))
                    .build();
            GoogleIdToken verified;
            try {
                verified = verifier.verify(idTokenValue);
            } catch (Exception e) {
                log.error("Exception during GoogleIdTokenVerifier.verify", e);
                return googleFail(response, "google");
            }
            if (verified == null) {
                log.error("Google ID token failed verification with audience: {}", googleClientId);
                return googleFail(response, "google");
            }

            GoogleIdToken.Payload payload = verified.getPayload();
            String expectedNonce = verifiedNonce != null ? verifiedNonce : nonceCookie;
            Object tokenNonce = payload.get("nonce");
            if (expectedNonce != null && tokenNonce != null && !expectedNonce.equals(tokenNonce)) {
                log.error("Google ID token nonce mismatch: expected={}, received in token={}", expectedNonce, tokenNonce);
                return googleFail(response, "google");
            }
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                log.error("Google account email is not verified: {}", payload.getEmail());
                return googleFail(response, "google");
            }
            if (payload.getEmail() == null || payload.getSubject() == null) {
                log.error("Google ID token missing email or subject claim");
                return googleFail(response, "google");
            }

            String email = payload.getEmail().trim().toLowerCase();
            Object hostedDomain = payload.get("hd");
            if (!email.endsWith("@gmail.com") && hostedDomain == null) {
                log.warn("Google email domain rejected: {}", email);
                return googleFail(response, "google_email");
            }

            Optional<UserEntity> existing = db.queryForOptional(
                    QueryConstants.USER_BY_EMAIL,
                    Map.of("email", email),
                    UserEntity.ROW_MAPPER
            );

            Long userId;
            if (existing.isPresent()) {
                userId = existing.get().id();
                log.info("Google OAuth login successful for existing user: {} (id={})", email, userId);
            } else {
                String displayName = Optional.ofNullable(text(payload.get("name")))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(value -> value.substring(0, Math.min(80, value.length())))
                        .orElse(email.split("@", 2)[0]);
                String avatar = text(payload.get("picture"));
                userId = createGoogleUser(email, displayName, avatar);
                log.info("Google OAuth login successful, created new user: {} (id={})", email, userId);
            }

            sessionService.setSession(response, userId);
            response.addHeader("Set-Cookie", sessionService.cookie("google_oauth_state", "", 0, true).toString());
            response.addHeader("Set-Cookie", sessionService.cookie("google_oauth_nonce", "", 0, true).toString());
            String targetUrl = frontendUrl.endsWith("/dashboard") ? frontendUrl : frontendUrl + "/dashboard";
            log.info("Google login successful for userId {}. Redirecting to frontend: {}", userId, targetUrl);
            return new RedirectView(targetUrl);
        } catch (Exception e) {
            log.error("Unexpected error during Google OAuth callback processing", e);
            return googleFail(response, "google");
        }
    }

    private Long createGoogleUser(String email, String name, String avatar) {
        return transactions.execute(status -> {
            Long userId = db.queryForObject(
                    QueryConstants.INSERT_USER_WITH_AVATAR,
                    params("name", name, "email", email, "hash", passwords.encode(sessionService.randomUrl(48)), "avatar", avatar),
                    Long.class
            );

            Long workspaceId = db.queryForObject(
                    QueryConstants.INSERT_WORKSPACE,
                    params("name", "Study Room", "code", sessionService.randomUrl(18)),
                    Long.class
            );

            db.queryForObject(
                    QueryConstants.INSERT_WORKSPACE_MEMBER,
                    params("workspace", workspaceId, "user", userId, "role", "OWNER"),
                    Long.class
            );

            db.queryForObject(
                    QueryConstants.INSERT_STUDY_PLAN,
                    params("workspace", workspaceId, "owner", userId, "name", name + "’s roadmap"),
                    Long.class
            );

            return userId;
        });
    }

    private RedirectView googleFail(HttpServletResponse response, String reason) {
        response.addHeader("Set-Cookie", sessionService.cookie("google_oauth_state", "", 0, true).toString());
        response.addHeader("Set-Cookie", sessionService.cookie("google_oauth_nonce", "", 0, true).toString());
        log.warn("Google OAuth failure with reason '{}', redirecting to {}/login?authError={}", reason, frontendUrl, reason);
        return new RedirectView(frontendUrl + "/login?authError=" + enc(reason));
    }
}
