# Portent

Portent tells a Minecraft server admin which of their plugins will break on a new
server version — before they upgrade, not from the stack traces afterwards.

It reads plugin jars as bytecode and compares every Bukkit/Paper symbol they use
against an index of the version you are moving to. Nothing is loaded, nothing is
executed, and every finding names a concrete symbol and the code that uses it.

```
Target:  26.1.2, Java 25  (paper-api-26.1.2.jar, 2596 types)
Plugins: /srv/mc/plugins  (3 jars)

RED    OldChat 1.4.2  (OldChat.jar)
  ERROR MISSING_METHOD         org/bukkit/entity/Player#setDisplayName(Ljava/lang/String;)V
        referenced from com/oldchat/ChatListener.onChat(Lorg/bukkit/entity/Player;)V
  ERROR LEGACY_NMS             org/bukkit/craftbukkit/v1_20_R3/entity/CraftPlayer
        version-stamped internals; 26.1 ships unobfuscated and Paper removed its
        remapper, so this package does not exist
        referenced from com/oldchat/NmsHook

YELLOW LuckPerms 5.5.71  (LuckPerms-Bukkit-5.5.71.jar)
  WARN  DEPRECATED_MEMBER      org/bukkit/entity/Player#getLocale()Ljava/lang/String;
        deprecated on the target version
        referenced from luckperms-bukkit.jarinjar!me/lucko/.../PlayerLocaleUtil.<clinit>()V

GREEN  Vault 1.7.3  (Vault.jar)

3 plugins scanned: 1 GREEN, 1 YELLOW, 1 RED
```

## Why it exists

Minecraft 26.1 removed jar obfuscation, Paper dropped its remapper, and the world
directory layout changed. A large share of the installed plugin base broke at once.
The usual upgrade procedure — copy the plugins folder, start the server, read stack
traces until something works — finds these one at a time, at the worst moment.

## Getting started

Portent comes two ways. Both use the same engine; pick whichever suits you.

### As a server plugin

Drop `Portent.jar` into your server's `plugins` folder, restart, and ask it about the
version you are considering:

```
/portent check 26.1.2
```

It downloads the API for that version, checks every plugin you have installed, prints
a verdict per plugin in the console, and writes the full report to
`plugins/Portent/report-26.1.2.txt`. Nothing else in your plugins folder is touched.

Runs on Java 17 and up, so it works on the old server you are trying to move off —
which is the one with the most to tell you.

### As a command-line tool

If you would rather not install anything on the server, or you want this in CI:

```
java -jar portent.jar index --minecraft-version 26.1.2 --out 26.1.json
java -jar portent.jar scan --plugins /path/to/your/plugins --index 26.1.json
```

The first command builds an index of the target version — do this once per version.
The second reads a plugins folder and prints the report.

### Building both

```
./gradlew build
```

produces `cli/build/libs/portent.jar` and `plugin/build/libs/Portent.jar`. Everything
is bundled inside each, so neither needs anything installed beyond a JDK.

`index` fetches `paper-api` and the dependencies its type hierarchies need, caching
them under `~/.portent/cache` so later runs work offline.

Already have the jars? Skip the network entirely:

```
portent index --api-jar paper-api-26.1.2.jar \
              --api-jar adventure-api-5.2.0.jar \
              --minecraft-version 26.1.2 --out 26.1.json
```

## Findings

| Finding | Severity | Meaning |
|---|---|---|
| `MISSING_CLASS` | ERROR | A referenced type is gone from the target |
| `MISSING_METHOD` | ERROR | A referenced method is gone |
| `MISSING_FIELD` | ERROR | A referenced field is gone |
| `LEGACY_NMS` | ERROR¹ | Version-stamped internals (`craftbukkit/v1_20_R3`, pre-1.17 `net/minecraft/server/v1_*`) |
| `UNSUPPORTED_CLASS_VERSION` | ERROR | A class file the target's JVM cannot load |
| `SERVER_INTERNALS` | WARN | Unversioned internals — present on an unobfuscated server, but unstable |
| `LEGACY_WORLD_PATH` | WARN | A hardcoded pre-26.1 world directory name |
| `DEPRECATED_MEMBER` | WARN | Deprecated on the target |
| `DEPRECATED_FOR_REMOVAL` | WARN | `@Deprecated(forRemoval = true)` — works today, will not later |
| `INTERNAL_API` | WARN | `@ApiStatus.Internal` |
| `EXPERIMENTAL_API` | WARN | `@ApiStatus.Experimental` |

¹ ERROR only when the index records a target of 26.1 or later. On an earlier or
unknown target it stays a warning, because the claim depends on the version.

**Verdicts.** `RED` if any error, `YELLOW` if only warnings, `GREEN` if nothing,
`SKIP` for a jar that is not a plugin. **Exit codes:** `0` clean, `1` at least one
RED, `2` bad arguments. Warnings alone do not fail a build — failing CI on a plugin
that works is how people learn to ignore a tool.

## Index completeness

`paper-api` declares Adventure as a Maven dependency rather than bundling it. An
index built from the API jar alone leaves 16.5% of its types with a supertype the
resolver cannot see, and the resolver correctly refuses to guess — so real breakage
goes unreported. `index` measures and reports this:

```
Hierarchy completeness: 16.5% of types have an unseen supertype
Warning: this index is missing types that scans need...
  net/kyori/adventure/audience/Audience    reached by 220 types
```

If you see that warning, add the named jars with `--api-jar`. Letting the tool
fetch handles it for you.

## Suppressions

Static analysis sees a reference; it cannot see whether the code runs. Plugins that
support several server versions keep dead references to old API behind runtime
version checks — ViaVersion calls `ItemStack.getTypeId()` from classes that only
load on a 1.8 server. The reference really is dead, and the plugin really does work.

Record the decision rather than have the tool guess:

```yaml
# suppressions.yml
suppressions:
  - plugin: ViaVersion
    finding: MISSING_METHOD
    symbol: org/bukkit/block/Block#getTypeId()I
    reason: >-
      v1_8to1_9 code, constructed in BukkitViaLoader.load() behind a protocol
      version check. Never loaded on 26.1. Verified 2026-08-29.
```

```
portent scan --plugins /srv/mc/plugins --index 26.1.json --suppressions suppressions.yml
```

A reason is mandatory — a suppression without one becomes folklore nobody dares
delete. Every rule must narrow by plugin, finding or symbol, so none can silence a
whole scan. Suppressed findings still appear in the report with their error count,
so breakage never disappears silently, and rules that match nothing are called out
as stale.

## Design

**It never runs what it reads.** Plugin jars are third-party and untrusted. Portent
parses them with ASM and never defines a class, never reflects, never opens a class
loader. Fetched jars get the same treatment.

**It prefers silence to guessing.** When the type hierarchy is not complete enough
to be sure, resolution answers UNKNOWN and no finding is produced. A tool that cries
wolf gets uninstalled, so a missed finding costs less than a false one.

**Every finding carries evidence.** A referenced symbol and the class and method
that reference it, always. There is no "may be incompatible".

**It only reports Bukkit/Paper namespaces.** Everything else in a plugin jar is a
shaded dependency or a soft-dep the server never provides. Other types may be
*indexed* to complete a hierarchy, which is not the same as reporting on them.

**Network lives in one package.** Only `dev.portent.fetch` opens a socket. Indexing,
scanning and reporting take local paths. The test suite never touches the network.

## Known limitations

- **Reachability.** A missing member throws only when its class is loaded. Portent
  reports the reference and names the class, but cannot tell you whether that code
  path runs. Suppressions exist for this.
- **Reflection.** A plugin that reaches the API through `Class.forName` is invisible.
- **Runtime libraries.** `libraries` in `paper-plugin.yml` is parsed but not analysed.

## Layout

| Module | What it is |
|---|---|
| `core` | The engine: indexing, scanning, resolution, reporting, fetching. Java 17. |
| `cli` | The command-line front end. Produces `portent.jar`. |
| `plugin` | The Bukkit/Paper front end. Produces `Portent.jar`. |

The plugin uses six Bukkit members, all stable since the early Bukkit days, and a test
pins that surface using Portent's own scanner — so reaching for newer API fails the
build rather than someone's server.

## Development

```
./gradlew test
```

The suite runs offline. Every fixture jar is synthesised with ASM at test time —
no binary fixtures are checked in and nothing is downloaded, including in the tests
for the fetching code, which run against a Maven repository laid out on disk.
