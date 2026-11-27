# Privacy hardening implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove confirmed privacy exposures and align the policy with actual behavior.

**Architecture:** Keep the legacy IME and storage contracts. Introduce only narrow policy/diagnostic seams needed for deterministic tests, guard the real learning and voice paths, and use an external browser for the policy.

**Tech Stack:** Java, Android Views/XML, JUnit, instrumentation, Python/prek gates.

**Spec:** docs/privacy-design.md

## Global constraints

- Preserve com.baodeep.hackerskeyboard, preference keys, signing identity and dictionary protocols.
- No ads, AD_ID, network permission or crash SDK; Android vitals is the initial crash/ANR route.
- Keep backup enabled and describe only verified scope.
- Do not read or record real user input; tests use synthetic sentinels.
- Keep work separate from PR103 and the independent version-lint blocker.

## Review focus

- Superclass diagnostics may expose extracted text even after mComposing is removed.
- Numeric/visible/web password variations and no-learning flags must reach write/voice guards.
- Focus changes or pending callbacks must not reuse permissive state from the previous editor.
- Policy entry must work without a browser and without exposing editor data in intents.
- Backup/delete descriptions must not claim untested OEM/D2D or system-provider behavior.

## Tasks

- [ ] Read current source/callers; identify website owning source without secrets or deployment.
- [ ] Add reproducing tests for safe diagnostics; remove content from the actual service dump path.
- [ ] Add editor-policy/write/voice regression tests; guard persistence and dispatch with lifecycle-safe state.
- [ ] Add policy entry resources/UI and a regression case; preserve settings contract.
- [ ] Prepare policy text and Android vitals documentation; stage website change through its owning process.
- [ ] Run full local prek, relevant tests and full Android CI; read the complete diff.
- [ ] Independent correctness and architecture reviews to convergence, green CI, then Copilot on final SHA.
- [ ] Present concrete results and remaining deployment/backup/voice limits before owner merge/publication approval.
