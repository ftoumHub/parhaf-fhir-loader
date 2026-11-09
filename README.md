# parhaf-fhir-loader

Charge le corpus **PARHAF** (projet PARTAGES, Health Data Hub) dans un serveur
`hapi-fhir-jpaserver-starter` configuré pour le contexte français.

PARHAF, c'est 4 259 patients fictifs et 6 190 comptes rendus rédigés et relus
par 120 internes, sur 20 spécialités. Aucune donnée de patient réel, donc aucun
DUA à signer — contrairement à MIMIC. Licences CC BY 4.0 et Etalab 2.0.

---

## Table des matières

1. [Prérequis](#1-prérequis)
2. [Démarrer le serveur FHIR](#2-démarrer-le-serveur-fhir)
3. [Récupérer le corpus](#3-récupérer-le-corpus)
4. [Vérifier le mapping avant de charger](#4-vérifier-le-mapping-avant-de-charger)
5. [Charger](#5-charger)
6. [Explorer les données](#6-explorer-les-données)
7. [Configuration](#7-configuration)
8. [Structure du projet](#8-structure-du-projet)
9. [Ce que le mapping fabrique, et ce qu'il ne code pas](#9-ce-que-le-mapping-fabrique-et-ce-quil-ne-code-pas)
10. [Dépannage](#10-dépannage)
11. [Étapes suivantes](#11-étapes-suivantes)

---

## 1. Prérequis

| Outil | Version | Pour |
|---|---|---|
| JDK | 21+ | le loader |
| Maven | 3.9+ | build |
| Docker + Compose | récent | serveur FHIR + PostgreSQL |
| Python | 3.10+ | récupération du corpus (une fois) |

Compte environ 4 Go de RAM libre pour le conteneur HAPI.

---

## 2. Démarrer le serveur FHIR

```bash
docker compose up -d
docker compose logs -f fhir
```

Attends la ligne `Started Application`. **Le premier démarrage prend plusieurs
minutes** : HAPI télécharge et installe FR Core, puis construit son schéma
PostgreSQL.

Vérifie :

```bash
curl -s http://localhost:8080/fhir/metadata | head -c 200
```

Et que FR Core est bien en place :

```bash
curl -s "http://localhost:8080/fhir/StructureDefinition?_summary=count"
```

Tu dois avoir plusieurs dizaines de `StructureDefinition`. Si le compte est à
zéro, l'IG n'a pas été installé — regarde les logs.

L'UI de test est sur <http://localhost:8080>.

---

## 3. Récupérer le corpus

```bash
pip install datasets
python scripts/export-parhaf.py --out data/parhaf --limit 50
```

Le script convertit la version Hugging Face vers le layout standalone
documenté par le HDH :

```
data/parhaf/
├── corpus.json                       index + métadonnées
└── docs/
    ├── PNEUMOLOGIE/
    │   ├── PNEUMOLOGIE-00001-0.txt
    │   └── ...
    └── CARDIOLOGIE/
```

**Pourquoi ce détour :** la version HF est en Parquet colonnaire (les listes de
dictionnaires y sont stockées comme des dictionnaires de listes), pénible à
lire depuis Java. Le format standalone est un simple JSON plus des fichiers
texte. Le loader n'a donc qu'un seul contrat d'entrée.

Options utiles :

```bash
# Une seule spécialité
python scripts/export-parhaf.py --out data/parhaf --specialty PNEUMOLOGIE

# Tout le corpus (~4 250 patients, quelques minutes)
python scripts/export-parhaf.py --out data/parhaf
```

Si tu récupères directement l'archive standalone du HDH, saute cette étape et
pointe `parhaf.corpus-path` sur son `corpus.json`.

---

## 4. Vérifier le mapping avant de charger

Lance les tests, qui ne demandent ni serveur ni réseau :

```bash
mvn test
```

Puis regarde ce qui serait envoyé, sans rien envoyer :

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--parhaf.dry-run=true
```

Ça sort la transaction complète du premier patient en JSON dans la console.
C'est le moment de vérifier que les codes, les références et les types de
documents te conviennent.

---

## 5. Charger

Commence petit :

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--parhaf.limit=20"
```

Sortie attendue :

```
14:32:01 INFO  JsonCorpusReader     - Corpus chargé : 20 patients retenus
14:32:01 INFO  LoadCorpusService    - 20 enregistrements à charger vers http://localhost:8080/fhir
14:32:02 INFO  LoadCorpusService    - Serveur FHIR 4.0.1 détecté
14:32:09 INFO  LoadCorpusRunner     - ---------------------------------------------
14:32:09 INFO  LoadCorpusRunner     - Chargement terminé en 7 s
14:32:09 INFO  LoadCorpusRunner     -   traités : 20
14:32:09 INFO  LoadCorpusRunner     -   succès  : 20
14:32:09 INFO  LoadCorpusRunner     -   échecs  : 0
```

Puis tout :

```bash
mvn spring-boot:run
```

**Le chargement est idempotent.** Toutes les ressources ont un id déterministe
et sont publiées en `PUT`. Tu peux relancer autant de fois que tu veux : les
mêmes ressources sont écrasées, aucune n'est dupliquée, aucun `Binary` ne reste
orphelin.

En jar autonome :

```bash
mvn clean package
java -jar target/parhaf-fhir-loader-0.1.0-SNAPSHOT.jar --parhaf.limit=100
```

---

## 6. Explorer les données

### En REST

```bash
# Combien de patients ?
curl -s "http://localhost:8080/fhir/Patient?_summary=count"

# Un patient par son identifiant PARHAF
curl -s "http://localhost:8080/fhir/Patient?identifier=urn:parhaf:patient|PNEUMOLOGIE-00001"

# Les séjours hospitaliers
curl -s "http://localhost:8080/fhir/Encounter?class=IMP&_count=5"

# Les comptes rendus de sortie
curl -s "http://localhost:8080/fhir/DocumentReference?type=http://loinc.org|18842-5&_count=5"

# Tout ce qui concerne un patient, d'un coup
curl -s "http://localhost:8080/fhir/Patient/parhaf-pat-PNEUMOLOGIE-00001/\$everything"
```

### Lire un compte rendu

Le texte est dans le `Binary` pointé par le `DocumentReference` :

```bash
curl -s "http://localhost:8080/fhir/Binary/parhaf-bin-PNEUMOLOGIE-00001-0" \
     -H "Accept: text/plain"
```

### En GraphQL

Activé dans la config. C'est de loin le meilleur moyen de comprendre comment le
graphe s'articule — une requête au lieu de cinq allers-retours REST :

```bash
curl -s -X POST "http://localhost:8080/fhir/\$graphql" \
  -H "Content-Type: application/json" \
  -d '{"query":"{ EncounterList(class:\"IMP\", _count:3) { id period { start end } subject { reference } hospitalization { admitSource { text } dischargeDisposition { text } } diagnosis { condition { reference } } } }"}'
```

Ou dans un client GraphQL (GraphiQL, Altair, Insomnia) pointé sur
`http://localhost:8080/fhir/$graphql` — l'introspection est supportée, tu
récupères le schéma complet.

---

## 7. Configuration

Tout est dans `src/main/resources/application.yaml`, sous le préfixe `parhaf`.
Chaque clé est surchargeable en ligne de commande.

| Clé | Défaut | Rôle |
|---|---|---|
| `server-base` | `http://localhost:8080/fhir` | URL du serveur FHIR |
| `corpus-path` | `./data/parhaf/corpus.json` | Index du corpus |
| `specialties` | `[]` | Filtre par spécialité (vide = tout) |
| `limit` | `0` | Nombre max de patients (0 = tout) |
| `threads` | `4` | Publications en parallèle |
| `max-attempts` | `3` | Tentatives par transaction |
| `backoff-millis` | `500` | Attente entre tentatives (croissante) |
| `socket-timeout-millis` | `60000` | Timeout HTTP |
| `custodian-name` | `PARHAF (corpus fictif)` | Affiché dans `DocumentReference.custodian` |
| `dry-run` | `false` | Affiche la première transaction et sort |

Exemple de surcharge :

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="\
  --parhaf.specialties[0]=PNEUMOLOGIE \
  --parhaf.specialties[1]=CARDIOLOGIE \
  --parhaf.limit=100 \
  --parhaf.threads=8"
```

Les spécialités valides sont les 20 du corpus : `ANATOMOPATHOLOGIE`,
`CANCERO ADULTE`, `CARDIOLOGIE`, `CHIR ORTHO ET TRAUMATO`, `CHIR.CARDIO-VASC.`,
`CHIRURGIE VISCERALE`, `GYNECOLOGIE`, `HEMATOLOGIE CLINIQUE`,
`HEPATO-GASTRO-ENTERO`, `MALADIES INFECTIEUSES`, `MEDECINE GERIATRIQUE`,
`MEDECINE INTER-SPECIALITES`, `MEDECINE INTERNE`, `MEDECINE PEDIATRIQUE`,
`NEPHROLOGIE`, `NEUROLOGIE`, `OBSTETRIQUE`, `PNEUMOLOGIE`, `REANIMATION`,
`UROLOGIE`.

---

## 8. Structure du projet

```
parhaf-fhir-loader/
├── pom.xml
├── docker-compose.yml              HAPI + PostgreSQL
├── config/hapi-fr.yaml             config HAPI (FR Core, GraphQL, terminologies)
├── scripts/export-parhaf.py        HF Parquet ──► layout standalone
└── src/
    ├── main/java/fr/ftoumhub/parhaf/
    │   ├── ParhafLoaderApplication.java
    │   ├── domain/                          ← aucune dépendance à Spring ni à HTTP
    │   │   ├── model/                       schéma PARHAF, un record par fichier
    │   │   │   ├── Corpus.java
    │   │   │   ├── PatientRecord.java
    │   │   │   ├── Scenario.java
    │   │   │   ├── StructuredAbstract.java
    │   │   │   ├── ClinicalDocument.java
    │   │   │   ├── CodedConcept.java
    │   │   │   └── Amount.java
    │   │   ├── port/
    │   │   │   ├── CorpusReader.java        d'où viennent les données
    │   │   │   ├── FhirPublisher.java       où elles vont
    │   │   │   ├── PublishSuccess.java
    │   │   │   └── PublishFailure.java
    │   │   └── mapping/                     le cœur : PARHAF ──► FHIR R4
    │   │       ├── ParhafToFhirMapper.java  orchestre le graphe
    │   │       ├── PatientMapper.java       Patient, Practitioner
    │   │       ├── ClinicalMapper.java      Condition, Procedure, Encounter
    │   │       ├── DocumentMapper.java      Binary, DocumentReference
    │   │       ├── TransactionBundleBuilder.java
    │   │       ├── Terminologies.java       URL canoniques
    │   │       ├── DocumentTypeCatalog.java tables de correspondance
    │   │       ├── LocalIdentifierSystems.java
    │   │       ├── ResourceIds.java         ids déterministes
    │   │       └── SyntheticDates.java      dates dérivées, isolées ici
    │   ├── infrastructure/
    │   │   ├── corpus/JsonCorpusReader.java
    │   │   └── fhir/HapiFhirPublisher.java
    │   ├── application/
    │   │   ├── LoadCorpusService.java       cas d'usage
    │   │   ├── DryRunService.java
    │   │   ├── LoadReport.java
    │   │   └── LoadCorpusRunner.java        CLI
    │   └── config/
    │       ├── LoaderProperties.java
    │       └── LoaderConfiguration.java     le seul point de câblage
    └── test/java/.../mapping/
        ├── ParhafFixtures.java
        ├── ParhafToFhirMapperTest.java
        ├── ResourceIdsTest.java
        └── SyntheticDatesTest.java
```

Le domaine n'importe ni Spring, ni Jackson, ni HTTP. Il ne connaît que les
modèles HAPI R4, qui sont de simples POJO. C'est ce qui permet aux tests de
tourner sans docker.

### Le graphe produit

Pour chaque patient, une transaction :

```
Patient
  ├── Practitioner            (auteur, mutualisé entre patients)
  ├── Condition               (diagnostic principal, CIM-10)
  ├── Procedure               (acte principal, CCAM)        [si présent]
  ├── Encounter               (le séjour)
  │     └── diagnosis ──────► Condition
  └── pour chaque compte rendu :
        Binary                (texte brut UTF-8)
        DocumentReference ──► Binary, Patient, Encounter, Practitioner
```

---

## 9. Ce que le mapping fabrique, et ce qu'il ne code pas

### Deux dérivations synthétiques

Isolées dans `SyntheticDates` pour qu'on ne les confonde pas avec des données
du corpus :

- **Les dates.** Les CR portent des dates dans leur texte, mais rien de
  structuré. Le loader génère une période déterministe sur 2025, dérivée de
  l'identifiant du patient, pour que les recherches par date restent testables.
- **Les dates de naissance.** Le corpus donne un âge. Le loader reconstruit une
  date approximative au 1er juillet.

### Ce qui n'est pas codé

Les terminologies françaises ne sont pas câblées. Les modes d'entrée et de
sortie sont posés en texte libre, sans codage. Les CIM-10 sont déclarés sur le
système ICD-10 international, la CCAM sur un OID. Les points de branchement
sont marqués `TODO-TRE` dans `Terminologies.java` et `ClinicalMapper.java`.

Raison : les nomenclatures TRE de l'ANS vivent sur le SMT et ne sont pas
embarquées dans les packages IG.

### Deux codes LOINC approximatifs

`ACCOUCHEMENT` et `MATERNITE` sont ramenés sur le code générique de CR
d'hospitalisation, faute d'un code obstétrique dont je sois sûr. Marqué
`TODO-VERIF` dans `DocumentTypeCatalog`.

### Pas de validation de profil

`enable_repository_validating_interceptor` est à `false`, et
`pre_expand_value_sets` aussi. Délibéré : on charge d'abord, on valide ensuite.

### Ce que le corpus ne couvre pas

Le dataset exclut explicitement l'imagerie, les prescriptions, les courriers
d'adressage et les CR opératoires standalone. Si ta chaîne GED traite ces
types, PARHAF ne les exercera pas.

---

## 10. Dépannage

**`HAPI-0702: Unable to expand ValueSet because CodeSystem could not be found`
au démarrage du conteneur**

Les CodeSystem TRE de l'ANS ne sont pas dans les packages IG. `hapi-fr.yaml`
règle déjà ça avec `pre_expand_value_sets: false`. Si l'erreur persiste,
vérifie que le fichier est bien monté :

```bash
docker compose exec fhir cat /app/config/application.yaml | head -5
```

**`BeanCreationException` sur `repositoryValidatingInterceptor` au second
démarrage**

Bug connu du starter. Repars d'une base propre :

```bash
docker compose down -v && docker compose up -d
```

**`Corpus introuvable`**

Le loader cherche `./data/parhaf/corpus.json` relativement au répertoire
courant. Lance-le depuis la racine du projet, ou passe un chemin absolu via
`--parhaf.corpus-path`.

**Timeouts pendant le chargement**

Baisse `threads` et monte `socket-timeout-millis`. HAPI en PostgreSQL avec
indexation à l'écriture n'aime pas la concurrence agressive.

**Le chargement passe mais rien n'apparaît en recherche**

Vérifie que tu interroges le bon type. Le texte des CR est dans les `Binary`,
pas dans les `DocumentReference` — ces derniers ne portent que les métadonnées.

---

## 11. Étapes suivantes

1. **Activer la validation.** Passer
   `enable_repository_validating_interceptor: true` dans `hapi-fr.yaml`, poser
   `meta.profile` sur les `Patient` vers `fr-core-patient`, et voir ce que FR
   Core reproche.
2. **Ajouter les IG ANS.** Annuaire Santé, puis le volet CI-SIS documents, pour
   passer d'un `DocumentReference` générique à un `DocumentReference` profilé
   Ségur.
3. **Brancher le SMT** pour résoudre les bindings CIM-10 FR et CCAM, et
   remplacer les `TODO-TRE`.
4. **Générer des docx.** Le corpus est en texte brut. Pour tester une chaîne de
   conversion, injecter ce texte dans des docx porteurs des structures Word qui
   font vraiment casser (styles, tables imbriquées, champs de fusion, macros).

---

## Non compilé

Le projet n'a pas été construit : l'environnement où il a été écrit n'a pas
accès à Maven Central. Attends-toi à quelques ajustements de signatures HAPI au
premier `mvn compile`. Les versions `hapi-fhir 7.4.0` et `hl7.fhir.fr.core
2.2.0` sont à confirmer avant de les figer.
