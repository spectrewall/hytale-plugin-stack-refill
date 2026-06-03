# Autonomous Hytale Reference Pipeline

Two GitHub Actions workflows keep Stack Refill building against the latest
Hytale Server API without manual babysitting:

| Workflow | File | Trigger | Job |
| --- | --- | --- | --- |
| **Refresh Hytale References** | `.github/workflows/refresh-hytale-references.yml` | manual (`workflow_dispatch`) or webhook (`repository_dispatch`) | Detect newest reference, bump, compile, then PR (green) or open an issue + dispatch the fixer (red) |
| **Auto-Fix Hytale Build** | `.github/workflows/auto-fix-hytale-build.yml` | `repository_dispatch` from the refresh workflow (or manual `workflow_dispatch`) | Run Claude Code to adapt the source to the new API and open a PR |

## How it flows

```
                 manual run / webhook
                          │
                          ▼
        ┌─────────────────────────────────────┐
        │      Refresh Hytale References       │
        │  read maven-metadata.xml (latest)    │
        │  bump gradle.properties + manifest   │
        │  ./gradlew build                     │
        └───────────────┬──────────────────────┘
              compiles? │
            ┌───────────┴────────────┐
         yes│                        │no
            ▼                        ▼
   open version-bump PR     push bump branch
                            + open tracking issue (for humans)
                            + repository_dispatch ─────┐
                                                       ▼
                                  ┌─────────────────────────────┐
                                  │     Auto-Fix Hytale Build    │
                                  │  Claude adapts src/ to the   │
                                  │  new API, builds green,      │
                                  │  opens a PR that Closes #N   │
                                  └─────────────────────────────┘
```

### Why a dispatch and not the issue event

The original idea — "open an issue, and let the fixer trigger off the issue
creation" — does not work on GitHub, for three independent reasons:

1. **GITHUB_TOKEN cannot trigger workflows.** Events created with the default
   `GITHUB_TOKEN` (including `issues`) deliberately do **not** start new
   workflow runs (anti-loop protection). `repository_dispatch` is one of the
   few events that *does* fire from `GITHUB_TOKEN`.
2. **Author gating is self-defeating.** Restricting the fixer to issues authored
   by `github-actions[bot]` only holds if the issue is made with `GITHUB_TOKEN`
   — which (per #1) never triggers anyway.
3. **claude-code-action blocks bot actors** (`checkHumanActor`) unless
   `allowed_bots` lists them.

So the issue is still opened (it is the human-readable failure record you
asked for), but the **trigger** is a `repository_dispatch` fired immediately
after, carrying the bump metadata in a server-controlled `client_payload`.
`allowed_bots: "github-actions"` lets the action run for the bot-initiated
dispatch.

## Libs vs refs (Hytale terminology)

In Hytale modding, two distinct things are easy to confuse:

- **Libs** — the *compile dependency*: the `com.hypixel.hytale:Server` jar you
  compile against. It comes from the official maven repo and **Gradle downloads
  it automatically**. The server provides these classes at runtime, so the repo
  correctly uses `compileOnly` (not `implementation` — you must not bundle them).
  This pipeline tracks/bumps the libs version. Hytale keeps the **last five
  releases** per channel, which is exactly what `maven-metadata.xml` lists.
  (You *could* float to the newest with `compileOnly("com.hypixel.hytale:Server:+")`,
  but this pipeline pins an exact version on purpose so each bump is build-tested
  and lands as a reviewable PR / tracked failure.)
- **Refs** — *decompiled server sources*, used only for IDE navigation /
  autocomplete / "Find Usages". They are generated **locally** from your own
  `HytaleServer.jar` (produced by running the game from the official launcher),
  e.g. with the community [`patcher`](https://github.com/HytaleModding/patcher)
  (`python run.py setup`) or the `example-mod` `setup.sh` (which writes `src-ref/`).

### Refs in CI (for the auto-fixer)

The Server artifact ships **no `-sources.jar`**, and real refs need a
`HytaleServer.jar` from a running game install — impossible on a headless
runner. So the auto-fix workflow does the CI-feasible equivalent: it downloads
the resolved `Server-<version>.jar` and **decompiles it with
[Vineflower](https://github.com/Vineflower/vineflower) 1.12.0** into
`.hytale-refs/` (gitignored), giving Claude readable, greppable sources of the
new API to adapt against. This step is best-effort (`continue-on-error`) — if it
fails, Claude falls back to the compiler errors alone.

To generate refs **locally** for IDE exploration, use the `patcher` tool with
your own `HytaleServer.jar` as described above; that is a developer convenience,
separate from this pipeline.

## Channel → branch mapping

You opted to track **both** channels:

| Patchline | Maven repo | Base branch |
| --- | --- | --- |
| `release` | `https://maven.hytale.com/release` | `main` |
| `pre-release` | `https://maven.hytale.com/pre-release` | `develop` (auto-created from `main` on first run if absent) |

The newest version is read from
`…/com/hypixel/hytale/Server/maven-metadata.xml` (`<release>`, falling back to
`<latest>`) and validated against `^[A-Za-z0-9._-]+$` before use. As of writing:
release `0.5.3`, pre-release `0.6.0-pre.1.1`.

> **Note on versioning:** Hytale's maven moved from the old
> `YYYY.MM.DD-<hash>` build strings to semver (`0.5.x`). The previously pinned
> `2026.03.26-89796e57b` no longer resolves (404), so the **first** run of the
> refresh workflow will bump to a current version. `manifest.json`'s
> `ServerVersion` is kept in sync with `hytale_build` (your decision to mirror
> the semver).

## Required setup

1. **`ANTHROPIC_API_KEY`** repository secret — already configured.
2. **Claude GitHub App** installed on the repo — already done.
3. **`CI_PAT`** (optional, recommended): a fine-grained PAT or GitHub App token
   with `contents:write` + `pull-requests:write`. It is **not** needed for the
   core handoff (the `repository_dispatch` works with `GITHUB_TOKEN`). It only
   affects whether the resulting **PR's own** `Build Plugin` CI check runs:
   PRs/commits made with the default `GITHUB_TOKEN` do not start other
   workflows. If set, it is picked up automatically via
   `${{ secrets.CI_PAT || secrets.GITHUB_TOKEN }}`. Note that `build.yml` was
   updated to run PR checks on both `main` and `develop` bases.

## Triggering the refresh

**Manually:** Actions tab → *Refresh Hytale References* → *Run workflow* →
choose `both` / `release` / `pre-release`.

**Webhook (`repository_dispatch`):**

```bash
curl -X POST \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer <PAT with repo scope>" \
  https://api.github.com/repos/spectrewall/hytale-plugin-stack-refill/dispatches \
  -d '{"event_type":"check-hytale-references","client_payload":{"patchline":"both"}}'
```

`client_payload.patchline` accepts `both` (default), `release`, or
`pre-release`.

## Re-running / debugging the fixer manually

Actions tab → *Auto-Fix Hytale Build* → *Run workflow*, supplying `branch`,
`base`, `patchline`, `to` (and optionally `from`, `issue`). Useful if a fix run
errored and you want to retry without re-running the whole refresh.

## Safety model

- The fixer's only automatic trigger is `repository_dispatch`, which **requires
  repo write access to send** — an unprivileged actor cannot start a Claude run.
- The bump metadata comes from the dispatch `client_payload` (built from the
  refresh workflow's own validated outputs), not from any user-editable text,
  and is **re-validated** (branch must match `auto/hytale-(release|pre-release)-*`,
  base ∈ {`main`,`develop`}, patchline ∈ {`release`,`pre-release`}, versions match
  `^[A-Za-z0-9._-]+$`) before any checkout.
- The externally-fetched maven version string is allowlisted before it is ever
  interpolated into a branch name, `sed` replacement, or PR/issue text, and all
  shell steps reference values via `env:` variables (no raw expression splicing
  into command text) — closing the runner-RCE vector.
- Claude is scoped to `src/`, forbidden from touching `.github/` or reverting
  the version bump, and limited to `Bash,Read,Edit,Write,Glob,Grep`.
- A guard skips the run if a PR for the bump branch already exists.

## Idempotency / no-spam guarantees

- Refresh skips a version whose `auto/hytale-…` branch already exists.
- It skips opening a failure issue when an open one with the **exact** title
  already exists (exact `jq` title match — not GitHub's fuzzy token search,
  which over-matches dotted/hyphenated semver like `0.6.0-pre.1.1`).
- The fix workflow skips if a PR for the branch already exists.
- `concurrency` groups serialise overlapping runs (refresh globally; fix per
  bump branch).
