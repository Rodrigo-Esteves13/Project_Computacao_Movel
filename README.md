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
- Accounts (email and password) are handled by Firebase Authentication; the account can be deleted in Settings.
- History and favorites are stored per account in Cloud Firestore (EU region), readable only by their owner (`firestore.rules`), and are removed when the account is deleted.
- A high score does not guarantee that a site is legitimate.

## Architecture
MVVM + Repository, manual dependency injection (`AppContainer`). Checks run in parallel; the score and evaluation logic lives in `domain/` (pure Kotlin, tested with JUnit).

## Firebase setup (accounts)
Sign-in needs a Firebase project: create it in the Firebase console, enable Authentication with the Email/Password provider, add an Android app with the id `pt.ismai.farol` and put the downloaded file at `app/google-services.json`. Then enable Cloud Firestore (production mode, an EU location) and publish the rules in `firestore.rules`. The file is ignored by git because it holds an API key: restrict that key in Google Cloud to the Android package `pt.ismai.farol` and the SHA-1 of your signing key (`./gradlew signingReport`); each machine or keystore needs its SHA-1 added. Without that file the app still builds and runs, but with no sign-in screen (no accounts).

## Release build
`./gradlew :app:assembleRelease` produces a minified (R8) and resource-shrunk APK. To sign it, create your own keystore and a file `keystore.properties` in the project root (both are ignored by git):

```
storeFile=farol-release.jks
storePassword=...
keyAlias=farol
keyPassword=...
```

Create the keystore with `keytool -genkeypair -v -keystore farol-release.jks -alias farol -keyalg RSA -keysize 2048 -validity 10000`. Without `keystore.properties` the release APK is built unsigned, which cannot be installed. If you use the Firebase login, add the release key's SHA-1 (`keytool -list -v -keystore farol-release.jks`) to the Android API key restriction in Google Cloud, or sign-in fails.

## VirusTotal (optional)
The reputation check asks VirusTotal whether its security engines flag a domain. It needs a free personal API key: create an account at virustotal.com, copy the key from your profile and add this line to `local.properties` (the file is ignored by git):

```
VIRUSTOTAL_API_KEY=your_key_here
```

Without the key the app works the same, and the reputation check shows as "Unavailable" and does not count toward the score. The free key is limited to 4 requests per minute and 500 per day. A key inside an APK can be extracted by anyone who gets the APK, so do not publish an APK built with your key; a public release would need a small backend that holds the key.
