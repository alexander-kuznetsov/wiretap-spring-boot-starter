# Releasing wiretap to Maven Central

Wiretap is published to Maven Central via Sonatype Central Portal,
using the [`com.vanniktech.maven.publish`][vt] Gradle plugin.

[vt]: https://vanniktech.github.io/gradle-maven-publish-plugin/

The artifact coordinates are `io.github.alexander-kuznetsov:wiretap`.

---

## One-time setup

### 1. Sonatype Central Portal account

1. Sign in at <https://central.sonatype.com> with your GitHub account.
2. Verify the namespace `io.github.alexander-kuznetsov`. The portal
   gives a short verification challenge — create a public repository
   under your GitHub account with that name (Sonatype reads it once,
   the repository can be deleted afterwards). The wiretap repository
   itself does not need to be public for this step.
3. Generate a **User Token** (top-right avatar → *View Account* →
   *Generate User Token*). Two strings come out — username and
   password. These are not your GitHub credentials.

### 2. PGP signing key

Maven Central requires every artifact to be PGP-signed.

```bash
# 1. Generate a 4096-bit RSA key tied to your release email.
gpg --quick-gen-key 'Aleksandr Kuznetsov <suntey.kuznetsov@gmail.com>' rsa4096

# 2. Note the long key id.
gpg --list-secret-keys --keyid-format LONG

# 3. Publish the public key (Central validates against keys.openpgp.org).
gpg --keyserver keys.openpgp.org --send-keys <LONG_KEY_ID>

# 4. Export an armored private key for CI (DO NOT commit).
gpg --armor --export-secret-keys <LONG_KEY_ID> > signing-key.asc
```

Keep `signing-key.asc` somewhere safe (1Password, a flash drive, etc.).

### 3. GitHub Secrets

In *Settings → Secrets and variables → Actions* on the wiretap repo,
add:

| Name | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Sonatype User Token — username string |
| `MAVEN_CENTRAL_PASSWORD` | Sonatype User Token — password string |
| `SIGNING_KEY` | Full contents of `signing-key.asc` (including the `-----BEGIN PGP PRIVATE KEY BLOCK-----` lines) |
| `SIGNING_PASSWORD` | PGP key passphrase, if any (empty string if none) |

The `release-to-central` workflow reads these via
`ORG_GRADLE_PROJECT_*` env variables.

---

## Releasing a version

### 1. Bump the version

`build.gradle.kts` defaults to `version = "0.1.0-SNAPSHOT"`. Switch
to the release version:

```diff
- version = "0.1.0-SNAPSHOT"
+ version = "0.1.0"
```

### 2. Update CHANGELOG.md

Move the *Unreleased* section under a new dated heading.

### 3. Commit, tag, push

```bash
git commit -am "release: 0.1.0"
git tag v0.1.0
git push origin main v0.1.0
```

The `v*` tag triggers `.github/workflows/release.yml`, which runs
tests and then `./gradlew publishAndReleaseToMavenCentral`. The
Sonatype validation usually finishes within ten minutes; the artifact
appears at
`https://repo1.maven.org/maven2/io/github/alexander-kuznetsov/wiretap/0.1.0/`
within an hour.

### 4. Bump to the next snapshot

```diff
- version = "0.1.0"
+ version = "0.1.1-SNAPSHOT"
```

Commit and push to `main`. From now on snapshots flow back to GitHub
Packages via `.github/workflows/publish.yml`.

### 5. Manual workflow run (fallback)

If the tag was pushed before workflow secrets were ready, re-run
`release-to-central` manually:

* GitHub → Actions → *release-to-central* → *Run workflow* →
  enter the tag name (e.g. `v0.1.0`).

---

## Smoke-checking the released artifact

Once Central indexes the new version (search bar at
<https://central.sonatype.com>):

```bash
cd ~/Develop/logger-demo
# In build.gradle.kts:
#   implementation("io.github.alexander-kuznetsov:wiretap:0.1.0")
./gradlew clean bootRun
```

Make sure mavenCentral() is the source (no `mavenLocal()` for this check).

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `400 Bad Request` from upload step | POM missing field | Compare local POM (`./gradlew publishToMavenLocal` + open `wiretap-VERSION.pom`) against [Central requirements](https://central.sonatype.org/publish/requirements/). |
| `Signature verification failed` | Public key not on `keys.openpgp.org`, or `SIGNING_KEY` truncated | Re-publish public key; check the secret has the full PEM with newlines. |
| `Namespace not registered` | Step 1.2 not done yet | Repeat namespace verification in the Central UI. |
| `403 Forbidden` | User Token expired | Generate a new one in the portal and update `MAVEN_CENTRAL_PASSWORD`. |
| Local `publishToMavenLocal` succeeds but signing is `SKIPPED` | Expected without PGP env — local verification is fine without signature. |
