package fr.ftoumhub.parhaf.domain.port;

/** Publication en echec, avec l'identifiant du patient concerne. */
public record PublishFailure(String recordId, String message, Throwable cause) {

    public static PublishFailure of(String recordId, Throwable cause) {
        String message = cause == null ? "cause inconnue" : cause.getMessage();
        return new PublishFailure(recordId, message, cause);
    }
}
