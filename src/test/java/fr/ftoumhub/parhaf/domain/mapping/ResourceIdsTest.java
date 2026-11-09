package fr.ftoumhub.parhaf.domain.mapping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un id FHIR doit respecter [A-Za-z0-9\-\.]{1,64}. Les specialites du corpus
 * contiennent des espaces ("CANCERO ADULTE") et des points ("CHIR.CARDIO-VASC."),
 * d'ou ces tests.
 */
class ResourceIdsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "ANATOMOPATHOLOGIE-00001",
            "CANCERO ADULTE-00123",
            "CHIR.CARDIO-VASC.-00007",
            "CHIR ORTHO ET TRAUMATO-00456",
            "MEDECINE INTER-SPECIALITES-00999"
    })
    void tout_identifiant_produit_est_un_id_fhir_valide(String recordId) {
        assertThat(ResourceIds.patient(recordId)).matches("[A-Za-z0-9\\-.]{1,64}");
        assertThat(ResourceIds.encounter(recordId)).matches("[A-Za-z0-9\\-.]{1,64}");
        assertThat(ResourceIds.documentReference(recordId, 2))
                .matches("[A-Za-z0-9\\-.]{1,64}");
    }

    @Test
    void les_espaces_deviennent_des_tirets_sans_doublon() {
        assertThat(ResourceIds.slugify("CANCERO  ADULTE-00123"))
                .isEqualTo("CANCERO-ADULTE-00123");
    }

    @Test
    void un_identifiant_tres_long_est_tronque_sous_la_limite_fhir() {
        String id = ResourceIds.documentReference("X".repeat(200), 0);

        assertThat(id.length()).isLessThanOrEqualTo(64);
    }

    @Test
    void chaque_document_d_un_meme_patient_a_son_propre_identifiant() {
        assertThat(ResourceIds.binary("PNEUMOLOGIE-00001", 0))
                .isNotEqualTo(ResourceIds.binary("PNEUMOLOGIE-00001", 1));
    }
}
