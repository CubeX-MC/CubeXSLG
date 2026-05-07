# Repository Guidelines

## Project Structure & Module Organization
- `src/main/kotlin/io/github/adlamb/cubex/bootstrap`: plugin startup wiring and registration.
- `src/main/kotlin/io/github/adlamb/cubex/feature`: gameplay subsystems (`town`, `resource`, `building`, `combat`, `tech`, etc.).
- `src/main/kotlin/io/github/adlamb/cubex/{gameplay,menu,command,registry,config,database,message}`: core logic, UI, commands, config, persistence, and localization.
- `src/main/resources`: plugin and runtime assets (`plugin.yml`, `config.yml`, `database.yml`, `messages.yml`, `menu-items.yml`, `gameplay/*.yml`).
- `build.gradle.kts`: build toolchain, dependencies, and run settings.
- `build/`, `run/`, `.gradle/`, `.kotlin/`: generated artifacts and local server state.

## Build, Test, and Development Commands
- `./gradlew build` — compiles and creates the plugin distribution.
- `./gradlew shadowJar` — outputs `build/libs/CubeXSLG-<version>-all.jar`.
- `./gradlew runServer` — starts Paper 1.21.8 for local validation.
- `./gradlew clean` — removes built artifacts.
- `./gradlew test` — task exists, but no tests are currently defined.
- Use Java 21 (`kotlin { jvmToolchain(21) }`).

## Local API Source References
- For local source lookup (offline-friendly), prefer `~/文档/thirdparty/Folia` when available:
  - `paper-api` Java source: `~/文档/thirdparty/Folia/paper-api/src/main/java`
  - `paper-server` Java source: `~/文档/thirdparty/Folia/paper-server/src/main/java`
  - `folia-server` (patched Mojang sources): `~/文档/thirdparty/Folia/folia-server/src/minecraft/java`
- These are user-machine local paths, not project-owned sources; keep project reproducibility in mind if committing changes.
- When unavailable, fall back to local Maven artifacts:
  - `~/.m2/repository/dev/folia/folia-api/1.21.8-R0.1-SNAPSHOT/`
- For CLI workflows, read both local source files and built jars to avoid repeated internet lookup.

## Coding Style & Naming Conventions
- Kotlin 2.3, 4-space indentation, and existing idioms from nearby modules.
- `PascalCase` for classes, `camelCase` for members/functions, explicit `val`/`var` preference.
- Keep one feature per module package, one clear responsibility per class.
- Use explicit imports and avoid wildcard imports.
- Use kebab-case keys in YAML (`validate-database`) and keep display text in `messages.yml`.

## Testing Guidelines
- No automated tests are present yet; PRs should include manual verification.
- Minimum pre-merge checks: `./gradlew build` plus `./gradlew runServer`.
- At minimum exercise `/slg help`, `/slg create <name>`, `/slg wand <building>`, and one command from each touched feature.
- For persistence changes, verify data survives a restart.
- New tests should be added under `src/test/kotlin` with `*Test.kt` naming.

## Commit & Pull Request Guidelines
- Use Conventional Commit-style subjects (`feat:`, `refactor(menu):`, `fix:`).
- PR description should include changed modules, config/files touched, and verification steps.
- Add screenshots or concise logs for menu/GUI behavior changes.
- Do not include generated artifacts in code changes.

## Security & Configuration Tips
- `database.yml` has credential fields; never commit production secrets.
- Prefer H2 for local development, and document MariaDB test credential changes clearly when used.
