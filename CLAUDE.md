# CLAUDE.md

Ce fichier fournit des indications à Claude Code (claude.ai/code) pour travailler sur le code de ce dépôt.

## De quoi s'agit-il

Charge le corpus **PARHAF** (comptes rendus cliniques fictifs en français, 4 259 patients, 6 190 comptes rendus sur 20 spécialités — projet PARTAGES, Health Data Hub) dans une instance `hapi-fhir-jpaserver-starter` configurée pour le contexte FHIR français (FR Core). C'est une CLI Spring Boot (pas de serveur web), Java 21, Maven.

## Commandes

```bash
# Démarrer le serveur FHIR (HAPI + PostgreSQL)
docker compose up -d
docker compose logs -f fhir          # le premier démarrage prend plusieurs minutes (installation de l'IG FR Core)

# Lancer les tests (ni docker ni réseau requis — la couche domaine est pure)
mvn test

# Lancer une seule classe ou méthode de test
mvn test -Dtest=ParhafToFhirMapperTest
mvn test -Dtest=ParhafToFhirMapperTest#someMethodName

# Récupérer le corpus (une fois, nécessite Python 3.10+)
pip install datasets
python scripts/export-parhaf.py --out data/parhaf --limit 50

# Dry run — affiche la transaction du premier patient, n'envoie rien
mvn spring-boot:run -Dspring-boot.run.arguments=--parhaf.dry-run=true

# Charger (petit lot d'abord)
mvn spring-boot:run -Dspring-boot.run.arguments="--parhaf.limit=20"

# Tout charger
mvn spring-boot:run

# Jar autonome
mvn clean package
java -jar target/parhaf-fhir-loader-0.1.0-SNAPSHOT.jar --parhaf.limit=100
```

Toute clé `parhaf.*` de `src/main/resources/application.yaml` peut être surchargée en ligne de commande (ex. `--parhaf.specialties[0]=PNEUMOLOGIE --parhaf.threads=8`).

**Note :** ce projet n'a pas été construit dans l'environnement où il a été écrit (pas d'accès à Maven Central). Attends-toi à d'éventuels ajustements de signatures de l'API HAPI au premier `mvn compile` ; les versions `hapi-fhir 7.4.0` / `hl7.fhir.fr.core 2.2.0` sont à confirmer.

## Architecture

Architecture hexagonale (ports & adapters), sous `fr.ftoumhub.parhaf` :

- **`domain/`** — Java pur, **aucune dépendance à Spring, Jackson ou HTTP** (seulement les classes de modèle R4 de HAPI, de simples POJO). C'est pour ça que `mvn test` ne nécessite ni docker ni réseau. Ne jamais ajouter d'import framework/infra ici.
  - `domain/model/` — un record par forme de fichier du corpus PARHAF (`Corpus`, `PatientRecord`, `Scenario`, `StructuredAbstract`, `ClinicalDocument`, `CodedConcept`, `Amount`).
  - `domain/port/` — interfaces `CorpusReader` (entrée) et `FhirPublisher` (sortie) ; `PublishSuccess`/`PublishFailure` sont les types de résultat `Either` de Vavr (l'échec de publication est une valeur, pas une exception).
  - `domain/mapping/` — le cœur de la logique de mapping PARHAF → FHIR R4 :
    - `ParhafToFhirMapper` orchestre l'ensemble du graphe de ressources par patient.
    - `PatientMapper` (Patient, Practitioner), `ClinicalMapper` (Condition, Procedure, Encounter), `DocumentMapper` (Binary, DocumentReference).
    - `TransactionBundleBuilder` assemble le Bundle de transaction FHIR.
    - `ResourceIds` génère des ids de ressources déterministes (les chargements sont idempotents — republier écrase, ne duplique jamais, aucun `Binary` orphelin).
    - `SyntheticDates` isole les deux données que le loader invente plutôt qu'il ne les lit du corpus : les dates de séjour (déterministes, dérivées de l'identifiant du patient, réparties sur 2025) et les dates de naissance (dérivées de l'âge, fixées au 1er juillet). Garder les dérivations synthétiques confinées à ce fichier pour qu'on ne les confonde jamais avec des données réelles du corpus.
    - `Terminologies` (URL canoniques) et `DocumentTypeCatalog` (tables de correspondance LOINC/type) marquent les bindings de terminologie non résolus avec `TODO-TRE` (les nomenclatures TRE de l'ANS vivent sur le SMT, pas embarquées dans les packages d'IG FHIR) et `TODO-VERIF` (codes LOINC incertains, ex. types de documents obstétriques ramenés sur un code générique de CR d'hospitalisation).
- **`infrastructure/`** — adapteurs implémentant les ports du domaine : `JsonCorpusReader` (lit `corpus.json` + les fichiers de documents), `HapiFhirPublisher` (client FHIR HAPI, transaction en `PUT`).
- **`application/`** — cas d'usage câblant les ports entre eux : `LoadCorpusService` (exécution réelle), `DryRunService` (affiche le premier bundle, sans réseau), `LoadCorpusRunner` (point d'entrée CLI via `CommandLineRunner`), `LoadReport`.
- **`config/`** — `LoaderProperties` (record `@ConfigurationProperties` lié à `parhaf.*`), `LoaderConfiguration` (le seul point de câblage Spring — les définitions de bean vivent uniquement ici).

### Graphe de ressources produit par patient

```
Patient
  ├── Practitioner            (auteur, mutualisé entre patients)
  ├── Condition               (diagnostic principal, CIM-10)
  ├── Procedure               (acte principal, CCAM)        [si présent]
  ├── Encounter                (le séjour hospitalier)
  │     └── diagnosis ──────► Condition
  └── pour chaque compte rendu :
        Binary                (texte brut UTF-8)
        DocumentReference ──► Binary, Patient, Encounter, Practitioner
```

Le texte des documents vit dans les ressources `Binary` ; `DocumentReference` ne porte que les métadonnées — le récupérer avec `Accept: text/plain` sur le `Binary`.

### Flux de données du corpus

La version Hugging Face de PARHAF est en Parquet colonnaire (les listes de dictionnaires y sont stockées comme des dictionnaires de listes), pénible à lire depuis Java. `scripts/export-parhaf.py` la convertit une fois vers un layout standalone (index `corpus.json` + fichiers `docs/<SPECIALITE>/*.txt`) qui correspond au format d'archive standalone du HDH lui-même, de sorte que `JsonCorpusReader` n'a qu'un seul contrat d'entrée. Si tu as déjà l'archive standalone du HDH, saute le script et pointe `parhaf.corpus-path` directement sur son `corpus.json`.

### Manques volontaires connus (pas des bugs)

- Les bindings de terminologie française (nomenclatures TRE) ne sont pas câblés — les codes CIM-10 sont déclarés sur le système ICD-10 international, la CCAM sur un OID provisoire. Voir les marqueurs `TODO-TRE`.
- `enable_repository_validating_interceptor` et `pre_expand_value_sets` sont à `false` dans `config/hapi-fr.yaml` — délibéré : on charge d'abord, on valide ensuite.
- Le corpus exclut l'imagerie, les prescriptions, les courriers d'adressage et les CR opératoires standalone.

### Dépannage

- `HAPI-0702: Unable to expand ValueSet` au démarrage du conteneur : attendu (les CodeSystem TRE de l'ANS ne sont pas dans les packages d'IG) ; `pre_expand_value_sets: false` dans `config/hapi-fr.yaml` gère déjà ça.
- `BeanCreationException` sur `repositoryValidatingInterceptor` au second démarrage : bug connu du starter — `docker compose down -v && docker compose up -d` pour repartir d'une base propre.
- `Corpus introuvable` : le loader cherche `./data/parhaf/corpus.json` relativement au répertoire courant — lance-le depuis la racine du projet, ou passe `--parhaf.corpus-path`.
- Timeouts pendant le chargement : baisse `parhaf.threads` et monte `parhaf.socket-timeout-millis` — HAPI sur PostgreSQL avec indexation à l'écriture n'aime pas la concurrence agressive.
