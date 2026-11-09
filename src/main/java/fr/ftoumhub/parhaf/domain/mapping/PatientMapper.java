package fr.ftoumhub.parhaf.domain.mapping;

import fr.ftoumhub.parhaf.domain.model.PatientRecord;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Traduit l'identite du patient fictif et son redacteur.
 */
public class PatientMapper {

    public Patient toPatient(PatientRecord record) {
        Patient patient = new Patient();
        patient.setId(ResourceIds.patient(record.id()));
        patient.addIdentifier()
                .setSystem(LocalIdentifierSystems.PATIENT)
                .setValue(record.id());
        patient.setActive(true);

        record.scenarioOpt().ifPresent(scenario -> {
            applyName(patient, scenario.name());
            patient.setGender(toGender(scenario.sex()));
            scenario.ageOpt()
                    .flatMap(SyntheticDates::birthDate)
                    .map(SyntheticDates::toDate)
                    .ifPresent(patient::setBirthDate);
        });

        // Marqueur explicite : ces patients sont fictifs. Extension locale
        // assumee, pour pouvoir isoler le corpus d'un autre jeu de donnees.
        patient.addExtension()
                .setUrl(LocalIdentifierSystems.FICTITIOUS_FLAG)
                .setValue(new BooleanType(true));

        return patient;
    }

    /**
     * Les auteurs sont identifies par un trigramme anonyme. On en fait des
     * Practitioner mutualises : le meme trigramme donne le meme id, donc les
     * 104 auteurs du corpus ne produisent que 104 ressources.
     */
    public Optional<Practitioner> toPractitioner(String trigram) {
        if (trigram == null || trigram.isBlank()) {
            return Optional.empty();
        }
        Practitioner practitioner = new Practitioner();
        practitioner.setId(ResourceIds.practitioner(trigram));
        practitioner.addIdentifier()
                .setSystem(LocalIdentifierSystems.PRACTITIONER)
                .setValue(trigram);
        practitioner.addName()
                .setUse(HumanName.NameUse.ANONYMOUS)
                .setFamily(trigram);
        practitioner.setActive(true);
        return Optional.of(practitioner);
    }

    /**
     * Le corpus donne un nom complet en une seule chaine. On prend le premier
     * token comme prenom et tout le reste comme nom de famille, ce qui preserve
     * les patronymes composes du corpus ("Le van gong", "Ten voorde").
     */
    private void applyName(Patient patient, String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return;
        }
        String[] parts = fullName.trim().split("\\s+");
        HumanName name = patient.addName().setUse(HumanName.NameUse.OFFICIAL);
        name.setText(fullName.trim());
        if (parts.length == 1) {
            name.setFamily(parts[0]);
            return;
        }
        name.addGiven(parts[0]);
        name.setFamily(String.join(" ", List.of(parts).subList(1, parts.length)));
    }

    private Enumerations.AdministrativeGender toGender(String sex) {
        if (sex == null) {
            return Enumerations.AdministrativeGender.UNKNOWN;
        }
        return switch (sex.trim().toUpperCase(Locale.ROOT)) {
            case "F" -> Enumerations.AdministrativeGender.FEMALE;
            case "M" -> Enumerations.AdministrativeGender.MALE;
            default -> Enumerations.AdministrativeGender.UNKNOWN;
        };
    }
}
