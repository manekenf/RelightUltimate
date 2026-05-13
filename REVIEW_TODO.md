[//]: # (# Code Review — Action Items)

[//]: # ()
[//]: # (Generated 2026-05-09. Severity: 🔴 critical · 🟠 high · 🟡 medium · 🟢 minor.)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (## `selectedcore`)

[//]: # ()
[//]: # (| # | Severity | Issue | File | Fix |)

[//]: # (|---|---|---|---|---|)

[//]: # (| 1 | ✅ | ~~DB single `Connection` shared across server thread + JDA threads~~ — fixed: every public method on `DatabaseManager` is now `synchronized` | `DatabaseManager.java` | done |)

[//]: # (| 2 | ✅ | ~~`DiscordBot.sendDirectMessage` uses `.complete&#40;&#41;` → blocks server thread~~ — fixed: full `.queue&#40;...&#41;` chain with error handlers | `DiscordBot.java` | done |)

[//]: # (| 3 | 🔴 | `VelocityHelper.RawPayload` codec never registered → Netty fallback throws | `VelocityHelper.java:120–128` | Register payload in `init&#40;&#41;` or remove fallback |)

[//]: # (| 4 | 🔴 | `ResourceWorldScheduler.sendVelocityMessage` is a TODO stub — open/close never reaches resource server | `ResourceWorldScheduler.java:198–217` | Implement via `VelocityHelper`, or remove until done |)

[//]: # (| 5 | ✅ | ~~`CoreConfig.load` Gson silently zeroes missing fields~~ — fixed: load → mergeNonNull onto fresh defaults via reflection | `CoreConfig.java` | done |)

[//]: # (| 6 | ✅ | ~~`HubPortalBlock.removeConnectedPortals` recursive cascade~~ — fixed: BFS flood-fill + ThreadLocal re-entrancy guard | `HubPortalBlock.java` | done |)

[//]: # (| 7 | ✅ | ~~Hologram destroys+respawns armor stands every second~~ — fixed: reuses stands, only updates name/position when changed | `HologramManager.java` | done |)

[//]: # (| 8 | ✅ | ~~`world.iterateEntities&#40;&#41;` per second~~ — fixed: tracked UUIDs by slot in `Map<String, List<UUID>>`; direct `world.getEntity&#40;uuid&#41;` lookup | `HologramManager.removeHologram` | done |)

[//]: # (| 9 | ✅ | ~~`getInstance&#40;&#41;` racy lazy init~~ — fixed: eager `static final INSTANCE` in both | `HologramManager`, `ResourceWorldScheduler` | done |)

[//]: # (| 10 | 🟠 | `DiscordBot` start/stop race during `awaitReady&#40;&#41;` | `DiscordBot.java` | `CountDownLatch` or volatile init flag |)

[//]: # (| 11 | ✅ | ~~Non-`volatile` static `instance` fields~~ — fixed: added `volatile` | `CoreConfig`, `CoreLocalization` | done |)

[//]: # (| 12 | 🟠 | Clan creation + leader-add not transactional → orphan clans on crash | `DatabaseManager.createClan` + caller | `setAutoCommit&#40;false&#41;` wrap |)

[//]: # (| 13 | 🟠 | `transferLeadership` / `removeMember` don't preserve "leader is a member" invariant | `DatabaseManager` | Validate in app layer |)

[//]: # (| 14 | 🟠 | No unique constraint on `&#40;invitee, clan, status='PENDING'&#41;` | DB schema | Partial unique index |)

[//]: # (| 15 | 🟠 | `link_codes` never expires &#40;Discord embed says 5 min&#41; | `DatabaseManager.getDiscordIdByCode` | Check `created_at` + cron cleanup |)

[//]: # (| 16 | 🟠 | No rate limit on `/discord link <code>` | `CoreCommands.handleDiscordLink` | N attempts / hour / UUID |)

[//]: # (| 17 | ✅ | ~~Localization fallback loops on same broken file~~ — fixed: try requested → en.json → hardcoded defaults | `CoreLocalization.java` | done |)

[//]: # (| 18 | 🟡 | Config filename `clansmod.json`, channel `clansmod:main` &#40;relics&#41; | `CoreConfig.java:74,64` | Migrate or comment why |)

[//]: # (| 19 | 🟡 | `/coins give` silently drops items if inventory full | `CoreCommands.java:170` | Drop leftover at feet |)

[//]: # (| 20 | 🟡 | `/discord link` race on duplicate code | `CoreCommands.handleDiscordLink` | Wrap in transaction; check insert |)

[//]: # (| 21 | 🟡 | Portal cleanup misses obsidian removed by TNT/lava/setblock | `HubPortalBlock.register` | Periodic frame validation tick |)

[//]: # (| 22 | 🟡 | `DiscordCommandHandler` — `getOption&#40;...&#41;` NPE risk | `DiscordCommandHandler.java:69,108,144` | Null guards |)

[//]: # (| 23 | 🟡 | `VelocityHelper.init` picks `plugins[0]` arbitrarily | `VelocityHelper.java:38` | Pick a known-stable plugin or own holder |)

[//]: # (| 24 | ✅ | ~~Dead code: `playerNameCache` / `getPlayerNameCached`~~ — fixed: deleted | `DatabaseManager.java` | done |)

[//]: # (| 25 | 🟢 | `/resourceworld nether/end` silently defaults unknown to disable | `CoreCommands.java:124,132` | Reject unknown values |)

[//]: # (| 26 | 🟢 | `PortalLighterItem` has no durability — document or change | `PortalLighterItem.java` | Decide intent |)

[//]: # (| 27 | 🟢 | Mixins lack explicit `priority` | `*Mixin.java` | Add for determinism |)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (## `selectedclans`)

[//]: # ()
[//]: # (| # | Severity | Issue | File |)

[//]: # (|---|---|---|---|)

[//]: # (| 1 | ✅ | ~~Hardcoded debug "Назва DVPF0" rendered to users~~ — fixed: removed from both screens | done |)

[//]: # (| 2 | ✅ | ~~`handleAcceptInvite` race~~ — fixed: addMember is atomic via UNIQUE constraint; on failure, invite stays PENDING &#40;player gets feedback&#41;. Also auto-expire other pending invites. | `ClanActionHandler.java`, `DatabaseManager.addMember` |)

[//]: # (| 3 | 🔴 | Pipe-delimited member list serialization is fragile + truncates large clans | `NetworkHandler.java:218–225` |)

[//]: # (| 4 | 🔴 | Client `ClanCreationScreen` uses hardcoded length validation | `ClanCreationScreen.java:190–193` |)

[//]: # (| 5 | 🔴 | Wealth leaderboard shows 0 for offline-leader clans → unstable ranking | `LeaderboardUpdater.java:96–106` |)

[//]: # (| 6 | ✅ | ~~`handleInvitePlayer` — no self-invite check, silent failures~~ — fixed: explicit self-check + feedback for non-leader / not-in-clan. Rate limit deferred &#40;low priority&#41;. | `ClanActionHandler.java` |)

[//]: # (| 7 | ✅ | ~~`handleKickPlayer` — silent failures with no feedback~~ — fixed: every reject path now sends a chat message | `ClanActionHandler.java` |)

[//]: # (| 8 | 🟠 | Rank capped at top-100, displays misleading "#101" past that | `NetworkHandler.java:202–208` |)

[//]: # (| 9 | 🟠 | `/lidc` does `getAllClans&#40;&#41;` per message | `ClanCommands.java:256` |)

[//]: # (| 10 | 🟠 | `ChatEventHandler` does DB lookup per chat message | `ChatEventHandler.java:52–53` |)

[//]: # (| 11 | ✅ | ~~Kick removes from local list before server confirms~~ — fixed: removed optimistic update; relies on server-driven resync | `ClanManagementScreen.java` |)

[//]: # (| 12 | ✅ | ~~`cachedClanData` not `volatile`~~ — fixed: both static caches are now `volatile` | `SelectedClansClient.java` |)

[//]: # (| 13 | 🟠 | Members/data sync ordering race → screen never opens or shows stale list | `SelectedClansClient.java:57–81` |)

[//]: # (| 14 | 🟠 | `/clansmod` literal — old mod ID | `ClanCommands.java:100` |)

[//]: # (| 15 | ✅ | ~~`/clan info` uses greedyString~~ — fixed: `string&#40;&#41;` instead | `ClanCommands.java` |)

[//]: # (| 16 | ✅ | ~~Stale `Clan` object passed to `checkClanMemberThreshold`~~ — fixed: caller fetches fresh `clan` via `getClanById` before checking | `ClanActionHandler.java` |)

[//]: # (| 17 | 🟡 | Empty `selectedclans.mixins.json` referenced from fabric.mod.json | mixins config |)

[//]: # (| 18 | ✅ | ~~Other PENDING invites not invalidated on accept~~ — fixed: now expired in `handleAcceptInvite` | `ClanActionHandler.handleAcceptInvite` |)

[//]: # (| 19 | 🟡 | Hardcoded English error strings in `/cc`, `/lidc` handlers | `ClanCommands.java:189, 232, 251` |)

[//]: # (| 20 | 🟡 | Hardcoded Ukrainian invite text instead of using `clan.invite.received` key | `ClanActionHandler.java:125` |)

[//]: # (| 21 | 🟡 | `clanTagColor` raw string from config concatenated as-is | `ChatEventHandler.java:60–61` |)

[//]: # (| 22 | 🟢 | `adminListClans` shows leader UUID prefix instead of name | `ClanCommands.java:393` |)

[//]: # (| 23 | 🟢 | `mouseScrolled` ignores verticalAmount magnitude | `ClanManagementScreen.java:235` |)

[//]: # (| 24 | 🟢 | `shakeOffset` declared but not visually rendered | `ClanCreationScreen.java:154–156` |)

[//]: # (| 25 | 🟢 | `OpenClanCreateScreenPayload` carries unused boolean | `NetworkHandler.java:92–100` |)

[//]: # (| 26 | ✅ | ~~`cachedMembers` reference shared with screen~~ — fixed: pass `List.copyOf&#40;parsed&#41;` so the screen owns its own list | `SelectedClansClient.java` |)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (## `selectedpolice`)

[//]: # ()
[//]: # (| # | Severity | Issue | File |)

[//]: # (|---|---|---|---|)

[//]: # (| 1 | ✅ | ~~`fabric.mod.json` invalid JSON — missing quotes on client entrypoint~~ — fixed: quoted entrypoint, added authors/license | `fabric.mod.json` |)

[//]: # (| 2 | ✅ | ~~`PlayerEntityModelMixin` not loaded~~ — fixed: added `selectedpolice.client.mixins.json` with correct package, referenced from `fabric.mod.json` as a client-only manifest | `selectedpolice.client.mixins.json` |)

[//]: # (| 3 | 🔴 | `PoliceEventHandler.tick` + `BindingHandler.onTick` do per-player per-tick DB queries | `PoliceEventHandler.java:51`, `BindingHandler.java:137` |)

[//]: # (| 4 | ✅ | ~~Bound-position state lost across server restart~~ — fixed: load from `pvp_status.spawn_*` when in-memory frozen pos is missing | `PoliceEventHandler.boundPositions` |)

[//]: # (| 5 | ✅ | ~~Unreachable dead branch — leash actionbar message never shown~~ — fixed: moved to `else if &#40;status.isLeashed&#40;&#41;&#41;` outside the bound branch | `PoliceEventHandler.java` |)

[//]: # (| 6 | ✅ | ~~`/pvp off` doesn't revoke police role~~ — fixed: police role auto-revoked when switching to PVE &#40;both `/pvp` and `/apvp`&#41; | `PoliceCommands` |)

[//]: # (| 7 | ✅ | ~~Memory leaks in `attackTimestamps`, `pendingBind`, `boundPositions`~~ — fixed: DISCONNECT handler clears per-UUID state | `PvpEventHandler.registerJoinHandler` |)

[//]: # (| 8 | 🟠 | `getPvpStatus` does INSERT-OR-IGNORE + SELECT every call | `PoliceDatabase.java:128–139` |)

[//]: # (| 9 | ✅ | ~~`BindingHandler.unleash` re-fetches DB pointlessly~~ — fixed: uses parameter `db` | `BindingHandler.java` |)

[//]: # (| 10 | ✅ | ~~Two `PlayerBlockBreakEvents.BEFORE` listeners → duplicate work~~ — fixed: merged into single listener handling both restraint and zone checks | `PoliceEventHandler.java` |)

[//]: # (| 11 | ✅ | ~~Caught players can still place blocks in prison zones~~ — fixed: `UseBlockCallback` rejects BlockItem placement inside zones for caught/bound players | `PoliceEventHandler.java` |)

[//]: # (| 12 | 🟠 | PvP cooldown hardcoded 7 days | `PoliceCommands.java:26` |)

[//]: # (| 13 | 🟠 | `attackTimestamps` is plain `HashMap` &#40;server-thread only — annotate or switch&#41; | `PvpEventHandler.java:31` |)

[//]: # (| 14 | ❌ | ~~Bind decrements lead but unleash always returns one~~ — re-checked: bind/unleash is symmetric within the same officer interaction; jail/release don't return leads &#40;intentional&#41;. Not a bug. | n/a |)

[//]: # (| 15 | 🟠 | `handlePrisonJail` arbitrary zone choice | `PoliceCommands.java:389–395` |)

[//]: # (| 16 | ✅ | ~~Min zone volume not validated → 1×1×1 zones possible~~ — fixed: enforce `MIN_ZONE_VOLUME = 8` | `PrisonSelectionHandler.commitZone` |)

[//]: # (| 17 | 🟡 | "tmp text" prefixes still in production | `PvpEventHandler.java:152, 197` |)

[//]: # (| 18 | 🟡 | PVE/PVP icons use private-use unicode requiring resource pack — no fallback | `PvpEventHandler.applyPvpTeam` |)

[//]: # (| 19 | 🟡 | `/police clean last` no confirmation | `PoliceCommands.handlePoliceClean` |)

[//]: # (| 20 | ✅ | ~~`handlePrisonJail` doesn't reset target velocity post-teleport~~ — fixed: zero velocity + fallDistance after teleport | `PoliceCommands.java` |)

[//]: # (| 21 | 🟡 | Particles spawn per-tick × per-leash — won't scale | `BindingHandler.spawnLeashParticles` |)

[//]: # (| 22 | ✅ | ~~Join-sync lambda doesn't null-check player~~ — fixed: checks `networkHandler != null && !isDisconnected&#40;&#41;` | `PvpEventHandler.registerJoinHandler` |)

[//]: # (| 23 | 🟡 | `MAX_ZONES_PER_OFFICER` / `MAX_ZONE_VOLUME` hardcoded | `PoliceCommands.java:27–28` |)

[//]: # (| 24 | ✅ | ~~`PoliceDatabase` second connection — same threading caveat as core #1~~ — fixed: every public method on `PoliceDatabase` is now `synchronized` | `PoliceDatabase.java` |)

[//]: # (| 25 | ✅ | ~~No `/police prison cancel` to exit selection mode~~ — fixed: added `/police prison cancel` | `PoliceCommands` |)

[//]: # (| 26 | 🟡 | Hardcoded Ukrainian strings instead of `CoreLocalization` keys | all police files |)

[//]: # (| 27 | 🟢 | `handlePvpToggle` cooldown text plurals off | `PoliceCommands.java:91–93` |)

[//]: # (| 28 | 🟢 | `applyCriminalTag` recursion into `applyPvpTeam` is implicit | `PvpEventHandler.java:167–169` |)

[//]: # (| 29 | ✅ | ~~`/police on/off` doesn't refresh team prefix immediately~~ — fixed: applyPvpTeam called after each toggle | `PoliceCommands.handlePoliceOn/Off` |)

[//]: # (| 30 | 🟢 | `PrisonZone.contains&#40;int,...&#41;` vs `&#40;double,...&#41;` upper-bound inconsistency | `PrisonZone.java:26–40` |)

[//]: # (| 31 | 🟢 | `BindingHandler.LEASH_HARD_RADIUS = 15` may feel jarring | `BindingHandler.java:31` |)

[//]: # (| 32 | ✅ | ~~`fabric.mod.json` missing `name`, `description`, `authors`, etc.~~ — fixed alongside #1 | manifest |)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (## `papibridge` + build/config)

[//]: # ()
[//]: # (| # | Severity | Issue | File |)

[//]: # (|---|---|---|---|)

[//]: # (| 1 | ✅ | ~~Single shared `Connection` across PAPI threads~~ — fixed: all 3 public methods are `synchronized` | `DbAccess.java` |)

[//]: # (| 2 | 🔴 | `PoliceExpansion.icon` / `status` do 2 DB calls per request | `PoliceExpansion.java:31–42` |)

[//]: # (| 3 | 🔴 | "tmp text" police labels with whitespace-padding alignment | `PoliceExpansion.java:32–33` |)

[//]: # (| 4 | 🟠 | `ClansExpansion.tag` space-padding assumes monospace font | `ClansExpansion.java:31–35` |)

[//]: # (| 5 | 🟠 | Stale `is_bound`/`is_leashed` reads &#40;race with police mod expiration tick&#41; | `PoliceExpansion`, `DbAccess.getPoliceStatus` |)

[//]: # (| 6 | 🟠 | `onEnable` no retry if DB missing &#40;silent failure forever&#41; | `RelightPapiBridge.java:38–44` |)

[//]: # (| 7 | 🟡 | `version = '1.0.0'` hardcoded vs gradle.properties pattern | `papibridge/build.gradle:6` |)

[//]: # (| 8 | 🟡 | `DbAccess.close` swallows `SQLException` | `DbAccess.java:46` |)

[//]: # (| 9 | 🟡 | Relocation magic string can drift from build.gradle | `DbAccess.java:32` |)

[//]: # (| 10 | 🟡 | `plugin.yml` missing `prefix` | `plugin.yml` |)

[//]: # (| 11 | 🟡 | `papibridge_version` missing from `gradle.properties` | `gradle.properties` |)

[//]: # (| 12 | 🟡 | Root build hardcodes Fabric subproject list | `build.gradle:31` |)

[//]: # (| 13 | 🟡 | Three identical `*_version=1.0.0` entries — single `project_version` would do | `gradle.properties:9–11` |)

[//]: # (| 14 | 🟡 | `selectedcore` bundles huge transitive dep tree &#40;JDA + okhttp + Kotlin + Jackson&#41; — classloader-conflict risk on Arclight | `selectedcore/build.gradle` |)

[//]: # (| 15 | 🟢 | `splitEnvironmentSourceSets&#40;&#41;` for `selectedcore` &#40;no client classes&#41; | root `build.gradle:57` |)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (# Cross-cutting summary)

[//]: # ()
[//]: # (These themes appeared in every module and dominate the priority list:)

[//]: # ()
[//]: # (1. **SQLite thread-safety.** `selectedcore.DatabaseManager`, `selectedpolice.PoliceDatabase`, `papibridge.DbAccess` each share a single `Connection` across server-tick + JDA + PAPI threads with no synchronization. This is the single biggest reliability risk and should be fixed first.)

[//]: # (2. **Per-tick / per-event DB queries.** `selectedpolice` does per-player per-tick queries; chat handler hits DB per message; PAPI placeholders hit DB per request. Once #1 is synchronized, lock contention will become acute. Cache state in memory keyed by UUID.)

[//]: # (3. **Hologram thrash.** `selectedcore.HologramManager` + leaderboard updater destroy/respawn armor stands every second.)

[//]: # (4. **Velocity messaging is partly stub / misregistered.** Cross-server signaling is broken in two places.)

[//]: # (5. **Hardcoded "tmp text" prefixes** for police/criminal labels are in three modules — make a single decision &#40;icons or text&#41; and apply everywhere.)

[//]: # (6. **Fabric manifest issues** — `selectedpolice/fabric.mod.json` is invalid JSON; client mixin not registered. The whole police mod silently fails to load.)

[//]: # (7. **Localization fragmentation** — selectedcore has the `CoreLocalization` system; clans + police mostly bypass it with inline strings.)

[//]: # ()
[//]: # (## Suggested overall fix order)

[//]: # ()
[//]: # (1. selectedpolice #1 + #2 &#40;manifest + mixin&#41; — without these, none of selectedpolice runs.)

[//]: # (2. selectedcore #1 &#40;DB sync&#41; — touches everything downstream.)

[//]: # (3. selectedcore #2 &#40;Discord `.complete&#40;&#41;`&#41; — server stalls.)

[//]: # (4. selectedpolice #3 + #4 &#40;per-tick DB; restart state loss&#41; — playable performance + jail correctness.)

[//]: # (5. selectedcore #3/#4 &#40;Velocity registration / TODO stub&#41; — pick one path.)

[//]: # (6. selectedclans #1 &#40;"Назва DVPF0" debug strings&#41; — visible to players.)

[//]: # (7. selectedclans #2 &#40;accept-invite race&#41; — silent data corruption.)

[//]: # (8. Then medium/minor in any order.)


New bugs that appeared after review: 
selectedpolice:
-wrong angles of a model (In future need to be replaced by blockbench model)
-after unleashing player teleports to his spawnpoint when he was caught after commiting a crime.
-/police prison jail command doesn't need to teleport a player to a prison. It must unleash player yet still prohibit any interaction for a specified period of time. Now it just teleports player and he's still leashed.