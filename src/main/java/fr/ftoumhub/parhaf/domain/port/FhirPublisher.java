package fr.ftoumhub.parhaf.domain.port;

import io.vavr.control.Either;
import org.hl7.fhir.r4.model.Bundle;

/**
 * Port de sortie : publie une transaction vers un serveur FHIR.
 *
 * <p>L'echec est une valeur de retour, pas une exception : un patient qui
 * echoue ne doit pas interrompre le chargement des 4 258 autres.</p>
 */
public interface FhirPublisher {

    Either<PublishFailure, PublishSuccess> publish(String recordId, Bundle bundle);

    /** Interroge /metadata et retourne la version FHIR annoncee par le serveur. */
    String probeFhirVersion();
}
