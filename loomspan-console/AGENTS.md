# Loomspan Console — Agent Notes

## Development stage

This project is pre-release and under active development. There are no external
consumers to preserve, so **breaking internal changes are welcome and preferred
over compatibility shims**. When a contract needs to change:

- Change record/struct/DTO signatures in place and update every construction
  site atomically in the same change.
- Do not add overloads, aliases, legacy readers, fallbacks, bridges, or dual
  behavior. The roadmap's cross-cutting invariants already forbid these absent
  an identified protected contract.
- Java, Go, TypeScript, fixtures, and semantic tests move together. The console
  rejects any application whose `consoleCompatibilityVersion` differs from its
  exact release string, so the layers are always in lockstep.
- The trace catalog is in-memory and begins empty after restart, so cataloged
  trace metadata never needs backfilling or migration.

This concerns source and wire contracts. It is not license for destructive
operations on a developer's machine or data (file deletion, history rewrites)
without asking.

## Verification commands

### Standard (no race detector)
```text
go test ./...
go run ./internal/buildtool verify
```

### Race detector (requires gcc/cgo)
The `-race` flag requires `CGO_ENABLED=1` and a C compiler. On this Windows machine, gcc is available via MSYS2 but is not on the default PATH. Prefix the command with the mingw64 bin directory:

```powershell
$env:PATH = "C:\msys64\mingw64\bin;" + $env:PATH
$env:CGO_ENABLED = "1"
go test -race ./...
```

### Java fixture corpus
The fixture corpus test in `loomspan-spring-boot-starter` validates that the Go trace-analysis processor matches the Java reference implementation byte-for-byte:

```powershell
.\mvnw.cmd -pl loomspan-spring-boot-starter test -Dtest=ConsoleTraceFixtureCorpusTest -DfailIfNoTests=false
```

To regenerate committed fixtures (after intentionally changing the corpus):

```powershell
.\mvnw.cmd -pl loomspan-spring-boot-starter test -Dtest=ConsoleTraceFixtureCorpusTest -Dloomspan.console.fixtures.regenerate=true -DfailIfNoTests=false
```

## Line endings

All committed fixture files (`loomspan-console-fixtures/traces/*.ndjson`, `loomspan-console-fixtures/expected/*.json`) and this repo's `README.md` use LF line endings. The Java test's `writeExpected` and `writeInvalid` helpers normalize to LF explicitly to avoid CRLF on Windows.
