# Publishing releases

Lumentika publishes these Maven coordinates:

```text
com.antepod:lumentika-core:<version>
com.antepod:lumentika-ksp:<version>
```

Both publications contain the binary JAR, sources JAR, a Javadoc-classified JAR containing Dokka
HTML, POM, Gradle module metadata, and detached signatures. `app`, `utils`, and `buildSrc` are not
published.

## Continuous integration

`.github/workflows/ci.yml` runs `./gradlew clean build spotlessCheck` on every pull request and every
push to `master` with Java 25. Failed test reports are retained as workflow artifacts.

## One-time repository setup

Before the first release:

1. Verify the `com.antepod` namespace in the Maven Central Portal.
2. Create a Maven Central publishing token.
3. Create an ASCII-armored GPG signing key whose public key is published to a key server.
4. In the GitHub repository settings, create an environment named `maven-central`.
5. Add these secrets to that environment, not to repository-level Actions secrets:

```text
MAVEN_CENTRAL_USERNAME
MAVEN_CENTRAL_PASSWORD
MAVEN_SIGNING_KEY
MAVEN_SIGNING_PASSWORD
```

`MAVEN_SIGNING_KEY` is the complete ASCII-armored private key, including its BEGIN/END lines. To
print it for copying into the GitHub web form without creating a temporary file:

```bash
gpg --armor --export-secret-keys <fingerprint>
```

GitHub Packages uses the workflow `GITHUB_TOKEN`; no personal access token is required for this
repository's workflow. The publishing job explicitly references `maven-central`, so these secrets
are only exposed after the unprivileged verification job succeeds. Configure a required reviewer on
the environment when releases should wait for manual approval.

## Releasing

Create and push a semantic-version tag from a commit that passed CI:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The release workflow validates the tag and runs the complete release gate before entering the
protected environment. It then signs and publishes both modules to Maven Central, publishes the
same coordinates to GitHub Packages, and creates a GitHub Release containing binary, sources, and
API-documentation JARs. Maven Central deployment is released automatically after portal validation.

Tags that do not match `v<semver>` fail before publication. Versions are derived only from the tag;
the default local build remains `0.1.0-SNAPSHOT`.
