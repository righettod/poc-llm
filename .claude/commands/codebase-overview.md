---
description: Generate a visual overview of a codebase as a Mermaid flowchart that maps each entry point directly to its final processing point (custom code or a third-party library). Use when the user wants to see, at a glance, where input enters a codebase and where it ultimately gets processed. Works for any common programming language. Optionally scope the analysis to a single entry point or package.
argument-hint: [entry-point-or-package]
allowed-tools: Read, Glob, Grep, TaskCreate
---

Generate a visual overview of a codebase as a Mermaid flowchart.

The objective is to spot, for each entry point, the **landing point** where information is ultimately processed. Only two things matter: where information *enters* the codebase (the entry point) and where it *ends up being processed* (the landing point — either custom code or a third-party library). Everything in between is plumbing and must be discarded, because tracing every package-to-package hop produces an unreadable diagram.

If a specific entry point or package was provided as an argument, restrict the analysis to it. Otherwise, include all detected entry points.

Requested scope (optional): $ARGUMENTS

## Scale guard

Count detected entry points before tracing. If there are more than **30 entry points** and no scope argument was given:
- Group entry points by package/module prefix and produce one diagram per group, or
- Ask the user to narrow the scope with a specific entry point or package argument.

Never emit a diagram with more than 40 nodes total — it becomes unreadable regardless of grouping.

## The three node roles (language-agnostic)

Every node in the diagram is exactly one of three roles. No other nodes exist.

- **Entry point** — where information enters the codebase: `main()` functions, HTTP route definitions, CLI command handlers, message/queue consumers, exported public API functions.
- **Landing point — custom code** — the terminus of the flow inside the project: the **deepest internal package** in the call chain that performs the substantive I/O or security-sensitive processing before the flow returns. Labeled with its package/namespace path plus a processing-type tag (see Output rules). If the call graph cycles back or multiple chains reach the same depth, pick the package closest to the I/O or security boundary — the one that directly touches the sink (DB cursor, file handle, exec call, etc.).
- **Landing point — third-party library** — the terminus of the flow outside the project: the external dependency the flow ends in. Tracing stops at the library boundary; the library is a terminal node.

"Package/namespace path" refers to the language's primary grouping unit — detect the project's language and convention first, then apply it consistently:

- **package** — Java, Kotlin, Go, Python, Ruby, Dart
- **namespace** — C#/.NET, PHP, C++ (when used)
- **module / crate** — Rust
- **module** — JavaScript/TypeScript (use the directory/barrel grouping, not individual files)
- **source directory/folder** — fallback for languages with no first-class packaging unit (C, plain C/C++, shell, etc.)

## Entry-point label conventions by language

The entry-point label MUST be the concrete code symbol so the reader can jump straight to the source. Use the form that matches the project's language:

| Language family | Label form | Example |
|---|---|---|
| Java, Kotlin, C#, PHP | `ClassName#methodName` | `OrderController#create` |
| Go, Rust | `packageName.FuncName` | `handler.CreateOrder` |
| Python | `module.function_name` or `ClassName.method_name` | `views.create_order` |
| JavaScript / TypeScript | `Router#POST /path` or bare exported name | `Router#POST /orders` |
| CLI (`main`) | `main` or `ClassName#main` | `App#main` |

Include the HTTP verb/path (`GET /users`, `POST /orders`) only when it disambiguates overloaded handlers. Otherwise keep the label to the symbol alone.

## Output rules

- The output must be a Mermaid flowchart wrapped in a fenced `mermaid` code block.
- Choose the diagram direction based on the entry-to-sink ratio: use `flowchart LR` (left-right) when there are more distinct landing points than entry points (fan-out shape); use `flowchart TD` (top-down) when there are more entry points than landing points (fan-in shape). Default to `flowchart LR` when equal.
- Use the **hexagon** form `id{{"label"}}` for entry points.
- Use the **rectangle** form `id["Package Path -- TAG"]` for custom-code landing points.
- Use the **circle** form `id(("Library Name"))` for third-party library landing points.
- Wrap every node label in double quotes — `id{{"label"}}`, `id["label"]`, `id(("label"))` — so reserved/special characters (`#`, `/`, spaces) parse correctly in Mermaid.
- These three shapes are the only nodes allowed. **Do not draw intermediate internal packages** — collapse the entire path between an entry point and its landing point.
- Every edge goes **entry point → landing point**, directed, with no label. The nature of the interaction is not important and must not be represented.
- An entry point may reach **several landing points** (e.g. a DB library *and* a custom crypto package). Draw one edge to each distinct landing point it reaches.
- A custom-code landing point is the deepest internal package where processing concludes. Do not include controller/router/dispatch packages as landing points — they are entry-point plumbing, not sinks.
- Stop tracing into third-party library internals; represent each called library as a single terminal node. Standard-library/runtime APIs that are themselves the substantive sink (JDBC/`java.sql`, file I/O, XML parsers, deserialization, `Runtime.exec`, etc.) ARE recorded as library landing points; do not record trivial standard-library utilities (collections, strings, dates) as landing points.
- **Landing-point** labels carry no file paths or line numbers. A custom-code label is the package/namespace path plus its processing-type tag (see below); a library label is the bare library name. Entry-point labels follow the per-language convention above.
- When one entry point reaches several landing points, emit a **single** entry-point node with one outgoing edge per distinct landing point. Never duplicate the entry-point node to attach multiple edges.
- Each **custom-code** landing label MUST end with a processing-type hint drawn from the fixed vocabulary below, in the form `package.path -- TAG`. Use `→` (Unicode arrow U+2192) to chain when one operation feeds another (e.g. `DESERIALIZE → CMD-EXEC`); use at most two tags. The hint summarizes the package's security-relevant role so a reviewer can spot sensitive sinks at a glance. Controlled vocabulary: `AUTHN`, `AUTHZ`, `SESSION`, `DESERIALIZE`, `CMD-EXEC`, `SQL/DB`, `FILE-IO`, `ARCHIVE`, `CRYPTO`, `XML-PARSE`, `XSS-SINK`, `REDIRECT`, `SSRF`, `HTTP-OUT`, `CSRF/STATE-CHANGE`, `ACCOUNT-RECOVERY`, `CONFIG-EXPOSURE`, `LOG-INJECTION`, `USER-MGMT`, `MAIL`, `VALIDATION`, `METADATA/I18N`. If none fit, coin a new short UPPER-CASE tag rather than writing prose.
- **Library-sink** labels keep the bare library name (no tag) — the library name already implies the processing. They receive only the risk coloring described next.
- Apply risk coloring with `classDef` to BOTH custom landings and library sinks. Tier `high` (red) for sinks enabling code execution or direct compromise — deserialization, command/code execution, SQL/DB, file/path I/O, XML parsing (XXE), SSRF, archive extraction (`ARCHIVE`). Tier `med` (amber) for authentication, authorization, crypto, session/token handling, redirect, CSRF/state-change, account-recovery, config/data exposure, and outbound HTTP (`HTTP-OUT`). Leave everything else unstyled. Define `classDef high fill:#fdd,stroke:#c00,color:#900` and `classDef med fill:#ffe9c7,stroke:#e08e00`, and attach with `:::high` / `:::med` on the node.
- Remove any empty line from the generated mermaid code.

## Steps to follow

1. **Discover the codebase structure** — Use Glob and Grep to explore the file tree and identify the language, framework, and project layout. Determine which packaging unit applies for the detected language (see "The three node roles" above). Locate all entry points: `main()` functions, HTTP route definitions, CLI command handlers, message/queue consumers, and exported API functions. If a specific entry point or package was provided as an argument, restrict the scope to the matching entry point or package. Apply the scale guard at this step before proceeding.

2. **Trace each entry point to its landing point(s)** — Starting from each entry point, follow information flow through the code, ignoring the intermediate packages it passes through. Determine where the flow terminates: a third-party library (record the library as a terminal node) or, if it never leaves the project, the deepest internal package that performs the substantive processing (record that package as a custom-code landing point). When the call graph is ambiguous or cyclic, apply the tiebreaker rule in "The three node roles". Record one (entry point → landing point) pair for each distinct terminus. Use the Task or TaskCreate tool to launch subagents that parallelize tracing across independent entry points on large codebases.

3. **Build the diagram** — Emit one node per entry point (hexagon), one node per distinct landing point (rectangle for custom code, circle for library), and one directed unlabeled edge for each (entry point → landing point) pair recorded in step 2. Do not emit any intermediate package nodes. Deduplicate landing-point nodes so a library or package reached from several entry points appears once. Choose diagram direction per the Output rules above.

4. **Validation** — Before emitting, verify the generated Mermaid code against this checklist:
   - Every node ID referenced in an edge (`A --> B`) has a corresponding node definition.
   - All node IDs are unique — no two nodes share the same ID string.
   - Every node label is wrapped in double quotes.
   - `:::high` and `:::med` are only attached to landing-point nodes (rectangles and circles), never to entry-point hexagons.
   - No bare `->` or `-->` appears inside a quoted label — use the Unicode arrow `→` (U+2192) for tag chaining.
   - `classDef high` and `classDef med` are defined before any node uses them.
   - The total node count does not exceed 40; if it does, apply the scale guard (split by package group).
   Fix any issue found before proceeding.

5. **Output the result** — Emit the completed diagram inside a fenced `mermaid` code block.
