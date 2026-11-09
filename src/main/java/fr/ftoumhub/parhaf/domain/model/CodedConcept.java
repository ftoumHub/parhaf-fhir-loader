package fr.ftoumhub.parhaf.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Couple code / libelle du corpus : diagnostic principal (CIM-10) ou acte
 * principal (CCAM).
 *
 * <p>Le dataset serialise ces deux champs tantot en chaine, tantot en tableau
 * de chaines. D'ou le type {@code Object} et les accesseurs de normalisation
 * plutot qu'un typage strict qui casserait a la deserialisation.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CodedConcept(Object code, Object description) {

    public Optional<String> firstCode() {
        return firstOf(code);
    }

    public Optional<String> firstDescription() {
        return firstOf(description);
    }

    private static Optional<String> firstOf(Object raw) {
        if (raw == null) {
            return Optional.empty();
        }
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .filter(value -> !value.isBlank())
                    .findFirst();
        }
        String value = raw.toString();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
