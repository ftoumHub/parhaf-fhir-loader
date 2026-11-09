package fr.ftoumhub.parhaf.domain.port;

/** Publication reussie d'une transaction. */
public record PublishSuccess(String recordId, int entryCount) {
}
