# Releasing Toto

Toto uses **reproducible builds** on F-Droid. F-Droid builds the app from source and checks that
its APK is byte-for-byte identical to the one we publish (ignoring the signature). If it matches,
F-Droid ships **our** signed APK. That means:

- F-Droid users and anyone installing from GitHub releases get the same signature, so they can switch between the two.
- Each release needs a signed APK attached to its GitHub release **before** F-Droid builds it.
- The reference APK must be built exactly the way F-Droid builds it. `scripts/release.sh` does
  this by running `fdroid build` in F-Droid's own buildserver container. Don't build release APKs
  in Android Studio for publishing: a different JDK or SDK can change the bytes.

## One-time setup

1. **Release key.** Create it once and keep it (and its password) safe forever. Losing it means
   you can never ship another update to existing installs.

   ```sh
   mkdir -p ~/keys
   keytool -genkeypair -v -keystore ~/keys/toto-release.jks -alias toto \
     -keyalg RSA -keysize 4096 -validity 36500
   ```

   Back up `~/keys/toto-release.jks` somewhere offline. Never commit it.

2. **Tools.** Docker, plus the F-Droid repos next to your home directory:

   ```sh
   git clone --depth=1 https://gitlab.com/fdroid/fdroidserver ~/fdroidserver
   git clone https://gitlab.com/<your-gitlab-user>/fdroiddata ~/fdroiddata   # your fork
   docker pull registry.gitlab.com/fdroid/fdroidserver:buildserver
   ```

## Each release

1. **Bump the version** in `app/build.gradle.kts` (`versionCode` +1, `versionName`), and add
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (500 characters max).
2. **Commit, tag, and push:**

   ```sh
   git commit -am "Release X.Y"
   git tag -a vX.Y -m "Toto X.Y"
   git push origin main vX.Y
   ```

3. **Add the build to your local fdroiddata** (on a scratch branch). Copy the previous `Builds`
   entry in `metadata/codes.shandon.toto.yml`, set the new `versionName`, `versionCode`, and
   `commit` (the **full** hash of the tagged commit: `git rev-parse vX.Y^{commit}`), and commit it.
   F-Droid's bot adds this entry upstream by itself; this local copy only drives the reference build.
4. **Build and sign:**

   ```sh
   scripts/release.sh <versionCode>
   ```

   This writes `release/Toto-X.Y.apk` and prints its signing certificate's SHA-256. That must
   match `AllowedAPKSigningKeys` in the F-Droid metadata.
5. **Publish:** create a GitHub release for `vX.Y` and attach the APK, named exactly
   `Toto-X.Y.apk` (the F-Droid metadata's `Binaries` URL expects that name).

F-Droid checks for new tags daily, opens its own update for the new version, rebuilds it, compares
it with the APK from step 5, and publishes our signed APK if they match.

## If F-Droid reports a mismatch

The build log on the app's F-Droid page shows the differences. Usual causes are a changed build
environment (a new buildserver image, so rebuild with `docker pull` first) or something
non-deterministic in the build. F-Droid's
[Reproducible Builds docs](https://f-droid.org/docs/Reproducible_Builds/) cover diagnosing with
`diffoscope`.
