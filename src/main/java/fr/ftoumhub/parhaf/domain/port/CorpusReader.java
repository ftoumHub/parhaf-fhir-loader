package fr.ftoumhub.parhaf.domain.port;

import fr.ftoumhub.parhaf.domain.model.PatientRecord;

import java.util.stream.Stream;

/**
 * Port d'entree : fournit les enregistrements du corpus, texte des comptes
 * rendus deja resolu.
 *
 * <p>Le domaine ignore d'ou viennent les donnees : JSON standalone, export
 * Hugging Face, ou une fixture de test.</p>
 */
public interface CorpusReader {

    Stream<PatientRecord> read();

    /** Nombre d'enregistrements retenus apres filtrage, pour la journalisation. */
    long size();
}
