package fr.ftoumhub.parhaf.application;

import ca.uhn.fhir.context.FhirContext;
import fr.ftoumhub.parhaf.domain.mapping.ParhafToFhirMapper;
import fr.ftoumhub.parhaf.domain.model.PatientRecord;
import fr.ftoumhub.parhaf.domain.port.CorpusReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Affiche la transaction generee pour le premier enregistrement, sans rien
 * envoyer. C'est le moyen le plus rapide de relire le mapping avant de charger
 * quoi que ce soit.
 */
@Service
public class DryRunService {

    private static final Logger log = LoggerFactory.getLogger(DryRunService.class);

    private final CorpusReader corpusReader;
    private final ParhafToFhirMapper mapper;
    private final FhirContext fhirContext;

    public DryRunService(CorpusReader corpusReader, ParhafToFhirMapper mapper,
                         FhirContext fhirContext) {
        this.corpusReader = corpusReader;
        this.mapper = mapper;
        this.fhirContext = fhirContext;
    }

    public void execute() {
        Optional<PatientRecord> sample = corpusReader.read().findFirst();
        if (sample.isEmpty()) {
            log.warn("Corpus vide : rien a montrer");
            return;
        }
        PatientRecord record = sample.get();
        String json = fhirContext.newJsonParser()
                .setPrettyPrint(true)
                .encodeResourceToString(mapper.toTransaction(record));
        log.info("Dry run — transaction generee pour {} ({}) :{}{}",
                record.id(), record.specialty(), System.lineSeparator(), json);
    }
}
