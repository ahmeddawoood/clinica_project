# AI branch integration

This change is based on PR #23 (`feature/ai-agent-unified-clean`). It targets that
branch so the diff contains only the authentication test fix, migration setup,
logging changes, and operational documentation. No existing PR is closed or merged.

| PR | Base | Relationship and proposed handling |
| --- | --- | --- |
| #17 | main | Tool boundary foundation. Its current test run fails. Merge #18 into its head branch, then require passing CI before merging #17. |
| #18 | feature/ai-agent-tools | Fixes #17's unauthenticated test token. Its commit is already an ancestor of #19 and #23. Merge it into the tools branch, without duplicating the fix by cherry-picking it into later branches. |
| #19 | feature/ai-agent-tools | Provider abstraction, registry, orchestration, four tools. Depends on #17 and contains #18. |
| #20 | feature/ai-agent-core | Earlier integration tests. Compare against #23 before closing as superseded. |
| #21 | feature/ai-agent-core | Availability tool. Included in #23 with different commit history. Compare behavior before closing as superseded. |
| #22 | feature/ai-agent-core | Earlier unified branch. Overlaps #23. Retain #23 as the proposed integration path. |
| #23 | feature/ai-agent-core | Unified availability and security tests. Apply this fix here first. |

## Proposed merge order

1. Merge this fix into `feature/ai-agent-unified-clean` through PR #24 after
   both `test` and `mysql-migration` pass. Verify PR #23's resulting checks.
2. Merge PR #18 into `feature/ai-agent-tools` after its `test` check passes.
   PR #17 currently fails
   `MyAppointmentsToolTest.usesAuthenticatedIdentityAndDoesNotAcceptAnArbitraryPatientId`
   with "Authentication is required". PR #18 corrects its mock token.
3. Verify PR #17's checks against `main`, then merge #17. Retarget #19 to
   `main`, verify its checks again, then merge #19.
4. Retarget #23 to `main`. Require both `test` and `mysql-migration` to pass
   on that final diff before merging it. The new migration workflow reaches
   `main` with this PR stack.
5. Compare #20, #21, and #22 with the merged result before proposing their
   closure as superseded. Do not close them merely because their titles overlap.

Do not infer passing main CI from a check against a different feature branch.
Re-check the current head and base after each merge or retarget. This plan does
not authorize any merge or closure.

## Merge gate

In GitHub repository Settings > Rules > Rulesets, protect `main`, require a pull
request, require the `test` status check from GitHub Actions, and require the
branch to be up to date before merging. Apply a test gate to the feature branches
during this stack's integration.

The legacy #17/#18/#19 workflows do not contain `mysql-migration`. Requiring
that missing check on them would block the prerequisite merges indefinitely.
For #24 and the updated #23, verify both jobs before merging. Once the migration
workflow lands on `main`, also make `mysql-migration` a required status check
for subsequent main-targeting PRs. Do not bypass failing checks.

Repository rules are separate server settings and are not enabled by committing
a workflow or this document. The GitHub connection used for this change cannot
administer branch protection.
