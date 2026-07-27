package com.wms.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.flywaydb.core.Flyway;

/**
 * Loads database configuration from a properties file.
 * Supports environment-variable overrides so secrets are never hardcoded in source.
 *
 * Priority: System env var > db.properties file > hardcoded defaults (dev only).
 */
public final class DatabaseConfig {

    private static final Properties PROP = new Properties();

    private static volatile boolean flywayMigrated = false;

    static {
        load();
    }

    private DatabaseConfig() {
        throw new UnsupportedOperationException();
    }

    private static void load() {
        String configPath = System.getenv("DB_PROPERTIES_PATH");
        if (configPath == null || configPath.isBlank()) {
            configPath = "/db.properties";
        }

        try (InputStream is = DatabaseConfig.class.getResourceAsStream(configPath)) {
            if (is != null) {
                PROP.load(is);
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load db.properties from classpath (" + configPath + "): "
                    + e.getMessage() + ". Using environment variables / defaults.");
        }

        overrideFromEnv("DB_HOST",      "db.host");
        overrideFromEnv("DB_PORT",      "db.port");
        overrideFromEnv("DB_NAME",      "db.name");
        overrideFromEnv("DB_USERNAME",   "db.username");
        overrideFromEnv("DB_PASSWORD",   "db.password");
        overrideFromEnv("DB_POOL_SIZE", "db.pool.maxSize");
    }

    private static void overrideFromEnv(String envKey, String propKey) {
        String val = System.getenv(envKey);
        if (val != null) {
            PROP.setProperty(propKey, val);
        }
    }

    /**
     * Runs Flyway migrations if not already done.
     * Safe to call multiple times — uses double-checked locking.
     */
    public static synchronized void migrateIfNeeded() {
        if (flywayMigrated) return;
        try {
            String migrationPath = PROP.getProperty("db.migration.path", "/db/migration");
            Flyway fw = Flyway.configure()
                    .dataSource(getJdbcUrl(), getUsername(), getPassword())
                    .locations("classpath:" + migrationPath)
                    .baselineOnMigrate(true)
                    .validateOnMigrate(false)
                    .load();
            fw.migrate();
            flywayMigrated = true;
        } catch (Exception e) {
            System.err.println("Warning: Flyway migration failed: " + e.getMessage()
                    + ". Application will continue — schema may already exist.");
        }
    }

    public static String getHost()     { return PROP.getProperty("db.host", "localhost"); }
    public static String getPort()     { return PROP.getProperty("db.port", "3306"); }
    public static String getDatabase() { return PROP.getProperty("db.name", "wms_hub"); }
    public static String getUsername() { return PROP.getProperty("db.username", "root"); }
    public static String getPassword() { return PROP.getProperty("db.password", ""); }
    public static String getJdbcUrl() {
        return "jdbc:mysql://" + getHost() + ":" + getPort() + "/" + getDatabase()
                + "?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh"
                + "&characterEncoding=UTF8&connectionCollation=utf8mb4_unicode_ci"
                + "&allowPublicKeyRetrieval=true";
    }

    public static int getPoolMinIdle() {
        return Integer.parseInt(PROP.getProperty("db.pool.minIdle", "2"));
    }

    public static int getPoolMaxSize() {
        return Integer.parseInt(PROP.getProperty("db.pool.maxSize", "10"));
    }

    public static int getPoolConnectionTimeout() {
        return Integer.parseInt(PROP.getProperty("db.pool.connectionTimeout", "30000"));
    }

    public static int getPoolIdleTimeout() {
        return Integer.parseInt(PROP.getProperty("db.pool.idleTimeout", "600000"));
    }

    public static int getPoolMaxLifetime() {
        return Integer.parseInt(PROP.getProperty("db.pool.maxLifetime", "1800000"));
    }

    /**
     * Returns a string property with fallback chain: env var > property file > default.
     */
    public static String getProperty(String key, String envVar, String defaultVal) {
        String envVal = System.getenv(envVar);
        if (envVal != null && !envVal.isBlank()) {
            return envVal;
        }
        return PROP.getProperty(key, defaultVal);
    }
}
