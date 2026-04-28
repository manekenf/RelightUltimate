# SelectedProject

Multi-module Fabric 1.21.1 mod for Arclight server (Fabric + Bukkit hybrid) with Velocity proxy.

## Modules
- `selectedcore` (mod ID: selectedcore) — shared infrastructure: database, config, Discord bot, hub dimension, portals, coins, Velocity helper, resource world scheduler. Package: `ua.selectedproject.core`
- `selectedclans` (mod ID: selectedclans) — clan system: board block, GUI, chat, leaderboards. Package: `ua.selectedproject.clans`
- `selectedpolice` (mod ID: selectedpolice) — PVP/PVE toggle, police/criminal system. Package: `ua.selectedproject.police`

## Technical
- Arclight provides Bukkit API via reflection
- VelocityHelper uses Bukkit's sendPluginMessage for cross-server
- SQLite database at config/selectedcore/selectedcore.db
- Config: Gson-based CoreConfig. Localization: JSON-based CoreLocalization
- Core API: SelectedAddon interface + AddonRegistry for addon lifecycle
- Refactored from single mod "clansmod" (ua.selectedproject.clansmod) — watch for old references

## Build
./gradlew clean build