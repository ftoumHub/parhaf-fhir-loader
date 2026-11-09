package fr.ftoumhub.parhaf.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.ftoumhub.parhaf.domain.mapping.ClinicalMapper;
import fr.ftoumhub.parhaf.domain.mapping.DocumentMapper;
import fr.ftoumhub.parhaf.domain.mapping.ParhafToFhirMapper;
import fr.ftoumhub.parhaf.domain.mapping.PatientMapper;
import fr.ftoumhub.parhaf.domain.port.CorpusReader;
import fr.ftoumhub.parhaf.domain.port.FhirPublisher;
import fr.ftoumhub.parhaf.infrastructure.corpus.JsonCorpusReader;
import fr.ftoumhub.parhaf.infrastructure.fhir.HapiFhirPublisher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Cablage : c'est ici, et nulle part ailleurs, que le domaine rencontre son
 * infrastructure.
 */
@Configuration
@EnableConfigurationProperties(LoaderProperties.class)
public class LoaderConfiguration {

    @Bean
    FhirContext fhirContext(LoaderProperties properties) {
        FhirContext context = FhirContext.forR4();
        context.getRestfulClientFactory().setSocketTimeout(properties.socketTimeoutMillis());
        context.getRestfulClientFactory().setConnectTimeout(10_000);
        // Le serveur peut ne pas etre pret quand le loader demarre ; on evite
        // que le client refuse de parler avant meme la premiere requete.
        context.getRestfulClientFactory()
                .setServerValidationMode(ServerValidationModeEnum.NEVER);
        return context;
    }

    @Bean
    IGenericClient fhirClient(FhirContext fhirContext, LoaderProperties properties) {
        return fhirContext.newRestfulGenericClient(properties.serverBase());
    }

    @Bean
    FhirPublisher fhirPublisher(IGenericClient fhirClient, LoaderProperties properties) {
        return new HapiFhirPublisher(fhirClient,
                properties.maxAttempts(), properties.backoffMillis());
    }

    @Bean
    CorpusReader corpusReader(LoaderProperties properties, ObjectMapper objectMapper) {
        return new JsonCorpusReader(Path.of(properties.corpusPath()),
                properties.specialties(), properties.limit(), objectMapper);
    }

    @Bean
    PatientMapper patientMapper() {
        return new PatientMapper();
    }

    @Bean
    ClinicalMapper clinicalMapper() {
        return new ClinicalMapper();
    }

    @Bean
    DocumentMapper documentMapper(LoaderProperties properties) {
        return new DocumentMapper(properties.custodianName());
    }

    @Bean
    ParhafToFhirMapper parhafToFhirMapper(PatientMapper patientMapper,
                                          ClinicalMapper clinicalMapper,
                                          DocumentMapper documentMapper) {
        return new ParhafToFhirMapper(patientMapper, clinicalMapper, documentMapper);
    }
}
