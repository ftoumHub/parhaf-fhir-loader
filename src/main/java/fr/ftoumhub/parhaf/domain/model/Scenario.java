package fr.ftoumhub.parhaf.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

/**
 * Scenario clinique fourni au redacteur du compte rendu.
 *
 * <p>Construit par echantillonnage sur les distributions observees dans le
 * SNDS : c'est ce qui donne au corpus sa vraisemblance epidemiologique sans
 * exposer la moindre donnee reelle.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Scenario(
        String name,
        Amount age,
        String sex,
        @JsonProperty("admission_mode") String admissionMode,
        @JsonProperty("discharge_mode") String dischargeMode,
        @JsonProperty("primary_procedure") CodedConcept primaryProcedure,
        @JsonProperty("primary_diagnosis") CodedConcept primaryDiagnosis,
        @JsonProperty("type_of_care") String typeOfCare) {

    public Optional<CodedConcept> diagnosis() {
        return Optional.ofNullable(primaryDiagnosis);
    }

    public Optional<CodedConcept> procedure() {
        return Optional.ofNullable(primaryProcedure);
    }

    public Optional<Amount> ageOpt() {
        return Optional.ofNullable(age);
    }
}
