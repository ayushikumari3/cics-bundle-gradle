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

3. **Publish to Maven Central and the Gradle Plugin Portal** — for a release (non-SNAPSHOT) version, `publishAll` also runs `jreleaserFullRelease` (JReleaser, configured in the `jreleaser` block of `build.gradle.kts` to deploy to Maven Central from `build/staging-deploy`) and `publishPlugins` (Gradle Plugin Portal). For a `-SNAPSHOT` version it only publishes to the local `build/staging-deploy` directory. The JReleaser `stage` is not set in `build.gradle.kts`. After a release, check that the new version appears on Maven Central and the Plugin Portal. If it is staged but not published on central.sonatype.com, publish it there as for the Maven plugins.

4. **Automation takes over** — `post-release.yml` runs when `build.gradle.kts` changes on `main` and the version is not a SNAPSHOT. It:
   - Computes the next patch SNAPSHOT (e.g. `1.0.14` → `1.0.15-SNAPSHOT`)
   - Runs `./gradlew setVersion` and opens a `develop/<version>` PR

   `post-release.yml` has no step that creates a GitHub Release.

5. **Merge the next-dev PR** — no rush, merge when ready.

## Adding a sample that names the plugin version

`setReleaseVersion` only updates the files listed in `build.gradle.kts`. If you add a sample whose `build.gradle` has `id 'com.ibm.cics.bundle' version '...'` or `id 'com.ibm.cics.appdeploy' version '...'`, add its path to that list, or it keeps the old version after a release.
