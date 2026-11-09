package fr.ftoumhub.parhaf.domain.mapping;

/**
 * Fabrique les identifiants techniques des ressources FHIR produites.
 *
 * <p>Ces identifiants sont <b>deterministes</b> : le meme enregistrement PARHAF
 * donne toujours les memes ids. Combine a une publication en PUT, cela rend le
 * chargement idempotent — rejouable sans doublon ni Binary orphelin — et permet
 * de resoudre les references sans passer par des urn:uuid.</p>
 *
 * <p>Contrainte FHIR : un id respecte {@code [A-Za-z0-9\-\.]{1,64}}. D'ou le
 * nettoyage et la troncature.</p>
 */
public final class ResourceIds {

    private ResourceIds() {
    }

    private static final int MAX_SLUG_LENGTH = 44;

    public static String patient(String recordId) {
        return "parhaf-pat-" + slugify(recordId);
    }

    public static String practitioner(String trigram) {
        return "parhaf-prac-" + slugify(trigram);
    }

    public static String condition(String recordId) {
        return "parhaf-cond-" + slugify(recordId);
    }

    public static String procedure(String recordId) {
        return "parhaf-proc-" + slugify(recordId);
    }

    public static String encounter(String recordId) {
        return "parhaf-enc-" + slugify(recordId);
    }

    public static String binary(String recordId, int documentIndex) {
        return "parhaf-bin-" + slugify(recordId) + "-" + documentIndex;
    }

    public static String documentReference(String recordId, int documentIndex) {
        return "parhaf-doc-" + slugify(recordId) + "-" + documentIndex;
    }

    /** Ramene une chaine dans le jeu de caracteres autorise pour un id FHIR. */
    public static String slugify(String raw) {
        String cleaned = (raw == null ? "x" : raw).trim()
                .replaceAll("[^A-Za-z0-9.\\-]", "-")
                .replaceAll("-{2,}", "-");
        if (cleaned.isEmpty()) {
            cleaned = "x";
        }
        return cleaned.length() > MAX_SLUG_LENGTH
                ? cleaned.substring(0, MAX_SLUG_LENGTH)
                : cleaned;
    }
}
