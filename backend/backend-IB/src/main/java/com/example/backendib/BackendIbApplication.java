package com.example.backendib;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@SpringBootApplication
public class BackendIbApplication {

    public static void main(String[] args) {
        // Load .env for local dev if present and propagate to System properties
        try {
            Map<String, String> env = loadDotEnv(PathsForDotEnv());
            // map .env keys -> Spring properties/expected names
            if (env != null) {
                String dbUrl = env.get("DB_URL");
                if (dbUrl != null && System.getProperty("spring.datasource.url") == null && System.getenv("SPRING_DATASOURCE_URL") == null) {
                    System.setProperty("spring.datasource.url", dbUrl);
                }
                String dbUser = env.get("DB_USERNAME");
                if (dbUser != null && System.getProperty("spring.datasource.username") == null) {
                    System.setProperty("spring.datasource.username", dbUser);
                }
                String dbPass = env.get("DB_PASSWORD");
                if (dbPass != null && System.getProperty("spring.datasource.password") == null) {
                    System.setProperty("spring.datasource.password", dbPass);
                }
                String jwt = env.get("JWT_SECRET");
                if (jwt != null && System.getProperty("JWT_SECRET") == null) {
                    System.setProperty("JWT_SECRET", jwt);
                    // if your code uses property name jwt.secret, set that too
                    System.setProperty("jwt.secret", jwt);
                }
                String master = env.get("MASTER_KEY");
                if (master != null && System.getProperty("app.master-key") == null) {
                    System.setProperty("app.master-key", master);
                }
                String mailFrom = env.get("MAIL_FROM");
                if (mailFrom != null && System.getProperty("spring.mail.from") == null) {
                    System.setProperty("spring.mail.from", mailFrom);
                }
                String mailUser = env.get("MAIL_USERNAME");
                if (mailUser != null && System.getProperty("spring.mail.username") == null) {
                    System.setProperty("spring.mail.username", mailUser);
                }
                String mailPass = env.get("MAIL_PASSWORD");
                if (mailPass != null && System.getProperty("spring.mail.password") == null) {
                    System.setProperty("spring.mail.password", mailPass);
                }
            }
        } catch (Throwable t) {
            // ignore failures to load .env in production
        }

        SpringApplication.run(BackendIbApplication.class, args);
    }

    // determine .env search locations (working dir and project root). Returns first existing path or null
    private static Path PathsForDotEnv() {
        try {
            Path pwd = Path.of(System.getProperty("user.dir"), ".env");
            if (Files.exists(pwd)) return pwd;
            // fallback to project root variant used in your environment (the repo root)
            Path alt = Path.of(System.getProperty("user.dir")).resolveSibling(".env");
            if (Files.exists(alt)) return alt;
            return pwd; // may not exist; loader will ignore
        } catch (Exception e) {
            return null;
        }
    }

    // simple .env loader (no external lib). Returns map of keys->values or empty map.
    private static Map<String, String> loadDotEnv(Path envPath) {
        Map<String, String> map = new HashMap<>();
        try {
            if (envPath == null) return map;
            if (!Files.exists(envPath)) return map;
            List<String> lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                // strip optional surrounding quotes
                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                map.put(key, value);
            }
        } catch (Exception e) {
            // ignore and return what we have
        }
        return map;
    }
}
