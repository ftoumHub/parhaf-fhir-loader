package fr.ftoumhub.parhaf.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * Une entree du corpus : un patient fictif, son scenario clinique et ses
 * un a trois comptes rendus.
 *
 * <p>C'est l'unite de chargement : un PatientRecord donne exactement une
 * transaction FHIR.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientRecord(
        String id,
        @JsonProperty("local_id") String localId,
        String specialty,
        String author,
        String reviewer,
        String pool,
        @JsonProperty("suggested_scenario") Scenario scenario,
        List<ClinicalDocument> documents,
        @JsonProperty("structured_abstract") StructuredAbstract structuredAbstract) {

    public List<ClinicalDocument> documentList() {
        return documents == null ? List.of() : documents;
    }

    public Optional<Scenario> scenarioOpt() {
        return Optional.ofNullable(scenario);
    }

    public Optional<StructuredAbstract> abstractOpt() {
        return Optional.ofNullable(structuredAbstract);
    }

    public int lengthOfStayInDays() {
        return abstractOpt().flatMap(StructuredAbstract::lengthOfStayInDays).orElse(0);
    }

    public PatientRecord withDocuments(List<ClinicalDocument> resolved) {
        return new PatientRecord(id, localId, specialty, author, reviewer, pool,
                scenario, resolved, structuredAbstract);
    }
}
