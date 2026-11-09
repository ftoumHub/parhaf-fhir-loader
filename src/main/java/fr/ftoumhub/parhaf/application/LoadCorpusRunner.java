package fr.ftoumhub.parhaf.application;

import fr.ftoumhub.parhaf.config.LoaderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Point d'entree en ligne de commande. Choisit entre dry run et chargement
 * reel, puis restitue le bilan.
 */
@Component
public class LoadCorpusRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoadCorpusRunner.class);
    private static final int MAX_REPORTED_FAILURES = 10;

    private final LoadCorpusService loadCorpusService;
    private final DryRunService dryRunService;
    private final LoaderProperties properties;

    public LoadCorpusRunner(LoadCorpusService loadCorpusService, DryRunService dryRunService,
                            LoaderProperties properties) {
        this.loadCorpusService = loadCorpusService;
        this.dryRunService = dryRunService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (properties.dryRun()) {
            dryRunService.execute();
            return;
        }
        printReport(loadCorpusService.execute());
    }

    private void printReport(LoadReport report) {
        log.info("---------------------------------------------");
        log.info("Chargement termine en {} s", report.elapsed().toSeconds());
        log.info("  traites : {}", report.processed());
        log.info("  succes  : {}", report.succeeded());
        log.info("  echecs  : {}", report.failed());
        report.failures().stream()
                .limit(MAX_REPORTED_FAILURES)
                .forEach(failure -> log.warn("  !! {} : {}",
                        failure.recordId(), failure.message()));
        if (report.failed() > MAX_REPORTED_FAILURES) {
            log.warn("  ... et {} autres", report.failed() - MAX_REPORTED_FAILURES);
        }
        log.info("---------------------------------------------");
    }
}
