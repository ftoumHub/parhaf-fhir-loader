package fr.ftoumhub.parhaf.domain.mapping;

import fr.ftoumhub.parhaf.domain.model.Amount;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

/**
 * Dates derivees, absentes du corpus source.
 *
 * <p><b>Attention</b> : rien ici ne vient de PARHAF. Les comptes rendus portent
 * des dates dans leur texte, mais le corpus n'expose aucune date structuree, et
 * il donne un age plutot qu'une date de naissance. Ces derivations existent
 * uniquement pour que les recherches FHIR par date et par age restent
 * testables. Elles sont deterministes, donc stables d'une execution a
 * l'autre.</p>
 */
public final class SyntheticDates {

    private SyntheticDates() {
    }

    /** Annee de reference du corpus : les scenarios se situent en 2025. */
    public static final int REFERENCE_YEAR = 2025;

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final LocalDate BIRTH_ANCHOR = LocalDate.of(REFERENCE_YEAR, 7, 1);

    /**
     * Date d'admission stable, derivee de l'identifiant du patient et repartie
     * sur l'annee de reference.
     */
    public static LocalDate admissionDate(String recordId) {
        int hash = Math.abs((recordId == null ? "" : recordId).hashCode());
        return LocalDate.ofYearDay(REFERENCE_YEAR, 1 + (hash % 364));
    }

    /**
     * Date de naissance approximative reconstruite depuis l'age, calee au
     * 1er juillet de l'annee de reference.
     */
    public static Optional<LocalDate> birthDate(Amount age) {
        if (age == null) {
            return Optional.empty();
        }
        return age.asInt().map(value -> age.isExpressedIn("mois")
                ? BIRTH_ANCHOR.minusMonths(value)
                : BIRTH_ANCHOR.minusYears(value));
    }

    public static Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZONE).toInstant());
    }
}
