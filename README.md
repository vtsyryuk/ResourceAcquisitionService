# ResourceAcquisitionService

[![CI](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/ci.yml/badge.svg)](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/ci.yml)
[![Demo E2E](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/demo-e2e.yml/badge.svg)](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/demo-e2e.yml)
[![Cloud E2E](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/cloud-e2e.yml/badge.svg)](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/cloud-e2e.yml)
[![CodeQL](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/codeql.yml/badge.svg)](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/codeql.yml)
[![SonarCloud](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/sonarcloud.yml/badge.svg)](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/sonarcloud.yml)
[![Dependency Review](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/dependency-review.yml/badge.svg)](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/dependency-review.yml)
[![Publish](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/publish.yml/badge.svg)](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/publish.yml)

Java library for exclusive resource acquisition with owner-controlled release, automatic expiry, and OpenTelemetry metrics. It uses JDK 25, RxJava 3 and JUnit 6.

## CI Status

| Check | What it verifies |
| --- | --- |
| CI | Library and demo HTTP tests, library JaCoCo coverage gate, runnable distribution |
| Demo E2E | Chromium against the deployable Docker image: competing users, release and expiry |
| SonarCloud | Separate Actions analysis, JaCoCo import, and enforced Sonar way quality gate |
| Cloud E2E | The same browser scenarios against a deployed service; requires its URL |

The library coverage gate remains 100% for instructions, branches, lines, methods and classes. The demo is a separate Gradle source set with HTTP integration and browser E2E tests; it is not included in the published library or its library coverage measurement. Workflow badges link to current runs rather than a fixed historical percentage.

## Project Links

- [SonarCloud project](https://sonarcloud.io/project/overview?id=vtsyryuk_ResourceAcquisitionService)
- [CI and coverage artifacts](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/ci.yml)
- [Local browser E2E](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/demo-e2e.yml)
- [Cloud E2E](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/cloud-e2e.yml)
- [Render Blueprint](render.yaml) and [Dockerfile](Dockerfile)

**Cloud setup required:** no hosted demo URL is configured yet. The runnable demo, deployment files, and cloud browser tests are included. Deploy the Blueprint and set `CLOUD_BASE_URL` before claiming a verified cloud run.

## Requirements

- Java 25; use the checked-in Gradle wrapper.
- Node.js 24 and Chromium for browser tests.
- Docker only for building or running the container.

Dependency versions live in [gradle/libs.versions.toml](gradle/libs.versions.toml). Build settings such as `javaVersion` and `coverageMinimum` live in [gradle.properties](gradle.properties).

## Build

```bash
./gradlew clean check installDist
```

The library JAR is `build/libs/rasjava-3.0.0.jar`. The demo JAR and launcher are included only in the runnable distribution at `build/install/ResourceAcquisitionService/`.

## Test and Coverage

```bash
./gradlew test jacocoTestReport
```

- HTML: `build/reports/jacoco/test/html/index.html`
- XML: `build/reports/jacoco/test/jacocoTestReport.xml`
- HTTP integration tests: `src/test/java/ras/demo/ResourceAcquisitionDemoTest.java`; expiry uses an RxJava `TestScheduler` to verify just-before/at-expiry behavior without sleeping.
- Browser scenarios: `tests/e2e/cloud-demo.spec.js`

## SonarCloud

GitHub Actions owns analysis and coverage reporting; Automatic Analysis is disabled. An analysis job waits for the quality gate and fails if the gate fails. Build/test CI remains independent. See [the setup guide](.github/SONARCLOUD.md) for credential, activation and untrusted-PR behavior.

## Basic Usage

```java
try (var service = new SimpleResourceAcquisitionService<String>(
        new TimeSpan(10, java.util.concurrent.TimeUnit.SECONDS))) {
    var acquired = service.commit(ResourceAcquisitionCommand.LOCK, "alice", "feed/prices");
    var competing = service.commit(ResourceAcquisitionCommand.LOCK, "bob", "feed/prices");
    var released = service.commit(ResourceAcquisitionCommand.UNLOCK, "alice", "feed/prices");
}
```

Import the classes from `ras`. The first call succeeds, the competing owner cannot acquire the same resource, and the current owner can release it. Otherwise the configured timeout releases it automatically.

### Migrating to 3.0

Java enum identifiers now use uppercase names: `LOCK`, `UNLOCK`, `LOCK_SUCCEEDED`, `LOCK_FAILED`, `UNLOCK_SUCCEEDED`, `UNLOCK_FAILED`, `LOCKED` and `UNLOCKED`. Update Java references and switch cases when upgrading from 2.x. The default library version is 3.0.0 because these identifier changes require recompilation.

Each enum retains its original external value through `getValue()` and `toString()`: for example, `LOCK.getValue()` returns `"Lock"`. Metric labels, demo JSON and resource display text keep their previous values. Java's `Enum.name()` and `Enum.valueOf()` use the new uppercase identifiers; use `getValue()` for the stable external string.

`TimeSpan` is now a record with `interval()` and `unit()` accessors; its constructor, `getInterval()`, `getUnit()` and `Default` remain available.

## Demo Deployment

The browser demo uses the actual library with simulated users `alice` and `bob`. It demonstrates acquisition conflicts, owner-only release and automatic expiry. It has no authentication and stores its demo state in memory.

Run locally:

```bash
./gradlew run
```

Open [localhost:8080](http://localhost:8080), or use the JSON API:

```bash
curl http://localhost:8080/health
curl -X POST 'http://localhost:8080/api/command?action=lock&user=alice&key=feed/prices'
curl -X POST 'http://localhost:8080/api/command?action=lock&user=bob&key=feed/prices'
curl -X POST 'http://localhost:8080/api/command?action=unlock&user=alice&key=feed/prices'
```

`PORT` defaults to `8080`; `DEMO_TTL_MILLIS` defaults to `10000`. The cloud tests expect the default ten-second expiry. Resource labels use up to 64 lowercase letters, digits, slashes, underscores and dashes, starting with a letter. Use a unique label when sharing a hosted demo.

### Cloud Deployment — User Setup Needed

1. Create a Render Blueprint from this repository using [render.yaml](render.yaml).
2. Keep `/health` as the health check, and keep `DEMO_TTL_MILLIS=10000` for the supplied expiry test.
3. Copy the actual service HTTPS URL into the GitHub repository variable `CLOUD_BASE_URL`.
4. Run **Cloud E2E**, or supply the URL through its `base_url` input.

No Render credentials or service creation are performed by this repository. The Blueprint selects a free demo service; cloud capacity and persistence are managed by the hosting account. This demonstration does not provide production authentication, persistence or distributed coordination.

### Browser E2E

Start the demo first, then run:

```bash
npm ci --ignore-scripts
./node_modules/.bin/playwright install chromium
PLAYWRIGHT_BASE_URL=http://localhost:8080 npm run test:e2e
```

For a hosted demo, replace the URL with its real deployment URL. The cloud workflow waits for cold-start readiness, then verifies browser interactions and expiry. Scheduled checks start only after `CLOUD_BASE_URL` is set. A manual run without a deployment URL fails with a setup error; it does not claim a cloud test passed.

## Metrics

`ResourceAcquisitionMetrics` publishes:

- `ras.resource_acquisition.commands`
- `ras.resource_acquisition.results`
- `ras.resource_acquisition.active_locks`

Use the constructor accepting an OpenTelemetry `Meter` to connect to a consuming application's exporter pipeline.

## Publish

GitHub Packages publishing runs when a release is created or through manual **Publish** dispatch:

```bash
./gradlew publishJarToMaven -PreleaseVersion=3.0.0
```

Provide GitHub Packages credentials through Gradle `gpr.user`/`gpr.key` properties or `GITHUB_ACTOR`/`GITHUB_TOKEN`. The published library, sources and Javadoc stay separate from the runnable demo.
