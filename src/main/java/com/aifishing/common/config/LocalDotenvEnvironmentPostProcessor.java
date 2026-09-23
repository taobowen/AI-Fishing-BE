package com.aifishing.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Loads local `.env` for the `dev` profile without overriding real environment variables. */
public class LocalDotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!devProfile(environment)) {
            return;
        }
        Path file = dotenvFile();
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        Map<String, Object> loaded = parse(file);
        if (loaded.isEmpty()) {
            return;
        }
        loaded.keySet().removeIf(key -> environment.getSystemEnvironment().containsKey(key));
        if (loaded.isEmpty()) {
            return;
        }
        MapPropertySource source = new MapPropertySource("aifishingDotenv", loaded);
        if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addAfter(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
        } else {
            environment.getPropertySources().addFirst(source);
        }
    }

    private static boolean devProfile(ConfigurableEnvironment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        String active = environment.getProperty("spring.profiles.active");
        return active != null && active.toLowerCase(Locale.ROOT).contains("dev");
    }

    private static Path dotenvFile() {
        Path cwd = Path.of(System.getProperty("user.dir", ".")).resolve(".env");
        if (Files.isRegularFile(cwd)) {
            return cwd;
        }
        Path nested = Path.of(System.getProperty("user.dir", ".")).resolve("AI-Fishing-BE").resolve(".env");
        return Files.isRegularFile(nested) ? nested : cwd;
    }

    private static Map<String, Object> parse(Path file) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = unquote(line.substring(eq + 1).trim());
                if (!key.isEmpty()) {
                    out.put(key, value);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
