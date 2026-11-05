# AI integration status

PRs #17, #18 and #19 have merged. PR #24 merged into the #23 branch.
PR #23 now integrates the latest main changes, including request validation
and empty-provider response handling. Its final target is main.

Before merging #23, require successful `test` and `mysql-migration` jobs on
the current head against main. The HTTP contract expects five registered tools.
The MySQL job exercises a new disposable database; adopting an existing database
requires the staging and baseline procedure in database-migrations.md.

PRs #20, #21 and #22 remain open pending comparison and a closure decision.
Do not infer passing CI from an older head or different base.

## Repository protection

Require pull requests and the `test` and `mysql-migration` checks on main using
GitHub repository rules. Workflow files do not enforce merge protection.
The connected GitHub app cannot administer these settings.

## Deployment checks

Demo accounts are disabled unless DEMO_DATA_ENABLED=true. Disabling seeding
will not remove accounts from an existing database. Review those accounts.
Public health responses expose status only. Docker database, model, and admin
ports bind to localhost. Live model behavior and deployment credentials still
require testing in the intended environment.
