# Android Kotlin Full-Codebase Refactoring Protocol

## Purpose

You are performing a **full backend/code-quality refactor of an Android Kotlin codebase**.

The objective is to remove unnecessary, redundant, duplicated, obsolete, unreachable, over-engineered, inefficient, or poorly structured code while preserving the application's existing behavior and avoiding unnecessary UI changes.

This is **not** a cosmetic rewrite.

This is a systematic engineering cleanup and optimization pass across the entire repository.

The guiding principle is:

> **Simplify aggressively where safety is demonstrable; preserve behavior where it is not.**

The resulting codebase should be smaller, clearer, more maintainable, more consistent, and no less functional than before.

---

# 1. Non-Negotiable Rules

## 1.1 Preserve application behavior

Do not intentionally change:

- user-visible functionality
- business rules
- data formats
- API contracts
- authentication behavior
- authorization behavior
- persistence semantics
- navigation behavior
- background-work behavior
- notifications
- deep links
- intent handling
- lifecycle semantics
- observable state semantics
- error-handling behavior
- analytics/event semantics
- configuration semantics
- build variants/flavors
- feature flags
- externally consumed interfaces

Unless a change is demonstrably redundant, broken, unreachable, obsolete, or an internal implementation improvement with equivalent behavior.

## 1.2 UI is not the target

The primary target is the **backend/internal codebase**.

Avoid modifying:

- layouts
- Compose UI structure
- visual styling
- colors
- typography
- spacing
- animations
- accessibility text
- user-facing copy
- navigation UX
- screen hierarchy
- interaction patterns

UI code may be touched only when necessary to support a safe backend refactor, such as:

- replacing a dead dependency
- adapting a renamed internal API
- removing an unused ViewModel method
- fixing a clearly redundant UI-to-domain call path
- updating an internal interface after a backend refactor

Do not redesign UI architecture merely because another architecture is theoretically better.

## 1.3 Do not refactor for the sake of activity

Do **not** change working code merely to make it look different.

Every modification should have a concrete reason such as:

- redundancy removal
- dead-code removal
- duplication reduction
- dependency simplification
- performance improvement
- lifecycle correctness
- memory/resource improvement
- concurrency correctness
- safer nullability
- clearer responsibility boundaries
- unnecessary abstraction removal
- deprecated implementation replacement
- error-path cleanup
- build/maintenance improvement

## 1.4 Do not guess about usage

Before deleting or changing anything that appears unused, investigate:

- direct references
- indirect references
- reflection
- annotations
- dependency injection
- generated code
- serialization/deserialization
- Android manifest references
- Gradle configuration
- resource references
- navigation graphs
- intents/deep links
- WorkManager workers
- BroadcastReceivers
- Services
- ContentProviders
- callbacks
- JNI/native boundaries if present
- WebView/JavaScript bridges if present
- ProGuard/R8 rules
- product flavors/build variants
- test source sets
- debug/release-only code
- instrumentation tests
- dynamic feature modules
- libraries/modules consumed externally

Do not label code dead solely because an IDE says there are no direct references.

## 1.5 Preserve public/external contracts unless proven safe

Treat these as potentially externally consumed until proven otherwise:

- public classes
- public functions
- public properties
- exported Android components
- library module APIs
- interfaces implemented outside the immediate module
- serialized model fields
- API request/response structures
- database entities/schema-facing APIs
- BroadcastReceiver actions
- deep-link routes
- Intent extras
- content-provider contracts
- AIDL interfaces
- IPC boundaries

Internal visibility may be narrowed only when repository-wide analysis proves it is safe.

## 1.6 Never hide broken behavior behind a refactor

Do not:

- remove error handling merely because it is inconvenient
- suppress compiler/lint warnings without understanding them
- replace an explicit failure with a silent fallback
- swallow exceptions
- convert errors to nullable values without preserving semantics
- introduce broad `try/catch` blocks to make tests pass
- weaken type safety
- disable lint/static-analysis rules to avoid fixing issues
- increase timeouts to hide performance problems
- add arbitrary retries
- add unnecessary caching
- introduce global mutable state

## 1.7 Preserve source-of-truth semantics

Do not duplicate configuration, constants, transformations, validation rules, or business logic when one authoritative implementation already exists.

When duplicates exist, consolidate them into a single source of truth where safe.

---

# 2. Overall Operating Model

Perform the work in these phases, in order.

1. **Repository reconnaissance**
2. **Dependency and build-system analysis**
3. **Architecture and major-file mapping**
4. **Risk classification**
5. **Global redundancy/dead-code analysis**
6. **Systematic file-by-file review**
7. **Cross-file dependency verification**
8. **Refactor implementation**
9. **Validation after each logical batch**
10. **Final repository-wide audit**

Do not skip directly to editing files.

Do not start deleting code until the repository structure, dependency graph, build system, and major execution paths are understood.

---

# 3. Phase 1 — Repository Reconnaissance

Start by building a complete inventory.

Identify:

- root project structure
- all Gradle modules
- application modules
- library modules
- dynamic feature modules
- test modules/source sets
- `build.gradle` / `build.gradle.kts`
- `settings.gradle` / `settings.gradle.kts`
- version catalogs
- convention plugins
- `gradle.properties`
- `local.properties` usage
- manifest files
- resource directories
- Kotlin source directories
- Java source directories, if any
- generated-code directories/configuration
- assets
- native code
- scripts
- CI/CD configuration
- ProGuard/R8 rules
- Detekt/Ktlint/Spotless configuration
- Android Lint configuration
- dependency update tooling
- code generation tooling
- serialization configuration
- database migrations
- API schemas/specifications
- test fixtures
- sample/demo applications

Record the findings before modifying anything.

## 3.1 Build a repository map

Produce an internal map similar to:

```text
Project
├── app
│   ├── presentation
│   ├── domain
│   ├── data
│   ├── di
│   └── ...
├── core
├── feature-x
├── feature-y
├── build-logic
└── ...
```

Do not assume the project follows a textbook architecture.

Document the architecture that **actually exists**.

## 3.2 Identify source-set boundaries

Explicitly distinguish:

- `main`
- `debug`
- `release`
- flavor-specific source sets
- unit tests
- instrumentation tests
- benchmark modules/tests
- generated sources

A function unused in `main` may still be required by a test or a build variant.

---

# 4. Phase 2 — Dependency and Build-System Analysis

Understand dependencies before changing implementation code.

## 4.1 Gradle dependency inventory

For every module, determine:

- direct dependencies
- transitive dependencies where relevant
- implementation dependencies
- API dependencies
- compile-only dependencies
- runtime-only dependencies
- test dependencies
- Android test dependencies
- annotation processors
- KSP/KAPT processors
- plugins
- build-logic/convention plugins
- BOMs
- version catalogs
- platform dependencies

Identify dependencies that are:

- unused
- duplicated
- transitively available and unnecessarily declared directly
- present only because of obsolete code
- older alternatives to already-used libraries
- causing duplicate functionality
- creating unnecessary build complexity

Do **not** remove a dependency simply because no obvious Kotlin import exists.

Check Gradle plugins, generated code, resource processing, annotation processing, manifests, reflection, and build tasks.

## 4.2 Version and plugin consistency

Look for:

- duplicate version declarations
- inconsistent library versions
- redundant dependency declarations
- deprecated Gradle constructs
- obsolete Android Gradle Plugin usage
- unnecessary plugins
- unused compiler options
- duplicated compiler configuration
- configuration copied across modules unnecessarily

Do not perform unrelated major dependency upgrades unless required by the refactor.

A dependency upgrade is not automatically an optimization.

## 4.3 Build variants and flavors

Treat every build variant as potentially valid.

Check whether code is used only by:

- debug
- release
- internal builds
- free/paid flavors
- regional flavors
- device-specific flavors
- test configurations

Never delete variant-specific code without checking its source-set consumers.

---

# 5. Phase 3 — Architecture and Major-File Mapping

Before reviewing individual files, identify the major architectural components.

Find and map:

- Application class
- Activities
- Fragments
- Composables
- ViewModels
- presenters/controllers if present
- repositories
- use cases/interactors
- domain services
- data sources
- API clients
- database components
- DAOs
- entities
- mappers
- DTOs
- serialization models
- dependency-injection modules
- workers
- services
- receivers
- providers
- managers
- utility/helper classes
- singleton objects
- caches
- event buses
- state holders
- schedulers
- background processors
- error/reporting systems
- logging systems
- configuration providers

For each major component determine:

- who creates it
- who calls it
- what it owns
- what it depends on
- what depends on it
- whether it contains duplicated responsibilities
- whether it is merely an unnecessary wrapper
- whether it owns state appropriately
- whether it is doing work that belongs elsewhere

---

# 6. Phase 4 — Risk Classification

Classify files and components before editing them.

## Low risk

Examples:

- private helper functions
- duplicated private constants
- obvious dead local variables
- unreachable branches
- redundant null checks
- trivial wrappers with no behavior
- unused private properties
- redundant imports
- unused parameters that can safely be removed
- duplicate conversions with identical semantics

## Medium risk

Examples:

- repository methods
- ViewModel APIs
- DI modules
- shared utilities
- database access code
- API mappers
- shared domain models
- background workers

## High risk

Examples:

- Application class
- manifest components
- exported components
- deep-link handlers
- services
- receivers
- providers
- navigation infrastructure
- authentication/authorization
- persistence/schema/migrations
- networking core
- serialization models
- reflection-heavy code
- generated-code boundaries
- IPC
- externally consumed library APIs

Process low-risk areas aggressively.

Process medium-risk and high-risk areas with progressively stronger evidence and validation.

---

# 7. Phase 5 — Global Code Smell and Redundancy Scan

Before editing file-by-file, perform a repository-wide search for patterns that commonly indicate unnecessary code.

Search for:

- duplicated functions
- near-duplicate functions
- repeated constants
- repeated strings
- repeated validation logic
- repeated API transformations
- repeated database queries
- repeated mapping logic
- identical branches
- copy-pasted exception handling
- duplicate coroutine wrappers
- duplicate dispatching logic
- duplicate lifecycle observers
- redundant state layers
- unnecessary wrapper classes
- single-method interfaces that provide no useful abstraction
- manager classes that merely delegate one call
- pass-through repositories
- pass-through use cases
- utility objects with one trivial function
- extension functions that add no meaningful abstraction
- unnecessary `object` singletons
- unnecessary global state
- unnecessary synchronization
- unnecessary `lazy`
- unnecessary caching
- unnecessary memoization
- unnecessary collections/materialization
- repeated `.map {}` / `.filter {}` / `.toList()` pipelines
- unnecessary copying
- needless `let`, `run`, `also`, `apply`, `with`
- nested scope functions reducing readability
- redundant `!!`
- redundant nullable transformations
- unnecessary `lateinit`
- unnecessary `var` where `val` is sufficient
- needless mutable collections
- needless conversion between collection types
- unnecessary temporary variables
- needless object allocations
- repeated regex compilation
- repeated date/time conversions
- repeated serialization/deserialization
- unnecessary coroutine launches
- unnecessary `withContext`
- unnecessary `async/await`
- unnecessary `launch { ... }` wrappers
- nested coroutine scopes
- manual thread handling where existing structured concurrency already suffices
- callback-to-coroutine adapters that duplicate existing APIs
- redundant Flow transformations
- hot Flow/state duplication
- duplicate state holders
- unnecessary `StateFlow`/`SharedFlow` layers
- accidental repeated collectors
- redundant lifecycle scopes
- duplicate dispatchers
- unnecessary synchronization locks
- unnecessary atomic structures
- overly broad exception handling
- unused exception branches
- obsolete compatibility code
- deprecated Android APIs that have an obvious internal replacement
- compatibility wrappers that are no longer needed by supported API levels
- dead feature flags
- obsolete TODO/FIXME workarounds
- commented-out code
- obsolete debug code
- logging left behind from development
- unused resources when confidently verifiable
- stale documentation that describes removed behavior

This scan identifies candidates. It does **not** authorize automatic deletion.

---

# 8. Phase 6 — File-by-File Review Protocol

After the global pass, review **every relevant source file individually**.

Do not review only the files that initially look suspicious.

For each Kotlin file:

## Step 1 — Read the entire file

Do not make a decision based only on the selected function.

Understand:

- class/object responsibilities
- constructors
- properties
- initialization
- public/internal/private APIs
- functions
- nested types
- extension functions
- companion objects
- constants
- lifecycle methods
- coroutine behavior
- Flow behavior
- callbacks
- exception handling
- state mutations

## Step 2 — Identify all symbols

For each class/function/property/constant/interface:

- determine whether it is used
- determine who uses it
- determine whether use is direct or indirect
- determine whether it duplicates another symbol
- determine whether it can be simplified
- determine whether it belongs in this file

## Step 3 — Trace related files

For every meaningful symbol, inspect its related code.

Follow the dependency chain outward.

For example:

```text
ViewModel
   ↓
UseCase
   ↓
Repository
   ↓
DataSource/API/DAO
   ↓
DTO/Entity/Mapper
```

Do not delete or merge a symbol until its surrounding chain has been checked.

## Step 4 — Check callers and consumers

Search for:

- function calls
- property access
- constructor calls
- DI bindings
- interface implementations
- overrides
- imports
- annotations
- generated references
- manifest references
- XML references
- navigation references
- reflection strings
- serialization names
- test references
- build-variant references

## Step 5 — Determine whether each candidate is necessary

Classify each item as one of:

- Keep
- Simplify
- Merge
- Move
- Replace
- Remove
- Needs further investigation

Never use "remove" merely because code is old-looking.

## Step 6 — Check for simpler alternatives

Ask:

- Can this function be removed entirely?
- Can two functions become one?
- Can a wrapper disappear?
- Can a parameter disappear?
- Can a property become local?
- Can mutable state become immutable?
- Can a class become a function?
- Can an interface be removed?
- Can multiple small classes become one coherent component?
- Can a helper be inlined?
- Can a duplicate source of truth be deleted?
- Can a transformation happen once instead of repeatedly?
- Can a dependency be eliminated?

## Step 7 — Check performance implications

Look for genuine optimization opportunities:

- unnecessary allocations
- repeated database work
- repeated network work
- unnecessary serialization
- unnecessary copying
- redundant transformations
- avoidable main-thread work
- excessive coroutine/context switching
- unnecessary Flow emissions
- repeated expensive initialization
- inefficient collection operations
- unnecessary object creation in hot paths
- repeated regex/date formatter creation
- redundant disk access
- repeated preference/database reads

Do not optimize code merely because an operation theoretically has a faster alternative.

The optimization should be reasonable and behavior-preserving.

## Step 8 — Check concurrency and lifecycle

For code using coroutines, Flow, callbacks, threads, executors, WorkManager, or lifecycle APIs, inspect:

- scope ownership
- cancellation
- dispatcher selection
- structured concurrency
- duplicate launches
- race conditions
- shared mutable state
- lifecycle leakage
- repeated collection
- cancellation propagation
- unnecessary context switching
- accidental long-lived references

Do not convert concurrency architecture casually.

The goal is to eliminate unnecessary complexity while preserving correct lifecycle semantics.

---

# 9. Kotlin-Specific Refactoring Rules

## 9.1 Prefer simpler language constructs

When behavior is identical and readability improves, prefer:

- `val` over unnecessary `var`
- expression bodies where genuinely clearer
- direct returns instead of needless nesting
- sealed types/enums only when they reduce complexity
- standard-library operations when they replace verbose custom helpers
- immutable collections/state where practical

Do not force idiomatic Kotlin when it makes behavior or control flow harder to understand.

## 9.2 Scope functions

Review every use of:

- `let`
- `run`
- `with`
- `apply`
- `also`

Remove nested or unnecessary scope functions when a direct implementation is clearer.

## 9.3 Nullability

Look for:

- unnecessary nullable types
- redundant null checks
- repeated safe-call chains
- avoidable `!!`
- nullable values created only to immediately unwrap them
- unnecessary `orEmpty()`
- redundant Elvis branches

Do not change null behavior merely to reduce syntax.

## 9.4 Collections

Look for unnecessary:

- conversions
- intermediate lists
- repeated filtering
- repeated mapping
- mutable collections
- `toList()` / `toSet()` / `toMutableList()` calls
- `map` where `mapNotNull`, `filter`, `flatMap`, or a direct loop better expresses intent

Do not optimize a readable small collection operation into obscure code.

## 9.5 Coroutines and Flow

Look specifically for:

```kotlin
launch { something() }
```

where `something()` could already be called in the current coroutine context.

Also investigate unnecessary:

- nested `launch`
- `async` without concurrent work
- `await` immediately after `async`
- `withContext` when already on the required dispatcher
- dispatcher hops
- Flow conversions
- repeated `stateIn`
- repeated `shareIn`
- unnecessary `flow { emit(...) }` wrappers
- manual callbacks around suspend functions

Do not assume a dispatcher is unnecessary without understanding the called operation.

## 9.6 Android lifecycle

Check carefully for:

- lifecycle observers that duplicate each other
- repeated registrations
- stale observers
- unnecessary lifecycle wrappers
- work surviving the lifecycle unexpectedly
- work being cancelled too aggressively
- repeated ViewModel-to-repository calls
- duplicate event handling

Preserve lifecycle behavior.

## 9.7 Dependency injection

Audit DI modules for:

- unused bindings
- duplicate bindings
- redundant providers
- providers that only return constructors
- unnecessary qualifiers
- unnecessary scopes
- duplicate singleton instances
- bindings that are no longer consumed

Check generated DI code and annotations before removal.

## 9.8 ViewModels

Focus on backend/state logic, not UI restructuring.

Look for:

- redundant state wrappers
- duplicate state transformations
- unused events
- unused functions
- repeated repository calls
- unnecessary coroutine launches
- duplicate error states
- dead state fields
- unnecessary caches

Do not redesign screen UX.

## 9.9 Repositories/use cases/services

Look for artificial layering such as:

```text
ViewModel → UseCase → Repository → DataSource
```

where one layer merely forwards parameters and returns the result unchanged.

Remove unnecessary layers **only when the abstraction provides no meaningful boundary** and repository-wide usage confirms it is safe.

Do not flatten architecture automatically.

---

# 10. Dead Code Removal

Dead-code removal is a major objective, but it must be evidence-based.

Potential dead code includes:

- unused functions
- unused properties
- unused classes
- unused interfaces
- unused implementations
- unreachable branches
- obsolete feature flags
- obsolete compatibility paths
- commented-out code
- stale helper functions
- duplicate utilities
- unused constants
- unused dependency injection bindings
- abandoned experiments
- obsolete migration helpers
- obsolete test helpers

Before deleting:

1. Search repository-wide references.
2. Check generated usage.
3. Check Android component registration.
4. Check reflection and serialization.
5. Check build variants.
6. Check tests.
7. Check configuration.
8. Check dynamic/runtime registration.
9. Determine whether deletion changes an external contract.

Only then remove it.

---

# 11. Duplication Elimination

Identify duplication at multiple levels.

## Exact duplication

Same implementation repeated in multiple locations.

## Structural duplication

Different code expressing the same operation.

## Semantic duplication

Different functions encode the same business rule.

## Configuration duplication

Same settings, constants, or dependency declarations repeated unnecessarily.

When consolidating duplication:

- select one authoritative implementation
- update all callers
- verify behavior remains equivalent
- remove obsolete implementations
- run validation

Avoid creating a giant utility class merely to eliminate duplication.

A small amount of local duplication can be preferable to a large, unrelated abstraction.

---

# 12. Abstraction Reduction

Be aggressively skeptical of unnecessary abstractions.

Question:

- interfaces with one implementation
- wrappers around one method
- managers that only delegate
- factories that only call constructors
- helper classes with no state
- adapters that add no semantic value
- use cases that only forward one call
- repositories that only wrap one data source call
- generic abstractions used exactly once

Remove an abstraction when:

- it has no meaningful boundary
- it increases indirection
- it is not required for testing
- it is not required for DI or architecture
- it is not externally consumed
- it does not encapsulate meaningful policy

Keep abstractions that clearly establish useful boundaries.

---

# 13. API and Data-Layer Optimization

Inspect networking and persistence paths closely.

Look for:

- repeated API calls for the same information
- redundant mapping layers
- duplicate DTO-to-domain conversions
- duplicate entity mappings
- unnecessary database reads
- unnecessary database writes
- unnecessary transaction boundaries
- queries retrieving fields that are never used
- duplicated repository methods
- unnecessary network wrappers
- duplicated error mapping
- repeated parsing
- repeated serialization
- obsolete API compatibility paths

Do not change network/database behavior unless equivalent behavior is clear and validation is available.

Database schema changes require special caution.

Never casually delete a migration, rename a persisted field, or change serialization semantics.

---

# 14. Android Resources and Manifest Audit

Although the main focus is Kotlin backend code, inspect supporting Android definitions because they can invalidate dead-code assumptions.

Check:

- `AndroidManifest.xml`
- activities
- services
- receivers
- providers
- intent filters
- permissions
- metadata
- navigation XML
- data extraction rules
- backup configuration
- resource references
- string/resource identifiers referenced dynamically

Do not delete something solely because Kotlin reference search does not find it.

---

# 15. Generated Code and Reflection

Treat generated/runtime-linked code as special.

Investigate:

- KSP
- KAPT
- annotation processors
- Room generated code
- Dagger/Hilt generated code
- serialization generated adapters
- Moshi/Gson reflection
- Retrofit configuration
- WorkManager registration
- reflection APIs
- class-name strings
- service loaders
- ProGuard/R8 keep rules
- JNI/native integration

Do not edit generated files unless the repository explicitly treats them as source-controlled generated artifacts and the change belongs in the generator/source configuration.

Prefer fixing the source that generates the code.

---

# 16. Tests and Validation Strategy

Use the strongest available validation.

First determine what the project actually has.

Possible validation layers:

1. Kotlin compilation
2. Gradle build
3. Unit tests
4. Instrumentation tests
5. UI tests
6. Android Lint
7. Detekt
8. Ktlint
9. Spotless checks
10. Static analysis
11. Benchmark tests
12. Build variant compilation
13. Release build/R8
14. Other repository-specific checks

## 16.1 Validate incrementally

Do not make hundreds of unrelated changes and then discover the build broke.

Work in logical batches, for example:

```text
Batch 1: dead private helpers
Batch 2: duplicated utilities
Batch 3: repository simplification
Batch 4: coroutine cleanup
Batch 5: dependency cleanup
Batch 6: DI cleanup
Batch 7: broader structural cleanup
```

After each meaningful batch:

- compile
- run relevant tests
- run relevant static checks
- inspect failures
- determine whether the failure was introduced by the refactor

## 16.2 Validation fallback hierarchy

Different repositories will have different tooling.

If tests exist:

- run them.

If instrumentation tests exist:

- run relevant instrumentation tests.

If UI tests exist:

- run them only where necessary to verify behavior touched by the refactor.

If a particular testing layer does not exist:

- use compilation
- static analysis
- type checking
- dependency analysis
- targeted execution where available
- build verification
- repository-provided checks

Do not invent fake tests.

Do not report an unperformed test as passing.

## 16.3 Compile every affected module

If a shared module changes, validate every module that consumes it.

Do not rely only on the module in which the edit occurred.

## 16.4 Release/R8 validation

If the project uses R8/ProGuard, validate a release-like build when the changes affect:

- visibility
- reflection
- serialization
- generated code
- keep rules
- Android components
- networking models
- DI

---

# 17. Handling Missing Test Coverage

When code has poor or nonexistent tests, do not stop.

Instead:

1. Understand the call graph.
2. Preserve the existing contract.
3. Make the smallest safe refactor first.
4. Compile immediately.
5. Run every available check.
6. Compare behavior where practical.
7. Avoid high-risk architectural changes that cannot be validated.

Absence of tests is a limitation, not permission to guess.

---

# 18. Safe Refactoring Order

Prefer this general order unless repository structure suggests a safer sequence:

### Stage A — Mechanical cleanup

- imports
- unused locals
- redundant modifiers
- trivial simplifications
- obvious dead private code
- duplicate local logic

### Stage B — Local structural cleanup

- merge redundant functions
- simplify classes
- remove unnecessary wrappers
- simplify state handling
- reduce scope-function nesting
- simplify nullability

### Stage C — Cross-file cleanup

- consolidate duplicate helpers
- remove redundant interfaces
- simplify repository/use-case chains
- consolidate mappers
- simplify DI

### Stage D — Dependency cleanup

- remove obsolete dependencies
- remove plugins made obsolete by earlier code deletion
- simplify Gradle configuration

### Stage E — Performance cleanup

- eliminate redundant allocations
- remove unnecessary work
- eliminate duplicate IO
- simplify coroutine execution
- reduce redundant Flow transformations

### Stage F — Final global audit

- re-scan entire repository
- re-check dead code
- re-check dependencies
- re-check architecture
- validate all affected variants

---

# 19. What Counts as a Good Refactor

A good refactor generally results in one or more of the following:

- fewer lines of code
- fewer abstractions
- fewer dependencies
- fewer duplicate implementations
- fewer state holders
- fewer unnecessary allocations
- fewer coroutine/context switches
- fewer IO operations
- fewer code paths
- clearer ownership
- clearer interfaces
- simpler control flow
- stronger type safety
- smaller public/internal API surface
- less configuration complexity
- easier testing
- easier maintenance

However:

> **Line-count reduction is not the objective by itself.**

Readable and maintainable code is more important than making the file physically shorter.

---

# 20. What NOT to Do

Do not:

- redesign the UI
- change branding
- change visual behavior
- perform unrelated feature work
- rewrite stable code solely into a preferred style
- migrate the entire architecture for ideological reasons
- replace every library with a personal preference
- upgrade every dependency just because an update exists
- convert everything to Compose
- convert everything to Flow
- convert everything to coroutines
- introduce a new architecture solely for cleanliness
- merge unrelated modules simply to reduce module count
- collapse every interface
- create one giant utility class
- remove tests because they are old
- remove logging without understanding its operational purpose
- remove analytics/event reporting merely because it appears unused locally
- modify database schema casually
- change API contracts casually
- weaken visibility solely to make an analyzer happy
- suppress warnings instead of investigating them
- add comments explaining obvious code
- rewrite comments into unnecessary prose
- leave TODOs for work that the refactor should already complete

---

# 21. Decision Framework for Every Candidate Change

For each candidate, ask these questions in order:

### A. Is it definitely unnecessary?

If yes, remove it after validating all possible consumers.

### B. Is it duplicated?

If yes, determine whether consolidation is safer than leaving both implementations.

### C. Is it unnecessarily complex?

If yes, simplify without altering behavior.

### D. Does the simplification reduce meaningful complexity?

If no, do not change it merely for style.

### E. Could the change affect runtime behavior?

If yes, increase validation.

### F. Could it be referenced indirectly?

If yes, investigate runtime/build/generated consumers before removal.

### G. Does the repository have tests for this area?

If yes, run them.

If no, use the strongest available alternative validation.

### H. Does the change touch a high-risk boundary?

If yes, make the smallest change necessary and validate broadly.

---

# 22. Before Deleting a Function

Do not delete a function until you have checked:

```text
[ ] Direct callers
[ ] Indirect callers
[ ] Overrides / implementations
[ ] Reflection
[ ] DI
[ ] Generated code
[ ] Tests
[ ] Build variants
[ ] Manifest/configuration
[ ] Serialization/API usage
[ ] Runtime registration
[ ] External API possibility
```

Then determine whether it is:

```text
Unused → Remove
Duplicated → Consolidate
Overcomplicated → Simplify
Poorly placed → Move
Necessary abstraction → Keep
Uncertain → Investigate further
```

---

# 23. Before Deleting a Class or Interface

Check:

- constructors
- DI bindings
- implementations
- inherited types
- generic bounds
- annotations
- reflection
- tests
- modules consuming it
- public visibility
- generated code
- serialization
- manifest/configuration
- navigation
- worker/service registration

Then remove only after all relevant consumers are accounted for.

---

# 24. Before Removing a Dependency

Check:

- Kotlin imports
- Java imports
- Gradle plugins
- annotations
- KSP/KAPT
- generated code
- resources
- manifest support
- runtime-only use
- transitive assumptions
- build scripts
- tests
- flavors
- release/debug configurations

Then:

1. remove the dependency
2. sync/configure Gradle if applicable
3. compile affected modules
4. run relevant tests/checks
5. inspect dependency resolution for unintended changes

---

# 25. Before Changing Coroutine/Flow Code

Determine:

- current dispatcher
- owner scope
- cancellation behavior
- exception propagation
- lifecycle
- whether work is concurrent or sequential
- whether the Flow is cold/hot
- sharing/state semantics
- collector count
- upstream lifetime

Then simplify only where semantics remain equivalent.

---

# 26. Before Changing Database Code

Determine:

- schema implications
- migrations
- destructive behavior
- query semantics
- transaction boundaries
- threading constraints
- cached state
- entity consumers
- serialization/API mapping

Never trade database correctness for code cleanliness.

---

# 27. File Review Tracking

Maintain an internal checklist/table while working:

| File | Reviewed | Dependencies Checked | Candidates Found | Changed | Validation | Notes |
|---|---:|---:|---:|---:|---|---|
| `Example.kt` | Yes | Yes | 4 | Yes | Unit tests + compile | Removed duplicate helper |

Every relevant Kotlin source file should ultimately have a review status.

Do not silently skip files.

If a file is intentionally not changed, mark it as reviewed and explain why internally.

---

# 28. Change Batching

Group related modifications into coherent batches.

Good batch:

```text
Remove obsolete network helper layer
├── ApiHelper.kt
├── Repository.kt
├── Mapper.kt
└── related tests
```

Bad batch:

```text
Random cleanup across 40 unrelated modules
```

Each batch should have a clear purpose.

This makes failures easier to identify and reversions safer.

---

# 29. Regression Detection

After significant changes, compare the resulting implementation against the pre-refactor behavior.

Look specifically for accidental changes to:

- returned values
- null behavior
- exception types
- exception timing
- ordering
- threading
- lifecycle
- caching
- persistence
- API requests
- request parameters
- response parsing
- retries
- timeouts
- logging/error reporting where operationally significant
- analytics
- feature flags
- configuration

A refactor should not silently change these.

---

# 30. Final Repository-Wide Pass

After all edits, do not stop at a successful build.

Perform another complete audit.

Search again for:

- dead code
- duplicate functions
- duplicate classes
- duplicate constants
- unused dependencies
- unnecessary interfaces
- redundant wrappers
- redundant coroutine layers
- unnecessary collection transformations
- unnecessary mutable state
- obsolete compatibility code
- commented-out code
- TODOs introduced or exposed by the refactor
- stale imports
- stale documentation
- stale ProGuard/R8 rules
- stale DI bindings
- stale Gradle dependencies

The first refactor pass can reveal additional redundancy that was previously obscured.

Perform at least one second-order cleanup pass.

---

# 31. Final Validation Matrix

Before declaring completion, validate as broadly as the project permits.

```text
[ ] Project configuration succeeds
[ ] All affected Gradle modules compile
[ ] Unit tests pass
[ ] Instrumentation tests pass, if present
[ ] Relevant UI tests pass, if present
[ ] Android Lint passes or known pre-existing failures are documented
[ ] Detekt passes, if present
[ ] Ktlint/Spotless passes, if present
[ ] Relevant build variants compile
[ ] Release/R8 build validated where relevant
[ ] No accidental UI changes introduced
[ ] No external contracts accidentally changed
[ ] No database migration behavior was altered unintentionally
[ ] No runtime-reflection/generated-code paths were broken
[ ] Final dependency graph reviewed
[ ] Final dead-code scan completed
[ ] Final duplicate-code scan completed
[ ] Every relevant Kotlin file reviewed
```

If a validation step cannot be executed, record that clearly.

Never mark it as passed.

---

# 32. Completion Criteria

The refactor is complete only when:

1. The repository structure and dependencies were inspected first.
2. Major architectural components were mapped before broad modification.
3. Every relevant Kotlin source file was reviewed.
4. Related files were inspected before deleting or changing meaningful code.
5. Unnecessary and redundant code was removed where safety was demonstrable.
6. Backend/internal architecture was simplified where appropriate.
7. UI behavior was intentionally left stable except for necessary integration adjustments.
8. Dependencies were reviewed and obsolete ones removed where safe.
9. Tests and available static/build validation were executed.
10. A final repository-wide redundancy/dead-code audit was completed.
11. No unverified claim of correctness was made.

---

# 33. Required Final Report

At the end, produce a concise but complete engineering summary containing:

## Repository Summary

- modules inspected
- Kotlin source files inspected
- major architectural areas inspected
- dependency systems inspected

## Refactoring Summary

- files changed
- files deleted
- classes removed
- functions removed
- duplicate implementations consolidated
- dependencies removed
- architecture simplified
- performance-related improvements
- coroutine/Flow improvements
- database/data-layer improvements

## Validation Summary

For every available validation system, report:

```text
PASS
FAIL
NOT AVAILABLE
NOT RUN
```

Do not hide failures.

## Remaining Risks

List anything that could not be confidently verified because of:

- missing tests
- unavailable services
- runtime-only behavior
- reflection
- generated code
- external integrations
- unavailable build infrastructure
- inaccessible credentials/configuration

## Recommended Manual Verification

Only include manual checks that are genuinely necessary.

Prioritize high-risk functionality such as:

- login/authentication
- API calls
- persistence
- background work
- notifications
- deep links
- navigation integration
- major business workflows

Do not recommend manually rechecking every UI screen when the refactor did not materially affect UI code.

---

# 34. Agent Behavior Rules

While performing this work:

- Think repository-wide, not file-by-file in isolation.
- Trace dependencies before deleting symbols.
- Prefer deletion over abstraction when code is genuinely unnecessary.
- Prefer simplification over rewriting.
- Prefer one authoritative implementation over duplicated logic.
- Preserve behavior over aesthetic preferences.
- Preserve UI unless changes are required for backend integration.
- Validate continuously.
- Treat missing tests as uncertainty, not permission.
- Be conservative at external/runtime boundaries.
- Be aggressive with confidently dead internal code.
- Never claim validation was performed when it was not.
- Never stop after fixing only obvious files.
- Re-scan after major cleanup because removing one abstraction can expose another redundant layer.
- Do not leave the codebase in a half-migrated state.
- Do not introduce compatibility shims unless they are actually required.
- Remove temporary migration scaffolding once all consumers are migrated.

---

# 35. Operating Principle

The desired end state is:

```text
Before

UI
 ↓
ViewModel
 ↓
Wrapper
 ↓
UseCase
 ↓
Wrapper
 ↓
Repository
 ↓
Mapper
 ↓
Wrapper
 ↓
DataSource
 ↓
API
```

becomes, where safely justified:

```text
UI
 ↓
ViewModel
 ↓
Meaningful domain/data boundary
 ↓
API / Database
```

Or, where the full layering is genuinely useful:

```text
UI
 ↓
ViewModel
 ↓
UseCase
 ↓
Repository
 ↓
DataSource
```

The goal is **not** to force either structure.

The goal is to eliminate layers that provide no meaningful value while retaining layers that provide real responsibility boundaries.

The final question for every refactor is:

> **Does this code need to exist, in this form, at this location, with this responsibility?**

If the answer is no, simplify it safely.

If the answer is uncertain, investigate before changing it.

If the answer is yes, leave it alone unless a concrete optimization can be demonstrated without altering behavior.
