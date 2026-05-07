# CubeXSLG Project Overview

CubeXSLG is a modular Strategy/Simulation (SLG) plugin scaffold for Minecraft, specifically targeting Paper and Folia 1.21.8. It introduces a comprehensive gameplay system including town management, virtualized resource storage, schematic-based building construction, technology research, and resident AI.

## Technical Stack

- **Language:** Kotlin 2.3
- **Runtime:** Java 21 (JVM Toolchain 21)
- **Platform:** PaperMC / Folia 1.21.8-R0.1-SNAPSHOT
- **Database:** Exposed ORM with HikariCP (H2 for local, MariaDB supported)
- **UI:** Adventure MiniMessage for text, Custom Menu Factory for inventory GUIs
- **Build System:** Gradle (Kotlin DSL) with `shadowJar` and `run-paper`
- **Dependencies:** WorldEdit (Soft dependency for schematic loading)

## Architecture

The project follows a modular and service-oriented architecture:

- **`io.github.adlamb.cubex.bootstrap`**: Manages plugin lifecycle, dependency injection (via `PluginContext`), and module registration.
- **`io.github.adlamb.cubex.feature`**: Contains independent gameplay modules (e.g., `town`, `building`, `resource`, `tech`, `combat`). Each feature implements `FeatureModule`.
- **`io.github.adlamb.cubex.gameplay`**: The core engine. `GameplayFacade` acts as the primary API for all gameplay operations, orchestrating storage, registry, and world interactions.
- **`io.github.adlamb.cubex.menu`**: A robust GUI framework for rendering inventory-based menus defined in configuration.
- **`io.github.adlamb.cubex.database`**: Persistence layer using Exposed. `GameplayRepository` handles specific data access patterns.
- **`io.github.adlamb.cubex.registry`**: Loads and manages gameplay definitions (buildings, tech, resources) from YAML files in `src/main/resources/gameplay`.

## Building and Running

Key Gradle tasks for development:

- `./gradlew build`: Compiles the project and runs checks.
- `./gradlew shadowJar`: Generates the shaded (fat) plugin JAR in `build/libs/`.
- `./gradlew runServer`: Launches a local Paper server with the plugin installed for testing.
- `./gradlew clean`: Removes all build artifacts.

## Development Conventions

- **Indentation:** 4 spaces (standard Kotlin).
- **Naming:** `PascalCase` for classes, `camelCase` for variables and functions.
- **Configuration:** Use `kebab-case` for keys in YAML files.
- **Localization:** All player-facing text should be defined in `src/main/resources/messages.yml`.
- **Concurrency:** The plugin is designed with Folia support in mind. Use `SchedulerFacade` for all task scheduling and region-aware operations.
- **Commits:** Follow Conventional Commits (e.g., `feat:`, `fix:`, `refactor:`).
- **Modularization:** Add new features as separate packages within `io.github.adlamb.cubex.feature` and implement the `FeatureModule` interface.

## Key Files

- `src/main/kotlin/.../CubeXSLG.kt`: Main plugin entry point.
- `src/main/kotlin/.../gameplay/GameplayFacade.kt`: Central gameplay logic hub.
- `src/main/resources/gameplay/`: Configuration-driven definitions for buildings, tech, and resources.
- `GAMEPLAY_PLAN.md`: Detailed functional specifications and development roadmap.
- `AGENTS.md`: Repository-specific guidelines and environment setup instructions.
