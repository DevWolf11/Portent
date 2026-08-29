# plugin-doctor

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

**No network in library or test code.** Index generation takes a local jar path. If a
convenience `fetch` command is ever added, it lives in its own class and no test touches it.

**Only report symbols in Bukkit/Paper namespaces**: `org/bukkit/`, `io/papermc/`,
`com/destroystokyo/`, `org/spigotmc/`, `net/md_5/bungee/`. Everything else is a shaded
dependency or an optional soft-dep and must be ignored. False positives destroy this
tool's entire value proposition.

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
