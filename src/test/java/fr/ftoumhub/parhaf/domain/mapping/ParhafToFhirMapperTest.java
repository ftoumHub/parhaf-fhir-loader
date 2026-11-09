package fr.ftoumhub.parhaf.domain.mapping;

import fr.ftoumhub.parhaf.domain.model.PatientRecord;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le mapper est une fonction pure : une fixture en entree, un Bundle en sortie.
 * Aucun serveur, aucun docker, aucun reseau — ces tests tournent en CI tels quels.
 */
class ParhafToFhirMapperTest {

    private final ParhafToFhirMapper mapper = new ParhafToFhirMapper(
            new PatientMapper(), new ClinicalMapper(), new DocumentMapper("Test"));

    @Nested
    @DisplayName("Structure de la transaction")
    class Structure {

        @Test
        void toutes_les_entrees_sont_des_put_pour_rester_idempotent() {
            Bundle bundle = mapper.toTransaction(ParhafFixtures.hospitalisation());

            assertThat(bundle.getType()).isEqualTo(Bundle.BundleType.TRANSACTION);
            assertThat(bundle.getEntry()).allSatisfy(entry -> {
                assertThat(entry.getRequest().getMethod()).isEqualTo(Bundle.HTTPVerb.PUT);
                assertThat(entry.getRequest().getUrl()).isEqualTo(entry.getFullUrl());
            });
        }

        @Test
        void deux_executions_produisent_exactement_les_memes_identifiants() {
            PatientRecord record = ParhafFixtures.hospitalisation();

            assertThat(idsOf(mapper.toTransaction(record)))
                    .isEqualTo(idsOf(mapper.toTransaction(record)));
        }

        @Test
        void un_enregistrement_sans_document_ni_scenario_ne_produit_que_le_patient() {
            Bundle bundle = mapper.toTransaction(ParhafFixtures.vide());

            assertThat(typesOf(bundle)).containsExactly("Patient");
        }
    }

    @Nested
    @DisplayName("Patient et redacteur")
    class Identite {

        @Test
        void le_patient_porte_son_identifiant_parhaf_et_le_marqueur_fictif() {
            Patient patient = firstOf(
                    mapper.toTransaction(ParhafFixtures.hospitalisation()), Patient.class);

            assertThat(patient.getIdentifierFirstRep().getSystem())
                    .isEqualTo(LocalIdentifierSystems.PATIENT);
            assertThat(patient.getIdentifierFirstRep().getValue())
                    .isEqualTo("PNEUMOLOGIE-00001");
            assertThat(patient.getExtensionByUrl(LocalIdentifierSystems.FICTITIOUS_FLAG))
                    .isNotNull();
        }

        @Test
        void un_patronyme_compose_reste_entier_dans_le_nom_de_famille() {
            Patient patient = firstOf(
                    mapper.toTransaction(ParhafFixtures.anatomopathologie()), Patient.class);

            assertThat(patient.getNameFirstRep().getGivenAsSingleString()).isEqualTo("Marc");
            assertThat(patient.getNameFirstRep().getFamily()).isEqualTo("Le van gong");
        }

        @Test
        void le_trigramme_auteur_donne_un_practitioner_mutualisable() {
            Practitioner practitioner = firstOf(
                    mapper.toTransaction(ParhafFixtures.hospitalisation()), Practitioner.class);

            assertThat(practitioner.getIdElement().getIdPart()).isEqualTo("parhaf-prac-ABC");
        }
    }

    @Nested
    @DisplayName("Sejour")
    class Sejour {

        @Test
        void un_crh_produit_un_sejour_hospitalier_avec_modes_entree_et_sortie() {
            Encounter encounter = firstOf(
                    mapper.toTransaction(ParhafFixtures.hospitalisation()), Encounter.class);

            assertThat(encounter.getClass_().getCode()).isEqualTo("IMP");
            assertThat(encounter.getStatus()).isEqualTo(Encounter.EncounterStatus.FINISHED);
            assertThat(encounter.getHospitalization().getAdmitSource().getText())
                    .isEqualTo("entree par les urgences");
            assertThat(encounter.getHospitalization().getDischargeDisposition().getText())
                    .isEqualTo("retour a domicile");
        }

        @Test
        void un_anapath_reste_ambulatoire_et_sans_bloc_hospitalization() {
            Encounter encounter = firstOf(
                    mapper.toTransaction(ParhafFixtures.anatomopathologie()), Encounter.class);

            assertThat(encounter.getClass_().getCode()).isEqualTo("AMB");
            assertThat(encounter.getHospitalization().isEmpty()).isTrue();
        }

        @Test
        void la_duree_de_sejour_alimente_length_et_la_fin_de_periode() {
            Encounter encounter = firstOf(
                    mapper.toTransaction(ParhafFixtures.hospitalisation()), Encounter.class);

            assertThat(encounter.getLength().getValue().intValue()).isEqualTo(5);
            assertThat(encounter.getPeriod().getEnd())
                    .isAfter(encounter.getPeriod().getStart());
        }

        @Test
        void le_diagnostic_principal_est_rattache_au_sejour_en_rang_1() {
            Encounter encounter = firstOf(
                    mapper.toTransaction(ParhafFixtures.hospitalisation()), Encounter.class);

            assertThat(encounter.getDiagnosisFirstRep().getRank()).isEqualTo(1);
            assertThat(encounter.getDiagnosisFirstRep().getCondition().getReference())
                    .isEqualTo("Condition/parhaf-cond-PNEUMOLOGIE-00001");
        }
    }

    @Nested
    @DisplayName("Codage clinique")
    class Codage {

        @Test
        void le_diagnostic_principal_devient_une_condition_cim10() {
            Condition condition = firstOf(
                    mapper.toTransaction(ParhafFixtures.hospitalisation()), Condition.class);

            assertThat(condition.getCode().getCodingFirstRep().getCode()).isEqualTo("C341");
            assertThat(condition.getCode().getCodingFirstRep().getSystem())
                    .isEqualTo(Terminologies.ICD10);
        }

        @Test
        void un_acte_principal_devient_une_procedure_ccam() {
            Procedure procedure = firstOf(
                    mapper.toTransaction(ParhafFixtures.anatomopathologie()), Procedure.class);

            assertThat(procedure.getCode().getCodingFirstRep().getCode()).isEqualTo("HHFA016");
            assertThat(procedure.getCode().getCodingFirstRep().getSystem())
                    .isEqualTo(Terminologies.CCAM);
        }

        @Test
        void sans_acte_principal_aucune_procedure_n_est_produite() {
            Bundle bundle = mapper.toTransaction(ParhafFixtures.hospitalisation());

            assertThat(typesOf(bundle)).doesNotContain("Procedure");
        }
    }

    @Nested
    @DisplayName("Documents")
    class Documents {

        @Test
        void le_compte_rendu_devient_un_binary_pointe_par_un_documentreference() {
            Bundle bundle = mapper.toTransaction(ParhafFixtures.hospitalisation());
            Binary binary = firstOf(bundle, Binary.class);
            DocumentReference reference = firstOf(bundle, DocumentReference.class);

            assertThat(binary.getContentType()).startsWith("text/plain");
            assertThat(reference.getContentFirstRep().getAttachment().getUrl())
                    .isEqualTo(binary.getIdElement().getValue());
        }

        @Test
        void un_crh_est_type_en_loinc_compte_rendu_de_sortie() {
            DocumentReference reference = firstOf(
                    mapper.toTransaction(ParhafFixtures.hospitalisation()),
                    DocumentReference.class);

            assertThat(reference.getType().getCodingFirstRep().getCode()).isEqualTo("18842-5");
            assertThat(reference.getStatus())
                    .isEqualTo(org.hl7.fhir.r4.model.Enumerations
                            .DocumentReferenceStatus.CURRENT);
        }

        @Test
        void le_document_est_rattache_au_sejour_et_a_son_auteur() {
            DocumentReference reference = firstOf(
                    mapper.toTransaction(ParhafFixtures.hospitalisation()),
                    DocumentReference.class);

            assertThat(reference.getContextFirstRep().getEncounterFirstRep().getReference())
                    .isEqualTo("Encounter/parhaf-enc-PNEUMOLOGIE-00001");
            assertThat(reference.getAuthorFirstRep().getReference())
                    .isEqualTo("Practitioner/parhaf-prac-ABC");
        }

        @Test
        void la_specialite_alimente_categorie_et_cadre_d_exercice() {
            DocumentReference reference = firstOf(
                    mapper.toTransaction(ParhafFixtures.hospitalisation()),
                    DocumentReference.class);

            assertThat(reference.getCategoryFirstRep().getText()).isEqualTo("PNEUMOLOGIE");
            assertThat(reference.getContext().getPracticeSetting().getText())
                    .isEqualTo("PNEUMOLOGIE");
        }
    }

    // ------------------------------------------------------------------ util

    private static List<String> typesOf(Bundle bundle) {
        return bundle.getEntry().stream()
                .map(entry -> entry.getResource().fhirType())
                .toList();
    }

    private static List<String> idsOf(Bundle bundle) {
        return bundle.getEntry().stream()
                .map(entry -> entry.getResource().getIdElement().getValue())
                .toList();
    }

    private static <T extends Resource> T firstOf(Bundle bundle, Class<T> type) {
        Optional<T> found = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst();
        return found.orElseThrow(() ->
                new AssertionError("Aucune ressource " + type.getSimpleName()
                        + " dans le bundle : " + typesOf(bundle)));
    }
}
