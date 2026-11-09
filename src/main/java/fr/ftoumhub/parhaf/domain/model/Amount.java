package fr.ftoumhub.parhaf.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Locale;
import java.util.Optional;

/**
 * Couple valeur / unite du corpus PARHAF : age du patient, duree de sejour.
 *
 * <p>Nomme {@code Amount} et non {@code Quantity} pour ne pas entrer en
 * collision avec {@code org.hl7.fhir.r4.model.Quantity} dans les mappers.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Amount(Double value, String unit) {

    public Optional<Integer> asInt() {
        return value == null ? Optional.empty() : Optional.of(value.intValue());
    }

    public boolean isExpressedIn(String prefix) {
        return unit != null && unit.toLowerCase(Locale.ROOT).startsWith(prefix);
    }
}
