package fr.ftoumhub.parhaf.domain.mapping;

import fr.ftoumhub.parhaf.domain.model.ClinicalDocument;
import fr.ftoumhub.parhaf.domain.model.PatientRecord;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Point d'entree du mapping : un enregistrement PARHAF, une transaction FHIR.
 *
 * <p>Cette classe n'assemble que le graphe ; le detail de chaque ressource est
 * delegue aux mappers specialises. Elle ne depend d'aucune infrastructure, ce
 * qui la rend testable sans serveur ni reseau.</p>
 *
 * <pre>
 *   Patient
 *     +-- Practitioner        (auteur, mutualise entre patients)
 *     +-- Condition           (diagnostic principal, CIM-10)
 *     +-- Procedure           (acte principal, CCAM)     [si present]
 *     +-- Encounter           (le sejour)
 *     |     +-- diagnosis --> Condition
 *     +-- par compte rendu :
 *           Binary            (texte brut UTF-8)
 *           DocumentReference (metadonnees) --> Binary, Encounter, Practitioner
 * </pre>
 */
public class ParhafToFhirMapper {

    private final PatientMapper patientMapper;
    private final ClinicalMapper clinicalMapper;
    private final DocumentMapper documentMapper;

    public ParhafToFhirMapper(PatientMapper patientMapper,
                              ClinicalMapper clinicalMapper,
                              DocumentMapper documentMapper) {
        this.patientMapper = patientMapper;
        this.clinicalMapper = clinicalMapper;
        this.documentMapper = documentMapper;
    }

    public Bundle toTransaction(PatientRecord record) {
        TransactionBundleBuilder builder = new TransactionBundleBuilder();

        Patient patient = patientMapper.toPatient(record);
        builder.put(patient);
        builder.putIfPresent(patientMapper.toPractitioner(record.author()));

        Reference patientRef = referenceTo(patient.getIdElement().getValue());
        LocalDate admission = SyntheticDates.admissionDate(record.id());

        Optional<Condition> condition =
                clinicalMapper.toCondition(record, patientRef, admission);
        builder.putIfPresent(condition);
        builder.putIfPresent(clinicalMapper.toProcedure(record, patientRef, admission));

        Optional<Encounter> encounter =
                clinicalMapper.toEncounter(record, patientRef, admission, condition);
        builder.putIfPresent(encounter);

        Reference encounterRef = encounter
                .map(value -> referenceTo(value.getIdElement().getValue()))
                .orElse(null);

        appendDocuments(builder, record, patientRef, encounterRef, admission);

        return builder.build();
    }

    private void appendDocuments(TransactionBundleBuilder builder, PatientRecord record,
                                 Reference patient, Reference encounter, LocalDate date) {
        List<ClinicalDocument> documents = record.documentList();
        for (int index = 0; index < documents.size(); index++) {
            ClinicalDocument document = documents.get(index);
            if (!document.hasText()) {
                continue;
            }
            Binary binary = documentMapper.toBinary(record, document, index);
            builder.put(binary);
            builder.put(documentMapper.toDocumentReference(
                    record, document, index, patient, encounter, binary, date));
        }
    }

    private Reference referenceTo(String resourceId) {
        return new Reference(resourceId);
    }
}
