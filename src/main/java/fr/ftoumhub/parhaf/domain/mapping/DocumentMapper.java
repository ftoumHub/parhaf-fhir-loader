package fr.ftoumhub.parhaf.domain.mapping;

import fr.ftoumhub.parhaf.domain.model.ClinicalDocument;
import fr.ftoumhub.parhaf.domain.model.PatientRecord;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Traduit un compte rendu en couple {@code Binary} + {@code DocumentReference}.
 *
 * <p>C'est la modelisation FHIR standard du document : le Binary porte le
 * contenu brut, le DocumentReference porte les metadonnees et pointe dessus.
 * On passe par une reference plutot que par un attachement inline parce que
 * les CR du corpus font en moyenne 640 mots.</p>
 */
public class DocumentMapper {

    private static final String CONTENT_TYPE = "text/plain; charset=utf-8";

    private final String custodianName;

    public DocumentMapper(String custodianName) {
        this.custodianName = custodianName;
    }

    public Binary toBinary(PatientRecord record, ClinicalDocument document, int index) {
        Binary binary = new Binary();
        binary.setId(ResourceIds.binary(record.id(), index));
        binary.setContentType(CONTENT_TYPE);
        binary.setData(document.text().getBytes(StandardCharsets.UTF_8));
        return binary;
    }

    public DocumentReference toDocumentReference(PatientRecord record,
                                                 ClinicalDocument document,
                                                 int index,
                                                 Reference patient,
                                                 Reference encounter,
                                                 Binary binary,
                                                 LocalDate date) {
        DocumentReference reference = new DocumentReference();
        reference.setId(ResourceIds.documentReference(record.id(), index));
        reference.addIdentifier()
                .setSystem(LocalIdentifierSystems.DOCUMENT)
                .setValue(record.id() + "#" + index);

        reference.setStatus(Enumerations.DocumentReferenceStatus.CURRENT);
        reference.setDocStatus(DocumentReference.ReferredDocumentStatus.FINAL);
        reference.setDate(SyntheticDates.toDate(date));
        reference.setSubject(patient);
        reference.setDescription(document.header());

        DocumentTypeCatalog.loincType(document.type())
                .ifPresent(coding -> reference.getType().addCoding(coding));
        // Le libelle source est conserve systematiquement, y compris quand le
        // code LOINC est approximatif (cas obstetrique).
        reference.getType().setText(document.label());

        if (record.specialty() != null) {
            reference.addCategory(new CodeableConcept().setText(record.specialty()));
            reference.getContext().setPracticeSetting(
                    new CodeableConcept().setText(record.specialty()));
        }

        if (record.author() != null && !record.author().isBlank()) {
            reference.addAuthor(new Reference(
                    "Practitioner/" + ResourceIds.practitioner(record.author())));
        }
        reference.setCustodian(new Reference().setDisplay(custodianName));

        if (encounter != null) {
            reference.getContext().addEncounter(encounter);
            reference.getContext().setPeriod(
                    new Period().setStart(SyntheticDates.toDate(date)));
        }

        reference.addContent().setAttachment(toAttachment(document, binary, date));
        return reference;
    }

    private Attachment toAttachment(ClinicalDocument document, Binary binary, LocalDate date) {
        Attachment attachment = new Attachment()
                .setContentType(CONTENT_TYPE)
                .setLanguage("fr-FR")
                .setUrl(binary.getIdElement().getValue())
                .setTitle(document.label())
                .setCreation(SyntheticDates.toDate(date));
        attachment.setSize(document.text().getBytes(StandardCharsets.UTF_8).length);
        return attachment;
    }
}
