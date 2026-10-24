package com.hishob.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RT-A guard: credentials must come from the deployment environment. A {@code ${VAR:default}}
 * fallback signs tokens, authenticates service calls or opens the database with a value that is
 * readable by anyone who can read the repository, so no production source may declare one.
 */
class CredentialFallbackGuardTest {

    private static final Pattern CREDENTIAL_DEFAULT = Pattern.compile(
            "\\$\\{(?:JWT_SECRET|INTERNAL_SERVICE_TOKEN|DB_PASSWORD|jwt\\.secret|hishob\\.internal\\.token):[^}]*}");

    private static final List<String> PRODUCTION_MODULES = List.of(
            "services/auth-service",
            "services/expense-service",
            "services/settlement-service",
            "services/group-service",
            "services/balance-service",
            "services/notification-service",
            "gateway/api-gateway",
            "infrastructure/config-server",
            "infrastructure/discovery-server");

    private static final List<String> SECRET_BEARING_SERVICES = List.of(
            "auth-service", "expense-service", "settlement-service",
            "group-service", "balance-service", "notification-service");

    @Test
    @DisplayName("no src/main file declares a credential property with a fallback value")
    void productionSourcesHaveNoCredentialDefaults() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String module : PRODUCTION_MODULES) {
            Path main = repoRoot().resolve(module).resolve("src/main");
            if (!Files.isDirectory(main)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(main)) {
                List<Path> sources = paths
                        .filter(path -> path.toString().matches("(?i).*\\.(yml|yaml|properties|java)$"))
                        .toList();
                for (Path source : sources) {
                    for (String line : Files.readAllLines(source)) {
                        if (CREDENTIAL_DEFAULT.matcher(line).find()) {
                            offenders.add(module + " / " + main.relativize(source) + " -> " + credentialNameOf(line));
                        }
                    }
                }
            }
        }
        assertThat(offenders)
                .as("credential properties in src/main that still fall back to a repository-visible value")
                .isEmpty();
    }

    @Test
    @DisplayName("the three active services read every credential from an environment variable")
    void activeServiceConfigsBindCredentialsToEnvironmentVariables() throws IOException {
        for (String service : List.of("auth-service", "expense-service", "settlement-service")) {
            List<String> lines = Files.readAllLines(
                    repoRoot().resolve("services").resolve(service)
                            .resolve("src/main/resources/application.yml"));
            String joined = String.join("\n", lines);
            assertThat(joined)
                    .as(service + " must sign and verify with the JWT_SECRET environment variable")
                    .contains("secret: ${JWT_SECRET}");
            assertThat(joined)
                    .as(service + " must authenticate service calls with INTERNAL_SERVICE_TOKEN")
                    .contains("${INTERNAL_SERVICE_TOKEN}");
            assertThat(joined)
                    .as(service + " must connect to the database with DB_PASSWORD")
                    .contains("password: ${DB_PASSWORD}");
        }
    }

    @Test
    @DisplayName("every secret-bearing service declares fail-fast credential guards in source")
    void servicesDeclareFailFastCredentialGuards() throws IOException {
        // This only proves the guard exists in all six modules; each service's own
        // *CredentialGuardTest exercises the rejection behaviour.
        for (String service : SECRET_BEARING_SERVICES) {
            String shortName = service.replace("-service", "");
            Path security = repoRoot().resolve("services").resolve(service)
                    .resolve("src/main/java/com/hishob").resolve(shortName).resolve("security");

            String jwtService = Files.readString(security.resolve("JwtService.java"));
            assertThat(jwtService)
                    .as(service + " JwtService must fail fast instead of signing with an unset secret")
                    .contains("@PostConstruct")
                    .contains("jwt.secret is not configured")
                    .doesNotContain("${jwt.secret:");

            String filter = Files.readString(security.resolve("InternalAuthFilter.java"));
            assertThat(filter)
                    .as(service + " InternalAuthFilter must fail fast instead of accepting a blank token")
                    .contains("hishob.internal.token is not configured");
        }
    }

    @Test
    @DisplayName("environment templates offer placeholders, never credentials")
    void templatesCarryPlaceholdersOnly() throws IOException {
        for (String service : List.of("auth-service", "expense-service", "settlement-service", "notification-service")) {
            Path example = repoRoot().resolve("services").resolve(service).resolve(".env.example");
            assertThat(example).as(service + " must document the secret variables").exists();
            String content = Files.readString(example);
            assertThat(content)
                    .as(service + " .env.example must require JWT_SECRET")
                    .contains("JWT_SECRET=replace-with-");
            assertThat(content)
                    .as(service + " .env.example must require INTERNAL_SERVICE_TOKEN")
                    .contains("INTERNAL_SERVICE_TOKEN=replace-with-");
        }
    }

    /**
     * Only the property name of an offending line is reported; a fallback value is never echoed into
     * a test report or stack trace.
     */
    private static String credentialNameOf(String line) {
        for (String name : List.of("JWT_SECRET", "INTERNAL_SERVICE_TOKEN", "DB_PASSWORD",
                "jwt.secret", "hishob.internal.token")) {
            if (line.contains("${" + name + ":")) {
                return name;
            }
        }
        return "unattributed credential default";
    }

    private Path repoRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.exists(candidate.resolve("render.yaml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new AssertionError("Repository root not found from " + Path.of("").toAbsolutePath());
    }
}
