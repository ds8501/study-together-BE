package com.studytogether.api.service;

import com.studytogether.api.model.dto.response.AuthResponse;
import com.studytogether.api.model.dto.request.LoginRequest;
import com.studytogether.api.model.dto.response.OkResponse;
import com.studytogether.api.model.dto.request.RegisterRequest;
import com.studytogether.api.model.entity.UserEntity;
import com.studytogether.api.model.entity.WorkspaceEntity;
import com.studytogether.api.repository.StudyTogetherRepository;
import com.studytogether.api.util.QueryConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class AuthService {
    private final StudyTogetherRepository db;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transactions;
    private final SessionService sessionService;

    public AuthService(StudyTogetherRepository db,
                       PasswordEncoder passwords,
                       TransactionTemplate transactions,
                       SessionService sessionService) {
        this.db = db;
        this.passwords = passwords;
        this.transactions = transactions;
        this.sessionService = sessionService;
    }

    private static String requiredText(String value, String key, int min, int max) {
        if (value == null || value.trim().length() < min || value.length() > max) {
            throw bad(key + " is invalid");
        }
        return value.trim();
    }

    private static String email(String raw) {
        if (raw == null) throw bad("Enter a valid email address");
        String value = raw.trim().toLowerCase();
        if (!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw bad("Enter a valid email address");
        }
        return value;
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

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public ResponseEntity<AuthResponse> register(RegisterRequest request, HttpServletResponse response) {
        if (request == null) throw bad("Request body is required");
        String name = requiredText(request.name(), "name", 2, 80);
        String email = email(requiredText(request.email(), "email", 3, 254));
        String password = requiredText(request.password(), "password", 10, 128);
        String workspaceName = text(request.workspaceName());
        String inviteCode = text(request.inviteCode());
        if (inviteCode != null && (inviteCode.length() < 4 || inviteCode.length() > 100)) {
            throw bad("Invite code is invalid");
        }
        if (inviteCode == null && workspaceName == null) workspaceName = "Study Room";
        if (inviteCode == null && (workspaceName.length() < 2 || workspaceName.length() > 80)) {
            throw bad("Workspace name must be between 2 and 80 characters");
        }
        final String planWorkspaceName = workspaceName == null ? "Study Room" : workspaceName;
        final String normalizedInvite = inviteCode;

        try {
            UserEntity user = transactions.execute(status -> {
                WorkspaceEntity workspace = null;
                if (normalizedInvite != null) {
                    workspace = db.queryForOptional(
                            QueryConstants.WORKSPACE_BY_INVITE_FOR_UPDATE,
                            Map.of("code", normalizedInvite),
                            WorkspaceEntity.ROW_MAPPER
                    ).orElseThrow(() -> bad("That invite code is not valid"));

                    Integer memberCount = db.getJdbcTemplate().queryForObject(
                            QueryConstants.WORKSPACE_MEMBER_COUNT,
                            Integer.class,
                            workspace.id()
                    );
                    if (memberCount != null && memberCount >= 2) {
                        throw bad("This workspace already has two members");
                    }
                }

                Long userId = db.queryForObject(
                        QueryConstants.INSERT_USER,
                        params("name", name, "email", email, "hash", passwords.encode(password)),
                        Long.class
                );

                Long workspaceId;
                String role;
                if (workspace == null) {
                    workspaceId = db.queryForObject(
                            QueryConstants.INSERT_WORKSPACE,
                            params("name", planWorkspaceName, "code", sessionService.randomUrl(18)),
                            Long.class
                    );
                    role = "OWNER";
                } else {
                    workspaceId = workspace.id();
                    role = "MEMBER";
                }

                db.queryForObject(
                        QueryConstants.INSERT_WORKSPACE_MEMBER,
                        params("workspace", workspaceId, "user", userId, "role", role),
                        Long.class
                );

                db.queryForObject(
                        QueryConstants.INSERT_STUDY_PLAN,
                        params("workspace", workspaceId, "owner", userId, "name", name + "’s roadmap"),
                        Long.class
                );

                return db.queryForOptional(
                        QueryConstants.SAFE_USER_BY_ID,
                        Map.of("id", userId),
                        UserEntity.ROW_MAPPER
                ).orElseThrow(() -> bad("Failed to load user"));
            });

            if (user == null) {
                throw bad("Registration failed");
            }
            sessionService.setSession(response, user.id());
            return ResponseEntity.status(201).body(new AuthResponse(user.toSafeUser()));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists");
        }
    }

    public ResponseEntity<AuthResponse> login(LoginRequest request, HttpServletResponse response) {
        if (request == null) throw bad("Request body is required");
        String email = email(requiredText(request.email(), "email", 3, 254));
        String password = requiredText(request.password(), "password", 1, 128);

        UserEntity user = db.queryForOptional(
                QueryConstants.USER_BY_EMAIL,
                Map.of("email", email),
                UserEntity.ROW_MAPPER
        ).orElse(null);

        if (user == null || !passwords.matches(password, user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect");
        }

        sessionService.setSession(response, user.id());
        return ResponseEntity.ok(new AuthResponse(user.toSafeUser()));
    }

    public OkResponse logout(HttpServletResponse response) {
        sessionService.clearSession(response);
        return new OkResponse(true);
    }

    public ResponseEntity<AuthResponse> me(HttpServletRequest request) {
        Long userId = sessionService.requireUserId(request);
        UserEntity user = db.queryForOptional(
                QueryConstants.SAFE_USER_BY_ID,
                Map.of("id", userId),
                UserEntity.ROW_MAPPER
        ).orElse(null);

        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found");
        }
        return ResponseEntity.ok(new AuthResponse(user.toSafeUser()));
    }
}
