# Repository Guidelines

## Project Structure & Module Organization

The Iceberg of Magic is a Java 21 mod for Minecraft 1.21.4 and NeoForge 21.4.157. Production code lives in `src/main/java/com/efkrdnz/magical/`, organized into feature packages such as `magic`, `forge`, and `tower`, alongside `client`, `network`, and `registry`. Tests mirror these packages under `src/test/java/`. Textures, models, shaders, and translations belong in `src/main/resources/assets/magical/`; datapack content belongs in `src/main/resources/data/`. Mod metadata templates live in `src/main/templates/`.

## Build, Test, and Development Commands

Use JDK 21 and the repository's Gradle wrapper. From PowerShell:

- `.\gradlew.bat build` — compile, run tests, and package the mod into `build/libs/`.
- `.\gradlew.bat test` — run the JUnit test suite.
- `.\gradlew.bat runClient` — launch the development Minecraft client.
- `.\gradlew.bat runServer` — launch the development dedicated server without a GUI.
- `.\gradlew.bat runData` — generate resources into `src/generated/resources/`; review generated changes before committing.

On Linux/macOS, use `./gradlew` instead. Version and mod identity settings are in `gradle.properties`.

## Coding Style & Naming Conventions

Follow existing Java style: four-space indentation, opening braces on the declaration line, PascalCase class names, camelCase methods and fields, and UPPER_SNAKE_CASE constants. Use lowercase underscore-separated resource identifiers, such as `astral_step_slab`, within the `magical` namespace. Keep client-only dependencies isolated from code loaded by dedicated servers. No formatter or linter is configured; match surrounding code and avoid unrelated formatting changes.

## Testing Guidelines

Tests use JUnit Jupiter with NeoForge's unit-test integration. Mirror the production package, name files `*Test.java`, and use descriptive camelCase test methods. Add relevant regression tests for behavior changes, including boundary conditions. Run targeted tests with `.\gradlew.bat test --tests '*GlyphQualityTest'`, then the full suite as appropriate. No numerical coverage threshold is configured. Manually check affected gameplay and visuals in the client, and verify server compatibility for networking or shared-code changes.

## Commit & Pull Request Guidelines

Follow recent history: use prefixes such as `feat:` and `fix:` with concise imperative descriptions. Keep commits focused. PRs should describe the problem and resulting behavior, link related issues when applicable, and report automated and manual validation. Include screenshots for visible changes. GitHub Actions runs `./gradlew build` with Java 21 on pushes and pull requests; ensure it passes before merging.
