# Flamingock CLI Specification

The CLI follows a **docker-like style**:

```
flamingock [command] [operation] [options]
```

## General Principles

- The CLI is **lightweight**:
  - It only builds an `OpsClient` via the `OpsClientBuilder`.
  - All business logic is delegated to the `OpsClient` (or components it delegates to).
  - The CLI only parses input, calls `OpsClient`, and formats output.

- Default behavior:
  - `flamingock audit list` (with no flags) → shows the **current snapshot** (latest state per change).
  - Flags modify or filter the behavior.

---

## Commands

### 1. `audit list`

List audits, with optional filters.

**Base command:**
```
flamingock audit list
```

**Options:**
- `--issues` (boolean, no value) → only audits with issues
- `--history` (boolean, no value) → full chronological audit history (all states, ordered by time)
- `--since <date>` (string, ISO-8601, e.g. `2025-01-01T00:00:00`) → list audits since a given date
- `--limit <n>` (integer) → pagination limit
- `--page <n>` (integer) → pagination page

**Examples:**
```bash
flamingock audit list
flamingock audit list --issues
flamingock audit list --history
flamingock audit list --since 2025-01-01T00:00:00
flamingock audit list --limit 50 --page 2
```

---

### 2. `audit mark`

Forcefully mark a given **change** with a new state.

**Base command:**
```
flamingock audit mark --change-unit <id> --state <state>
```

**Options:**
- `--change-unit <id>` / `-c <id>` → the changeId (required)
- `--state <state>` / `-s <state>` → the state to mark (`APPLIED` or `ROLLED_BACK`) (required)

**Examples:**
```bash
flamingock audit mark --change-unit CU123 --state APPLIED
flamingock audit mark -c CU123 -s ROLLED_BACK
```

---

## Global Options

- `--config <file>` / `-c <file>` → path to configuration file (default: `flamingock-cli.yml`)
- `--help` / `-h` → show help information
- `--version` / `-V` → show version information

---

## Summary

- Command structure: `flamingock [command] [operation] [options]`
- For now, only the `audit` command is implemented with:
  - `list` (default snapshot, optional filters `--issues`, `--history`, `--since`, pagination)
  - `mark` (force a state for a change)
- The CLI is a thin layer: config → build OpsClient → call → format result.

---

## Implementation Notes

- **Default behavior change**: The `audit list` command now shows a **snapshot view** (latest state per change) by default, not just conflicted entries.
- **History flag**: Use `--history` to get the full chronological audit history.
- **Issues flag**: Use `--issues` to filter for only entries with problems.
- **Since flag**: Use `--since` with ISO-8601 format to filter by date.
- **Pagination**: Client-side pagination is supported with `--limit` and `--page` flags.
