# Publishing

Lumentika publishes these Maven coordinates:

```text
com.antepod:lumentika-core:<version>
com.antepod:lumentika-ksp:<version>
```

Both publications contain the binary JAR, sources JAR, a Javadoc-classified JAR containing generated
Dokka HTML, POM, Gradle module metadata, and detached signatures. `app`, `utils`, and `buildSrc` are
not public artifacts.

## Continuous integration

`.github/workflows/ci.yml` runs `./gradlew clean build spotlessCheck` on every pull request and every
push to `master` with Java 25. Failed test reports are retained as workflow artifacts.

## Release configuration

Before the first release:

1. Verify the `com.antepod` namespace in the Maven Central Portal.
2. Create a Maven Central publishing token.
3. Create an ASCII-armored GPG signing key whose public key is published to a key server.
4. In the GitHub repository settings, create an environment named `maven-central`.
5. Add these secrets to that environment, not to repository-level Actions secrets:

```text
MAVEN_CENTRAL_USERNAME
MAVEN_CENTRAL_PASSWORD
SIGNING_KEY
SIGNING_PASSWORD
```

`SIGNING_KEY` is the complete ASCII-armored private key, including its BEGIN/END lines. GitHub
Packages uses the workflow `GITHUB_TOKEN`; no personal access token is required for publication
from this repository. The release job explicitly references `maven-central`, so GitHub exposes
these credentials only to that job. A required reviewer may be configured on the environment to
hold every tagged deployment for approval before any secret becomes available.

## Releasing

Create and push a semantic-version tag pointing at a commit that passed CI:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The release workflow validates the tag and completes the release gate in an unprivileged job. Only
after verification succeeds does the publishing job enter the protected `maven-central`
environment, receive its secrets, sign and publish both modules to Maven Central, publish the same
coordinates to GitHub Packages, and create a GitHub Release containing all binary, sources, and
generated API-documentation JARs. Maven Central deployment uses automatic release after portal
validation.

Tags that do not match `v<semver>` fail before publication. Versions are derived only from the tag;
the default local build remains `0.1.0-SNAPSHOT`.
