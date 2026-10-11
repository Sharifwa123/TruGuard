# Releasing

Releases are built by GitHub Actions and published as **permanent GitHub Releases** (assets stay attached to the tag; unlike workflow artifacts they do not expire).

## Version rules
- `version.properties` is the only place a version is written: `VERSION_NAME=MAJOR.MINOR.PATCH` (an optional `-suffix` marks a pre-release).
- `versionCode = MAJOR*10000 + MINOR*100 + PATCH`, derived in Gradle, so it always increases with the version (supports minor/patch < 100).
- The release workflow aborts unless the pushed tag equals `v` + `VERSION_NAME`.

## Cut a release
```bash
# 1. edit version.properties (e.g. VERSION_NAME=1.0.1) and add a CHANGELOG.md entry, then
git commit -am "Release 1.0.1"
git tag v1.0.1
git push origin main v1.0.1
```
The `Release` workflow then runs engine and app unit tests, builds the APKs, writes `SHA256SUMS.txt`, and publishes the release with generated notes. Do not delete or move published tags.

## Signing (one-time setup, required for update-compatible releases)
Create a keystore **once** and keep it safe; losing it means users must uninstall to take later releases.
```bash
keytool -genkeypair -v -keystore release.jks -alias scammonitor -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks     # paste into the secret below
```
Add repository secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Never commit the keystore (`.gitignore` blocks `*.jks`/`*.keystore`).

Without these secrets the workflow still publishes, but as a **pre-release with debug-signed APKs** (named `…-debug.apk`) and a warning, because an unsigned release APK is not installable.

## Other workflows
- `ci.yml`: engine tests; Android unit tests, lint, debug APKs uploaded as 30-day workflow artifacts (named with version and commit).
- `instrumented.yml`: manual emulator run of the `androidTest` suite.
