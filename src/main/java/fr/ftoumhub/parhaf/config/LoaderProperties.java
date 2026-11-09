package fr.ftoumhub.parhaf.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * Parametres du chargement, lies depuis le prefixe {@code parhaf} de
 * application.yaml. Toute valeur peut etre surchargee en ligne de commande :
 * {@code --parhaf.limit=20}.
 */
@ConfigurationProperties(prefix = "parhaf")
public record LoaderProperties(
        String serverBase,
        String corpusPath,
        Set<String> specialties,
        int limit,
        int threads,
        int maxAttempts,
        long backoffMillis,
        int socketTimeoutMillis,
        String custodianName,
        boolean dryRun) {

    public LoaderProperties {
        serverBase = valueOrDefault(serverBase, "http://localhost:8080/fhir");
        corpusPath = valueOrDefault(corpusPath, "./data/parhaf/corpus.json");
        custodianName = valueOrDefault(custodianName, "PARHAF (corpus fictif)");
        specialties = specialties == null ? Set.of() : Set.copyOf(specialties);
        limit = Math.max(limit, 0);
        threads = threads <= 0 ? 4 : threads;
        maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
        backoffMillis = backoffMillis <= 0 ? 500L : backoffMillis;
        socketTimeoutMillis = socketTimeoutMillis <= 0 ? 60_000 : socketTimeoutMillis;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
