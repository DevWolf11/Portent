# portent

Static analysis CLI that predicts which Bukkit/Paper plugins will break on a target
Minecraft version, before the admin upgrades. Input: a plugins folder + a target API
index. Output: a per-plugin verdict with evidence.

## Stack
- Java 25, Gradle (Kotlin DSL), `application` plugin, single module
- ASM (`org.ow2.asm:asm`, `asm-tree`) for bytecode
- picocli (CLI), Jackson (JSON), SnakeYAML (plugin.yml)
- JUnit 5 + AssertJ

## Hard rules

**Never load or execute scanned code.** No URLClassLoader over plugin jars, no
`Class.forName`, no reflection on them. These are untrusted third-party jars. ASM
parsing only, always.

**Network lives only in `dev.portent.fetch`.** Everything else -- indexing, scanning,
resolution, reporting -- takes local paths and must work with the network unplugged. `fetch`
downloads API jars from Maven so an admin does not have to assemble them by hand, and it is the
only package permitted to open a socket. No test hits the network: the fetch package is tested
against a local repository laid out on disk. `./gradlew test` passes offline, always.

Downloaded artifacts are untrusted input. Verify the SHA-1 Maven publishes beside every artifact,
cache by coordinate so a second run needs no network, and never execute what was downloaded --
a fetched jar is read with ASM exactly like a scanned one.

**Only report symbols in Bukkit/Paper namespaces**: `org/bukkit/`, `io/papermc/`,
`com/destroystokyo/`, `org/spigotmc/`, `net/md_5/bungee/`. Everything else is a shaded
dependency or an optional soft-dep and must be ignored. False positives destroy this
tool's entire value proposition.

Two refinements learned from real jars. Types outside those namespaces may still be *indexed*
when they complete a hierarchy -- paper-api leaves Adventure to Maven, and without it 16.5% of
the API has a supertype the resolver cannot see. Indexing them is not reporting them. And
`net/minecraft/` and `org/bukkit/craftbukkit/` are matched by package name for the NMS findings
only; we hold no index of internals, so a finding never claims one of their members is missing.

**Never claim a type is gone from a package the index does not cover.** If the index holds no
`net/kyori` types, it knows nothing about them.

**Every finding carries evidence**: referenced owner + method name + descriptor, plus the
class and method inside the plugin that references it. Never emit "may be incompatible"
without a concrete symbol.

**Prefer false negatives.** When resolution is ambiguous, downgrade severity rather than
guess. A tool that cries wolf gets uninstalled.

**Do not guess Bukkit/Paper method signatures from memory.** Read them from the API index
or the vendored api jar. Training data for the 26.x API is unreliable.

## Domain facts (relevant to finding types)
- MC 26.1+ ships unobfuscated and Paper removed its internal remapper. A plugin referencing
  Spigot-mapped NMS or `org.bukkit.craftbukkit.v1_*` names is dead on 26.1+.
- 26.1 moved world storage to `world/dimensions/minecraft/<dim>/`. String constants
  `world_nether`, `world_the_end`, `DIM-1`, `DIM1` in plugin code are a distinct finding.
- 26.1 requires Java 25. Class files targeting a newer major version than the server's JVM
  are a distinct finding.

## Verification
- `./gradlew test` must pass offline.
- Test fixtures are jars synthesised at test time with ASM in a `FixtureJars` helper.
  Never check in binary fixtures, never download them.
- Before telling me a feature works, run `./gradlew test` and show me the actual output.
