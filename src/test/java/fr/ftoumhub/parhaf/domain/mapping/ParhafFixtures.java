package fr.ftoumhub.parhaf.domain.mapping;

import fr.ftoumhub.parhaf.domain.model.Amount;
import fr.ftoumhub.parhaf.domain.model.ClinicalDocument;
import fr.ftoumhub.parhaf.domain.model.CodedConcept;
import fr.ftoumhub.parhaf.domain.model.PatientRecord;
import fr.ftoumhub.parhaf.domain.model.Scenario;
import fr.ftoumhub.parhaf.domain.model.StructuredAbstract;

import java.util.List;

/**
 * Fixtures calquees sur la structure reelle du corpus, y compris ses
 * irregularites : codes serialises en tableau, resume structure parfois nul.
 */
final class ParhafFixtures {

    private ParhafFixtures() {
    }

    /** Sejour hospitalier complet : CRH, diagnostic, duree de sejour. */
    static PatientRecord hospitalisation() {
        Scenario scenario = new Scenario(
                "Claire Martin",
                new Amount(72.0, "ans"),
                "F",
                "entree par les urgences",
                "retour a domicile",
                null,
                new CodedConcept(List.of("C341"),
                        List.of("Tumeur maligne du lobe superieur, bronche ou poumon")),
                "hospitalisation complete");

        ClinicalDocument document = new ClinicalDocument(
                "CRH",
                "Compte rendu d'hospitalisation du service de Pneumologie",
                null,
                "Madame Claire Martin, 72 ans, est hospitalisee le 14/03/25...",
                42);

        StructuredAbstract summary = new StructuredAbstract(
                null, null, null, null, new Amount(5.0, "jours"));

        return new PatientRecord("PNEUMOLOGIE-00001", "00001", "PNEUMOLOGIE",
                "ABC", "XYZ", "General", scenario, List.of(document), summary);
    }

    /** Anatomopathologie : ambulatoire, pas de resume structure. */
    static PatientRecord anatomopathologie() {
        Scenario scenario = new Scenario(
                "Marc Le van gong",
                new Amount(55.0, "ans"),
                "M",
                "domicile",
                "retour a domicile",
                new CodedConcept("HHFA016", "Lobectomie pulmonaire"),
                new CodedConcept(List.of("C509"), List.of("Tumeur maligne du sein")),
                null);

        ClinicalDocument document = new ClinicalDocument(
                "ANAPATH", "COMPTE-RENDU ANATOMO PATHOLOGIQUE", null,
                "COMPTE-RENDU ANATOMO PATHOLOGIQUE\n\nBiopsie...", 30);

        return new PatientRecord("ANATOMOPATHOLOGIE-00042", "00042", "ANATOMOPATHOLOGIE",
                "DEF", "GHI", "General", scenario, List.of(document), null);
    }

    /** Enregistrement degrade : ni scenario, ni document exploitable. */
    static PatientRecord vide() {
        return new PatientRecord("NEUROLOGIE-00099", "00099", "NEUROLOGIE",
                null, null, "General", null, List.of(), null);
    }
}
