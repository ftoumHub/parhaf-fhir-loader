package fr.ftoumhub.parhaf.domain.mapping;

/**
 * Systemes d'identifiants metier propres au corpus.
 *
 * <p>Ils permettent de retrouver une ressource par son identifiant PARHAF
 * ({@code GET /Patient?identifier=urn:parhaf:patient|PNEUMOLOGIE-00001}) et de
 * distinguer ce jeu de donnees de tout autre charge sur le meme serveur.</p>
 */
public final class LocalIdentifierSystems {

    private LocalIdentifierSystems() {
    }

    public static final String PATIENT = "urn:parhaf:patient";
    public static final String PRACTITIONER = "urn:parhaf:practitioner";
    public static final String ENCOUNTER = "urn:parhaf:encounter";
    public static final String DOCUMENT = "urn:parhaf:document";

    /** Extension locale marquant les ressources issues du corpus fictif. */
    public static final String FICTITIOUS_FLAG = "urn:parhaf:fictitious";
}
