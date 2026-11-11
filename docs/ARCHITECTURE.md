# Farol - Architecture and Project Document

Status date: 2026-11-11. This document describes the Android project Farol (package
`pt.ismai.farol`, version 1.0) as it exists in the source tree on that date.

It explains how the project is organized, why each important decision was taken, and which
features exist today. It is written for two readers: the professor grading the project, and
the author returning to the code months from now.

Status labels used throughout:

- **Implemented**: the code is in the source tree and works in the running app.
- **Partial**: the code exists but has a gap, which is always stated.
- **Planned**: not in the source tree; described from the project specification only.

Statements about the code were checked against the source files. Statements about planned
work come from the project specification (the development guide; its second version replaces
Room with Firebase Auth and Firestore and adds a VirusTotal reputation check).

## Contents

1. Overview and goals
2. Features and status
3. Architecture
4. Domain model and scoring
5. Data sources
6. Decision log
7. Testing
8. Reproducibility
9. Security, privacy and ethics
10. Roadmap
11. Known limitations

---

## 1. Overview and goals

### 1.1 What Farol is

Farol (Portuguese for "lighthouse") is an Android app written in Kotlin with Jetpack Compose.
Its motto, shown on the home screen, is "Look before you click." A lighthouse does not stop a
ship; it shows where the rocks are. Farol does the same for a web address: before the user
opens a link, buys from an unknown shop or trusts an email, it shows what public sources say
about that domain or IP address.

The user types or pastes a domain, a full URL or an IPv4 address. The app runs a small set of
independent checks, shows each one as a card as soon as it finishes, and condenses them into a
score from 0 to 100 with a plain-language explanation per check.

### 1.2 Goals

| Everyday situation | Question Farol answers | Status |
|---|---|---|
| An SMS says a parcel is on hold and gives a link | Was the domain registered a few days ago? Is the certificate valid? | Implemented |
| An unknown online shop with prices that are too good | Domain age, site protections, certificate | Implemented |
| An email that claims to come from the bank | Does the domain publish SPF and DMARC? Without them anyone can send mail in its name | Implemented |
| "Is my router exposed to the internet?" | Does my public IP have open ports or known vulnerabilities? | Planned (My network) |

Farol is decision support for a non-technical user. It is not a vulnerability scanner and it
does not give a verdict (see section 11).

### 1.3 Design principle: passive reconnaissance only

Farol only reads data that is already public. An analysis produces exactly four kinds of
network traffic:

1. DNS questions (A, TXT, MX) sent to Google Public DNS over HTTPS.
2. A lookup of one IP address in Shodan InternetDB, a database Shodan has already published.
3. RDAP lookups of registration data published by domain and IP registries.
4. One ordinary HTTPS GET of the site's front page, the same request any browser makes. The
   certificate is read from the TLS handshake and the security headers from the response.

Farol never opens connections to find out which ports answer (no port scanning), never sends
requests built to get around authentication or access control, and never tries credentials.
The "Port exposure" check shows what InternetDB already knows; the app itself scans nothing.

Legal rationale. The Portuguese Cybercrime Law (Lei 109/2009), article 6, punishes access to
a computer system without authorization "by any means". The project's reading, taken from the
specification, is that the attempt itself is what matters, so actively scanning someone else's
machine is at best a grey area even when the scan finds nothing. Farol stays on the safe side
of a simple line: reading what has been published is acceptable, probing what is closed is
not. Going passive-only was a deliberate design decision, not a shortcut for lack of time.
This paragraph records the design rationale; it is not legal advice.

### 1.4 Status at a glance

- **Implemented**: input validation, five checks for domains and two for IPs, the normalized
  score, the progressive result screen, the detail screen, history, favorites with sequential
  re-check and change detection, the 10-minute cache, plain-text sharing, the theme switch,
  the error model and 69 unit tests.
- **Implemented in memory only**: history, favorites, the cache and the settings are lost when
  the app process ends, because the persistent store is the next large step.
- **Planned**: accounts (Firebase Auth), synchronized storage (Firestore), the "My network"
  data, persisted settings (DataStore) and the VirusTotal reputation check.

---

## 2. Features and status

Paths are relative to `app/src/main/java/pt/ismai/farol/` unless they start with `res/`.

| Feature | Status | Screens | Main files |
|---|---|---|---|
| Search a domain, URL or IPv4; validation and normalization | Implemented | Home | `ui/screens/home/*`, `ui/components/SearchField.kt`, `domain/TargetNormalizer.kt` |
| SSL certificate check | Implemented | Result, Detail | `data/remote/HttpChecker.kt`, `Checkers.certificateAndHeaders`, `domain/evaluators/CertificateEvaluator.kt` |
| Security headers check | Implemented | Result, Detail | same request as the certificate; `HeadersEvaluator.kt` |
| Email anti-spoofing check (SPF, DMARC; MX shown as information) | Implemented | Result, Detail | `data/remote/DnsApi.kt`, `Checkers.email`, `EmailEvaluator.kt`, `domain/CleanTxt.kt` |
| Port exposure check (known open ports and CVEs) | Implemented | Result, Detail | `data/remote/InternetDbApi.kt`, `Checkers.exposure`, `ExposureEvaluator.kt` |
| Domain age check | Implemented | Result, Detail | `data/remote/RdapApi.kt`, `Checkers.age`, `AgeEvaluator.kt` |
| IP owner (IP targets only, informational) | Implemented | Result, Detail | `Checkers.ipOwner`, `IpOwnerEvaluator.kt` |
| Score 0-100 over the available checks | Implemented | Result, lists | `domain/ScoreCalculator.kt`, `domain/ScoreWeights.kt`, `ui/components/ScoreGauge.kt` |
| Progressive result: cards fill in as checks finish | Implemented | Result | `AnalysisRepositoryImpl.analyze`, `ui/screens/result/*`, `CheckCard.kt`, `LoadingCard.kt` |
| Explanation of one check | Implemented | Detail | `ui/screens/detail/*` |
| History: list, reopen, delete one, delete all with confirmation; latest five on Home | Implemented, in memory only | History, Home | `ui/screens/history/*`, `data/repository/AnalysisStore.kt` |
| 10-minute cache with "Re-analyze" | Implemented, in memory only | Result | `AnalysisRepositoryImpl.readCache` |
| Favorites: star on the result, add by name, remove, sequential "Re-check all", change detection | Implemented, in memory only | Favorites, Result | `ui/screens/favorites/*`, `AnalysisRepositoryImpl`, `Parsers.resultChanged` |
| Share the report as plain text | Implemented (basic); polish Planned | Result | `domain/Report.kt`, `ResultScreen.kt` |
| Theme: follow system, dark, light | Partial: applies at once, not persisted | Settings | `ui/screens/settings/*`, `ui/theme/*`, `data/fake/FakeSettingsRepository.kt` |
| Request timeout option (5, 10, 20, 30 s) | Partial: shown and kept in memory, but not yet applied to the HTTP client, which stays at 10 s | Settings | `SettingsScreen.kt`, `data/remote/HttpClient.kt` |
| "About and privacy" text | Implemented with first-version wording; revision Planned | Settings | `SettingsScreen.kt`, `res/values/strings_settings.xml` |
| Error handling: no internet, domain not found, rate limit, invalid input, unexpected errors | Implemented | all | `domain/model/Models.kt`, `ui/screens/common/ErrorUi.kt`, `AnalysisRepositoryImpl.runGuarded` |
| My network: public IP and its exposure | Partial: screen and ViewModel exist; `myNetwork()` throws, so the screen shows the generic error | My network | `ui/screens/network/*` |
| Accounts: login and registration screen, navigation gate, auth error mapper | Planned | new auth screen | not in the tree |
| Cloud store: synchronized history and favorites, security rules, account deletion | Planned | History, Favorites, Settings | future implementation of `AnalysisStore` |
| Persisted settings (DataStore) | Planned; the dependency is already declared | Settings | future implementation of `SettingsRepository` |
| VirusTotal reputation check | Planned, last | Result, Detail | not in the tree |

---

## 3. Architecture

### 3.1 Layers

The app is a single Gradle module (`:app`) with one activity. The code follows MVVM with a
repository, in three layers:

```
ui      Compose screens  <-- StateFlow --  ViewModels
        (ui/screens, ui/components, ui/navigation, ui/theme)
                                |
                                v
domain  models, repository interfaces, TargetNormalizer, evaluators, ScoreCalculator, Report
                                ^
                                |
data    AnalysisRepositoryImpl -> Checkers -> Retrofit APIs + HttpChecker   (data/remote)
                               -> AnalysisStore (InMemoryAnalysisStore)     (data/repository)
        FakeSettingsRepository                                              (data/fake)

AppContainer (composition root) builds the data objects once and hands the domain
interfaces to the ViewModel factories.
```

Dependency rules, verified by searching the imports:

- `domain` is pure Kotlin. No file under `domain/` imports Android, AndroidX, OkHttp, Retrofit
  or Gson; it uses only the JDK and `kotlinx.coroutines.flow.Flow`.
- No file under `ui/` imports anything from `data`. ViewModels receive `AnalysisRepository` or
  `SettingsRepository`, the interfaces declared in `domain/Repositories.kt`.
- `data` implements the domain interfaces and calls the domain evaluators.
- `AppContainer` is the only class that names the concrete implementations.

### 3.2 Package layout

```
app/src/main/java/pt/ismai/farol/
|-- FarolApp.kt              Application class; creates the AppContainer lazily
|-- AppContainer.kt          composition root: manual dependency injection
|-- MainActivity.kt          the only activity: theme, edge-to-edge, navigation host
|-- domain/                  pure Kotlin, unit-tested on the JVM
|   |-- model/Models.kt      Check, CheckState, CheckId, Target, AnalysisResult,
|   |                        AnalysisProgress, AnalysisSummary, FavoriteInfo, NetworkResult,
|   |                        ErrorType, FarolException, ThemePref
|   |-- Repositories.kt      AnalysisRepository and SettingsRepository interfaces
|   |-- TargetNormalizer.kt  validates and normalizes what the user typed
|   |-- ScoreWeights.kt      every weight and threshold used by the evaluators
|   |-- ScoreCalculator.kt   normalized 0-100 score
|   |-- Checks.kt            canonical check titles; factory for UNAVAILABLE checks
|   |-- CleanTxt.kt          cleans TXT records returned by dns.google
|   |-- Report.kt            plain-text report for the Share menu
|   `-- evaluators/          CertificateEvaluator, AgeEvaluator, HeadersEvaluator,
|                            EmailEvaluator, ExposureEvaluator, IpOwnerEvaluator,
|                            StateForPoints
|-- data/
|   |-- remote/
|   |   |-- HttpClient.kt    the single OkHttpClient, ConfigurableTimeout, createApi()
|   |   |-- DnsApi.kt, InternetDbApi.kt, RdapApi.kt      Retrofit interfaces
|   |   |-- HttpChecker.kt   raw OkHttp request: certificate + headers
|   |   `-- dto/             DnsDtos.kt, InternetDbDtos.kt, RdapDtos.kt
|   |-- repository/
|   |   |-- AnalysisRepositoryImpl.kt    orchestration, cache, history, favorites
|   |   |-- Checkers.kt      one function per check: call the API, run the evaluator
|   |   |-- Parsers.kt       pure helpers that interpret API responses
|   |   |-- AnalysisStore.kt storage interface + InMemoryAnalysisStore
|   |   `-- DataTexts.kt     "unavailable" reasons read from string resources
|   `-- fake/FakeSettingsRepository.kt   in-memory settings stand-in
|-- ui/
|   |-- navigation/          Routes.kt, FarolNavHost.kt
|   |-- theme/               Color.kt, Theme.kt
|   |-- components/          CheckCard, StateIcon, ScoreGauge, SearchField, LoadingCard,
|   |                        EmptyState
|   `-- screens/
|       |-- common/          BackTopBar, CompactScore, ErrorUi, DateFormatting
|       `-- home/ result/ detail/ network/ favorites/ history/ settings/
|                            each with XxxScreen.kt and XxxViewModel.kt
`-- util/Connectivity.kt     wrapper around ConnectivityManager

app/src/debug/java/.../data/remote/LogHttp.kt      HTTP logging interceptor (debug only)
app/src/release/java/.../data/remote/LogHttp.kt    returns null: no logging in release
app/src/test/java/pt/ismai/farol/                  11 JUnit test classes (section 7)
app/src/main/res/values/strings*.xml               11 string files, one per screen or area
```

Size: 61 Kotlin files (about 3,600 lines) in `main` and 11 test files (about 700 lines).

### 3.3 Data flow of one analysis

Example: the user pastes `https://www.GitHub.com/explore` and taps Analyze.

```
HomeScreen                 Analyze tapped
  HomeViewModel.validate()   TargetNormalizer.parse -> Target("www.github.com", DOMAIN)
                             invalid input: error under the field, no request is made
FarolNavHost               navigate to result/www.github.com?analysisId=-1
ResultViewModel            init -> analyze(force = false); state = Loading
  AnalysisRepositoryImpl.analyze(target, force)       cold channelFlow on Dispatchers.IO
    1. TargetNormalizer.parse          invalid -> FarolException(INVALID_INPUT)
    2. readCache (skipped if force)    hit -> emit one finished progress, fromCache = true
    3. Connectivity.hasInternet()      false -> FarolException(NO_INTERNET)
    4. Checkers.resolveIpv4            dns.google A query; Status 3 -> DOMAIN_NOT_FOUND
    5. emit progress: completed = [], pending = [certificate, headers, age, email, exposure]
    6. four tasks in parallel (async / awaitAll):
         certificate + headers   HttpChecker: one GET https://www.github.com
         age                     RDAP lookup: www.github.com, then github.com
         email                   SPF, DMARC and MX lookups (also in parallel)
         exposure                InternetDB lookup of the IPv4 found in step 4
       after each task, under a Mutex: move its ids from pending to completed, emit progress
    7. ScoreCalculator.calculate(checks)
    8. AnalysisStore.save(result) -> id       skipped if every check is UNAVAILABLE
    9. emit progress: finished = true, score, analysisId = id
  every emission  -> state = Success(progress)
  FarolException  -> state = Error(string resource id)
ResultScreen               ScoreGauge, one CheckCard per completed check, one LoadingCard
                           per pending id; cards open the detail once the analysis is saved
DetailViewModel            route detail/{analysisId}/{check}; loads the saved analysis by id
```

For an IP target step 4 is skipped and step 6 runs two tasks: exposure and IP owner.
Opening an entry from History passes its id in the route, so the Result screen loads the
saved analysis and makes no request.

### 3.4 Concurrency model

- **Where work runs.** `analyze` is a `channelFlow` moved to `Dispatchers.IO` with `flowOn`.
  Retrofit `suspend` functions and the callback bridge in `HttpChecker` never block the main
  thread.
- **Parallel checks.** Each task is an `async` inside `coroutineScope`, joined with
  `awaitAll`. The email check starts its three lookups in parallel in its own scope. Total
  time is close to the slowest check, not the sum of all checks.
- **Shared state.** Tasks finish on different threads and update the same `completed` and
  `pending` lists, so the update and the emission happen inside `mutex.withLock`.
- **Isolation.** `runGuarded` turns the failure of one task into UNAVAILABLE checks for that
  task's ids. Only `FarolException` and cancellation propagate.
- **Structured cancellation.** If a task throws `FarolException`, `coroutineScope` cancels
  its siblings and the flow fails. When the user leaves the Result screen its ViewModel is
  cleared, `viewModelScope` is cancelled and the in-flight HTTP calls are cancelled too
  (`HttpChecker` registers `invokeOnCancellation { cancel() }`). "Re-analyze" cancels the
  previous job before starting a new one.
- **Rotation.** The analysis runs in `viewModelScope` and the ViewModel survives
  configuration changes; the screen re-subscribes with `collectAsStateWithLifecycle`.
- **Sequential re-check.** `recheckFavorites()` analyzes favorites one after another with
  `force = true`. It stops on "no internet" or "rate limited" and skips other failures.

The core of the parallel step (comments added for this document):

```kotlin
coroutineScope {
    tasks.map { task ->
        async {
            val result = runGuarded(task)        // one failing check becomes UNAVAILABLE
            mutex.withLock {
                completed += result
                pending.removeAll(result.map { it.id }.toSet())
                send(partial())                  // new snapshot for the progressive UI
            }
        }
    }.awaitAll()
}
```

### 3.5 Error model

Repositories throw `FarolException(type)` where `ErrorType` is `NO_INTERNET`,
`DOMAIN_NOT_FOUND`, `RATE_LIMITED`, `INVALID_INPUT` or `UNKNOWN`. One function,
`Throwable.messageRes()` in `ui/screens/common/ErrorUi.kt`, maps any error to a string
resource. The guiding rule: an expected failure of one source is a result (an UNAVAILABLE
check), not an error; only failures that make the whole analysis meaningless abort it.

| Situation | Detected by | Outcome |
|---|---|---|
| Invalid input | `TargetNormalizer.parse` returns null | Error under the field, no request. `INVALID_INPUT` if it ever reaches the repository |
| No connection at the start | `Connectivity.hasInternet()` | Abort with `NO_INTERNET`; error screen with "Try again" |
| First DNS lookup cannot reach dns.google | `UnknownHostException`, `ConnectException` | Abort with `NO_INTERNET` |
| First DNS lookup times out or returns another HTTP error | `SocketTimeoutException`, non-2xx | Abort with `UNKNOWN` (generic message) |
| Domain does not exist | dns.google `Status == 3` | Abort with `DOMAIN_NOT_FOUND` |
| HTTP 429 on the first DNS lookup | `bodyOrNull()` | Abort with `RATE_LIMITED` |
| HTTP 429 or another HTTP error in a later check | caught inside that checker | Only that check is UNAVAILABLE |
| Timeout or I/O error in one check | `runGuarded` | That check is UNAVAILABLE with a reason; if the connection is gone, abort with `NO_INTERNET` |
| Invalid certificate | `SSLException` in `HttpChecker` | A result: Certificate FAIL with 0 points, Headers UNAVAILABLE |
| Site refuses the connection | `ConnectException`, `UnknownHostException` | Certificate FAIL with 0 points, Headers UNAVAILABLE |
| InternetDB has no data for the IP | HTTP 404 | Not an error: Exposure OK, "No known open ports" |
| RDAP has no data | HTTP 404 after trying parent domains | Domain age UNAVAILABLE, no penalty |
| Domain has no IPv4 address | no A record in the answer | Exposure UNAVAILABLE |
| Anything unexpected | `catch (e: Exception)` in `runGuarded`; `CoroutineExceptionHandler` in the ViewModels | Check UNAVAILABLE, or a generic error state; logged with tag `Farol`; never a crash |

### 3.6 Caching and storage model

Storage sits behind one small interface:

```kotlin
interface AnalysisStore {
    val analyses: Flow<List<AnalysisResult>>
    val favorites: Flow<List<String>>
    suspend fun save(result: AnalysisResult): Long
    suspend fun delete(id: Long)
    suspend fun clear()
    suspend fun setFavorite(target: String, favorite: Boolean)
}
```

The store keeps two collections only. The repository derives everything else from them:

| Need | How it is derived |
|---|---|
| History rows | `analyses` sorted by date, mapped to `AnalysisSummary` |
| Latest five on Home | the first five history rows |
| Saved analysis by id (reopen, detail) | lookup in `analyses` |
| 10-minute cache | newest saved analysis of the same target, if `now - date <= 10 min` |
| Favorite score and previous score | the two newest saved analyses of that target |
| Favorite "changed" flag | `resultChanged(previous, latest)`: the score or the state of any check differs |

Cache rules, as coded:

- The cache is read before the connectivity test, so a cached result is shown even offline.
- It is skipped when `force = true` (the "Re-analyze" button and the favorites re-check).
- An analysis in which every check is UNAVAILABLE is neither saved nor served from cache.
- A cache hit is emitted as one finished `AnalysisProgress` with `fromCache = true`; the
  Result screen then shows a notice inviting the user to re-analyze for fresh data.

Today the only implementation is `InMemoryAnalysisStore` (two `MutableStateFlow`s and a
counter). It is a stand-in: data lives as long as the process. The planned cloud store
replaces this one class and one line in `AppContainer`.

### 3.7 Navigation and routes

One `NavHost` (`ui/navigation/FarolNavHost.kt`); routes are built in `Routes.kt`.

| Route | Screen | Arguments |
|---|---|---|
| `home` | Home (start destination) | none |
| `result/{target}?analysisId={analysisId}` | Result | `target`: URI-encoded string. `analysisId`: long, default -1. With -1 the screen runs an analysis; otherwise it loads that saved analysis |
| `detail/{analysisId}/{check}` | Detail | id of the saved analysis, id of the check |
| `network` | My network | none |
| `favorites` | Favorites | none |
| `history` | History | none |
| `settings` | Settings | none |

- Routes carry only strings and ids. The destination loads its data from the repository.
- `goTo` and `goBack` act only while the source entry is `RESUMED`, so repeated taps during
  a transition are ignored; navigation uses `launchSingleTop`.
- ViewModels are created with `viewModelFactory { initializer { ... } }`, receive their
  dependencies from `AppContainer`, and are scoped to their navigation entry.
- There is no authentication gate yet (Planned): the start destination is always `home`.

### 3.8 UI state and reusable components

Each ViewModel exposes its state as a `StateFlow`, and every screen except Home models that
state as a sealed interface rendered with an exhaustive `when`: `ResultUiState` (Loading,
Success, Error), `DetailUiState`, `HistoryUiState`, `FavoritesUiState` plus `RecheckState`
(Idle, Running, Failed), `NetworkUiState` (Initial, Loading, Success, Error) and
`SettingsUiState`. Home only needs the typed text and an optional error, so it uses a small
data class (`HomeUi`). Errors travel as `@StringRes` ids, never as text.

| Component | Used by | Role |
|---|---|---|
| `CheckCard` | Result, My network | Draws any `Check`; the only card composable |
| `StateIcon` with `color()`, `icon()`, `labelRes()` | cards, score, lists, detail | The single mapping from `CheckState` to icon, color and label |
| `ScoreGauge`, `CompactScore` | Result; History, Favorites | Score number with its state |
| `SearchField` | Home, Favorites | Shared input; validation stays in the ViewModel |
| `LoadingCard` | Result | Spinner card for a check that is still running |
| `EmptyState` | Home, History, Favorites | Icon and message, so no list is ever a blank screen |
| `BackTopBar` | every screen except Home | Top bar with back arrow and optional actions |

A state is never conveyed by color alone: every state has its own icon, a text label and a
content description for screen readers.

### 3.9 Theme and system bars

- `FarolTheme` defines two fixed color schemes: night blue with lighthouse amber as primary
  in the dark scheme, and a light counterpart with a darker amber. Material 3 dynamic color
  is not used.
- State colors (good, warning, critical, neutral) are provided through `LocalStateColors`,
  with darker variants for the light theme so text and icons keep contrast on a light
  background.
- `MainActivity` collects the theme preference with an initial value of `null` and draws
  nothing until the real value arrives, which avoids a flash of the wrong theme.
- `enableEdgeToEdge` is called with transparent `SystemBarStyle.auto` styles whose dark flag
  follows the theme actually in use, and is re-applied when that changes. Every screen is a
  Material 3 `Scaffold`, which applies the window insets.
- The XML window backgrounds (`values/themes.xml`, `values-night/themes.xml`) use the same
  two background colors as the Compose theme, so there is no white frame at startup.

---

## 4. Domain model and scoring

### 4.1 Models (`domain/model/Models.kt`)

| Type | Purpose |
|---|---|
| `Check` | Result of one check: `id`, `title`, `state`, `summary` (one line for the card), `detail` (longer text for the detail screen), `points`, `weight` |
| `CheckState` | `OK`, `WARNING`, `FAIL`, `UNAVAILABLE` |
| `CheckId` | Stable ids: `certificate`, `age`, `headers`, `email`, `exposure`, `ip_owner` |
| `Target`, `TargetType` | Validated input and its kind: `DOMAIN` or `IP` |
| `AnalysisResult` | Finished analysis: target, type, checks, score (nullable), date, id |
| `AnalysisProgress` | Snapshot emitted during an analysis: completed checks, pending ids, score, `finished`, `fromCache`, `analysisId` |
| `AnalysisSummary`, `FavoriteInfo` | Rows for the History and Favorites lists |
| `NetworkResult` | Data for My network (the model exists; the data source is Planned) |
| `ErrorType`, `FarolException` | Error model (section 3.5) |
| `ThemePref` | `SYSTEM`, `DARK`, `LIGHT` |

### 4.2 Check states

| State | Label on screen | Meaning | Effect on the score |
|---|---|---|---|
| `OK` | Good | The check passed | Counts with its points |
| `WARNING` | Warning | Partly satisfied | Counts with its points |
| `FAIL` | Critical | Not satisfied | Counts, usually with 0 points |
| `UNAVAILABLE` | Unavailable | The source had no data or could not be reached | Not counted: weight 0 |

### 4.3 Weights (`domain/ScoreWeights.kt`)

| Check | Weight | Applies to |
|---|---|---|
| SSL certificate | 25 | domains |
| Domain age | 20 | domains |
| Security headers | 20 | domains |
| Email anti-spoofing | 20 | domains |
| Port exposure | 15 | domains and IPs |
| IP owner | 0 (informational) | IPs |
| Reputation, VirusTotal (Planned) | 15 | domains |

The five domain weights add up to 100. Because the score is normalized (4.5), adding the
planned sixth check does not require changing the other weights.

### 4.4 Evaluator criteria, exactly as coded

**Certificate** (`CertificateEvaluator`, weight 25). Rows are evaluated from top to bottom.

| Condition | State | Points | Summary |
|---|---|---|---|
| An SSL error text is present | FAIL | 0 | Chosen from the error text (see below) |
| No HTTPS and no error text | FAIL | 0 | "No secure connection (HTTPS)" |
| HTTPS succeeded, expiry date unknown | OK | 25 | "Valid" |
| Expired (days remaining below 0) | FAIL | 5 | "Certificate expired" |
| 0 to 7 days remaining | WARNING | 5 | "Expires in N days" |
| 8 to 30 days remaining | WARNING | 15 | "Valid, expires in N days" |
| More than 30 days remaining | OK | 25 | "Valid, expires in N days" |

The error text is the class names and messages of the whole exception cause chain
(`describeError`), lowercased. It maps to "Certificate expired" if it contains `expir`; to
"The certificate does not match the site" if it contains `hostname` or `not verified`; to
"Certificate not trusted" if it contains `trust`, `self` or `certification path`; otherwise
to "Invalid certificate". On a real device an expired or untrusted certificate normally
fails the TLS handshake, so it arrives through the first row with 0 points; the "expired"
row with 5 points is a defensive branch for a response whose certificate date is in the past.

**Domain age** (`AgeEvaluator`, weight 20). Input: whole days since the oldest RDAP
`registration` event.

| Days since registration | State | Points |
|---|---|---|
| No date available | UNAVAILABLE | not counted |
| More than 365 | OK | 20 |
| 90 to 365 | WARNING | 12 |
| 30 to 89 | WARNING | 6 |
| Fewer than 30 | FAIL | 0 |

**Security headers** (`HeadersEvaluator`, weight 20). A header counts when it is present
with a non-blank value; names are compared case-insensitively; values are not judged.

| Header | Points |
|---|---|
| Strict-Transport-Security | 6 |
| Content-Security-Policy | 5 |
| X-Content-Type-Options | 3 |
| X-Frame-Options, or `frame-ancestors` inside the CSP | 3 |
| Referrer-Policy | 3 |

State: 16 points or more is OK, 8 to 15 is WARNING, below 8 is FAIL. The detail text lists
each missing header with what it is for. Headers are evaluated only when the TLS connection
succeeded; otherwise the check is UNAVAILABLE.

**Email anti-spoofing** (`EmailEvaluator`, weight 20).

| Condition | Points |
|---|---|
| SPF: a TXT record that starts with `v=spf1` | 8 |
| DMARC: a TXT record at `_dmarc.<host>` that starts with `v=DMARC1` | 6 |
| The DMARC policy tag is `p=quarantine` or `p=reject` | 6 |

State: 20 is OK, 0 is FAIL, anything between is WARNING (shared rule `stateForPoints`).
Matching ignores case and surrounding spaces; the `p=` tag is read, `sp=` is ignored. The
lookup tries the host and then its parent domains (at most two levels up). If a TXT lookup
failed and no record was found, the check is UNAVAILABLE instead of FAIL. MX servers (up to
five) are appended to the detail as information and never change the points.

**Port exposure** (`ExposureEvaluator`, weight 15). Input: ports and CVE ids that InternetDB
lists for the IP (for a domain, its first IPv4 address).

- Any CVE listed: FAIL with 0 points.
- Otherwise points = max(0, 15 - 3 x number of distinct ports other than 80 and 443).
- State by `stateForPoints`: 15 is OK, 0 is FAIL (five or more extra ports), else WARNING.
- No data for the IP (HTTP 404) means empty lists: 15 points, "No known open ports".
- UNAVAILABLE when the domain has no IPv4 address or InternetDB answers with an error.

**IP owner** (`IpOwnerEvaluator`, weight 0). OK with the summary "Name (country)" when RDAP
gives a registrant or network name, otherwise UNAVAILABLE. It never affects the score.

### 4.5 Normalization formula and worked example

```
score = round( sum of points of counted checks / sum of weights of counted checks x 100 )
```

A check is counted when its state is not UNAVAILABLE and its weight is above 0. Points are
clamped to the range 0..weight. With no counted checks the score is `null`: the screens show
a dash and the report says "not enough data". Rounding is to the nearest integer, halves up.

```kotlin
val counted = checks.filter { it.state != CheckState.UNAVAILABLE && it.weight > 0 }
val weights = counted.sumOf { it.weight }
if (weights <= 0) return null
val points = counted.sumOf { it.points.coerceIn(0, it.weight) }
return (points.toDouble() / weights * ScoreWeights.SCALE).roundToInt()
```

Worked example: a `.pt` shop whose registry publishes no RDAP record.

| Check | Finding | Points | Weight counted |
|---|---|---|---|
| Certificate | valid, 60 days remaining | 25 | 25 |
| Domain age | no RDAP data: UNAVAILABLE | - | - |
| Security headers | HSTS, X-Content-Type-Options and X-Frame-Options present | 12 | 20 |
| Email | SPF present, DMARC with `p=none` | 14 | 20 |
| Exposure | ports 22, 80 and 443 known | 12 | 15 |
| Total | | 63 | 80 |

score = round(63 / 80 x 100) = round(78.75) = 79, in the Warning band. Counting the missing
age as zero would give 63, and counting it as full would give 83. Normalizing over the
available weight avoids both distortions: an unavailable check neither helps nor hurts.

IP example: `8.8.8.8` with known ports 53 and 443 gets 12 of 15 for exposure; the IP owner
has weight 0; the score is round(12 / 15 x 100) = 80.

### 4.6 Score bands

| Score | Label | Defined in |
|---|---|---|
| 80 to 100 | Good | `stateForScore` in `ui/components/ScoreGauge.kt` |
| 50 to 79 | Warning | same |
| 0 to 49 | Critical | same |

### 4.7 Input normalization (`TargetNormalizer`)

- Trim, lowercase, remove `http://` or `https://`, cut at the first `/`, `?`, `#` or `:`
  (port), remove one trailing dot.
- Four decimal octets from 0 to 255 without leading zeros make an IPv4 target.
- Otherwise the host goes through `IDN.toASCII` (accented names become punycode) and must be
  a valid domain: at most 253 characters, at least two labels, each label at most 63
  characters of letters, digits and inner hyphens, and a top-level label of at least two
  letters or one starting with `xn--`.
- Anything else returns `null`: empty text, `abc`, `localhost`, spaces, underscores, IPv6.

---

## 5. Data sources

| Source | Request | Used for | API key |
|---|---|---|---|
| Google Public DNS, JSON API | `GET https://dns.google/resolve?name={name}&type={type}` with type A, TXT or MX | A: the domain exists, and its first IPv4. TXT: SPF on the host, DMARC on `_dmarc.{host}`. MX: information only | none |
| Shodan InternetDB | `GET https://internetdb.shodan.io/{ip}` | Known open ports and CVE ids of an IP | none |
| RDAP through rdap.org | `GET https://rdap.org/domain/{domain}` and `GET https://rdap.org/ip/{ip}`, with `Accept: application/rdap+json, application/json` | Registration date (domain age); registrant or network name and country (IP owner) | none |
| The analyzed site | `GET https://{domain}` with OkHttp directly | Certificate issuer and expiry from the TLS handshake; security headers from the response | none |
| ipify (Planned) | `GET https://api.ipify.org?format=json` | Public IP for My network | none |
| VirusTotal (Planned) | `GET https://www.virustotal.com/api/v3/domains/{domain}` with header `x-apikey` | Reputation counts in `last_analysis_stats` | yes, a free personal key |

Special behaviors handled in the code:

- **dns.google.** `Status` 3 means NXDOMAIN and aborts the analysis with "Domain not found".
  TXT values arrive inside quotes and long records arrive split into quoted pieces;
  `cleanTxt()` removes the quotes and joins the pieces. An answer can contain other record
  types (for example CNAME), so records are filtered by numeric type and the IPv4 text is
  validated.
- **InternetDB.** HTTP 404 means "no data about this IP", not an error. Retrofit functions
  return `Response<T>` so the status code can be read.
- **RDAP.** rdap.org redirects to the registry responsible for the name and OkHttp follows
  the redirect. RDAP only knows registrable domains, so after a miss the lookup moves up to
  the parent domain (at most two levels, always keeping two labels). A final 404, a 429 or a
  missing date gives UNAVAILABLE without penalty. The date is read from `events[]` where
  `eventAction` is `registration`; three date formats are accepted and the oldest date wins.
  The IP owner is the registrant entity's jCard name (`fn`, then `org`, nested entities
  included), falling back to the network `name`.
- **Site request.** An `SSLException` is a result, not a crash. If the site redirected to
  another host, the certificate detail says so, because the certificate and headers analyzed
  belong to the final host.
- **Rate limits.** HTTP 429 from any Retrofit API becomes `FarolException(RATE_LIMITED)` in
  `bodyOrNull()`; section 3.5 describes when it aborts and when it only disables one check.

An analysis of an apex domain such as `github.com` makes seven calls (A, TXT, DMARC TXT, MX,
RDAP, InternetDB and the site request) plus any redirects; a subdomain adds parent-domain
lookups.

Terms and courtesy. The notes below are recorded in the specification and must be re-checked
before delivery: InternetDB is free for non-commercial use and is refreshed weekly; rdap.org
is a redirector and each registry applies its own limits; dns.google needs no key but should
be used sensibly; the VirusTotal public API allows 4 requests per minute and 500 per day, for
non-commercial use only. The code is polite by construction: an identifiable `User-Agent`
(`Farol/1.0 (ISMAI academic project)`), a 10-minute cache, a "Re-analyze" button that is
disabled while an analysis runs, and a sequential favorites re-check.

Why no API keys are needed. Every implemented source is an open, read-only endpoint that
requires no account. This was chosen for reproducibility: there is nothing secret in the
repository and nothing to configure on another machine, so "clone and run" is enough. The
only planned exception is VirusTotal, whose key will live in the untracked `local.properties`
and reach the code through `BuildConfig`; with a blank key the check will simply be
UNAVAILABLE and the rest of the app will work as before.

---

## 6. Decision log

Each row gives the context, the decision, the alternatives considered and the reason.

### 6.1 Architecture and process

| # | Decision | Context | Alternatives considered | Why |
|---|---|---|---|---|
| A1 | MVVM with a repository | Screens need state that survives rotation while slow network results arrive | Logic inside composables or the activity; MVI with reducers; a full use-case layer | The ViewModel outlives configuration changes, so a running analysis continues when the screen rotates. Composables only draw. One repository interface is enough at this size; a class per use case would add files without adding behavior |
| A2 | Manual dependency injection in `AppContainer`, no Hilt | About ten objects to wire: one HTTP client, three Retrofit APIs, the HTTP checker, the checkers, the store and two repositories | Hilt; Koin | The whole object graph is readable in one file of about 45 lines and easy to explain. No annotation processor and no extra Gradle plugin whose version must match Kotlin, which protects the build on the university PC. Replacing an implementation is a one-line change |
| A3 | Repository interfaces in `domain`, implementations in `data` | The UI was built before the network code existed | UI depending on concrete classes | Screens and ViewModels import only `domain` types, so the fake repository used in phase 2 was replaced by the real one without touching the UI. The same seam will take the cloud store and the DataStore settings |
| A4 | Deliver in phases: UI on fake data first, then one data source per phase | Fixed deadline; the specification requires every phase to end with an app that compiles and runs | Backend first (nothing to show until late); one complete vertical feature at a time | Building all seven screens first fixed the contracts (models, repository interface, routes) early. Every phase can be demonstrated, and if time runs out the last finished phase is a valid delivery. Stand-ins still in place: `FakeSettingsRepository`, `InMemoryAnalysisStore`, the `myNetwork()` placeholder |
| A5 | Pin tool versions and commit the Gradle wrapper | The project must build on the university PC, which runs Android Studio Quail 3 2026.1.3 (per the specification it accepts AGP up to 9.3) | Latest versions; accepting the AGP Upgrade Assistant mid-project | AGP 9.3.0, Gradle 9.5.0 and Kotlin 2.4.10 are fixed in the version catalog and the wrapper, so every machine uses the same toolchain. A newer AGP would not open in the pinned Studio |
| A6 | English for all repository content | The specification is in Portuguese and names classes in Portuguese (`CalculadoraScore`, `Verificacao`) | Portuguese identifiers as in the specification; Portuguese UI with English code | One language across identifiers, comments, UI strings, tests and documents, the same language as the platform and library APIs the code sits next to. UI strings are in the default `values/` folder, so a `values-pt` translation can be added later without code changes |

### 6.2 Networking

| # | Decision | Context | Alternatives considered | Why |
|---|---|---|---|---|
| N1 | One shared `OkHttpClient` | Three Retrofit services with different base URLs, plus the raw checker | One client per API | One connection pool and one place for the User-Agent, the timeouts and the debug logging |
| N2 | User-Agent and timeouts set in one interceptor that reads `ConfigurableTimeout` on every request | Timeout is a user setting; API operators should be able to identify the client | Fixed timeouts on the client builder, which would require rebuilding the client when the setting changes | The value can change at run time without recreating the client; an overall `callTimeout` of 60 s is a hard ceiling. The default is 10 s. The link from the settings screen to this holder is still Planned |
| N3 | Retrofit with Gson for JSON APIs, raw OkHttp for certificate and headers | The certificate is in the TLS handshake object, not in a response body | Retrofit for everything (no clean access to the handshake); a separate TLS socket for the certificate plus a second request for the headers | One request gives both checks: `response.handshake.peerCertificates` and the response headers. That is half the traffic to the target, both results describe the same response, and it is the request a browser would make |
| N4 | Retrofit functions return `Response<T>` | Status codes carry meaning: InternetDB 404 is "no data", 429 is a rate limit | Plain body types and catching `HttpException` | Status handling is explicit in one helper (`bodyOrNull`), and exceptions are not used for expected outcomes |
| N5 | Checks run in parallel | Four independent tasks produce the five domain checks, each bounded by its own timeout | Sequential execution, where the worst case is the sum of all timeouts | Waiting time is about the slowest check. Checks are independent, so one failure does not stop the others. A `Mutex` is needed because tasks finish on different threads and update shared lists |
| N6 | Progressive results as `Flow<AnalysisProgress>` | A full analysis can take several seconds | A single suspend function returning the final result; one flow per check | The user sees cards fill in one by one instead of a blank spinner. One immutable snapshot type keeps the screen a pure function of state. `channelFlow` is used because emissions come from concurrent coroutines |
| N7 | Failures of one source are data: an UNAVAILABLE check with a reason | Third-party services fail, time out or have no data | Failing the whole analysis on any error; hiding failed checks | The user still gets the other results and can read why one is missing. Only no internet, a non-existent domain or a rate limit on the first lookup abort. An invalid certificate is the finding itself, so `SSLException` becomes a result |
| N8 | HTTP logging only in debug, through source sets | Logs help during development but must not ship | `if (BuildConfig.DEBUG)` with the library in every build | `logging-interceptor` is a `debugImplementation` dependency and `LogHttp` exists once in `src/debug` and once in `src/release` (returning null). The release APK does not contain the logging library at all. In debug the level is BASIC: request and response lines only, no headers or bodies |
| N9 | HTTPS only, no keys in implemented sources | A security app is judged on its own hygiene | Allowing cleartext; sources that need accounts | The manifest does not enable cleartext traffic and every base URL is HTTPS. Keyless sources keep the repository free of secrets |

### 6.3 Domain and user interface

| # | Decision | Context | Alternatives considered | Why |
|---|---|---|---|---|
| U1 | One `Check` model and one `CheckCard` for every check | Six kinds of check today, a seventh planned | One composable per check (certificate card, DNS card, and so on) | No duplicated UI. Adding a check means writing an evaluator and a checker function; no new composable is needed. `StateIcon` keeps the state-to-icon mapping in one place |
| U2 | Scoring and evaluation in pure Kotlin | The rules are the heart of the app and must be testable without an emulator | Logic inside ViewModels or the repository | `ScoreCalculator`, the evaluators, `TargetNormalizer`, `cleanTxt`, the report text and the parsers have no Android imports, so the 69 JUnit tests run on the JVM of any PC, without an emulator |
| U3 | Every weight and threshold in `ScoreWeights` | The scoring table has about thirty numbers | Literals inside each evaluator | No magic numbers; the scoring rules can be reviewed in one file and the tests refer to the same constants |
| U4 | Score normalized over available checks | RDAP is often missing and any API can be down | Counting an unavailable check as zero (punishes the site for a third-party outage) or as full (inflates the score) | An unavailable check neither helps nor hurts. A check can be added later, such as VirusTotal, without rebalancing the weights |
| U5 | Screen text in string resources, split per screen | Android practice; text should change without touching logic | Hard-coded strings in composables | All labels, buttons, error messages, the About text and the data-layer "unavailable" reasons are in `res/values/strings*.xml`; ViewModels expose resource ids. Exception, on purpose: titles, summaries and details produced by the evaluators and the shared report are English text built in the pure domain layer, which keeps that layer free of Android and directly testable (see section 11) |
| U6 | Sealed interfaces for screen state | Each screen has distinct loading, data and error situations | Several nullable fields or boolean flags | The compiler forces every state to be handled; impossible combinations cannot be represented |
| U7 | Material 3 dynamic color turned off | The app has its own identity: night blue with lighthouse amber | Dynamic color taken from the wallpaper on Android 12 and later | The identity stays the same on every device, and the state colors were chosen against these fixed backgrounds |
| U8 | Explicit edge-to-edge and theme handling in `MainActivity` | With `targetSdk` 36 the app draws behind the system bars; the theme preference arrives asynchronously | Manual inset padding on each screen; starting with a default theme and switching later | `Scaffold` handles insets on every screen; system bar icons follow the theme actually in use, even when the user forces a theme different from the system; drawing nothing until the preference arrives avoids a flash of the wrong theme |
| U9 | Routes carry only strings and ids | Result and Detail need an analysis that can be large | Passing whole objects through navigation arguments | Small arguments, one source of truth (the store), and the same route works for a fresh analysis and for a saved one |
| U10 | State is never shown by color alone | Accessibility requirement in the specification | Colored text or dots only | Icon, label and content description always accompany the color |

### 6.4 Data and build

| # | Decision | Context | Alternatives considered | Why |
|---|---|---|---|---|
| S1 | `AnalysisStore` abstraction; history, favorites data and cache derived by the repository | The persistence technology changed between specification versions and the cloud phase is not built yet | Keeping Room now and migrating later; storing derived fields (last score, changed flag) in a favorites table; building Firestore first | The store has six members, so the future cloud implementation is small and is the only class that changes. Derived values cannot drift out of sync with the analyses they come from. Trade-offs: nothing survives the process until the cloud store exists, and deleting history also removes what favorites can show |
| S2 | Room and KSP removed in favor of Firestore | The updated specification adds accounts and synchronization across devices | Room together with Firestore; Room only | The Firestore SDK already persists locally and synchronizes when the network returns, so Room next to it would duplicate the same responsibility. Removing KSP also removes a plugin whose version is tied to the Kotlin version |
| S3 | DataStore kept for device settings | Theme and timeout belong to the device, not to the account | Storing settings in Firestore; SharedPreferences | A device preference should not require a login or synchronize to another phone. The Flow-based API matches the existing `SettingsRepository` interface. Until it is implemented, `FakeSettingsRepository` holds the values in memory |
| S4 | Keep rules for DTOs and models in `proguard-rules.pro` | Gson and Retrofit read field names by reflection; R8 could rename or remove them | No rules; annotating every field | Without the rules a minified release would build and then fail to parse JSON only in the final APK. The rules keep `data.remote.dto.**` and `domain.model.**`. Minification is currently off (`isMinifyEnabled = false`), so the rules are a safeguard for the day it is turned on |
| S5 | `minSdk` 26 | The code uses `java.time` (`Instant`, `ChronoUnit`) | A lower `minSdk` with core library desugaring | `java.time` works without desugaring from Android 8.0 |
| S6 | Do not save an analysis in which every check is UNAVAILABLE | Such a result carries no information | Saving everything | Keeps history and the cache free of empty entries; a retry runs a real analysis instead of serving an empty cached one |

---

## 7. Testing

### 7.1 Unit tests

There are 69 JUnit 4 tests in 11 classes under `app/src/test/`. They need no emulator. The
most recent local run recorded in `app/build/test-results` (2026-11-11) reports 69 tests,
0 failures and 0 errors.

| Test class | Tests | What it covers |
|---|---|---|
| `ScoreCalculatorTest` | 8 | `null` for empty or weightless lists; 100 and 0 using the real evaluators; unavailable checks excluded, even when they carry a weight; rounding; IP with exposure only |
| `TargetNormalizerTest` | 8 | URL to host; scheme, port, query, fragment and trailing dot removed; IDN to punycode; valid and invalid IPv4; invalid inputs; IPv6 rejected; length limits |
| `CleanTxtTest` | 4 | Quotes removed; records split in two and three pieces joined; unquoted text unchanged |
| `ReportTest` | 2 | Report contains target, score, one line per check and the limitation notice; "not enough data" without a score |
| `CertificateEvaluatorTest` | 7 | Every day threshold (31, 30, 8, 7, 0, -1); no HTTPS and SSL errors give 0; readable summaries for expired, wrong host and untrusted; HTTPS without a date is valid |
| `AgeEvaluatorTest` | 3 | Thresholds (366, 365, 90, 89, 30, 29, 0); no date is unavailable; readable summaries |
| `HeadersEvaluatorTest` | 5 | All headers 20 OK; none 0 FAIL; 17 OK against 15 WARNING; 8 WARNING against 6 FAIL; `frame-ancestors` accepted; blank value ignored |
| `EmailEvaluatorTest` | 6 | 20, 14, 8, 12 and 0 point combinations; `sp=` not confused with `p=`; case and spaces tolerated |
| `ExposureEvaluatorTest` | 6 | No data 15; web ports 15; 3 points per extra port; floor at 0; duplicates counted once; any CVE gives 0 |
| `IpOwnerEvaluatorTest` | 3 | Name with and without country; no name is unavailable; weight 0 |
| `ParsersTest` | 17 | First valid IPv4 ignoring CNAME; TXT prefix match and cleanup; host hierarchy; MX names; days remaining; issuer name; error chain text; RDAP dates (three formats, oldest event, invalid, future); registrant name from jCard |

Not covered by automated tests: the orchestration in `AnalysisRepositoryImpl` (emissions,
cache, error mapping), the HTTP status handling in `Checkers`, the ViewModels and the Compose
screens. There is no instrumented test source set. `kotlinx-coroutines-test` is already
declared as a test dependency for future repository and ViewModel tests, but no test uses it
yet. These areas are verified manually.

### 7.2 Manual tests

The table adapts section 10.2 of the specification to what exists today. The expected
results follow from the code paths described above; they are checked by hand on an emulator
or a phone.

| Input or action | Expected result today | Applies today |
|---|---|---|
| `github.com` | High score; certificate valid; SPF and DMARC present | Yes |
| `expired.badssl.com` | Certificate card Critical, "Certificate expired"; headers Unavailable | Yes |
| `self-signed.badssl.com` | Certificate card Critical, "Certificate not trusted" | Yes |
| `wrong.host.badssl.com` | Certificate card Critical, "The certificate does not match the site" | Yes |
| `https://www.GitHub.com/explore` | Normalized to `www.github.com` | Yes (also a unit test) |
| `doesnotexist123abc.pt` | "Domain not found." with a "Try again" button | Yes |
| `8.8.8.8` | IP analysis: exposure card with the known ports, IP owner from RDAP, score from exposure only | Yes |
| A `.pt` domain | Domain age Unavailable; the score is computed without it | Yes |
| `abc` | Validation error under the field; no request is made. With an empty field the Analyze button is disabled | Yes |
| Airplane mode | "No internet connection..." with "Try again"; no crash. A target analyzed less than 10 minutes ago still opens from the cache | Yes |
| Rotate the screen during an analysis | The analysis continues; no check restarts | Yes |
| Same target twice within 10 minutes | The second result shows the cached-result notice; "Re-analyze" forces a fresh run | Yes, within one app session |
| Add a favorite, then "Re-check all" | Progress "Re-checking n of N"; a changed score or state is flagged | Yes, within one app session |
| Open My network and tap the button | Today: the generic error message. Planned: public IP and its exposure | Planned |
| VirusTotal rows (key present, key removed, five quick searches) | Reputation check; Unavailable without a key; rate-limit message | Planned |
| Account rows (duplicate registration, wrong password, two devices, sign out then back, delete account) | As in the specification | Planned |

---

## 8. Reproducibility

### 8.1 Requirements

| Requirement | Value | Defined in |
|---|---|---|
| Android Studio | Quail 3, 2026.1.3 | `README.md` |
| Android Gradle Plugin | 9.3.0 | `gradle/libs.versions.toml` |
| Gradle | 9.5.0, through the committed wrapper | `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin and Compose compiler plugin | 2.4.10 | `gradle/libs.versions.toml` |
| Gradle JDK | Android Studio's embedded JDK (JBR) | `README.md` |
| Gradle daemon JVM | toolchain version 25 | `gradle/gradle-daemon-jvm.properties` |
| Java and Kotlin bytecode target | 17 | `app/build.gradle.kts` |
| `compileSdk` and `targetSdk` | 36 (the Android 36 platform must be installed) | `app/build.gradle.kts` |
| `minSdk` | 26 (Android 8.0) | `app/build.gradle.kts` |
| Network | Needed for the Gradle sync and by the app itself | - |
| API keys | None today | - |

Library versions, all in the version catalog: Compose BOM 2025.12.01 with Material 3,
activity-compose 1.12.2, navigation-compose 2.9.6, lifecycle 2.10.0, core-ktx 1.17.0,
Retrofit 3.0.0 with the Gson converter, OkHttp 4.12.0, Gson 2.15.0, coroutines 1.11.0,
DataStore Preferences 1.3.0 (declared, not used yet) and JUnit 4.13.2. The module applies
only the Android application plugin and the Compose compiler plugin; Kotlin compilation
comes from AGP's built-in Kotlin support.

### 8.2 Build and run

1. `git clone` the repository.
2. In Android Studio choose File, Open, select the project folder and wait for the Gradle
   sync.
3. Run on an emulator (recommended in the README: a Pixel device with an x86_64 Google APIs
   image, API 36) or on a phone connected by USB cable.

From a terminal: `./gradlew :app:testDebugUnitTest` runs the unit tests without an emulator,
and `./gradlew :app:assembleDebug` builds the debug APK into `app/build/outputs/apk/debug/`.

### 8.3 What makes the build repeatable

- The Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) is in the repository, so
  every machine downloads the same Gradle version.
- All plugin and library versions are in `gradle/libs.versions.toml`; nothing uses a dynamic
  version.
- `settings.gradle.kts` uses `FAIL_ON_PROJECT_REPOS` with only `google()` and
  `mavenCentral()`.
- `gradle.properties` contains no machine-specific path (no `org.gradle.java.home`).
- `.gitignore` excludes `local.properties`, `.idea/`, `.gradle/`, build folders and APKs.

### 8.4 local.properties

`local.properties` holds only `sdk.dir`, the path of the Android SDK on the current machine.
Android Studio creates it and Git ignores it. Nothing else is needed today. The planned
VirusTotal check adds one line, `VIRUSTOTAL_API_KEY=...`, which the build script will copy
into a `BuildConfig` field (`buildConfig = true` is already enabled in the module). The key
must never be committed; a missing or blank key will only make that check UNAVAILABLE.

### 8.5 Points to confirm on a new machine

Taken from the specification's checklist: the API 36 platform is installed in the SDK
Manager; the network allows the Gradle sync and reaches the four services of section 5
(institutional firewalls sometimes block security-related domains such as Shodan); the
emulator starts, or a phone is available over USB; the emulator clock is right, because a
wrong clock makes every TLS certificate look invalid. Also confirm the JDK: the
specification mentions JBR 21, while the committed daemon-JVM file requests version 25,
which is what Gradle enforces (the file lists download locations Gradle can use if that JVM
is not installed).

---

## 9. Security, privacy and ethics

### 9.1 What leaves the device, and to whom

| Recipient | What it receives | When |
|---|---|---|
| Google Public DNS (dns.google) | The domain name, its parent domains and the matching `_dmarc.` names, in A, TXT and MX questions | Every domain analysis |
| Shodan InternetDB | One IPv4 address: the IP typed, or the first A record of the domain | Every analysis with a known IPv4 |
| rdap.org and the registry it redirects to | The domain (and up to two parent domains) or the IP | Every analysis |
| The analyzed site | One HTTPS GET of its front page | Every domain analysis |
| An app chosen by the user | The plain-text report | Only when the user taps Share |

Every request carries the `User-Agent` `Farol/1.0 (ISMAI academic project)` and, like any
internet request, reveals the device's IP address to the recipient. Nothing else is sent:
the app has no server of its own, no analytics, no crash reporting and, today, no accounts.
No request goes to ipify yet, although the in-app About text and the README already mention
it for the planned My network feature.

### 9.2 What is stored

Today nothing is written to disk by the app: analyses, favorites and settings live in memory
(`InMemoryAnalysisStore`, `FakeSettingsRepository`) and disappear with the process. The About
text ("stored only on this device") describes the first-version design and will be rewritten
with the cloud store. The user can delete one analysis, all history (with confirmation) and
any favorite.

### 9.3 Protections in the app

- Permissions: `INTERNET` and `ACCESS_NETWORK_STATE` only, both granted without a prompt.
- HTTPS everywhere; cleartext traffic is not enabled in the manifest.
- `android:allowBackup="false"`.
- HTTP logging exists only in debug builds, at BASIC level.
- No secrets in the repository.

### 9.4 Ethics

- Passive reconnaissance only, for the reasons given in section 1.3.
- Transparency: the "About and privacy" card in Settings names the third parties that see
  the searches and states the limitations.
- Honest limits: the Result screen always shows "A high score does not guarantee that a site
  is legitimate", and the shared report ends with the same notice.
- Plain language: each check carries an explanation written for a non-technical reader.

### 9.5 Planned: GDPR implications once accounts exist

With Firebase Auth and Firestore the privacy position changes, as the updated specification
states. These points are requirements for the planned phases, not features of today's code.

- An account email and the user's search history and favorites become personal data stored
  on Google Cloud and linked to an identity. The author decides what is stored and why, and
  is therefore responsible for it; Firebase acts as the processor.
- Data minimization: store only the email, the history and the favorites.
- Right to erasure: "Delete account" must remove both the Firebase Auth user and every
  document under `users/{uid}/`; removing only one leaves orphan data.
- Location: the Firestore database should be created in an EU region; the region cannot be
  changed later.
- Isolation: security rules must allow a user to read and write only below their own
  `users/{uid}/` path, and a `firestore.rules` file should be versioned in the repository
  and tested with two accounts.
- Transparency: the About text must say what is stored, where, why and how to delete it.
- Secrets: `google-services.json` identifies the Firebase project but grants no access by
  itself (the rules protect the data); the VirusTotal key is a real secret and stays in
  `local.properties`.

---

## 10. Roadmap

### 10.1 Delivered

The project is built in phases, each ending with an app that compiles and runs. The author
closes each phase with a commit; `git log` is the authoritative record of what has been
committed. The table lists the phases whose code is in the source tree.

| Phase | Content |
|---|---|
| 1 | Gradle project with pinned versions, Farol theme, domain models, initial README |
| 2 | All seven screens and the navigation, running on fake data |
| 3 | `ScoreCalculator`, the evaluators, `cleanTxt` and the domain unit tests |
| 4 | Real repository, DNS lookups, email check (SPF, DMARC, MX) |
| 5 | Exposure check (InternetDB) and IPv4 resolution of the domain |
| 6 | Certificate and security headers (`HttpChecker`) |
| 7 | RDAP: domain age and IP owner |

### 10.2 Remaining

The order of the remaining phases is being re-planned by the author. The one fixed point is
that VirusTotal comes last.

| Remaining work | Purpose |
|---|---|
| Firebase Auth | Accounts: one login and registration screen, an auth route with a navigation gate, a pure error-message mapper with unit tests |
| Firestore store | Replace `InMemoryAnalysisStore`: history that survives restarts and synchronizes across devices, the security rules file, account deletion |
| Synchronized favorites | Favorites kept in the same per-user store, so re-check results follow the account |
| My network | Public IP from ipify plus its InternetDB exposure, implementing `myNetwork()` behind the existing screen |
| DataStore settings | Persist theme and timeout on the device and connect the timeout to the HTTP client |
| Share and export polish | Improve the shared report |
| About and privacy text | Rewrite for the account-based design (section 9.5) |
| Final README | Final requirements, setup steps and manual test table |
| VirusTotal reputation check (last) | Sixth scored check, weight 15, using the key from `local.properties` |

Why VirusTotal is last: it is the only source that needs a secret key, which changes the
"clone and run" story; its free API is limited to 4 requests per minute; and everything else
is independent of it, because the score formula already tolerates a missing check. The
planned criteria from the specification are: no `malicious` and no `suspicious` detections
gives 15; no `malicious` but some `suspicious` gives 8; one or two `malicious` gives 3; more
than two gives 0.

---

## 11. Known limitations

About the result:

- A high score is not proof that a site is legitimate. A phishing site can have a valid
  certificate, good headers and correct email records. Farol supports a decision; it does not
  make it.
- A low score is not proof of fraud either: many honest sites lack security headers.
- Domain age is often Unavailable. Many registries, including many `.pt` domains, publish no
  RDAP registration date. The score is then computed without that check.
- InternetDB is a third-party snapshot, refreshed weekly and free for non-commercial use. The
  absence of ports or CVEs there does not mean there are none. For a site behind a CDN or on
  shared hosting, the data describes the shared IP, not the site.
- Only the first IPv4 address of a domain is used. IPv6 targets are rejected, and a domain
  without an A record gets no exposure check.
- Only the front page is requested. Headers are counted by presence, not by quality, and
  other pages of the site may differ. After a redirect, the certificate and headers analyzed
  belong to the final host.
- SPF, DMARC and RDAP lookups fall back to parent domains by removing labels, without a
  public suffix list. For SPF this is a simplification, since SPF is not inherited by
  subdomains.
- DNSSEC and Certificate Transparency logs are not checked.

About the current implementation:

- No persistence yet: history, favorites, the cache and the settings are lost when the
  process ends.
- The timeout option in Settings is not applied to the HTTP client; requests use 10 s.
- My network shows a generic error because its data source is not implemented.
- Some user-facing texts are ahead of the code: the About card and the README mention ipify
  and on-device storage.
- A site that refuses HTTPS connections is labelled "Invalid certificate". The dedicated
  wording "No secure connection (HTTPS)" exists in `CertificateEvaluator` but is not reached,
  because `Checkers` passes the reason through the SSL-error parameter. The points (0) and
  the state (Critical) are correct.
- A rate limit aborts the analysis only when it hits the first DNS lookup; elsewhere it
  disables a single check. For IP targets it never aborts.
- Texts produced by the evaluators (titles, summaries, details) and the shared report are
  English strings inside the domain layer, so they cannot be translated through Android
  resources without refactoring. The report date uses the `pt-PT` format.
- The release build type is not minified and no release signing is configured; the debug APK
  is the delivery artifact.
- No automated tests for the repository orchestration, the ViewModels or the screens.
