package fr.ftoumhub.parhaf.domain.mapping;

import fr.ftoumhub.parhaf.domain.model.ClinicalDocument;
import fr.ftoumhub.parhaf.domain.model.CodedConcept;
import fr.ftoumhub.parhaf.domain.model.PatientRecord;
import fr.ftoumhub.parhaf.domain.model.Scenario;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Duration;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Traduit le volet clinique : diagnostic principal, acte principal, et le
 * sejour qui les porte.
 *
 * <p>Le sejour est un {@code Encounter}. Quand le type de compte rendu implique
 * une hospitalisation, il recoit un bloc {@code hospitalization} porteur des
 * modes d'entree et de sortie — l'equivalent FHIR des donnees de mouvement du
 * PMSI.</p>
 */
public class ClinicalMapper {

    public Optional<Condition> toCondition(PatientRecord record, Reference patient,
                                           LocalDate onset) {
        Optional<CodedConcept> source = record.scenarioOpt().flatMap(Scenario::diagnosis);
        Optional<String> code = source.flatMap(CodedConcept::firstCode);
        if (code.isEmpty()) {
            return Optional.empty();
        }
        String display = source.flatMap(CodedConcept::firstDescription).orElse(null);

        Condition condition = new Condition();
        condition.setId(ResourceIds.condition(record.id()));
        condition.getClinicalStatus().addCoding()
                .setSystem(Terminologies.CONDITION_CLINICAL)
                .setCode("active");
        condition.getVerificationStatus().addCoding()
                .setSystem(Terminologies.CONDITION_VERIFICATION)
                .setCode("confirmed");
        condition.getCode().addCoding()
                .setSystem(Terminologies.ICD10)
                .setCode(code.get())
                .setDisplay(display);
        if (display != null) {
            condition.getCode().setText(display);
        }
        condition.setSubject(patient);
        condition.setOnset(new DateTimeType(SyntheticDates.toDate(onset)));
        return Optional.of(condition);
    }

    public Optional<Procedure> toProcedure(PatientRecord record, Reference patient,
                                           LocalDate performed) {
        Optional<CodedConcept> source = record.scenarioOpt().flatMap(Scenario::procedure);
        Optional<String> code = source.flatMap(CodedConcept::firstCode);
        if (code.isEmpty()) {
            return Optional.empty();
        }
        String display = source.flatMap(CodedConcept::firstDescription).orElse(null);

        Procedure procedure = new Procedure();
        procedure.setId(ResourceIds.procedure(record.id()));
        procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
        procedure.getCode().addCoding()
                .setSystem(Terminologies.CCAM)
                .setCode(code.get())
                .setDisplay(display);
        if (display != null) {
            procedure.getCode().setText(display);
        }
        procedure.setSubject(patient);
        procedure.setPerformed(new DateTimeType(SyntheticDates.toDate(performed)));
        return Optional.of(procedure);
    }

    public Optional<Encounter> toEncounter(PatientRecord record, Reference patient,
                                           LocalDate admission,
                                           Optional<Condition> primaryDiagnosis) {
        List<ClinicalDocument> documents = record.documentList();
        if (documents.isEmpty()) {
            return Optional.empty();
        }

        String drivingType = drivingDocumentType(documents);
        int lengthOfStay = record.lengthOfStayInDays();

        Encounter encounter = new Encounter();
        encounter.setId(ResourceIds.encounter(record.id()));
        encounter.addIdentifier()
                .setSystem(LocalIdentifierSystems.ENCOUNTER)
                .setValue(record.id());
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);
        encounter.setClass_(DocumentTypeCatalog.encounterClass(drivingType));
        encounter.setSubject(patient);

        if (record.specialty() != null) {
            encounter.getServiceType().setText(record.specialty());
        }

        encounter.setPeriod(new Period()
                .setStart(SyntheticDates.toDate(admission))
                .setEnd(SyntheticDates.toDate(admission.plusDays(Math.max(lengthOfStay, 0)))));

        if (lengthOfStay > 0) {
            encounter.setLength(toDuration(lengthOfStay));
        }

        primaryDiagnosis.ifPresent(condition -> encounter.addDiagnosis()
                .setCondition(new Reference(condition.getIdElement().getValue()))
                .setUse(new CodeableConcept().addCoding(new Coding()
                        .setSystem(Terminologies.DIAGNOSIS_ROLE)
                        .setCode("billing")
                        .setDisplay("Billing")))
                .setRank(1));

        if (DocumentTypeCatalog.impliesInpatientStay(drivingType)) {
            applyHospitalization(encounter, record);
        }

        return Optional.of(encounter);
    }

    /**
     * Un patient peut avoir plusieurs comptes rendus de types differents. Le
     * type le plus "lourd" gagne : une hospitalisation prime sur une
     * consultation pour determiner la nature du sejour.
     */
    private String drivingDocumentType(List<ClinicalDocument> documents) {
        return documents.stream()
                .map(ClinicalDocument::type)
                .filter(DocumentTypeCatalog::impliesInpatientStay)
                .findFirst()
                .orElseGet(() -> documents.get(0).type());
    }

    /**
     * Modes d'entree et de sortie : le corpus les donne en francais libre
     * ("entree par les urgences", "retour a domicile"). On les pose en texte,
     * sans codage.
     *
     * <p>TODO-TRE : c'est le point de branchement des nomenclatures ANS de mode
     * d'entree et de sortie, une fois les CodeSystem du SMT charges.</p>
     */
    private void applyHospitalization(Encounter encounter, PatientRecord record) {
        record.scenarioOpt().ifPresent(scenario -> {
            Encounter.EncounterHospitalizationComponent hospitalization =
                    encounter.getHospitalization();
            if (scenario.admissionMode() != null) {
                hospitalization.setAdmitSource(
                        new CodeableConcept().setText(scenario.admissionMode()));
            }
            if (scenario.dischargeMode() != null) {
                hospitalization.setDischargeDisposition(
                        new CodeableConcept().setText(scenario.dischargeMode()));
            }
        });
    }

    /**
     * Duration herite de Quantity, dont les setters renvoient Quantity et non
     * Duration. D'ou la construction en plusieurs instructions plutot qu'en
     * chainage fluide.
     */
    private Duration toDuration(int days) {
        Duration duration = new Duration();
        duration.setValue(days);
        duration.setUnit("jours");
        duration.setSystem(Terminologies.UCUM);
        duration.setCode("d");
        return duration;
    }
}
