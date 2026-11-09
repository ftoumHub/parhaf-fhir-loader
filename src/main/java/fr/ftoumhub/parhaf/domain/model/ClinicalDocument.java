package fr.ftoumhub.parhaf.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Un compte rendu du corpus.
 *
 * <p>Dans la version standalone, le texte n'est pas dans le JSON : il vit dans
 * un fichier separe pointe par {@code path}. Le reader resout ce chemin et
 * renseigne {@code text}. Dans la version Hugging Face, {@code text} est deja
 * present et {@code path} est nul.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClinicalDocument(
        String type,
        String header,
        String path,
        String text,
        @JsonProperty("word_count") Integer wordCount) {

    public ClinicalDocument withText(String resolvedText) {
        return new ClinicalDocument(type, header, path, resolvedText, wordCount);
    }

    public boolean hasText() {
        return text != null && !text.isBlank();
    }

    /** Libelle du document, avec repli sur son type quand l'entete est absente. */
    public String label() {
        return header != null && !header.isBlank() ? header : type;
    }
}
