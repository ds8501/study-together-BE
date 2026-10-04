package com.studytogether.api.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiConfiguration {
    @Bean
    DataSource dataSource(@Value("${DATABASE_URL:${JDBC_DATABASE_URL:}}") String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) throw new IllegalStateException("DATABASE_URL must be set");
        String url = rawUrl.trim();
        String username = null;
        String password = null;
        if (!url.startsWith("jdbc:")) {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"postgres".equals(scheme) && !"postgresql".equals(scheme))
                throw new IllegalStateException("DATABASE_URL must be a PostgreSQL URL");
            String rawQuery = uri.getRawQuery();
            if (rawQuery != null) rawQuery = rawQuery.replaceAll("(^|&)schema=", "$1currentSchema=");
            String query = rawQuery == null ? "" : "?" + rawQuery;
            String authority = uri.getRawAuthority();
            url = "jdbc:postgresql://" + authority.substring(authority.lastIndexOf('@') + 1) + uri.getRawPath() + query;
            if (uri.getRawUserInfo() != null) {
                String[] credentials = uri.getRawUserInfo().split(":", 2);
                username = URLDecoder.decode(credentials[0], StandardCharsets.UTF_8);
                if (credentials.length > 1) password = URLDecoder.decode(credentials[1], StandardCharsets.UTF_8);
            }
        }
        DataSourceBuilder<?> builder = DataSourceBuilder.create().driverClassName("org.postgresql.Driver").url(url);
        if (username != null) builder.username(username);
        if (password != null) builder.password(password);
        return builder.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }

    @Bean
    WebMvcConfigurer cors(@Value("${FRONTEND_ORIGIN:}") String configuredOrigins, @Value("${NODE_ENV:development}") String nodeEnv) {
        List<String> origins = new ArrayList<>();
        for (String origin : configuredOrigins.split(",")) if (!origin.isBlank()) origins.add(origin.trim());
        if (!"production".equalsIgnoreCase(nodeEnv))
            for (String local : List.of("http://localhost:3000", "http://127.0.0.1:3000", "http://localhost:3001", "http://127.0.0.1:3001"))
                if (!origins.contains(local)) origins.add(local);
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**").allowedOrigins(origins.toArray(String[]::new)).allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS").allowedHeaders("*").allowCredentials(true).maxAge(3600);
            }
        };
    }
}
