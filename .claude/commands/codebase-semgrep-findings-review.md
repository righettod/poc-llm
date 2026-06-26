---
description: Review a Semgrep SARIF or JSON output file, validate each finding using semantic code analysis, and produce a filtered report with CONFIRMED, FALSE_POSITIVE, or NEEDS_HUMAN_REVIEW verdicts. Drops false positives that pattern-based rules cannot distinguish from real issues.
argument-hint: "<semgrep-output-file> [source-root (optional, default: .)] [min-confidence: CONFIRMED|PARTIAL (optional, default: PARTIAL)]"
allowed-tools: Read, Glob, Grep, Bash, TaskCreate, Write
---


Review each finding from a Semgrep output file and determine whether it is a real issue, a false positive, or requires human review.

The objective is to apply **semantic reasoning** to every Semgrep finding: read the flagged code location and its surrounding context, understand what the code actually does, and decide whether the pattern match reflects a genuine security weakness or a false positive that a purely syntactic rule cannot distinguish.

Parse `$ARGUMENTS` as follows:

- **First token**: path to the Semgrep output file (SARIF `.sarif` or JSON `.json` format). Required.
- **Second token** (optional): base directory of the source code under review. All relative file paths extracted from the Semgrep output are resolved against this root. Default: `.` (current working directory). If the second token is exactly `CONFIRMED` or `PARTIAL` it is treated as the min-confidence threshold instead (backward-compatible shorthand when no source root is needed).
- **Third token** (optional): minimum confidence threshold for inclusion in output. Accepted values: `CONFIRMED` (only confirmed findings) or `PARTIAL` (confirmed + needs-human-review findings). Default: `PARTIAL`. `FALSE_POSITIVE` verdicts are always excluded from the findings list but are recorded in the summary table.

## Scope

If no file path is provided in `$ARGUMENTS`, exit immediately with: `Error: a Semgrep output file path is required. Usage: /codebase-semgrep-findings-review <path-to-sarif-or-json> [source-root] [CONFIRMED|PARTIAL]`.

## Methodology

Follow this procedure, in order:

1. **Parse the Semgrep output file** using Read or Bash. Detect the format automatically:
   - **SARIF** (`.sarif` or `"$schema": "https://...sarif..."` key present): extract findings from `runs[*].results[]`. For each result, extract: `ruleId`, `message.text`, `locations[0].physicalLocation.artifactLocation.uri`, `locations[0].physicalLocation.region.startLine`, `level`.
   - **JSON** (Semgrep native format): extract findings from `results[]`. For each result, extract: `check_id`, `message`, `path`, `start.line`, `extra.severity`, `extra.metadata` (if present).

   If the file cannot be read or parsed, report the error and stop.

2. **Deduplicate** findings by `(rule_id, file_path, start_line)` before analysis. Identical tuples are collapsed into one finding. Record the deduplicated count in the summary.

3. **Parallelize when it pays off.** If there are more than 10 findings, spawn subagents via the TaskCreate tool to analyze them concurrently — batch findings by file so each subagent works on a coherent slice of the codebase. Each subagent prompt must:
   - Include the list of findings to analyze (rule_id, file_path, start_line, message).
   - Include the resolved `source-root` value so the subagent can locate source files correctly.
   - Include the full Methodology (steps 4–5), the Verdict definitions, the Semantic reasoning guide, and the output format from this command.
   - Declare: "You may use: Read, Glob, Grep, Bash."

   With 10 or fewer findings, analyze them inline without subagents.

4. **Analyze each finding** by reading the flagged location and its context:
   - Resolve each file path from the Semgrep output against the `source-root` argument: if the path is relative, prepend `source-root`; if it is already absolute, use it as-is.
   - Read the resolved file at the reported line, plus **20 lines before and after** for context.
   - If the finding involves a function call, read the function's definition if it is in the same codebase (use Grep/Glob scoped to `source-root` to locate it).
   - If the finding involves a class field or constant, read the class definition for additional context.
   - Apply the **Semantic reasoning guide** below to decide the verdict.

5. **Collect findings.** When subagents were used, collect all their findings into a single set before proceeding to step 6. When analyzing inline, proceed directly to step 6.

6. **Deduplicate** by `(rule_id, file_path, start_line)` across subagent outputs (in case batches overlapped), then **order** findings by Verdict (`CONFIRMED` first, then `NEEDS_HUMAN_REVIEW`, then `FALSE_POSITIVE`); within each verdict group, order by Severity (`CRITICAL` first).

## Semantic reasoning guide

Use this guide to decide whether a Semgrep pattern match reflects a real security issue. Apply it in order; stop at the first rule that yields a verdict.

**Cryptography and hashing**

- A weak algorithm (`MD5`, `SHA1`, `DES`, `RC4`, `TripleDES`) used as a **cryptographic primitive for security-sensitive purposes** (password hashing, HMAC, token generation, signature verification, key derivation) → `CONFIRMED`.
- The same algorithm used for **non-security purposes** (cache key, content fingerprint for deduplication, checksum for data integrity without security implications, unit test fixture) → `FALSE_POSITIVE`.
- Cannot determine the purpose from the surrounding context alone → `NEEDS_HUMAN_REVIEW`.

**Hardcoded credentials and secrets**

- A string that looks like a secret is assigned to a variable whose name contains `password`, `secret`, `key`, `token`, `credential`, `api_key`, `private` (case-insensitive), and the value is not a placeholder (`changeme`, `todo`, `example`, `test`, `xxx`, `<...>`, `your-*`) → `CONFIRMED`.
- The value is clearly a placeholder, a test fixture string, a public identifier (OAuth client ID, not secret), or is immediately overridden by an environment variable read → `FALSE_POSITIVE`.
- Cannot determine from context → `NEEDS_HUMAN_REVIEW`.

**Disabled TLS / certificate verification**

- `verify=False`, `CURLOPT_SSL_VERIFYPEER = 0`, `InsecureRequestWarning`, `checkServerIdentity: () => undefined`, or equivalent, present in **production code paths** (not inside a block clearly guarded by a `test`, `dev`, `debug`, or `localhost` condition) → `CONFIRMED`.
- Present only in test files (`test_*.py`, `*.test.js`, `*_test.go`, `spec/**`) or guarded by an explicit non-production condition → `FALSE_POSITIVE`.

**Dangerous function calls (`eval`, `exec`, `compile`, `Function()`)**

- The argument to the dangerous call contains a **variable** (not a pure string literal) → `CONFIRMED` if the variable is reachable from an entry point, `NEEDS_HUMAN_REVIEW` if the call is internal-only.
- The argument is a **pure string literal** with no variable interpolation → `FALSE_POSITIVE`.

**SQL / NoSQL query construction**

- String concatenation or interpolation used to build a query, and at least one interpolated value is derived from a function parameter or field that could originate outside this module → `CONFIRMED`.
- All interpolated values are **hardcoded constants** or **integer literals** with no string input → `FALSE_POSITIVE`.
- Interpolated values are present but their origin cannot be determined from the local context → `NEEDS_HUMAN_REVIEW`.

**Path construction and file access**

- User-controlled or parameter-derived value used in `open()`, `readFile()`, `Path()`, `os.path.join()`, etc., with no `realpath` / canonical-path guard comparing against an allowed base → `CONFIRMED`.
- All path components are **hardcoded string literals** or come from a server-side configuration file → `FALSE_POSITIVE`.

**Weak random number generation**

- `Math.random()`, `rand()`, `random.random()`, or similar non-CSPRNG used for a **security-sensitive output** (session token, password reset token, CSRF token, OTP, cryptographic nonce) → `CONFIRMED`.
- Same function used for a **non-security purpose** (UI animation, shuffle, A/B test bucket, game logic) → `FALSE_POSITIVE`.
- Cannot determine purpose → `NEEDS_HUMAN_REVIEW`.

**Missing security headers / insecure defaults**

- A response is constructed and dispatched without a required security header (`Content-Security-Policy`, `X-Frame-Options`, `Strict-Transport-Security`, etc.), and no middleware upstream sets it (verify with Grep) → `CONFIRMED`.
- A middleware, framework default, or decorator provably sets the header for all responses of this type → `FALSE_POSITIVE`.
- Middleware exists but its scope cannot be confirmed → `NEEDS_HUMAN_REVIEW`.

**Dead code and unreachable paths**

- If the flagged code is inside a block that is provably unreachable (e.g. `if (false)`, a function that is never called anywhere in the codebase per Grep) → `FALSE_POSITIVE` regardless of the rule category. Note the unreachability evidence in the rationale.

**Default for unrecognized rule categories**

- If the Semgrep rule does not match any category above and the code at the flagged location clearly matches the rule's intent → `CONFIRMED`.
- If the match is syntactically correct but the surrounding context makes exploitation implausible → `NEEDS_HUMAN_REVIEW`.
- Never silently drop a finding as a false positive without a stated rationale.

## Verdict definitions

- **CONFIRMED**: The finding is a genuine security weakness. The semantic context confirms that the flagged code is security-sensitive and the concern is real.
- **FALSE_POSITIVE**: The pattern matched but the semantic context shows the code is not security-sensitive in this usage, or the code is provably unreachable.
- **NEEDS_HUMAN_REVIEW**: The finding is plausible but the local context is insufficient to confirm or dismiss it. A human reviewer must inspect the flagged location.

## Severity mapping

If the Semgrep output provides a severity level, preserve it. If absent or if you override it based on semantic context, use this mapping:

- **CRITICAL**: code injection, SQL/NoSQL injection, command injection, insecure deserialization, authentication bypass.
- **HIGH**: path traversal, hardcoded credential, disabled TLS, weak key for encryption/signing, SSRF.
- **MEDIUM**: weak RNG for security-sensitive purpose, deprecated hash algorithm for security use, missing critical security header, XSS.
- **LOW**: deprecated hash algorithm for non-security use flagged in error, informational missing header, dead code with a theoretical weakness.

Downgrade severity by one level if the flagged code is behind an authenticated endpoint with no known bypass path.

## Output rules

Before listing individual findings, output a **summary table**:

| Verdict              | CRITICAL | HIGH | MEDIUM | LOW | Total |
| -------------------- | -------- | ---- | ------ | --- | ----- |
| CONFIRMED            | n        | n    | n      | n   | n     |
| NEEDS_HUMAN_REVIEW   | n        | n    | n      | n   | n     |
| FALSE_POSITIVE       | n        | n    | n      | n   | n     |
| **Total analyzed**   | n        | n    | n      | n   | n     |

Below the table, add one line: `Semgrep findings before deduplication: N → after deduplication: N`.

Then list findings filtered by the `min-confidence` argument (default: include `CONFIRMED` and `NEEDS_HUMAN_REVIEW`; always exclude `FALSE_POSITIVE` from the findings list). `FALSE_POSITIVE` findings are only represented in the summary table.

For each finding, use this structure:

- **Finding identifier**: Unique number starting at 1, incremented per finding.

- **Verdict**: CONFIRMED / FALSE_POSITIVE / NEEDS_HUMAN_REVIEW.

- **Severity**: CRITICAL / HIGH / MEDIUM / LOW.

- **Rule**: The Semgrep rule ID as reported in the output (e.g. `python.lang.security.audit.md5-used.md5-used`).

- **Location**: `path/to/file.py:42`.

- **Semgrep message**: The original message from the Semgrep finding, reproduced verbatim on a single line.

- **Rationale**: One to three sentences explaining why this verdict was assigned. For `FALSE_POSITIVE`, the rationale must identify the specific contextual evidence that rules out the concern (e.g. "The MD5 call at line 42 is used to generate a cache key for static asset filenames; no authentication or authorization decision depends on this value."). For `NEEDS_HUMAN_REVIEW`, state what context is missing and what a reviewer should check.

- **Recommended action** (only when Verdict is `CONFIRMED`): A single concrete remediation step, e.g. "Replace `MD5` with `SHA-256` via `hashlib.sha256()`" or "Use a parameterized query via `cursor.execute(sql, params)`".

After displaying the output, determine the output filename as `Semgrep-Review-$DATE.md` where `$DATE` is today's date in `YYYY-MM-DD` format, then save the full output — summary table and all individual findings including `FALSE_POSITIVE` entries — to that file in the current working directory using the Write tool. The saved file always contains all verdicts regardless of the `min-confidence` filter applied to the console output, so the full audit trail is preserved.
