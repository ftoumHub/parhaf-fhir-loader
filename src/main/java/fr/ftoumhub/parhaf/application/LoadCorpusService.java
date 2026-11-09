package fr.ftoumhub.parhaf.application;

import fr.ftoumhub.parhaf.config.LoaderProperties;
import fr.ftoumhub.parhaf.domain.mapping.ParhafToFhirMapper;
import fr.ftoumhub.parhaf.domain.model.PatientRecord;
import fr.ftoumhub.parhaf.domain.port.CorpusReader;
import fr.ftoumhub.parhaf.domain.port.FhirPublisher;
import fr.ftoumhub.parhaf.domain.port.PublishFailure;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cas d'usage : charger le corpus dans le serveur FHIR.
 *
 * <p>Une transaction par patient. Un echec isole n'invalide donc pas le reste
 * du chargement, et remonte nommement dans le rapport final.</p>
 */
@Service
public class LoadCorpusService {

    private static final Logger log = LoggerFactory.getLogger(LoadCorpusService.class);
    private static final int PROGRESS_EVERY = 200;

    private final CorpusReader corpusReader;
    private final FhirPublisher publisher;
    private final ParhafToFhirMapper mapper;
    private final LoaderProperties properties;

    public LoadCorpusService(CorpusReader corpusReader, FhirPublisher publisher,
                             ParhafToFhirMapper mapper, LoaderProperties properties) {
        this.corpusReader = corpusReader;
        this.publisher = publisher;
        this.mapper = mapper;
        this.properties = properties;
    }

    public LoadReport execute() throws InterruptedException {
        Instant startedAt = Instant.now();
        List<PatientRecord> records = corpusReader.read().toList();
        log.info("{} enregistrements a charger vers {}", records.size(),
                properties.serverBase());
        log.info("Serveur FHIR {} detecte", publisher.probeFhirVersion());

        AtomicInteger succeeded = new AtomicInteger();
        List<PublishFailure> failures = new CopyOnWriteArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(properties.threads());
        try {
            List<Future<?>> tasks = records.stream()
                    .map(record -> pool.submit(
                            () -> publishOne(record, succeeded, failures)))
                    .toList();
            awaitAll(tasks);
        } finally {
            pool.shutdown();
            pool.awaitTermination(2, TimeUnit.MINUTES);
        }

        return new LoadReport(records.size(), succeeded.get(), List.copyOf(failures),
                Duration.between(startedAt, Instant.now()));
    }

    private void publishOne(PatientRecord record, AtomicInteger succeeded,
                            List<PublishFailure> failures) {
        try {
            Bundle transaction = mapper.toTransaction(record);
            publisher.publish(record.id(), transaction)
                    .peek(success -> reportProgress(succeeded.incrementAndGet()))
                    .peekLeft(failures::add);
        } catch (RuntimeException e) {
            failures.add(PublishFailure.of(record.id(), e));
        }
    }

    private void reportProgress(int done) {
        if (done % PROGRESS_EVERY == 0) {
            log.info("{} patients charges...", done);
        }
    }

    private void awaitAll(List<Future<?>> tasks) {
        for (Future<?> task : tasks) {
            try {
                task.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Tache interrompue : {}", e.getMessage());
            }
        }
    }
}
