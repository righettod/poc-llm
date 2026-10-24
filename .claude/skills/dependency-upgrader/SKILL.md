---
name: dependency-upgrader
description: Upgrade all direct dependencies of a project to their latest stable release, run the test suite, and produce targeted code fixes for any compatibility breakage. Use this skill whenever the user asks to upgrade, update, or bump dependencies/libraries/packages — even if they say "keep things up to date", "fix dependency vulnerabilities", or "modernise my project". Works across JavaScript/TypeScript, Python, Java (Maven & Gradle), Rust, Go, Ruby, PHP, and .NET.
---

# Dependency Upgrader

## Purpose

Security vulnerabilities are increasingly discovered in third-party libraries. The fix is almost always "upgrade to the patched version" — but upgrading is blocked when the project's direct dependencies are pinned to an old range, or when the codebase uses APIs that changed in a newer release. This skill closes that gap: it upgrades every direct dependency to its latest **released** (GA) version, runs the existing test suite, and — when tests fail — reads the failure output and the relevant source code to produce a concrete diff that restores green tests.

**Scope**: direct dependencies only. Transitive (indirect) dependencies are the library authors' responsibility.

**Released versions only**: pre-release versions (alpha, beta, RC, snapshot, dev, milestone, nightly, canary, etc.) are **never** selected as upgrade targets, regardless of the current version pinned in the project. See Step 3 for the full filtering rules.

---

## Step 1 — Detect the ecosystem

Scan the project root (or the path the user specified) for the following indicator files, in priority order:

| File(s) | Ecosystem | Toolchain |
|---------|-----------|-----------|
| `package.json` | JavaScript / TypeScript | npm / yarn / pnpm / bun |
| `pyproject.toml` | Python (modern) | uv / poetry / pdm / pip |
| `requirements.txt` (+ optional `setup.cfg` / `setup.py`) | Python (classic) | pip / pip-tools |
| `Pipfile` | Python | pipenv |
| `pom.xml` | Java | Maven |
| `build.gradle` or `build.gradle.kts` | Java / Kotlin | Gradle |
| `Cargo.toml` | Rust | Cargo |
| `go.mod` | Go | Go modules |
| `Gemfile` | Ruby | Bundler |
| `composer.json` | PHP | Composer |
| `*.csproj` or `packages.config` | .NET / C# | NuGet (dotnet CLI) |

**Multiple files found**: list them all and ask the user which ecosystem(s) to target.
**None found**: ask the user where the project root is or which file to look at.
**Monorepo**: if `package.json` files exist in subdirectories (e.g. `packages/*/package.json`), ask whether to upgrade the root, a specific sub-package, or all of them.

---

## Step 2 — Read direct dependencies

Read only the sections that declare **direct** dependencies. Ignore lock files here (they describe the full resolved tree, including transitive deps).

| Ecosystem | Sections to read |
|-----------|-----------------|
| `package.json` | `dependencies`, `devDependencies`, `peerDependencies` (optional: ask) |
| `pyproject.toml` | `[project] dependencies`, `[project.optional-dependencies]`, `[tool.poetry.dependencies]` |
| `requirements.txt` | every non-comment, non-`-r` line |
| `Pipfile` | `[packages]`, `[dev-packages]` |
| `pom.xml` | `<dependencies>` inside `<project>` (not inside `<dependencyManagement>` unless asked) |
| `build.gradle(.kts)` | `implementation`, `api`, `testImplementation`, `compileOnly`, etc. |
| `Cargo.toml` | `[dependencies]`, `[dev-dependencies]`, `[build-dependencies]` |
| `go.mod` | `require` block (direct deps only — those **without** `// indirect`) |
| `Gemfile` | every `gem` line outside a conditional block (discuss with user if blocks exist) |
| `composer.json` | `require`, `require-dev` |
| `*.csproj` | `<PackageReference>` elements |

Summarise what you found: a table of package name → current version specifier.

---

## Step 3 — Resolve latest **released** versions

### 3a — Released-only policy (mandatory)

**Only consider versions that are fully released (GA / Generally Available).** This rule is absolute and applies regardless of what the current version specifier is — even if the project is currently pinned to a pre-release, the upgrade target must still be a released version.

A version is **excluded** if it matches any of the following patterns:

| Ecosystem | Excluded version patterns |
|-----------|--------------------------|
| npm | dist-tag is not `latest`; version string contains `-alpha`, `-beta`, `-rc`, `-next`, `-canary`, `-experimental`, `-pre`, `-snapshot` |
| PyPI | version contains `a`, `b`, `rc`, `.dev`, `.post` segments (PEP 440 pre-/post-release markers); e.g. `2.0a1`, `1.3b2`, `1.0rc1`, `0.9.dev0` |
| Maven / Gradle | version contains `SNAPSHOT`, `-alpha`, `-Alpha`, `-beta`, `-Beta`, `-RC`, `-M` (milestone), `-EA` (early access), or `.BUILD-SNAPSHOT` |
| Cargo | version contains `-alpha`, `-beta`, `-rc`, `-pre`, `-nightly`, `-dev` (semver pre-release segment after `-`) |
| Go modules | version tag contains `-alpha`, `-beta`, `-rc`, `-pre` or is a pseudo-version (`v0.0.0-YYYYMMDDHHMMSS-abcdefabcdef`) |
| Bundler / RubyGems | gem version has a pre-release segment (`.pre`, `.alpha`, `.beta`, `.rc`, `.dev`) |
| Composer | version contains `-alpha`, `-beta`, `-RC`, `-patch`, `-dev`, `dev-` prefix (dev branch alias) |
| NuGet | package version has a pre-release label (any hyphen-separated suffix, e.g. `1.0.0-preview1`, `2.3.0-beta`) |

When querying a registry, **always request only released versions**. Use the filtering flags shown in Step 3b below. If a registry returns mixed results, apply the exclusion patterns above to the list before selecting the latest.

If **no released version exists** for a dependency (the package is still pre-release only), skip it, include it in the final report as "no released version available", and do **not** modify its version specifier.

### 3b — Resolution commands (released versions only)

**Proxy-aware rule (mandatory)**: always use the **package manager CLI** to resolve versions — never call a registry REST API directly (no `curl https://registry.npmjs.org/…`, `curl https://pypi.org/pypi/…`, `curl https://search.maven.org/…`, etc.). The package manager CLI reads the proxy/mirror configuration set at the system or project level (Artifactory, Nexus, Azure Artifacts, etc.), so it automatically routes through whatever artifact proxy the environment has configured. A direct HTTP call bypasses that proxy and will fail or return wrong results in corporate environments.

| Ecosystem | Command (released versions only) | Why this respects the proxy |
|-----------|----------------------------------|-----------------------------|
| npm | `npm view <pkg> dist-tags.latest` — the `latest` dist-tag always points to the latest released version; never use `npm view <pkg> version` (returns the newest tag regardless of stability) | Reads `.npmrc` / `NPM_CONFIG_REGISTRY`; routes through the configured registry/proxy |
| PyPI (pip / uv) | `pip index versions <pkg>` — lists all released versions; apply the 3a exclusion patterns to the output to identify the latest released one | Reads `pip.conf` / `PIP_INDEX_URL` / `UV_INDEX_URL`; routes through the configured index |
| PyPI (poetry) | `poetry show --outdated` to get the latest released version per package | Reads `pyproject.toml` `[[tool.poetry.source]]`; routes through the configured source |
| Maven | `mvn versions:display-dependency-updates -DprocessDependencyManagement=false -DallowSnapshots=false -DallowAlphas=false -DallowBetas=false -DallowReleaseProxies=false` | Reads `settings.xml` mirrors and repositories; routes through the configured proxy |
| Gradle | `./gradlew dependencyUpdates -Drevision=release` (requires `com.github.ben-manes.versions` plugin; `revision=release` excludes all non-release versions) | Reads `build.gradle` repository declarations and `gradle.properties`; routes through the configured proxy |
| Cargo | `cargo search <crate> --limit 1` returns the latest stable release; verify the version has no pre-release segment | Reads `~/.cargo/config.toml` `[source]` table; routes through the configured registry replacement |
| Go modules | `go list -m -json -versions <module>` — then filter the output: discard any tag containing `-alpha`, `-beta`, `-rc`, `-pre`, or matching the pseudo-version pattern `v0.0.0-\d{14}-[0-9a-f]{12}` | Reads `GOPROXY` env var; routes through the configured module proxy |
| Bundler | `bundle outdated --parseable` — excludes pre-release gems by default; never pass `--pre` | Reads `Gemfile` source declarations and `~/.bundle/config`; routes through the configured gem source/proxy |
| Composer | `composer outdated --direct --format=json` — excludes dev aliases by default; additionally filter out any result whose `latest` field contains `-alpha`, `-beta`, `-RC`, or `dev-` | Reads `composer.json` repositories and `COMPOSER_MIRROR_PATH_REPOS`; routes through the configured repository/proxy |
| NuGet | `dotnet list package --outdated` — by default excludes pre-release; do **not** add `--include-prerelease` | Reads `NuGet.Config` `packageSources`; routes through the configured feed/proxy |

**Network failure policy — strict**: if any registry call fails due to network unavailability (connection refused, DNS failure, timeout, proxy error, or any other connectivity issue), treat the entire operation as failed:
1. Report the error clearly: which package(s) could not be resolved and the exact error message.
2. Do **not** proceed to Step 4 or make any changes to any file.
3. If any files were already modified in a previous partial run, restore them to their original content before stopping.
4. Tell the user to check their network connection and re-run the skill once connectivity is restored.

A registry call that succeeds but returns "package not found" is a different problem (the package may have been renamed or removed) — report it separately and ask the user how to proceed rather than aborting the whole run.

Build a second table: package name → current version → latest released version. Mark packages that are already at latest with ✓. If **all** are already at latest, report this and stop — no changes needed.

---

## Step 4 — Update the dependency descriptor

Rewrite only the version specifiers for packages that are not yet at latest. Preserve everything else in the file (formatting, comments, order, other sections).

Version specifier conventions by ecosystem:

| Ecosystem | Write as |
|-----------|----------|
| `package.json` | `"^X.Y.Z"` (allow patch+minor) unless the current spec uses `~` or exact — match the existing style |
| `pyproject.toml` / `requirements.txt` | `>=X.Y.Z` or `==X.Y.Z` — match existing style; if unpinned (`package` with no specifier) leave unpinned |
| `Pipfile` | match existing style |
| `pom.xml` | exact `<version>X.Y.Z</version>` |
| `build.gradle` | exact version string inside the existing dependency notation |
| `Cargo.toml` | `"X.Y"` (semver-compatible) or exact `"=X.Y.Z"` — match existing style |
| `go.mod` | `vX.Y.Z` (exact) |
| `Gemfile` | `"~> X.Y"` or exact `"X.Y.Z"` — match existing style |
| `composer.json` | `"^X.Y"` or exact — match existing style |
| `*.csproj` | exact `Version="X.Y.Z"` |

After editing, show a unified diff of the changes to the descriptor file(s) for the user to review before proceeding.

---

## Step 5 — Install updated dependencies

Run the install command to pull the new versions and regenerate the lock file.

| Ecosystem | Command |
|-----------|---------|
| npm | `npm install` |
| yarn (v1) | `yarn install` |
| yarn (v2/v3+) | `yarn install` |
| pnpm | `pnpm install` |
| bun | `bun install` |
| pip | `pip install -r requirements.txt --upgrade` |
| uv | `uv pip install -r requirements.txt --upgrade` |
| poetry | `poetry update` |
| pdm | `pdm update` |
| pipenv | `pipenv update` |
| Maven | `mvn dependency:resolve` |
| Gradle | `./gradlew dependencies` |
| Cargo | `cargo fetch` |
| Go | `go mod tidy` |
| Bundler | `bundle update` |
| Composer | `composer update --no-scripts` |
| NuGet | `dotnet restore` |

If install fails (e.g. a new version requires a newer runtime), capture the error and report it to the user before proceeding. Do **not** silently roll back to the old version.

---

## Step 6 — Run the test suite

Detect and run the project's unit tests. Common conventions:

| Ecosystem | Detection heuristics | Default command |
|-----------|---------------------|-----------------|
| JS/TS (npm) | `"test"` script in `package.json` | `npm test` |
| JS/TS (yarn/pnpm/bun) | same script | `yarn test` / `pnpm test` / `bun test` |
| Python | `pytest.ini`, `pyproject.toml [tool.pytest]`, `setup.cfg [tool:pytest]`, or `tests/` dir | `pytest` |
| Python | `tox.ini` present | `tox` |
| Python | no pytest config | `python -m unittest discover` |
| Java (Maven) | `pom.xml` present | `mvn test` |
| Java (Gradle) | `build.gradle` present | `./gradlew test` |
| Rust | `Cargo.toml` present | `cargo test` |
| Go | `*_test.go` files present | `go test ./...` |
| Ruby | `spec/` dir or `Rakefile` with rspec | `bundle exec rspec` |
| Ruby | `test/` dir | `bundle exec rake test` |
| PHP | `phpunit.xml` or `composer.json` with phpunit | `./vendor/bin/phpunit` |
| .NET | `*.Tests.csproj` or `*Tests.cs` present | `dotnet test` |

If none of the above match, ask the user for the test command before continuing.

Capture full stdout + stderr from the test run.

---

## Step 7 — Evaluate results

### All tests pass ✅

Report success: list every upgraded package (name, old version → new version) and confirm the test suite is green. Mention that the lock file has been updated and the changes are ready to commit.

### Some or all tests fail ❌ — enter the fix loop

Go to **Step 8**. Run the fix loop up to **3 times**. If tests are still red after 3 iterations, report the remaining failures with the full error output and explain what manual intervention is likely needed (e.g. a breaking API change that requires architectural decisions beyond a mechanical fix).

---

## Step 8 — Analyse failures and produce fixes

This step runs inside the fix loop (at most 3 iterations).

### 8a — Parse the failure output

Extract from the test output:
- Which test(s) failed and their names/locations
- The exception type and message
- The stack trace (especially the frames inside the project's own source, not inside the library)
- Any deprecation warnings that became errors

### 8b — Identify the root cause

For each failure, determine the likely cause category:

| Category | Signs | Typical fix |
|----------|-------|-------------|
| Removed API | `AttributeError`, `NoSuchMethodError`, `undefined is not a function`, symbol not found | Replace with the new API; consult the library's CHANGELOG or migration guide |
| Renamed symbol | Same as above but with a hint in the error | Find the new name |
| Changed signature | `TypeError: argument …`, wrong number of args | Update call sites |
| Changed return type | Assertion on shape/type of return value fails | Adapt consumers |
| Changed default behaviour | Tests pass wrong values to assertions | Update test expectations if the new behaviour is correct, or pass explicit options to restore old behaviour |
| New required dependency / import | `ModuleNotFoundError`, `ClassNotFoundException` | Add the import / install the peer dep |
| Stricter validation | Input that used to be silently accepted now raises | Fix the input or wrap with the new API |

Read the relevant source files (the frames in the stack trace that point into the project's own code, not into the library) to understand the context before writing a fix.

### 8c — Write the fix

Produce a minimal, targeted diff that:
- Fixes only what is broken — do not refactor unrelated code
- Preserves the original intent of each test and each call site
- Prefers using the new library API as intended, over hacks that suppress errors

Show the diff to the user with a brief explanation for each changed file.

### 8d — Apply and re-run

Apply the diff, then re-run the test suite (Step 6). If tests pass, go to **Step 7 ✅**. If they still fail and iterations remain, go to **Step 8** again with the new failure output.

---

## Step 9 — Final report

Produce a summary table:

| Package | Old version | New version | Notes |
|---------|-------------|-------------|-------|
| lodash | 4.17.15 | 4.17.21 | ✅ no code changes needed |
| express | 4.18.2 | 5.0.1 | ✅ fixed 2 call sites (see diff) |
| axios | 0.27.2 | 1.7.7 | ✅ updated interceptor signature |

Include:
- The list of files changed in source code (with a one-line description of each change)
- A reminder to review and commit both the descriptor file, the lock file, and any source changes together in a single atomic commit
- Any packages that could **not** be upgraded (with the reason: runtime constraint, unresolvable conflict, still-failing tests after 3 iterations, etc.)

---

## Edge cases to handle gracefully

- **Conflicting peer dependencies**: if two packages require incompatible versions of a shared peer, report the conflict and ask the user how to resolve it. Do not silently pick one.
- **Runtime version constraints**: if the latest library version requires Node 22, Python 3.12, Java 21, etc. and the project is pinned to an older runtime, flag this explicitly.
- **No test suite**: if no tests are found, upgrade the dependencies, then warn the user that there is no automated validation and they should test manually.
- **Pinned transitive deps**: if `requirements.txt` or similar has packages that are clearly transitive (not imported directly in the project's own code), point this out and suggest moving them to a lock file instead of pinning them in the descriptor.
- **Workspace / monorepo packages**: skip packages whose version is `workspace:*` or similar — these are internal packages, not registry deps.
- **Currently pinned to a pre-release**: if the project's current version specifier resolves to a pre-release (e.g. `2.0.0-beta.3`), still upgrade to the latest **released** version. Never treat a pre-release current pin as a reason to target another pre-release. If no released version exists for the package at all, skip it and report it as "no released version available".
- **Version numbers that look stable but aren't**: some ecosystems use convention-breaking version strings (e.g. Maven milestones like `5.0.0.M6`, Go pseudo-versions). Apply the exclusion patterns from Step 3a to catch these even if they don't contain obvious pre-release keywords.
- **Artifact proxy misconfiguration**: if a resolution command returns "package not found" or a version lower than the one already installed, the artifact proxy (Artifactory, Nexus, etc.) may not have the package cached or the remote repository may not be configured on the proxy. Do **not** fall back to a direct registry HTTP call. Instead, report the issue, name the package manager configuration file where the proxy is declared (e.g. `.npmrc`, `pip.conf`, `settings.xml`, `NuGet.Config`), and ask the user to verify the proxy configuration before retrying.