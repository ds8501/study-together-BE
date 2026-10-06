package com.studytogether.api.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class SessionService {
    public static final String SESSION_COOKIE = "study_session";
    public static final long SESSION_TTL_SECONDS = 14L * 24 * 60 * 60;
    public static final long SESSION_TTL_MILLIS = SESSION_TTL_SECONDS * 1000;

    private final String authSecret;
    private final boolean production;
    private final SecureRandom random = new SecureRandom();

    public SessionService(@Value("${AUTH_SECRET:}") String authSecret,
                          @Value("${NODE_ENV:development}") String nodeEnv) {
        if (authSecret == null || authSecret.length() < 32) {
            throw new IllegalStateException("AUTH_SECRET must be set to at least 32 characters");
        }
        this.authSecret = authSecret;
        this.production = "production".equalsIgnoreCase(nodeEnv);
    }

    public Long requireUserId(HttpServletRequest request) {
        String value = cookieValue(request, SESSION_COOKIE);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue");
        }
        String[] token = value.split("\\.", 2);
        if (token.length != 2) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
        try {
            String expected = sign(token[0]);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token[1].getBytes(StandardCharsets.UTF_8))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
            }
            String data = new String(Base64.getUrlDecoder().decode(token[0]), StandardCharsets.UTF_8);
            int separator = data.lastIndexOf(':');
            if (separator < 1 || Long.parseLong(data.substring(separator + 1)) < System.currentTimeMillis()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired");
            }
            return Long.parseLong(data.substring(0, separator));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
    }

    public void setSession(HttpServletResponse response, Long userId) {
        String payload = userId + ":" + (System.currentTimeMillis() + SESSION_TTL_MILLIS);
        String tokenData = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        response.addHeader("Set-Cookie", cookie(SESSION_COOKIE, tokenData + "." + sign(tokenData), SESSION_TTL_SECONDS, true).toString());
    }

    public void clearSession(HttpServletResponse response) {
        response.addHeader("Set-Cookie", cookie(SESSION_COOKIE, "", 0, true).toString());
    }

    public ResponseCookie cookie(String name, String value, long maxAge, boolean httpOnly) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(production)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (var c : request.getCookies()) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    public String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(authSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            StringBuilder result = new StringBuilder();
            for (byte b : mac.doFinal(value.getBytes(StandardCharsets.UTF_8))) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String randomUrl(int bytes) {
        byte[] data = new byte[bytes];
        random.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
