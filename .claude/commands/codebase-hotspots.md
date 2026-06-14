---
description: Generate a list of locations in the codebase where risky processing is performed from a security perspective.
argument-hint: [entry-point-or-package]
allowed-tools: Read, Glob, Grep, Task
---

Generate a list of locations in the codebase where risky processing is performed from a security perspective.

The objective is to spot, for each entry point, the location in the code in which the information received is used to perform **risky** processing from a security perspective.

**Entry point** is where information enters the codebase: `main()` functions, HTTP route definitions, CLI command handlers, message/queue consumers, exported public API functions.

Input information is considered **not validated** when there is no *effective* check for the specific sink it reaches. A length check or type cast is not effective validation for a path-traversal sink; an allow-list is. When you cannot determine whether validation happens upstream (middleware, decorator, framework binding, another layer you did not read), report the finding with **Confidence: NO** rather than dropping it.

## Scope

If an argument is provided in `$ARGUMENTS`, restrict the analysis to that entry point or package. Otherwise, analyze all entry points in the codebase.

## Methodology

Follow this procedure, in order:

1. **Enumerate entry points** using Glob/Grep (route definitions, `main()`, CLI handlers, queue consumers, exported public API functions). Restrict to `$ARGUMENTS` if provided.
2. **Parallelize when it pays off.** If there are several entry points (roughly more than three), spawn subagents via the Task tool to trace them concurrently — one subagent per entry point, or a batch of entry points per subagent for large codebases — to keep contexts isolated. With only a few entry points, trace them inline without subagents.
3. **Trace the data flow (taint analysis)** from each tainted input through the call chain until it reaches a sink listed below. Record the path: `source → intermediate calls → sink:line`.
4. **Confirm the sink actually executes/evaluates the input** before reporting Confidence: YES (the regex is actually run, the command is actually exec'd, the archive is actually extracted, the query is actually executed).
5. **Collect all findings** (from every subagent, or from the inline traces) into a single set, then perform the aggregation in step 6.
6. **Deduplicate** findings by the (entry point, sink location) pair, then **group** the output by entry point; **within each group, order** by Confidence (YES first), then by Severity (CRITICAL first). A single sink reachable from several entry points is a distinct taint path for each, so it appears once under each entry point; only identical (entry point, sink location) findings are collapsed.

## Risky processing

The following processing must be considered **risky** from a security perspective:

- Input information not validated and used within an XML/XSD parser (XXE).
- Input information not validated and used to create a message written into a logging function (log injection/forging).
- Input information not validated and used to perform a network request (SSRF, including DNS-rebinding and redirect-based variants).
- Input information not validated and used to create an HTTP response (response splitting, header injection, open redirect via `Location`/`Set-Cookie`).
- Input information not validated and used to generate Comma-Separated Values (CSV) content (CSV/formula injection).
- Input information not validated and used for authentication decisions (authentication bypass).
- Input information not validated and used for authorization decisions (including IDOR / object reference, mass assignment / object binding).
- Input information not validated and used to decompress an archive (zip-slip, decompression bomb).
- Input information not validated and used to access a filesystem (path traversal, file upload with input-controlled filename/extension/content-type).
- Input information not validated and used for a shell or process execution (command injection, tainted format string).
- Input information not validated and used to create a regular expression that is evaluated (ReDoS).
- Input information not validated and used to construct a SQL/NoSQL/ORM/LDAP/XPath/GraphQL query (injection).
- Input information not validated and used in a template engine (server-side template injection).
- Input information not validated and used for a deserialization processing using another format than JSON (insecure deserialization).
- Input information not validated and used to generate random values for a security-sensitive purpose (weak RNG, e.g. a predictable or input-derived seed, or a non-CSPRNG such as `Math.random`).
- Input information not validated and used to compute a cryptographic digest without a values separator (hash input ambiguity).

## Confidence

Use the following value for the **Confidence** indicator:

- **YES**: If you are sure of the problem and you can provide a code snippet as a proof of concept to trigger the malicious processing.
- **NO**: If the problem is theoretical or you cannot provide a proof of concept.

Report findings of both confidence levels. Never silently drop a NO finding.

## Severity

Assign **Severity** from the weakness category, then adjust up or down for context (reachability, authentication required, blast radius). Use this default mapping:

- **CRITICAL**: command injection, SQL/NoSQL/ORM/LDAP/XPath/GraphQL injection, insecure deserialization, server-side template injection, authentication bypass.
- **HIGH**: SSRF, path traversal / unsafe file access, zip-slip / decompression bomb, authorization bypass (including IDOR / mass assignment), XXE.
- **MEDIUM**: response splitting / header injection / open redirect, log injection, ReDoS, weak RNG for a security-sensitive purpose.
- **LOW**: CSV / formula injection, cryptographic digest without a values separator.

## Output rules

Group findings by entry point. For each finding, use this structure:

- **Confidence**: YES / NO.
- **Severity**: CRITICAL / HIGH / MEDIUM / LOW, assigned per the Severity mapping above.
- **Category**: The specific weakness class for the finding, taken from the parenthetical label of the matched item above (e.g. SSRF, XXE, ReDoS, path traversal, command injection).
- **Processing location**: `path/to/file.go:42`.
- **Processing summary**: The risky processing identified as a single line summary.
- **Taint path**: `file.ext:method:line → file.ext:method:line → file.ext:method:line` — prefix **every** node with its filename and the enclosing method/function name using the format `filename:method:line` (e.g. `JwtConsumer.java:process:290`) so steps that cross files and functions are unambiguous. The first node is the entry-point input (the source); the last is the sink.
- **Proof of concept**: A code snippet or input value triggering the processing (required when Confidence is YES; omit otherwise).
