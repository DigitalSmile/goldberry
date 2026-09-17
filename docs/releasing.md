# Releasing

How Goldberry is versioned, published and released. The reasoning is in
[ADR-0333](../book/src/adr/0333-a-version-is-a-year-and-a-count.md) (versions),
[ADR-0334](../book/src/adr/0334-central-is-fed-once-per-run.md) (Maven Central) and
[ADR-0335](../book/src/adr/0335-the-showcase-is-a-package-and-example-yml-is-folded-in.md)
(the showcase on GitHub Packages),
[ADR-0336](../book/src/adr/0336-one-dependency-to-start-from-and-a-bom-to-line-up-the-rest.md)
(the BOM and the umbrella) and
[ADR-0337](../book/src/adr/0337-the-native-showcase-is-built-on-every-platform.md)
(the native showcase); this page is the checklist.

## Status

| Piece | State |
|---|---|
| Calendar versions, resolved in Gradle | **built**, tested (`CalendarVersionTest`, `BuildVersionTest`) |
| `goldberry.publish` — POMs, sources, javadoc, signing, classifier jars | **built**, rehearsed locally into a throwaway repository |
| `goldberry-bom` and the `goldberry` umbrella, `html`/`gpu` optional | **built**, resolved by a local consumer build |
| `snapshot.yml` → `publish.yml` → Central snapshots | **built, never run** — waits on the secrets below |
| `release.yml` → `publish.yml` → Central Portal deployment | **built, never run** |
| `showcase.yml` → GitHub Packages, runtime images | **built, never run** — needs nothing but a push |
| `showcase.yml` → GitHub Packages, GraalVM native images | **built, never run in CI** — see `book/src/status.md` for the local Linux run |
| `example.yml` | **retired**, folded into `showcase.yml` |
| Licence texts vendored (`checkLicenses -Pgoldberry.releaseCheck=true`) | **not done** — blocks the first release, not snapshots |

## Versions

`YEAR.RELEASE[.PATCH]`: `2026.1`, `2026.2`, `2026.2.1`. The release count starts at
1 each year. There is no `.0` patch.

`gradle.properties` holds the line being worked towards — `goldberryVersion=2026.1`
— and **never** `-SNAPSHOT`:

```sh
./gradlew -q :core:printVersion                    # 2026.1-SNAPSHOT
./gradlew -q :core:printVersion \
    -Pgoldberry.release=true -Pgoldberry.releaseTag=v2026.1   # 2026.1
```

A release tag that does not match the property fails the build before anything is
compiled.

## Where things go

| What | Where | When |
|---|---|---|
| `goldberry-{common,natives,core,widgets,html,gpu}`, `goldberry-bom`, `goldberry` — `-SNAPSHOT` | `https://central.sonatype.com/repository/maven-snapshots/` | every push to master (`snapshot.yml`) |
| the same, released | Maven Central | a `v*` tag (`release.yml`) |
| `goldberry-natives` classifiers `linux-x64`, `linux-aarch64`, `macos-aarch64`, `windows-x64` | beside `goldberry-natives` | with it |
| `goldberry-showcase` (jlink runtime image) classifiers `linux-x64` / `macos-aarch64` (`.tar.gz`), `windows-x64` (`.zip`) | GitHub Packages, `https://maven.pkg.github.com/DigitalSmile/goldberry` | every push to master and every `v*` tag (`showcase.yml`) |
| `goldberry-showcase-native` (GraalVM native image) classifiers `linux-x64` / `macos-aarch64` (`.tar.gz`), `windows-x64` (`.exe`) | the same | the same |

Consuming it — the BOM for the version, the umbrella for the toolkit, and the
optional modules by name:

```groovy
repositories {
    mavenCentral()
    maven { url = 'https://central.sonatype.com/repository/maven-snapshots/' }   // snapshots only
}
dependencies {
    implementation platform('io.github.digitalsmile:goldberry-bom:2026.1-SNAPSHOT')
    implementation 'io.github.digitalsmile:goldberry'                // common, natives, core, widgets
    implementation 'io.github.digitalsmile:goldberry-html'           // optional: Markdown and HTML
    runtimeOnly 'io.github.digitalsmile:goldberry-natives::linux-x64'
}
```

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.digitalsmile</groupId>
      <artifactId>goldberry-bom</artifactId>
      <version>2026.1</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
<dependencies>
  <dependency><groupId>io.github.digitalsmile</groupId><artifactId>goldberry</artifactId></dependency>
  <dependency><groupId>io.github.digitalsmile</groupId><artifactId>goldberry-gpu</artifactId></dependency>
</dependencies>
```

A new optional module is one line in `PublishedModules`
(`new Library("pdf", Inclusion.OPTIONAL)`) plus `id 'goldberry.publish'` in its
build script; the BOM and the umbrella pick it up.

Fetching a showcase image needs a GitHub token with `read:packages`, even though
the repository is public — that is how GitHub Packages' Maven registry works:

```sh
curl -L -u "$GITHUB_USER:$GITHUB_TOKEN" -O \
  https://maven.pkg.github.com/DigitalSmile/goldberry/io/github/digitalsmile/goldberry-showcase/2026.1/goldberry-showcase-2026.1-linux-x64.tar.gz
curl -L -u "$GITHUB_USER:$GITHUB_TOKEN" -O \
  https://maven.pkg.github.com/DigitalSmile/goldberry/io/github/digitalsmile/goldberry-showcase-native/2026.1/goldberry-showcase-native-2026.1-windows-x64.exe
```

## One-time setup

Done by a person, once. Nothing here can be checked from the repository.

1. **Central Portal namespace.** Sign in at <https://central.sonatype.com> with the
   GitHub account that owns `DigitalSmile`; `io.github.digitalsmile` verifies from
   it.
2. **Enable snapshots for the namespace** (Namespaces → the namespace → *Enable
   SNAPSHOTs*). Off by default; without it every snapshot upload is refused.
3. **A user token** (Account → *Generate User Token*). Its two halves are the
   secrets `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` — not the account
   password.
4. **A signing key.**
   ```sh
   gpg --quick-gen-key "Goldberry <maintainer@example.org>" rsa4096 sign 5y
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   gpg --armor --export-secret-keys <KEY_ID>      # -> secret SIGNING_KEY
   ```
   The passphrase is `SIGNING_KEY_PASSWORD`. Central checks the public key against
   the keyservers, so step two of this item is not optional.
5. **Repository secrets** (Settings → Secrets and variables → Actions):
   `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`,
   `SIGNING_KEY_PASSWORD`.
6. **Optional variable** `CENTRAL_AUTO_RELEASE=true`, once a few releases have been
   pressed through the Portal by hand. Until then a release stops there as a
   validated deployment.

GitHub Packages needs no setup: the workflow's own `GITHUB_TOKEN` writes to it.

Until step 5, snapshot runs rehearse into `mavenLocal` and say so in the run's
summary instead of failing.

## Cutting a release

1. **Rehearse.** Actions → *Release* → *Run workflow* on master. It builds every
   platform and runs the whole chain into `mavenLocal`, uploading nothing — javadoc,
   POMs, the classifier jars. Fix anything red before tagging.
2. **Vendor the licences** if not done: `./gradlew checkLicenses
   -Pgoldberry.releaseCheck=true` must pass. The release run enforces it.
3. **Tag the commit that declares the version**:
   ```sh
   git tag -a v2026.1 -m "Goldberry 2026.1"
   git push origin v2026.1
   ```
   `release.yml` publishes to a Portal deployment; `showcase.yml` publishes the
   images.
4. **Publish in the Portal** (Deployments → the deployment → *Publish*), unless
   `CENTRAL_AUTO_RELEASE` is on. Central takes a few minutes to an hour to sync.
5. **Bump master straight away** — `goldberryVersion=2026.2`. Until then master
   publishes `2026.1-SNAPSHOT`, which Maven orders *below* the release it follows.

## A patch

```sh
git switch -c release/2026.1 v2026.1
# fix, then set goldberryVersion=2026.1.1
git tag -a v2026.1.1 -m "Goldberry 2026.1.1" && git push origin release/2026.1 v2026.1.1
```

Patch branches publish no snapshots: `snapshot.yml` runs on master only.

## Rehearsing locally

The chain without any upload, with stand-in libraries:

```sh
for t in linux-x64:libgoldberry.so linux-aarch64:libgoldberry.so \
         windows-x64:goldberry.dll macos-aarch64:libgoldberry.dylib; do
  mkdir -p /tmp/art/${t%%:*} && echo stand-in > /tmp/art/${t%%:*}/${t#*:}
done
./gradlew publishToMavenLocal -Dmaven.repo.local=/tmp/m2 \
    -Pgoldberry.skipNative=true -Pgoldberry.artifactsDir=/tmp/art -x test
```
