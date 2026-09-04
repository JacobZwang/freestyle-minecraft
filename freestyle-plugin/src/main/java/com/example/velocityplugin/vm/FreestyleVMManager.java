package com.example.velocityplugin.vm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Manages Freestyle VMs backing Minecraft servers, against the Freestyle v5 API.
 *
 * <p>Forking a world is two calls: snapshot the source VM — memory and disk, so the running
 * Minecraft server is captured mid-tick — then create a VM from that snapshot. The new VM resumes
 * into the same process state, which is why a fork lands in roughly the time one HTTP round trip
 * takes rather than the time a Minecraft server takes to boot.
 *
 * <p>Each VM is published on its own domain with a {@code minecraft} TLS rule. The rule is created
 * inline with the VM, so deleting the VM deletes the route with it.
 *
 * @see <a href="https://www.freestyle.sh/docs/vms">Freestyle VM docs</a>
 */
public class FreestyleVMManager {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * Intermediate fork snapshots are disposable: this is "delete once this many seconds pass
     * without a VM being created from it", and we create from it immediately.
     */
    private static final int FORK_SNAPSHOT_TTL_SECONDS = 3600;

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    private final FreestyleConfig config;
    private final Logger logger;

    public FreestyleVMManager(Logger logger) {
        this(logger, FreestyleConfig.load(logger));
    }

    public FreestyleVMManager(Logger logger, FreestyleConfig config) {
        this.logger = logger;
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        logger.info("Freestyle VM manager ready: api={}, base VM='{}', domains '*.{}'",
                config.getApiUrl(), config.getBaseVm(), config.getDomainSuffix());
    }

    public FreestyleConfig getConfig() {
        return config;
    }

    /** Creates a world by forking the configured base VM, which already has a Minecraft server on it. */
    public ServerInstance createServer(String name) throws IOException, InterruptedException {
        return forkServer(config.getBaseVm(), name);
    }

    /**
     * Forks a VM into a new one running the same Minecraft server, published on its own domain.
     *
     * @param sourceVm the VM to fork, by Freestyle id or account slug
     * @param newName  a name for the new world; also seeds its domain
     */
    public ServerInstance forkServer(String sourceVm, String newName)
            throws IOException, InterruptedException {

        String snapshotId = snapshot(sourceVm, newName);
        logger.info("Snapshotted {} as {} for world '{}'", sourceVm, snapshotId, newName);

        String domain = generateDomain(newName);
        JsonNode vm = createVmFromSnapshot(snapshotId, newName, domain);

        ServerInstance instance = toInstance(vm, newName, domain);
        logger.info("Forked {} -> {} ({})", sourceVm, instance.getId(), domain);
        return instance;
    }

    /** Pauses a VM, freezing memory so a later start resumes the same Minecraft process. */
    public void suspendServer(String vmId) throws IOException, InterruptedException {
        send(post(path("/v5/vms/%s/pause", vmId), null), "pause VM " + vmId);
        logger.info("Paused VM {}", vmId);
    }

    /** Starts a paused VM back up. Players joining the domain also wake it on their own. */
    public void resumeServer(String vmId) throws IOException, InterruptedException {
        send(post(path("/v5/vms/%s/start", vmId), null), "start VM " + vmId);
        logger.info("Started VM {}", vmId);
    }

    /** Deletes a VM. Its TLS rule and firewall rules go with it, freeing the domain. */
    public void deleteServer(String vmId) throws IOException, InterruptedException {
        HttpRequest request = request(path("/v5/vms/%s", vmId)).DELETE().build();
        send(request, "delete VM " + vmId);
        logger.info("Deleted VM {}", vmId);
    }

    /** Looks up a VM and the domain routing to it. */
    public Optional<ServerInstance> getServer(String vmId) {
        try {
            HttpRequest request = request(path("/v5/vms/%s", vmId)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (!isSuccess(response.statusCode())) {
                logger.warn("Failed to get VM {}: {}", vmId, describeError(response));
                return Optional.empty();
            }

            JsonNode vm = json.readTree(response.body());
            String id = vm.path("id").asText();
            String domain = findDomain(id).orElse(null);
            if (domain == null) {
                logger.warn("VM {} has no Minecraft domain routing to it", id);
                return Optional.empty();
            }

            String name = vm.path("metadata").path("world").asText(vm.path("slug").asText(id));
            return Optional.of(toInstance(vm, name, domain));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException e) {
            logger.warn("Failed to get VM {}: {}", vmId, e.getMessage());
            return Optional.empty();
        }
    }

    /** Lists every VM this plugin created, identified by the {@code world} metadata key. */
    public List<ServerInstance> listServers() throws IOException, InterruptedException {
        HttpRequest request = request("/v5/vms?limit=100").GET().build();
        JsonNode body = send(request, "list VMs");

        Map<String, String> domains = minecraftDomainsByVm();

        List<ServerInstance> instances = new ArrayList<>();
        for (JsonNode vm : body.path("vms")) {
            String world = vm.path("metadata").path("world").asText(null);
            if (world == null) {
                continue; // not one of ours
            }
            String domain = domains.get(vm.path("id").asText());
            if (domain != null) {
                instances.add(toInstance(vm, world, domain));
            }
        }
        return instances;
    }

    // --- Freestyle API calls -------------------------------------------------

    private String snapshot(String sourceVm, String worldName) throws IOException, InterruptedException {
        ObjectNode body = json.createObjectNode()
                .put("displayName", "fork for world " + worldName)
                .put("autoDeleteSeconds", FORK_SNAPSHOT_TTL_SECONDS);

        JsonNode response = send(post(path("/v5/vms/%s/snapshot", sourceVm), body),
                "snapshot VM " + sourceVm);
        return response.path("snapshotId").asText();
    }

    private JsonNode createVmFromSnapshot(String snapshotId, String worldName, String domain)
            throws IOException, InterruptedException {

        ObjectNode body = json.createObjectNode()
                .put("snapshotId", snapshotId)
                .put("idleTimeoutSeconds", config.getIdleTimeoutSeconds());

        // Tagged so listServers() can find the worlds we own.
        body.putObject("metadata").put("world", worldName);

        // A VM reaches nothing it has not been allowed to, so egress is stated explicitly.
        // Inbound needs no rule: the edge reaches the VM over a standing grant, and the
        // Minecraft TLS rule below is what admits players.
        ObjectNode egress = json.createObjectNode().put("action", "allow");
        egress.putObject("source"); // empty source means the VM being created
        egress.putObject("destination").put("public", true);
        ArrayNode firewallRules = json.createArrayNode().add(egress);
        body.putObject("firewall").set("rules", firewallRules);

        // protocol "minecraft" puts the rule on the edge's Minecraft front on 25565, which
        // routes on the server address in the client handshake. No certificate involved.
        ObjectNode tlsRule = json.createObjectNode()
                .put("action", "allow")
                .put("domain", domain)
                .put("protocol", "minecraft");
        tlsRule.putObject("source").put("public", true);
        tlsRule.putObject("destination").put("port", config.getMinecraftPort());
        ArrayNode tlsRules = json.createArrayNode().add(tlsRule);
        body.putObject("tls").set("rules", tlsRules);

        return send(post("/v5/vms", body), "create VM for world " + worldName);
    }

    /** Maps VM id to the domain of the Minecraft TLS rule routing to it, in one call. */
    private Map<String, String> minecraftDomainsByVm() throws IOException, InterruptedException {
        JsonNode body = send(request("/v5/tls").GET().build(), "list TLS rules");

        Map<String, String> domains = new HashMap<>();
        for (JsonNode rule : body.path("rules")) {
            if (!"minecraft".equals(rule.path("protocol").asText())) {
                continue;
            }
            String vmId = rule.path("destination").path("vmId").asText(null);
            if (vmId != null) {
                domains.putIfAbsent(vmId, rule.path("domain").asText());
            }
        }
        return domains;
    }

    /** Finds the domain of the Minecraft TLS rule pointing at this VM, if there is one. */
    private Optional<String> findDomain(String vmId) {
        try {
            return Optional.ofNullable(minecraftDomainsByVm().get(vmId));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            logger.warn("Failed to look up domain for VM {}: {}", vmId, e.getMessage());
        }
        return Optional.empty();
    }

    // --- Helpers -------------------------------------------------------------

    private ServerInstance toInstance(JsonNode vm, String name, String domain) {
        return new ServerInstance(
                vm.path("id").asText(),
                name,
                domain,
                config.getMinecraftPort(),
                ServerInstance.State.from(vm.path("state").asText(null)));
    }

    /**
     * Builds a domain for a world. {@code style.dev} names are first-come-first-served across all
     * of Freestyle, so a random suffix keeps a plain world name like "survival" from colliding
     * with someone else's.
     */
    private String generateDomain(String worldName) {
        String slug = worldName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            slug = "world";
        }
        if (slug.length() > 24) {
            slug = slug.substring(0, 24).replaceAll("-+$", "");
        }

        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(SUFFIX_ALPHABET.charAt(random.nextInt(SUFFIX_ALPHABET.length())));
        }

        return config.getDomainPrefix() + "-" + slug + "-" + suffix + "." + config.getDomainSuffix();
    }

    /** Builds a path with one VM id or slug interpolated, percent-encoded so it stays one segment. */
    private String path(String template, String segment) {
        return String.format(template, URLEncoder.encode(segment, StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(config.getApiUrl().resolve(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Accept", "application/json");
    }

    private HttpRequest post(String path, JsonNode body) throws IOException {
        HttpRequest.Builder builder = request(path);
        if (body == null) {
            return builder.POST(HttpRequest.BodyPublishers.noBody()).build();
        }
        return builder
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
    }

    private JsonNode send(HttpRequest request, String action) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (!isSuccess(response.statusCode())) {
            throw new FreestyleApiException("Failed to " + action + ": " + describeError(response),
                    response.statusCode());
        }

        String body = response.body();
        return body == null || body.isBlank() ? json.createObjectNode() : json.readTree(body);
    }

    private static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    /** Freestyle errors carry a machine-readable code and a message; surface both when present. */
    private String describeError(HttpResponse<String> response) {
        String body = response.body();
        try {
            JsonNode error = json.readTree(body);
            String code = error.path("code").asText(null);
            String message = error.path("message").asText(null);
            if (message != null) {
                return response.statusCode() + " " + (code != null ? "[" + code + "] " : "") + message;
            }
        } catch (Exception ignored) {
            // Fall through to the raw body.
        }
        return response.statusCode() + " " + body;
    }

    /** Thrown when the Freestyle API rejects a request. */
    public static class FreestyleApiException extends IOException {
        private final int statusCode;

        public FreestyleApiException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
