# Kotlin support — deferred follow-ups

**Status:** deferred — captured for a follow-up PR. Surfaced during review of the
[#918](https://github.com/flamingock/flamingock-java/issues/918) Kotlin-support fix (auto-apply
`org.jetbrains.kotlin.kapt` when Kotlin JVM is detected, register `flamingock-processor` under
the `kapt` configuration, harden the reflective Kotlin-compile-task arg wiring).

Two related concerns are intentionally **not** addressed in the bug-fix PR so the patch release
stays focused. They are described here in enough detail that the next PR doesn't have to
re-derive the analysis.

---

## 1. Kapt auto-apply: opt-out and KSP roadmap

### Current behaviour

`FlamingockPlugin.apply()` reacts to `org.jetbrains.kotlin.jvm` being present and
unconditionally applies `org.jetbrains.kotlin.kapt` (via `FlamingockConstants.KAPT_PLUGIN_ID`)
unless it is already applied. There is no opt-out. `DependencyConfigurator.configure()` then
adds `flamingock-processor` (and `mongock-support` when Mongock support is enabled) under the
`kapt` configuration alongside `annotationProcessor`.

### Why this is fine for now

The plugin's stated positioning is zero-boilerplate (`"id 'io.flamingock'"` and the user is
done). Kotlin users hitting #918 were dropping into raw kapt configuration and getting it
wrong; auto-apply removes that failure mode. As long as `flamingock-processor` ships only as a
`javax.annotation.processing.Processor`, **kapt is the only path** for running it against
Kotlin sources — KSP cannot run a `javax.annotation.processing.Processor` directly.

### Why it deserves an opt-out

- **Build cost.** Kapt generates Java stubs from Kotlin sources and runs the AP against the
  stubs. This is slower than KSP and historically blocks some Kotlin language features (e.g.
  certain inline-class scenarios) from being visible to the AP.
- **Long-term direction.** KSP is Google/JetBrains' recommended replacement for kapt for new
  annotation processing in Kotlin projects. Teams that have moved their stack to KSP will
  resent kapt being silently force-applied.
- **Composability.** Users who deliberately don't want kapt — for instance, they have their
  own kapt setup, or they want to register the processor under `ksp` once that's supported —
  have no way to disable the auto-apply today.

### Deferred items

**a) Add an extension toggle.** Shape suggestion:

```kotlin
flamingock {
    // Default: "kapt" (current behaviour, no breaking change).
    // Future values: "ksp" (once the processor is ported), "none" (caller wires it themselves).
    kotlinAnnotationProcessor = "kapt"
}
```

A simpler `autoApplyKapt: Boolean` is acceptable if KSP support is genuinely far off; the
three-valued enum is preferred because it makes the eventual KSP migration a value change
rather than an option deprecation.

Default must remain the current behaviour (`"kapt"` or `autoApplyKapt = true`) so existing
users see no change.

Implementation touches `FlamingockExtension`, `FlamingockPlugin.apply()` (gate the
`project.plugins.withId(KOTLIN_JVM_PLUGIN_ID) { … }` block on the new value), and
`DependencyConfigurator.configure()` (skip the `kapt` dependency registration when the toggle
is `"none"`).

**b) KSP port of `flamingock-processor`.** Larger, separate piece of work touching
`core/flamingock-processor/`. Not every javax.annotation.processing API has a direct KSP
equivalent — `RoundDiscovery`, the `Filer`-based incremental cache in `FlamingockMetadataStore`,
and the SPI-file roundtrip in `MetadataModuleIdentity` all need careful porting. Worth a
scoping issue before implementation to enumerate the API gaps. The auto-apply toggle above
should land first so the KSP work has somewhere to plug in.

---

## 2. Mixed Java+Kotlin modules: duplicate same-name default stages

### Mechanism

When a single module has **both** Java and Kotlin sources annotated with Flamingock, the
processor runs **twice** per build — once under `compileJava` (via the `annotationProcessor`
configuration) and once under `kaptKotlin` (via the `kapt` configuration). The two invocations
happen with **different `CLASS_OUTPUT` directories**:

- kapt: `build/tmp/kapt3/classes/<sourceSet>/`
- javac: `build/classes/java/<sourceSet>/`

`MetadataModuleIdentity.resolve()`
(`core/flamingock-processor/src/main/java/io/flamingock/core/processor/util/MetadataModuleIdentity.java`,
the `readExistingProviderFqn` path) reads the SPI registration from `CLASS_OUTPUT` to discover
or generate a per-module suffix. Because the two `CLASS_OUTPUT` dirs are disjoint, the two
invocations each find no existing SPI file and each generate their own random 8-hex suffix.
End result:

- kapt writes `META-INF/services/io.flamingock.internal.common.core.metadata.FlamingockMetadataProvider`
  pointing at `…Impl_aaaa1111`, plus `META-INF/flamingock/metadata_aaaa1111.json`.
- javac writes the same SPI filename pointing at `…Impl_bbbb2222`, plus
  `META-INF/flamingock/metadata_bbbb2222.json`.

Both end up in the packaged JAR. At runtime,
`MetadataLoader.loadAggregated()`
(`core/flamingock-core-commons/src/main/java/io/flamingock/internal/common/core/metadata/MetadataLoader.java`)
uses `ServiceLoader` to discover **both** providers.

### Runtime aggregation behaviour

In `MetadataLoader.CompositePipelineBuilder.buildFrom()`:

- **System stages**: `mergeSystem()` id-deduplicates and logs at DEBUG.
- **Legacy stages**: `collapseLegacyStagesByName()` + `mergeSameNameLegacyStages()` group by
  name and id-deduplicate the change set per group.
- **Default stages**: the loop in `buildFrom()` adds every default stage from every provider
  into `defaultStages`. The duplicate-name detection logs a **WARN** (`"Duplicate stage name
  '{}' across modules — proceeding with both."`) but **keeps both copies**. There is no
  id-dedup at the default-stage level.

For the kapt + javac case in a single module, both metadata files contain the **same**
default-stage structure (both derive it from the same `@EnableFlamingock`). Within those
stages, each AP only contributes elements visible to its own compiler (kapt: Kotlin sources;
javac: Java sources). So the composite ends up with the same default stage appearing **twice**
— one populated with the module's Kotlin changes, the other with its Java changes.

### Why this is messy but not catastrophic

- **No double-execution of the same change.** kapt's metadata has Kotlin changes, javac's has
  Java changes; they don't overlap.
- **Audit and reporting are messy.** The execution report and the audit log will reference the
  same stage name twice with different change subsets each time, which diverges from what the
  user declared in `@EnableFlamingock` and is confusing to read.
- **A WARN fires every startup.** Currently the WARN is genuinely useful for catching
  multi-module configuration mistakes; a fix should keep that signal alive while suppressing
  it for the legitimate kapt+javac case.

### Solution directions

**(a) Merge-layer dedup (recommended).** Extend `MetadataLoader.CompositePipelineBuilder` to
id-union same-name default stages, mirroring `mergeSameNameLegacyStages`. Concretely: refactor
`mergeSameNameLegacyStages` and `collapseLegacyStagesByName` into a stage-type-agnostic helper
(`collapseStagesByName(List<PreviewStage>)` returning one collapsed stage per name); call it
for both `defaultStages` and `legacyStages`. Preserve the duplicate-name WARN for the
deduplicated-but-still-suspicious case where the contents weren't fully aligned across modules
(e.g. different `sourcesPackage` on stages of the same name) so genuine misconfigurations are
still surfaced.

Pros: one fix lives in one place (commons), reuses an existing pattern, robust to any future
multi-provider scenario (not just kapt+javac). Cons: weakens the current invariant that
"default stages with the same name across modules is suspicious" — mitigated by keeping the
WARN for content mismatches.

**(b) Shared `CLASS_OUTPUT` / shared module identity (rejected).** Make kapt and javac in the
same module reuse the same suffix. Conceptually cleaner — one metadata file end-to-end — but
the build-side mechanics are fragile: Gradle's task ordering across compilers, incremental
recompilation scenarios, and clean-build orderings all become surface area for this to drift.
The merge-layer fix achieves the same end result without that fragility.

### Verification fixture

A test fixture under `flamingock-gradle-plugin/src/test/resources/` (or as an integration test
under `core/flamingock-core-commons/`) exercising a module with at least one `@Change` in Java
and one in Kotlin, both under the same `@EnableFlamingock` stage. Assertions:

1. Build succeeds, producing two `META-INF/flamingock/metadata_*.json` files in the JAR (this
   is expected — the fix is at the aggregator, not the build).
2. After `MetadataLoader.loadAggregated()`, the composite pipeline contains exactly one
   default stage with that name, and its change list contains both changes (one from each
   language).
3. No `"Duplicate stage name … proceeding with both."` WARN is emitted when the contents are
   compatible.

---

## Tracking

When the follow-up PR is filed, please open two issues referencing this document:

1. **Kotlin annotation processing: add opt-out toggle and scope KSP port** — implements
   Section 1's deferred items (a) and (b).
2. **MetadataLoader: dedupe same-name default stages across providers** — implements
   Section 2's solution direction (a).
