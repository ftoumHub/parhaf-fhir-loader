package fr.ftoumhub.parhaf.domain.mapping;

import org.hl7.fhir.r4.model.Coding;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Correspondances entre les types de documents PARHAF et les codes FHIR.
 *
 * <p>Le vocabulaire source est ferme : ACCOUCHEMENT, ANAPATH, CRC, CRH, CRO,
 * MATERNITE, URGENCES.</p>
 */
public final class DocumentTypeCatalog {

    private DocumentTypeCatalog() {
    }

    /**
     * Type PARHAF vers code LOINC de type de document.
     *
     * <p>Les cinq premiers sont des codes LOINC de type de document bien
     * etablis. ACCOUCHEMENT et MATERNITE sont ramenes sur le CR
     * d'hospitalisation generique : je n'ai pas de code LOINC obstetrique dont
     * je sois sur. TODO-VERIF avant tout usage de conformite.</p>
     */
    private static final Map<String, Coding> LOINC_TYPES = Map.of(
            "CRH", Terminologies.loinc("18842-5", "Discharge summary"),
            "CRC", Terminologies.loinc("11488-4", "Consultation note"),
            "CRO", Terminologies.loinc("11504-8", "Surgical operation note"),
            "ANAPATH", Terminologies.loinc("11526-1", "Pathology study"),
            "URGENCES", Terminologies.loinc("34111-5", "Emergency department note"),
            "ACCOUCHEMENT", Terminologies.loinc("18842-5", "Discharge summary"),
            "MATERNITE", Terminologies.loinc("18842-5", "Discharge summary"));

    /** Type PARHAF vers classe de rencontre v3-ActCode. */
    private static final Map<String, Coding> ENCOUNTER_CLASSES = Map.of(
            "CRH", Terminologies.actCode("IMP", "inpatient encounter"),
            "CRO", Terminologies.actCode("IMP", "inpatient encounter"),
            "ACCOUCHEMENT", Terminologies.actCode("IMP", "inpatient encounter"),
            "MATERNITE", Terminologies.actCode("IMP", "inpatient encounter"),
            "URGENCES", Terminologies.actCode("EMER", "emergency"),
            "CRC", Terminologies.actCode("AMB", "ambulatory"),
            "ANAPATH", Terminologies.actCode("AMB", "ambulatory"));

    /**
     * Types impliquant un sejour hospitalier, donc un Encounter porteur d'un
     * bloc hospitalization (modes d'entree et de sortie).
     */
    private static final Set<String> INPATIENT_TYPES =
            Set.of("CRH", "CRO", "ACCOUCHEMENT", "MATERNITE");

    private static final Coding DEFAULT_CLASS =
            Terminologies.actCode("AMB", "ambulatory");

    public static Optional<Coding> loincType(String parhafType) {
        return Optional.ofNullable(LOINC_TYPES.get(normalize(parhafType)));
    }

    public static Coding encounterClass(String parhafType) {
        return ENCOUNTER_CLASSES.getOrDefault(normalize(parhafType), DEFAULT_CLASS);
    }

    public static boolean impliesInpatientStay(String parhafType) {
        return INPATIENT_TYPES.contains(normalize(parhafType));
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}
