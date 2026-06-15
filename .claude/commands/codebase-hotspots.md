---
description: Generate a list of locations in the codebase where risky processing is performed from a security perspective.
argument-hint: [entry-point-or-package]
allowed-tools: Read, Glob, Grep, Bash, TaskCreate, Write
---

Generate a list of locations in the codebase where risky processing is performed from a security perspective.

The objective is to spot, for each entry point, the location in the code in which the information received is used to perform **risky** processing from a security perspective.

**Entry point** is where information enters the codebase: `main()` functions, HTTP route definitions, CLI command handlers, message/queue consumers, exported public API functions.

Input information is considered **not validated** when there is no *effective* check for the specific sink it reaches. A length check or type cast is not effective validation for a path-traversal sink; an allow-list is. When you cannot determine whether validation happens upstream (middleware, decorator, framework binding, another layer you did not read), report the finding with **Confidence: PARTIAL** rather than dropping it.

## Scope

If an argument is provided in `$ARGUMENTS`, restrict the analysis to that entry point or package. Otherwise, analyze all entry points in the codebase.

## Methodology

Follow this procedure, in order:

1. **Enumerate entry points** using Glob/Grep (route definitions, `main()`, CLI handlers, queue consumers, exported public API functions). Restrict to `$ARGUMENTS` if provided.
2. **Parallelize when it pays off.** If there are more than 5 entry points, spawn subagents via the TaskCreate tool to trace them concurrently — one subagent per entry point, or a batch of entry points per subagent for large codebases — to keep contexts isolated. Each subagent prompt must:
   - State the entry point(s) to trace.
   - Include the full Methodology (steps 3–4), Risky processing list, Effective validation reference, Confidence and Severity definitions, and output format from this command.
   - Declare: "You may use: Read, Glob, Grep, Bash."
   - Respect the same `$ARGUMENTS` scope restriction.

   With 5 or fewer entry points, trace them inline without subagents.
3. **Trace the data flow (taint analysis)** from each tainted input through the call chain until it reaches a sink listed below. Record the path: `source → intermediate calls → sink:line`. Trace through framework middleware, decorators, and binding layers **only when their source is readable**; if you cannot read them, report the finding as **Confidence: PARTIAL** rather than assuming they sanitize. For **second-order sinks** (data written to a store and later retrieved as input to another sink, e.g. stored XSS or second-order SQL injection), record both the write path and the read-then-sink path as separate taint paths under the same finding.
4. **Confirm the sink actually executes/evaluates the input** before reporting Confidence: YES (the regex is actually run, the command is actually exec'd, the archive is actually extracted, the query is actually executed).
5. **Collect findings.** When subagents were used, collect all their findings into a single set before proceeding to step 6. When tracing inline, proceed directly to step 6.
6. **Deduplicate** findings by the (entry point, sink location) pair, then **group** the output by entry point; **within each group, order** by Severity (CRITICAL first), then by Confidence (YES first). A single sink reachable from several entry points is a distinct taint path for each, so it appears once under each entry point; only identical (entry point, sink location) findings are collapsed.

## Risky processing

The following processing must be considered **risky** from a security perspective:

- Input information not validated and used within an XML/XSD parser (XXE).
- Input information not validated and used to create a message written into a logging function (log injection/forging).
- Input information not validated and used to perform a network request (SSRF, including DNS-rebinding and redirect-based variants).
- Input information not validated and used to create an HTTP response (response splitting, header injection, open redirect via `Location`/`Set-Cookie`).
- Input information not validated and used to render HTML or write to the DOM — `innerHTML`, `document.write`, `dangerouslySetInnerHTML`, unescaped template output, or equivalent — (XSS).
- Input information not validated and used to generate Comma-Separated Values (CSV) content (CSV/formula injection).
- Input information not validated and used for authentication decisions (authentication bypass).
- Input information not validated and used for authorization decisions (including IDOR / object reference, mass assignment / object binding).
- Input information not validated and used for Cross-Origin Resource Sharing (CORS) decisions (CORS validation bypass).
- Input information not validated and used to decompress an archive (zip-slip, decompression bomb).
- Input information not validated and used to access a filesystem (path traversal, file upload with input-controlled filename/extension/content-type).
- Input information not validated and used for a shell or process execution (command injection, tainted format string).
- Input information not validated and used to create a regular expression that is evaluated (ReDoS).
- Input information not validated and used to construct a SQL/NoSQL/ORM/LDAP/XPath/GraphQL query (injection).
- Input information not validated and used in a template engine (server-side template injection).
- Input information not validated and passed to a dynamic code evaluation function such as `eval()`, `Function()`, `exec()`, `compile()`, or equivalent (code injection).
- Input information not validated and used for a deserialization processing using another format than JSON (insecure deserialization).
- Input information not validated and used to merge into or assign properties of an object in a way that can overwrite inherited properties such as `__proto__`, `constructor`, or `prototype` — e.g. `_.merge`, `Object.assign`, recursive merge with user-controlled keys — (prototype pollution).
- Input information not validated and used to generate random values for a security-sensitive purpose (weak RNG, e.g. a predictable or input-derived seed, or a non-CSPRNG such as `Math.random`).
- Input information not validated and used to compute a cryptographic digest without a values separator (hash input ambiguity).
- Input information not validated and used to control a resource allocation size, loop iteration count, or similar bound — resulting in memory exhaustion, CPU exhaustion, or excessive I/O — (uncontrolled resource allocation / DoS).

## Effective validation reference

Use this table to decide whether a guard constitutes *effective* validation for a given sink. When you cannot determine which category applies, default to **Confidence: PARTIAL**.

| Sink | Effective | NOT effective |
|---|---|---|
| SSRF | Exact-match allow-list of hosts/IPs | Regex on URL string, prefix check, URL parsing then domain check |
| Path traversal | `realpath()` / canonical path compared against allowed base | Blocking `..`, string contains check |
| Command injection | Parameterized exec (no shell invocation), allow-list of values | Escaping or quoting user input inside a shell string |
| SQL / NoSQL injection | Parameterized queries / prepared statements | String escaping, regex replacement |
| ReDoS | Static regex with no user-controlled characters | Any user input present anywhere in the pattern |
| XSS | Context-aware output encoding, strict CSP | HTML entity encoding applied once to the whole string |
| Open redirect | Exact-match allow-list of redirect targets | Checking that URL starts with `/`, domain regex |
| Deserialization | Allow-list of expected types enforced before deserializing | Blocking known gadget class names |
| XXE | External entity processing disabled in parser config | Filtering `<!DOCTYPE` or `<!ENTITY` from the input string |
| CORS bypass | Exact-match allow-list of origins | Wildcard `*`, regex on origin string, checking that origin ends with a domain suffix |
| Zip-slip / decompression bomb | Canonical path check before extraction, enforced entry count and decompressed-size limits | Blocking `..` in entry names, trusting archive metadata for size |
| Authentication bypass | Verified signed token (JWT RS256/ES256) or server-side session; signature verified before any claim is trusted | Trusting user-supplied role or identity fields, HS256 with an exposed or weak secret |
| Authorization bypass / IDOR | Server-side ownership check on every resource access, re-fetched from the authoritative store | Trusting client-supplied resource ID without re-checking ownership |
| Server-side template injection | Logic-less templates (Mustache, Handlebars in escape-only mode) or sandboxed engine with no access to dangerous globals | Escaping user input before inserting it into a template string |
| Code injection | No dynamic evaluation of user input; input never reaches `eval`/`exec`/`compile` | Sanitizing or escaping input before passing it to an eval function |
| Prototype pollution | `Object.create(null)` for merged objects; JSON schema validation rejecting `__proto__`, `constructor`, `prototype` keys before merge | Deleting `__proto__` from input after the merge has already occurred |
| Weak RNG | CSPRNG (`crypto.randomBytes`, `SecureRandom`, `os.urandom`) with no user-controlled seed | `Math.random()`, `rand()`, seeding with any user-controllable value |
| Log injection | Strip or encode newline characters (`\n`, `\r`) before the value reaches a log call | General HTML encoding, which does not neutralize log-format newlines |
| CSV / formula injection | Prefix any cell starting with `=`, `+`, `-`, `@`, `\t`, or `\r` with a single quote `'` | Quoting values with `"` without prefixing formula-trigger characters |
| Hash input ambiguity | Fixed-length inputs, length-prefixed fields, or HMAC | Simple concatenation with a separator character that could appear in user input |
| Uncontrolled resource allocation | Enforced server-side hard limit on size, entry count, or iteration bound before processing begins | Trusting a client-supplied limit value, no limit at all |

## Confidence

Use the following value for the **Confidence** indicator:

- **YES**: You traced the full taint path in code and can provide a proof-of-concept input that reaches the sink.
- **PARTIAL**: The taint path is plausible but you could not confirm that upstream validation (middleware, decorator, framework binding, or another layer not read) is absent. The sink is real; the absence of a guard is uncertain.
- **NO**: The finding is theoretical — no concrete code evidence of a reachable path exists.

Report findings of all three confidence levels. Never silently drop a PARTIAL or NO finding.

## Severity

Assign **Severity** from the weakness category, then apply the adjustment rules below. Use this default mapping:

- **CRITICAL**: command injection, code injection, SQL/NoSQL/ORM/LDAP/XPath/GraphQL injection, insecure deserialization, server-side template injection, authentication bypass.
- **HIGH**: SSRF, path traversal / unsafe file access, zip-slip / decompression bomb, authorization bypass (including IDOR / mass assignment), XXE, XSS (stored), prototype pollution.
- **MEDIUM**: XSS (reflected / DOM), response splitting / header injection / open redirect, CORS validation bypass, log injection, ReDoS, weak RNG for a security-sensitive purpose, uncontrolled resource allocation.
- **LOW**: CSV / formula injection, cryptographic digest without a values separator.

**Adjustment rules** (apply in order; each rule moves the severity by one level at most):

- **Downgrade** by one level if exploiting the finding requires prior authentication (i.e. the entry point is behind a login gate with no known bypass): CRITICAL → HIGH, HIGH → MEDIUM, MEDIUM → LOW. Do not downgrade LOW findings.
- **Upgrade** by one level if the entry point is unauthenticated, internet-facing, and processes sensitive data (PII, credentials, financial data, session tokens): LOW → MEDIUM, MEDIUM → HIGH, HIGH → CRITICAL. Do not upgrade CRITICAL findings.

If both rules apply to the same finding, they cancel and the base severity stands.

## Output rules

Before listing individual findings, output a **summary table** so the reader can triage at a glance:

| Entry point | CRITICAL | HIGH | MEDIUM | LOW | Total |
|---|---|---|---|---|---|
| `path/to/entrypoint` | n | n | n | n | n |
| … | | | | | |

Entry points with no findings must still appear in the table with zero counts across all columns, confirming they were analyzed. Counts include findings of all confidence levels (YES, PARTIAL, NO) — consult individual findings for Confidence details before acting on the counts.

Then group findings by entry point. For each finding, use this structure:

- **Finding identifier**: Unique identifier of the finding, it is a number starting at 1 and incremented for each finding.
- **Confidence**: YES / PARTIAL / NO.
- **Severity**: CRITICAL / HIGH / MEDIUM / LOW, assigned per the Severity mapping above.
- **Category**: The specific weakness class for the finding, taken from the parenthetical label of the matched item above (e.g. SSRF, XXE, ReDoS, path traversal, command injection), plus the corresponding CWE ID (e.g. CWE-89, CWE-79).
- **Processing location**: `path/to/file.go:42`.
- **Processing summary**: The risky processing identified as a single line summary.
- **Taint path**: `file.ext:method:line → file.ext:method:line → file.ext:method:line` — prefix **every** node with its filename and the enclosing method/function name using the format `filename:method:line` (e.g. `JwtConsumer.java:process:290`) so steps that cross files and functions are unambiguous. The first node is the entry-point input (the source); the last is the sink. For second-order paths, show the write path and the read-then-sink path on separate lines prefixed `[write]` and `[read→sink]`.
- **Proof of concept** (required when Confidence is YES; omit otherwise): Use the format that matches the sink type:

  | Sink type | Required format |
  |---|---|
  | HTTP endpoint (SSRF, XSS, SQLi, header injection, open redirect) | Minimal `curl` command or raw HTTP request reproducing the payload |
  | XXE | The minimal XML document containing the external entity declaration and the injected entity reference |
  | CORS bypass | `curl` command with `Origin: <attacker-origin>` header, showing the permissive `Access-Control-Allow-Origin` in the response |
  | CLI / shell (command injection) | Exact argument string passed to the binary |
  | Library call (eval, deserialization, template injection, prototype pollution, weak RNG, hash input ambiguity) | Minimal code snippet showing the tainted value reaching the call |
  | Filesystem (path traversal, zip-slip) | The exact filename or archive entry name that triggers the traversal |
  | ReDoS | The input string and the regex pattern that causes catastrophic backtracking |
  | Authentication / authorization bypass (IDOR) | The HTTP request showing the bypassed credential or the substituted resource identifier |
  | CSV / formula injection | The cell value (e.g. `=cmd\|' /C calc'!A0`) that triggers formula execution when opened in a spreadsheet |
  | Log injection | The input string containing a newline followed by a forged log line |
  | Uncontrolled resource allocation / DoS | The input value or payload size that triggers the excessive allocation or iteration |

  Keep it minimal — the goal is to confirm reachability, not to produce a working exploit.

After displaying the output, determine the output filename as `Findings-$DATE.md` where `$DATE` is today's date in `YYYY-MM-DD` format, then save the full output — summary table and all individual findings — to that file in the current working directory using the Write tool.
