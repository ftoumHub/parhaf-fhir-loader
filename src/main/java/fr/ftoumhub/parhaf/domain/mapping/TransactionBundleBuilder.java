package fr.ftoumhub.parhaf.domain.mapping;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

import java.util.Optional;

/**
 * Assemble un Bundle de type transaction.
 *
 * <p>Toutes les entrees sont des PUT sur un identifiant deterministe, donc
 * "creer ou mettre a jour". C'est ce qui rend le chargement rejouable : une
 * seconde execution ecrase les memes ressources au lieu d'en creer de
 * nouvelles.</p>
 */
public class TransactionBundleBuilder {

    private final Bundle bundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);

    public TransactionBundleBuilder put(Resource resource) {
        String url = resource.getIdElement().getValue();
        bundle.addEntry()
                .setFullUrl(url)
                .setResource(resource)
                .getRequest()
                .setMethod(Bundle.HTTPVerb.PUT)
                .setUrl(url);
        return this;
    }

    public TransactionBundleBuilder putIfPresent(Optional<? extends Resource> resource) {
        resource.ifPresent(this::put);
        return this;
    }

    public Bundle build() {
        return bundle;
    }
}
