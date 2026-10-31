# Achievement Templates: ID picker UX — roadmap

Recap of the work planned to replace manual ID entry in the "Achievement templates"
screen with pickers, in priority order.

## 1. Shared unlock condition / Task ID across tiers — done

## 2. Tiers → Task ID picker — done

## 3. Existing Category ID picker — done

## Naming: rename "Shared settings" — done

## 4. Skill IDs picker — done

## 5. Item rewards — done

## 6. Form errors in the "Before generating" recap — done

## Deferred, not prioritized

Decided against building these now; noting the reasoning so it isn't relitigated.

**More unlock condition types.** `ConditionTypes.kt` mirrors codes 0–20 from the
[achievement API doc](https://github.com/Ayagikei/LifeUp-SDK/blob/main/mcp/skills/lifeup-cloud/references/methods/achievement.md)
(§ "Unlock Condition Types"). Two documented codes are missing: `520` (complete N
distinct tasks daily) and `524` (N task completions daily, LifeUp v1.104.4+) — trivial
to add. The LifeUp app itself offers more conditions than the doc lists; their codes
could be found read-only by creating an achievement with one in the app and reading
it back through the `achievement_conditions` provider, then testing whether
`conditions_json` accepts that code.

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
