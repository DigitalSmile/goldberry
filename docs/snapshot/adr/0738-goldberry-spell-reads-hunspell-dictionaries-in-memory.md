# ADR-0738: `goldberry-spell` reads Hunspell dictionaries in memory, behind one lock

- **Status:** Accepted
- **Date:** 2026-11-22
- **Relates to:** ADR-0733 (D1 and D2, built here), ADR-0735 (the provider
  service this implements)

## Context

D1 chose Lucene's Hunspell, in Java, to read the same `.aff`/`.dic` files the
C library reads. D2 put it in a module of its own, provided to `:core` as a
`SpellCheckerProvider`. The plan named two risks for this phase. One was
Lucene's offline sort through a temporary `Directory`. The other was whether
its checker may be called from several threads.

## Decision

1. **`:spell`, published as `goldberry-spell`, optional in the umbrella.** It
   depends on `lucene-analysis-common` 10.6.0, which brings `lucene-core`.
   Both are JPMS modules under Apache 2.0, the toolkit's own licence. They
   are Maven dependencies, not bundled, so they add no row to the
   third-party notices. The module exports `dev.goldberry.spell`
   (`HunspellChecker`, for an application that holds a checker itself) and
   provides `HunspellProvider` in `module-info` and in `META-INF/services`.
2. **The dictionary is sorted in memory.** `SortingStrategy.inMemory()` takes
   the `Directory` out, so loading writes nothing to disk and needs no
   temporary directory. The `.aff` file's `SET` line decides the encoding of
   both files, and Lucene follows it. A parse failure is an `IOException`
   naming the locale.
3. **One `ReentrantLock` around every call.** Lucene's stemmer keeps scratch
   buffers, so a `Hunspell` is not safe from two threads at once. A check
   takes microseconds, and a field asks from one background batch at a time.
   The lock is a `ReentrantLock` rather than `synchronized` so that a virtual
   thread waiting on it does not pin its carrier.
4. **A suggestion searches for at most 250 ms** (`SUGGEST_MILLIS`,
   `TimeoutPolicy.RETURN_PARTIAL_RESULT`) and answers what it found by then.
   A correct word gets no suggestions.
5. **The tests' dictionaries are written for the tests.** One is a UTF-8 English
   dictionary with a suffix, a prefix and `REP` rules, and one is ISO 8859-1
   German, so no dictionary licence enters the repository.

## Consequences

- An application adds `goldberry-spell` and calls `SpellCheckers.load` with
  its own dictionary files. It never names this module's classes.
- The jars add 6.6 MB to an application that adds the module, and nothing to
  one that does not.
- A GraalVM native image may need reachability metadata for Lucene's
  service lookups. That is checked when the showcase uses the module
  (phase 6).
- The benchmark the plan named for this phase moves to phase 6, where a real
  `en_US` dictionary makes the numbers mean something.
