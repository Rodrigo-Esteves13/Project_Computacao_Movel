# Farol

> Look before you click.

Android app (Kotlin + Jetpack Compose) for passive reconnaissance of domains and IPs. It uses public data only (DNS, RDAP, InternetDB, a normal HTTPS request to the site). It does not do port scanning.

## Requirements
- Android Studio **Quail 3 | 2026.1.3** (or newer, but the project is pinned to this version)
- JDK: Android Studio's **Embedded JDK (JBR)** (*Settings → Build Tools → Gradle → Gradle JDK*)
- Versions pinned in `gradle/libs.versions.toml`: AGP 9.3.0, Gradle 9.5.0 (wrapper), Kotlin 2.4.10
- `minSdk` 26, `compileSdk`/`targetSdk` 36 (install the Android 36 platform in the SDK Manager)
- Needs internet access. No API keys are required.

## Running
1. `git clone` the repository
2. *File → Open* in Android Studio and wait for the Gradle sync
3. ▶ Run (recommended AVD: Pixel, x86_64 image with Google APIs, API 36) or a phone over USB cable

Unit tests (no emulator): `./gradlew :app:testDebugUnitTest`
Debug APK: `./gradlew :app:assembleDebug` (output in `app/build/outputs/apk/debug/`)

## Manual tests
| Input | Expected |
|---|---|
| `github.com` | High score; valid certificate; SPF + DMARC present |
| `expired.badssl.com` | Critical certificate (expired) |
| `self-signed.badssl.com` | Critical certificate (untrusted) |
| `wrong.host.badssl.com` | Critical certificate (name mismatch) |
| `https://www.GitHub.com/explore` | Normalized to `www.github.com` |
| `doesnotexist123abc.pt` | "Domain not found" |
| `8.8.8.8` | IP analysis: ports 53/443, owner via RDAP |
| `abc` / empty | Validation error, no request made |
| Airplane mode | "No internet connection", no crash |
| Rotate the screen mid-analysis | Analysis continues |
| Same target twice within 10 min | Result comes from the cache |

## Privacy and ethics
- Passive reconnaissance only (Portuguese Law no. 109/2009, art. 6): the app reads public data and does not probe third-party systems.
- Searched targets are sent to third parties: Google (DNS), Shodan (InternetDB), RDAP registries and ipify. The app has no server of its own.
- History and favorites currently live in memory on the device only and can be deleted at any time (this section is updated when cloud sync is added).
- A high score does not guarantee that a site is legitimate.

## Architecture
MVVM + Repository, manual dependency injection (`AppContainer`). Checks run in parallel; the score and evaluation logic lives in `domain/` (pure Kotlin, tested with JUnit).
