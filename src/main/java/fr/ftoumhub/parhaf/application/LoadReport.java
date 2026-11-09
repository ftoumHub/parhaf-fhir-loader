package fr.ftoumhub.parhaf.application;

import fr.ftoumhub.parhaf.domain.port.PublishFailure;

import java.time.Duration;
import java.util.List;

/** Bilan d'une execution de chargement. */
public record LoadReport(int processed, int succeeded, List<PublishFailure> failures,
                         Duration elapsed) {

    public int failed() {
        return failures.size();
    }

    public boolean isClean() {
        return failures.isEmpty();
    }
}
