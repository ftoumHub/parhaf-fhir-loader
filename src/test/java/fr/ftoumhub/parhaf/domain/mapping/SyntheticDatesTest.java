package fr.ftoumhub.parhaf.domain.mapping;

import fr.ftoumhub.parhaf.domain.model.Amount;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ces dates ne viennent pas du corpus : elles sont derivees. Le seul contrat
 * qui compte est donc leur stabilite entre deux executions.
 */
class SyntheticDatesTest {

    @Test
    void la_date_d_admission_est_stable_pour_un_meme_identifiant() {
        assertThat(SyntheticDates.admissionDate("PNEUMOLOGIE-00001"))
                .isEqualTo(SyntheticDates.admissionDate("PNEUMOLOGIE-00001"));
    }

    @Test
    void la_date_d_admission_tombe_dans_l_annee_de_reference() {
        LocalDate date = SyntheticDates.admissionDate("CARDIOLOGIE-00042");

        assertThat(date.getYear()).isEqualTo(SyntheticDates.REFERENCE_YEAR);
    }

    @Test
    void un_age_en_annees_donne_une_date_de_naissance_coherente() {
        LocalDate birth = SyntheticDates.birthDate(new Amount(72.0, "ans")).orElseThrow();

        assertThat(birth.getYear()).isEqualTo(SyntheticDates.REFERENCE_YEAR - 72);
    }

    @Test
    void un_age_en_mois_est_traite_a_part_pour_la_pediatrie() {
        LocalDate birth = SyntheticDates.birthDate(new Amount(6.0, "mois")).orElseThrow();

        assertThat(birth.getYear()).isEqualTo(SyntheticDates.REFERENCE_YEAR);
        assertThat(birth.getMonthValue()).isEqualTo(1);
    }

    @Test
    void un_age_absent_ne_produit_pas_de_date() {
        assertThat(SyntheticDates.birthDate(null)).isEmpty();
        assertThat(SyntheticDates.birthDate(new Amount(null, "ans"))).isEmpty();
    }
}
