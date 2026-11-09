package fr.ftoumhub.parhaf.infrastructure.corpus;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.ftoumhub.parhaf.domain.model.ClinicalDocument;
import fr.ftoumhub.parhaf.domain.model.Corpus;
import fr.ftoumhub.parhaf.domain.model.PatientRecord;
import fr.ftoumhub.parhaf.domain.port.CorpusReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lit la version standalone du corpus PARHAF : un corpus.json qui sert d'index
 * et de couche de metadonnees, plus des fichiers texte bruts ranges par
 * specialite, relies par le champ documents[].path.
 *
 * <p>Le texte des comptes rendus n'est pas dans le JSON. Ce reader resout donc
 * chaque chemin relatif par rapport au repertoire du corpus.json.</p>
 */
public class JsonCorpusReader implements CorpusReader {

    private static final Logger log = LoggerFactory.getLogger(JsonCorpusReader.class);

    private final Path corpusFile;
    private final Set<String> specialtyFilter;
    private final int limit;
    private final ObjectMapper objectMapper;

    private volatile List<PatientRecord> cached;

    public JsonCorpusReader(Path corpusFile, Set<String> specialtyFilter, int limit,
                            ObjectMapper objectMapper) {
        this.corpusFile = corpusFile;
        this.specialtyFilter = specialtyFilter == null ? Set.of() : specialtyFilter;
        this.limit = Math.max(limit, 0);
        this.objectMapper = objectMapper;
    }

    @Override
    public Stream<PatientRecord> read() {
        return records().stream();
    }

    @Override
    public long size() {
        return records().size();
    }

    private synchronized List<PatientRecord> records() {
        if (cached != null) {
            return cached;
        }
        if (!Files.isRegularFile(corpusFile)) {
            throw new IllegalStateException(
                    "Corpus introuvable : " + corpusFile.toAbsolutePath()
                    + System.lineSeparator()
                    + "Lance d'abord : python scripts/export-parhaf.py --out data/parhaf");
        }
        cached = List.copyOf(parse());
        log.info("Corpus charge : {} patients retenus depuis {}", cached.size(), corpusFile);
        return cached;
    }

    private List<PatientRecord> parse() {
        try {
            Corpus corpus = objectMapper.readValue(corpusFile.toFile(), Corpus.class);
            Path root = corpusFile.toAbsolutePath().getParent();

            List<PatientRecord> selected = new ArrayList<>();
            for (PatientRecord record : corpus.records()) {
                if (!matchesFilter(record)) {
                    continue;
                }
                selected.add(resolveTexts(record, root));
                if (limit > 0 && selected.size() >= limit) {
                    break;
                }
            }
            return selected;
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du corpus impossible", e);
        }
    }

    private boolean matchesFilter(PatientRecord record) {
        return specialtyFilter.isEmpty()
                || (record.specialty() != null && specialtyFilter.contains(record.specialty()));
    }

    /**
     * Remplit le champ text de chaque document. Si l'export a deja embarque le
     * texte (variante Hugging Face), rien n'est relu sur disque.
     */
    private PatientRecord resolveTexts(PatientRecord record, Path root) {
        List<ClinicalDocument> resolved = new ArrayList<>();
        for (ClinicalDocument document : record.documentList()) {
            if (document.hasText() || document.path() == null) {
                resolved.add(document);
                continue;
            }
            Path file = root.resolve(document.path()).normalize();
            try {
                resolved.add(document.withText(Files.readString(file, StandardCharsets.UTF_8)));
            } catch (IOException e) {
                log.warn("Compte rendu illisible, ignore : {} ({})", file, e.getMessage());
                resolved.add(document);
            }
        }
        return record.withDocuments(resolved);
    }
}
