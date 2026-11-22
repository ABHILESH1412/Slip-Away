# Releasing Slip Away

Release APKs are built by GitHub Actions ([.github/workflows/android.yml](../.github/workflows/android.yml)).
You run one script. GitHub builds the APK, signs it, and attaches it to a GitHub release.

- **Publishing a release:** GitHub builds the signed, shrunk release APK (about 2.5 MB). It attaches it to the release as `slipaway-<version>.apk`.
- **Pushing to `main` or opening a pull request:** GitHub builds a debug APK and runs lint, to catch mistakes. The APK is kept for 7 days under the run's *Artifacts* (`slipaway-debug`).

## One-time setup

1. **Install and log in to the GitHub CLI:** <https://cli.github.com>, then:

   ```bash
   gh auth login
   ```

2. **Create the release signing key** (once, ever):

   ```bash
   bash scripts/make-release-key.sh
   ```

   It asks for a password and writes `~/slipaway-release.jks`. **Back up the file and the password.** Every release must be signed with this same key. Without it, phones refuse to install updates over the old version, and people have to uninstall first, which loses their callers and setup.

3. **Add two repository secrets** (*Settings → Secrets and variables → Actions → New repository secret*):

   | Name | Value |
   |---|---|
   | `SLIPAWAY_KEYSTORE_BASE64` | output of `base64 -w0 ~/slipaway-release.jks` |
   | `SLIPAWAY_STORE_PASSWORD` | the password you typed in step 2 |

   Or with the GitHub CLI, from this folder:

   ```bash
   base64 -w0 ~/slipaway-release.jks | gh secret set SLIPAWAY_KEYSTORE_BASE64
   ```

   ```bash
   gh secret set SLIPAWAY_STORE_PASSWORD
   ```

4. **Check that everything is ready:**

   ```bash
   scripts/release.sh --dry-run
   ```

## Making a release

Commit your changes, then run one of:

```bash
scripts/release.sh          # patch: 0.2.1 -> 0.2.2 (fixes)
```

```bash
scripts/release.sh minor    # minor: 0.2.2 -> 0.3.0 (new features)
```

```bash
scripts/release.sh major    # major: 0.3.0 -> 1.0.0 (big changes)
```

```bash
scripts/release.sh 1.4.0    # exactly this version
```

The script:

1. refuses if you have uncommitted changes, you aren't on `main`, or `main` is behind GitHub;
2. checks that the two signing secrets exist;
3. works out the next version from the latest `v*` tag, shows it, and asks you to confirm;
4. pushes any unpushed commits and creates the GitHub release, with notes generated from the commits;
5. waits for the *Android app* workflow and prints the APK's download link. Ctrl+C stops watching, but the build carries on.

Options:

- `--dry-run`: does all the checks and shows the version, but publishes nothing.
- `--yes`: skips the confirmation question.
- `--no-wait`: doesn't wait for the build.

### How the version number works

The release tag sets the version. Nothing in the code needs editing, and there's no "bump version" commit:

| Tag | versionName | versionCode |
|---|---|---|
| `v0.2.1` | `0.2.1` | `201` |
| `v0.3.0` | `0.3.0` | `300` |
| `v1.0.0` | `1.0.0` | `10000` |

versionCode is major × 10000 + minor × 100 + patch, so minor and patch must stay below 100. Android only installs a higher versionCode as an update, and the script refuses to release a version that isn't higher than the last one.

For the very first release there are no tags yet, so the script counts on from the fallback version in `app/build.gradle.kts` (`0.2.0`). A plain `scripts/release.sh` gives `v0.2.1`. Local builds without a tag also use that fallback.

### By hand

Doing it by hand means pushing `main`, then:

```bash
gh release create v0.2.1 --generate-notes
```

The release's tag must point at a commit that contains `.github/workflows/android.yml`, or nothing gets built.

## How phones get the update

Installed copies of Slip Away check the latest GitHub release about twice a day, and when the app is opened. They download `slipaway-<version>.apk` and install it, unless the user has turned off **Settings → Updates → Automatic updates**. The updater finds the APK by its name, so keep the `slipaway-<version>.apk` naming that the workflow uses.

Phones only pick up a release once its APK is attached. A release whose build is still running, or failed, is skipped until the APK is there.

A fork that publishes its own releases should change `UPDATE_REPO` in `app/build.gradle.kts` to its own `owner/repo`.

## Installing on a phone

On the phone, download `slipaway-<version>.apk` from the release and open it. The first time, Android asks you to allow installs from your browser.

**If you've been running a build from Android Studio or `./gradlew installDebug`:** that build is signed with your computer's debug key, and the release is signed with the release key. Android won't install one over the other. Uninstall the old one first. That removes your callers and setup, so you'll go through the one-time setup again. Later releases install over each other normally.

## Building a release locally

You rarely need to. Without the signing environment variables, the APK is signed with the debug key, which is fine for testing but never for sharing:

```bash
./gradlew :app:assembleRelease -PversionName=0.2.1
```

To sign it with the real key:

```bash
SLIPAWAY_KEYSTORE=~/slipaway-release.jks SLIPAWAY_STORE_PASSWORD='…' ./gradlew :app:assembleRelease -PversionName=0.2.1
```
