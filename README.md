# ResourceAcquisitionService

[![CI](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/ci.yml/badge.svg)](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/ci.yml)
[![Coverage](https://img.shields.io/badge/coverage-100%25-brightgreen.svg)](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/ci.yml)
[![GitHub Actions](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/actions.yml/badge.svg)](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/actions.yml)
[![CodeQL](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/codeql.yml/badge.svg)](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/codeql.yml)
[![Dependency Review](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/dependency-review.yml/badge.svg)](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/dependency-review.yml)
[![Dependency Submission](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/dependency-submission.yml/badge.svg)](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/dependency-submission.yml)
[![Publish](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/publish.yml/badge.svg)](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/workflows/publish.yml)

Small Java helper library for lock-style resource acquisition with automatic expiry. Version `2.0.0` targets JDK 25, RxJava 3, JUnit 6, Gradle, and OpenTelemetry metrics.

<!-- ci-status:start -->
## CI Status

| Build | Line Coverage | Branch Coverage | Instruction Coverage | Workflow Run |
| --- | ---: | ---: | ---: | --- |
| ✅ Passing | 100.00% | 100.00% | 100.00% | [#35](https://github.com/vtsyryuk/ResourceAcquisitionService/actions/runs/31697038999) |

Last updated from `master` at 2026-08-13 11:47 UTC for commit `9dac716`.
<!-- ci-status:end -->

## Build

This project uses Gradle 9.5.1 and JDK 25 by default. Dependency versions live in `gradle/libs.versions.toml`; build knobs such as `javaVersion` and `coverageMinimum` live in `gradle.properties` and can be overridden with `-P`.

```sh
./gradlew clean check
```

The CI workflow runs tests, enforces 100% JaCoCo instruction, branch, line, method, and class coverage, uploads the compiled jars and HTML/XML coverage reports as artifacts, and publishes a Gradle build scan. CodeQL, Dependency Review, Dependabot, Gradle dependency submission, and GitHub Actions workflow linting are enabled for supply-chain and workflow scanning.

## Coverage

The CI workflow enforces the configured `coverageMinimum`, currently `1.00`, for instructions, branches, lines, methods, and classes. Generate the full JaCoCo HTML/XML report locally with:

```sh
./gradlew jacocoTestReport
```

## Metrics

`SimpleResourceAcquisitionService` publishes OpenTelemetry metrics through `ResourceAcquisitionMetrics`:

- `ras.resource_acquisition.commands`
- `ras.resource_acquisition.results`
- `ras.resource_acquisition.active_locks`

Use the constructor that accepts an OpenTelemetry `Meter` to connect the service to your application's SDK/exporter pipeline.

## Publishing

GitHub Packages publishing runs from the `Publish` workflow when a GitHub release is created, or manually through `workflow_dispatch`.

```sh
./gradlew publishJarToMaven -PreleaseVersion=2.0.0
```

The publication includes `rasjava-2.0.0.jar`, `rasjava-2.0.0-sources.jar`, and `rasjava-2.0.0-javadoc.jar`.

## VS Code

The repository includes VS Code settings, tasks, and extension recommendations for Java and Gradle development.

## Deployment

This repository is a Java library, not a runnable service, so there is no cloud runtime to deploy directly. Publishing the package to GitHub Packages lets consuming applications deploy it from their own pipelines.
