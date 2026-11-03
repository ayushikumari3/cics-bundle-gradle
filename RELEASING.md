# Releasing

This repository contains two Gradle plugins, `com.ibm.cics.bundle` and `com.ibm.cics.appdeploy`. They are one Gradle project with one version in `build.gradle.kts`, so every release includes both.

## Prerequisites

- Write access to this repository
- The `version` in `build.gradle.kts` on `main` must be a `-SNAPSHOT` version (e.g. `1.0.14-SNAPSHOT`)

## Steps

1. **Trigger the release workflow** — go to Actions → Prepare Release → Run workflow, or run:
   ```bash
   gh workflow run prepare-release.yml
   ```
   This runs `./gradlew setReleaseVersion` and opens a PR on a `release/<version>` branch. The PR strips `-SNAPSHOT` from `build.gradle.kts` and sets the plugin version (`id 'com.ibm.cics.bundle'` and `id 'com.ibm.cics.appdeploy'`) in `README.md` and in the `build.gradle` of each sample listed in the `setReleaseVersion` task.

2. **Review and merge the PR** — merging triggers the existing build and publish via `gradle-build.yml`, which runs `./gradlew build publishAll` on `main`.

3. **Publish to Maven Central and the Gradle Plugin Portal** — for a release (non-SNAPSHOT) version, `publishAll` publishes to both automatically: Maven Central through JReleaser (`jreleaserFullRelease`) and the Gradle Plugin Portal through `publishPlugins`. `build.gradle.kts` does not set a JReleaser `stage`, and in JReleaser 1.25.0 an unset stage becomes `FULL` (upload and publish), so this build has no manual publish step on central.sonatype.com. This was read from the JReleaser 1.25.0 jars and has not been confirmed by a real release. For a `-SNAPSHOT` version `publishAll` only publishes to the local `build/staging-deploy` directory.

4. **Automation takes over** — `post-release.yml` runs when `build.gradle.kts` changes on `main` and the version is not a SNAPSHOT. It:
   - Computes the next patch SNAPSHOT (e.g. `1.0.14` → `1.0.15-SNAPSHOT`)
   - Runs `./gradlew setVersion` and opens a `develop/<version>` PR

   `post-release.yml` has no step that creates a GitHub Release.

5. **Merge the next-dev PR** — no rush, merge when ready.

## Adding a sample that names the plugin version

`setReleaseVersion` only updates the files listed in `build.gradle.kts`. If you add a sample whose `build.gradle` has `id 'com.ibm.cics.bundle' version '...'` or `id 'com.ibm.cics.appdeploy' version '...'`, add its path to that list, or it keeps the old version after a release.
