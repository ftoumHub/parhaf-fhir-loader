package fr.ftoumhub.parhaf.domain.mapping;

import org.hl7.fhir.r4.model.Coding;

/**
 * URL canoniques des systemes de codage utilises par le mapping.
 *
 * <p>Choix assume : on reste sur des systemes internationaux. Les nomenclatures
 * francaises (TRE de l'ANS) ne sont pas cablees, parce qu'elles vivent sur le
 * SMT et ne sont pas embarquees dans les packages IG. Les points de branchement
 * sont signales TODO-TRE.</p>
 */
public final class Terminologies {

    private Terminologies() {
    }

    public static final String LOINC = "http://loinc.org";
    public static final String ACT_CODE = "http://terminology.hl7.org/CodeSystem/v3-ActCode";
    public static final String DIAGNOSIS_ROLE =
            "http://terminology.hl7.org/CodeSystem/diagnosis-role";
    public static final String CONDITION_CLINICAL =
            "http://terminology.hl7.org/CodeSystem/condition-clinical";
    public static final String CONDITION_VERIFICATION =
            "http://terminology.hl7.org/CodeSystem/condition-ver-status";
    public static final String UCUM = "http://unitsofmeasure.org";

    /**
     * Les codes PARHAF sont des CIM-10 FR, qui derivent de l'ICD-10. On declare
     * le systeme international, valable pour la racine des codes.
     * TODO-TRE : basculer sur le CodeSystem CIM-10 FR du SMT pour valider Segur.
     */
    public static final String ICD10 = "http://hl7.org/fhir/sid/icd-10";

    /**
     * La CCAM n'a pas d'URL canonique internationale.
     * TODO-TRE : remplacer par le CodeSystem CCAM publie sur le SMT de l'ANS.
     */
    public static final String CCAM = "urn:oid:1.2.250.1.213.2.5";

    public static Coding loinc(String code, String display) {
        return new Coding().setSystem(LOINC).setCode(code).setDisplay(display);
    }

    public static Coding actCode(String code, String display) {
        return new Coding().setSystem(ACT_CODE).setCode(code).setDisplay(display);
    }
}
