# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Stack Refill is a Hytale server plugin that automatically refills the player's hand stack when placing blocks by searching the hotbar, inventory, and backpack for matching items. Built against the Hytale Server API using their ECS (Entity Component System) plugin architecture.

## Build & Run Commands

```bash
./gradlew build          # Build the plugin JAR (output in build/libs/)
./gradlew test           # Run tests (none exist yet)
./gradlew runServer      # Start a local Hytale server with the plugin loaded
./gradlew spotlessApply  # Auto-format code (Eclipse formatter style)
./gradlew spotlessCheck  # Check formatting without applying
./gradlew shadowJar      # Build fat JAR with bundled dependencies
```

`runServer` requires Hytale installed locally. It looks for `HYTALE_HOME` env var or the default OS install path.

## Architecture

The plugin follows Hytale's ECS event system pattern:

- **`StackRefill`** — Main plugin entry point (`JavaPlugin` subclass). Registers event systems in `setup()`. Singleton via `getInstance()`.
- **`PlaceBlockEventSystem`** — Core logic. Listens for `PlaceBlockEvent`, triggers when a player places their last block (quantity == 1). Detects which hand (main or off-hand) placed the block using item ID and quantity comparison, then searches for replacements in the appropriate containers: main hand searches hotbar → storage → backpack; off-hand searches utility → hotbar → storage → backpack. Refills into the correct hand's slot.
- **`InventorySearch`** — Static utility that iterates container slots to find a matching item ID. Accepts single or multiple `SearchQuery` objects and returns a `SearchResult`.
- **`SearchQuery`** / **`SearchResult`** — Java records with validation. `SearchQuery` holds container + item ID + optional slot to ignore. `SearchResult` uses factory methods `found()` / `notFound()` with invariant checks.

## Key Configuration

- **`manifest.json`** (`src/main/resources/`) — Plugin metadata: name, version, entry point class, server version compatibility.
- **`gradle.properties`** — Java version (25), Hytale build version, project version.
- **`build.gradle`** — Dependencies, Hytale maven repos, Shadow/Spotless plugins.

## CI/CD

GitHub Actions (`.github/workflows/build.yml`): builds on push to `main`/`develop` and PRs. Tags matching `v*` trigger a GitHub release with the JAR.
