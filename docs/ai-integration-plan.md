# AI branch integration

This change is based on PR #23 (`feature/ai-agent-unified-clean`). It targets that
branch so the diff contains only the authentication test fix, migration setup,
logging changes, and operational documentation. No existing PR is closed or merged.

| PR | Base | Relationship and proposed handling |
| --- | --- | --- |
| #17 | main | Tool boundary foundation. Merge only after its checks pass. |
| #18 | feature/ai-agent-tools | Authenticated token unit-test fix. Its commit is already an ancestor of #19 and #23. Avoid applying it twice. |
| #19 | feature/ai-agent-tools | Provider abstraction, registry, orchestration, four tools. Depends on #17 and contains #18. |
| #20 | feature/ai-agent-core | Earlier integration tests. Compare against #23 before closing as superseded. |
| #21 | feature/ai-agent-core | Availability tool. Included in #23 with different commit history. Compare behavior before closing as superseded. |
| #22 | feature/ai-agent-core | Earlier unified branch. Overlaps #23. Retain #23 as the proposed integration path. |
| #23 | feature/ai-agent-core | Unified availability and security tests. Apply this fix here first. |

Proposed order after review: this fix into #23, #17 into main, #19 into main,
then #23 into main. Retarget stacked PRs after each base merges. Re-run both
`test` and `mysql-migration` on the final main-targeting diff. Do not infer
passing main CI from a check against a different feature branch.

## Merge gate

In GitHub repository Settings > Rules > Rulesets, protect `main`, require a pull
request, require the `test` and `mysql-migration` status checks from GitHub Actions,
and require the branch to be up to date before merging. Apply the same checks to
the feature branches during this stack's integration. Do not bypass failures.

The workflow supplies both checks. Repository rules are separate server settings
and are not enabled by committing a workflow or this document.
