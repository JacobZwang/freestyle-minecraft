package com.example.velocityplugin.vm;

import org.slf4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuration for the Freestyle API, read from {@code freestyle-config.properties}
 * with environment variables taking precedence so a key never has to be committed.
 */
public class FreestyleConfig {

    private static final String CONFIG_FILE = "freestyle-config.properties";

    private final URI apiUrl;
    private final String apiKey;
    private final String baseVm;
    private final String domainSuffix;
    private final String domainPrefix;
    private final int idleTimeoutSeconds;
    private final int minecraftPort;

    private FreestyleConfig(Properties props) {
        String url = get(props, "freestyle.api.url", "FREESTYLE_API_URL", "https://api.freestyle.sh");
        String key = get(props, "freestyle.api.key", "FREESTYLE_API_KEY", null);

        if (key == null || key.isBlank() || key.equals("your-api-key-here")) {
            throw new IllegalStateException(
                    "No Freestyle API key. Set FREESTYLE_API_KEY in the environment, or "
                            + "freestyle.api.key in " + CONFIG_FILE + ". Keys come from https://dash.freestyle.sh");
        }

        this.apiUrl = URI.create(url.endsWith("/") ? url.substring(0, url.length() - 1) : url);
        this.apiKey = key.trim();
        this.baseVm = get(props, "freestyle.base.vm", "FREESTYLE_BASE_VM", "minecraft");
        this.domainSuffix = get(props, "freestyle.domain.suffix", "FREESTYLE_DOMAIN_SUFFIX", "style.dev");
        this.domainPrefix = get(props, "freestyle.domain.prefix", "FREESTYLE_DOMAIN_PREFIX", "mc");
        this.idleTimeoutSeconds = Integer.parseInt(
                get(props, "freestyle.idle.timeout.seconds", "FREESTYLE_IDLE_TIMEOUT_SECONDS", "300"));
        this.minecraftPort = Integer.parseInt(
                get(props, "freestyle.minecraft.port", "FREESTYLE_MINECRAFT_PORT", "25565"));
    }

    /** Loads configuration, preferring environment variables over the properties file. */
    public static FreestyleConfig load(Logger logger) {
        Properties props = new Properties();
        Path path = Path.of(CONFIG_FILE);

        if (Files.exists(path)) {
            try (FileInputStream in = new FileInputStream(path.toFile())) {
                props.load(in);
                logger.info("Loaded Freestyle configuration from {}", CONFIG_FILE);
            } catch (IOException e) {
                logger.warn("Could not read {}: {}", CONFIG_FILE, e.getMessage());
            }
        } else {
            logger.info("No {} found, reading configuration from the environment", CONFIG_FILE);
        }

        return new FreestyleConfig(props);
    }

    private static String get(Properties props, String property, String envVar, String fallback) {
        String env = System.getenv(envVar);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return props.getProperty(property, fallback);
    }

    public URI getApiUrl() {
        return apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    /** The VM new servers are forked from, by id or account slug. */
    public String getBaseVm() {
        return baseVm;
    }

    /** Domain suffix worlds are published under. {@code style.dev} names are free and need no verification. */
    public String getDomainSuffix() {
        return domainSuffix;
    }

    /** Prefix for generated world domains, keeping one account's names together. */
    public String getDomainPrefix() {
        return domainPrefix;
    }

    /** Seconds without network activity before Freestyle pauses a world. -1 disables. */
    public int getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    /** Port the Minecraft server listens on inside the VM. */
    public int getMinecraftPort() {
        return minecraftPort;
    }
}
