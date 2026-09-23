# Achievement Templates: ID picker UX — roadmap

Recap of the work planned to replace manual ID entry in the "Achievement templates"
screen with pickers, in priority order.

## 1. Shared unlock condition / Task ID across tiers

Add an optional toggle (e.g. near the top of the Tiers section) letting the user set
one unlock condition + related ID once and apply it to every tier, instead of
configuring each tier row individually.

- When enabled: show a single condition spinner + related-ID input; hide/disable the
  per-tier ones.
- When disabled: current per-tier behavior, unchanged.
- Data model: add a nullable "shared condition" (code + related ID) at the template
  level; generation logic uses it in place of each tier's own values when set.
- Done first because it decides *where* the related-ID input (and its future picker
  button, see #2) lives — per tier row vs. one shared control — avoiding rework.

## 2. Tiers → Task ID picker

Reuse the existing "Pick from LifeUp tasks…" pattern from
`BulkCompleteActivity`/`LifeUpBridge.listTasks()` (background query against LifeUp's
`tasks` content provider URI → `AlertDialog.setItems` → write back into the text
field).

- Add the same picker button next to the related-ID input(s) resulting from #1.
- Only show/enable it when the selected condition is task-based (`ConditionTypes`
  codes `0`, `1`, `9`, `18` — completion count/streak, pomodoro count, focus
  duration), mirroring the existing `applyRelatedIdState()` enable/disable logic.
- No new provider dependency — `listTasks()` already works, but note it must supply
  the task's own `id` (row id) for `related_id`, not its `gid` (recurring-group id,
  what `complete`/`BulkCompleteActivity` use) — the two LifeUp APIs want different
  identifiers for the same task.

## 3. Existing Category ID picker

LifeUp-SDK's `AchievementApi.listCategories(): Result<List<AchievementCategory>>`
([source](https://github.com/Ayagikei/LifeUp-SDK/blob/main/core/src/main/java/net/lifeupapp/lifeup/api/content/achievements/AchievementApi.kt))
indicates a category list is queryable from the content provider, the same way
`listTasks()` works today.

- Needs testing against the actual LifeUp provider to confirm the query URI/columns
  and behavior (this codebase currently has no read path for categories — only
  write via `createCategory`/`createSubcategory`).
- If confirmed: single-select picker for "Existing Category ID" (Shared settings),
  same shape as the task picker (single value written back to one field).

## Naming: rename "Shared settings"

Its actual role is "things every tier shares that LifeUp would otherwise make you
repeat per achievement" — category, skill IDs, and (once #1 lands) the shared
unlock condition/task ID. Worth renaming the section (e.g. to "Common to all
tiers") so the UI itself communicates why these fields are pulled out, instead of
reading as an arbitrary grab-bag next to the per-tier fields.

## 4. Skill IDs picker

LifeUp-SDK's `SkillsApi.listSkills(): Result<List<Skill>>` (and `listSkillGroups()`)
([source](https://github.com/Ayagikei/LifeUp-SDK/blob/main/core/src/main/java/net/lifeupapp/lifeup/api/content/skills/SkillsApi.kt))
indicates skills are also queryable from the content provider.

- Needs testing against the actual LifeUp provider, same caveat as #3.
- Unlike tasks/categories, the "SKILL IDS" field (Shared settings — New category &
  Existing category) is **multi-select** (comma-separated), so the picker UI needs
  multi-choice selection rather than the task picker's single-pick list.
- Note: per-tier condition code `13` ("Skill level reached") also uses the
  related-ID field for a single skill ID — worth reusing the same skill-fetching
  code in single-select form there, rather than building it twice.

## Deferred, not prioritized

Decided against building these now; noting the reasoning so it isn't relitigated.

**Item rewards.** LifeUp's "Reward" card supports rewarding items on completion,
alongside coin/exp/skills. Not supported by the toolkit today. It's a straightforward additive
extension of the existing reward params if that changes.

**Multiple unlock conditions per tier.** LifeUp allows unlimited conditions per
achievement; the toolkit models exactly one per tier (decided in favor of shipping
#1/#2 first — see conversation). Checked against real usage: every currently
tiered/incremental achievement in use has a single condition on a single task with
an incrementing value; achievements with multiple conditions are not the
incremental/tiered kind this tool targets, so they wouldn't benefit from template
generation anyway. Low priority as a result — revisit only if that usage pattern
changes.

If it ever becomes worth doing, it's a cheap upgrade, not a redesign:
`LifeUpBridge.createAchievement()` already sends conditions as a `conditions_json`
JSON **array** (currently populated with one element), so the write path already
supports it. The work would be confined to: swapping `AchievementTier`'s three
scalar condition fields for a `List<Condition>` (plus a one-time migration for
saved templates), extending `createAchievement()` to loop over the list, and
adding a nested repeatable condition-row UI within each tier row (same
list-of-rows pattern already used for tiers-within-template and
variables-within-template, one level deeper). #1's shared-condition toggle would
generalize from "share one condition" to "share one list," no rethink needed.
