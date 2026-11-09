package fr.ftoumhub.parhaf.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

/**
 * Resume structure optionnel, redige par l'auteur du compte rendu.
 *
 * <p>Le dataset previent qu'il n'est pas curate et peut diverger du contenu
 * reel du document. On n'en exploite donc que la duree de sejour, qui est la
 * seule donnee temporelle structuree de tout le corpus.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StructuredAbstract(
        @JsonProperty("primary_diagnosis") Object primaryDiagnosis,
        @JsonProperty("primary_procedure") Object primaryProcedure,
        @JsonProperty("admission_mode") String admissionMode,
        @JsonProperty("discharge_mode") String dischargeMode,
        @JsonProperty("length_of_stay") Amount lengthOfStay) {

    public Optional<Integer> lengthOfStayInDays() {
        return Optional.ofNullable(lengthOfStay)
                .filter(amount -> amount.isExpressedIn("jour"))
                .flatMap(Amount::asInt);
    }
}
