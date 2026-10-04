package com.studytogether.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StudyTogetherApplication {
    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(StudyTogetherApplication.class, args);
    }

    private static void loadDotEnv() {
        Path env = Path.of(".env");
        if (!Files.isRegularFile(env)) return;
        try {
            for (String line : Files.readAllLines(env)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int equals = trimmed.indexOf('=');
                if (equals < 1) continue;
                String key = trimmed.substring(0, equals).trim();
                String value = trimmed.substring(equals + 1).trim();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))))
                    value = value.substring(1, value.length() - 1);
                if (System.getenv(key) == null) System.setProperty(key, value);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read .env", e);
        }
    }
}
