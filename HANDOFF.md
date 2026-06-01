# HANDOFF — 2026-06-01

## Last Session

Investigated CLAUDE.md re-bloat concern. Found the previous 51KB → 15KB extraction (May 25, commit `f707efa`) is fully intact — the file is only 23 bytes larger. The real issue: `update-claude-md` skill Step 1a references `scripts/document_discovery.py` which was never created. Silent fallthrough to single-file mode meant modular routing (new gotchas → GOTCHAS.md, Flyway notes → FLYWAY.md) never triggered. Fixed with bash file-presence checks + explicit Step 4e routing table. Committed to cc-praxis `issue-108-remove-empty-command-dirs`, synced to installed skills, HANDOFF updated in cc-praxis workspace.

No changes to casehub-work source code this session.

## Immediate Next Step

Resume normal casehub-work development — no branch in progress. Pick the next issue from the backlog or check `issue-235-sxs-sweep` (4 hrs old, not yet marked closed).

## What's Left

- `issue-235-sxs-sweep` — last commit is spec promotion, not a `chore: branch closed` marker — verify if work is done and close the branch · XS · Low
- `backup/pre-squash-main-20260507` (4 weeks) and `backup/pre-squash-v1-main-20260508` (3 weeks) — past 14-day retention threshold; prompt for deletion next session · XS · Low

## What's Next

*Unchanged — `git show HEAD~1:HANDOFF.md`*

## References

- Diary: `_notes/2026-06-01-mdp08-detection-never-fired.md`
- Garden entry: `GE-20260601-5a71f1` (tools/) — update-claude-md silent modular detection failure
- cc-praxis fix: commit `e22ada7` on `issue-108-remove-empty-command-dirs`
