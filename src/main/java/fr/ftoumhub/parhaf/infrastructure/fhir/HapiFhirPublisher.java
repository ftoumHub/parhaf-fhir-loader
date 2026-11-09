package fr.ftoumhub.parhaf.infrastructure.fhir;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import fr.ftoumhub.parhaf.domain.port.FhirPublisher;
import fr.ftoumhub.parhaf.domain.port.PublishFailure;
import fr.ftoumhub.parhaf.domain.port.PublishSuccess;
import io.vavr.control.Either;
import io.vavr.control.Try;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publie les transactions vers un serveur HAPI FHIR, avec quelques tentatives
 * espacees en cas d'echec transitoire : le serveur reste indisponible un moment
 * apres l'installation des Implementation Guides.
 */
public class HapiFhirPublisher implements FhirPublisher {

    private static final Logger log = LoggerFactory.getLogger(HapiFhirPublisher.class);

    private final IGenericClient client;
    private final int maxAttempts;
    private final long backoffMillis;

    public HapiFhirPublisher(IGenericClient client, int maxAttempts, long backoffMillis) {
        this.client = client;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffMillis = Math.max(0, backoffMillis);
    }

    @Override
    public Either<PublishFailure, PublishSuccess> publish(String recordId, Bundle bundle) {
        Throwable lastCause = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Try<Bundle> outcome =
                    Try.of(() -> client.transaction().withBundle(bundle).execute());
            if (outcome.isSuccess()) {
                return Either.right(
                        new PublishSuccess(recordId, outcome.get().getEntry().size()));
            }
            lastCause = outcome.getCause();
            log.debug("Echec publication {} (tentative {}/{}) : {}",
                    recordId, attempt, maxAttempts, lastCause.getMessage());
            if (attempt < maxAttempts) {
                pause(backoffMillis * attempt);
            }
        }
        return Either.left(PublishFailure.of(recordId, lastCause));
    }

    @Override
    public String probeFhirVersion() {
        CapabilityStatement statement = client.capabilities()
                .ofType(CapabilityStatement.class)
                .execute();
        return statement.getFhirVersion() == null
                ? "inconnue"
                : statement.getFhirVersion().toCode();
    }

    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
