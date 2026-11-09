package fr.ftoumhub.parhaf.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Racine du fichier corpus.json de PARHAF : {@code { "data": [ ... ] }}.
 *
 * @see <a href="https://huggingface.co/datasets/HealthDataHub/PARHAF">Dataset PARHAF</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Corpus(List<PatientRecord> data) {

    public List<PatientRecord> records() {
        return data == null ? List.of() : data;
    }
}
