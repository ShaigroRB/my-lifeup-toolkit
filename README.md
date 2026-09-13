# My LifeUp Toolkit

An Android app I made to be used for [LifeUp](https://lifeup.ulives.io/) — the
gamified task/habit tracker. LifeUp exposes a
[ContentProvider API](https://docs.lifeupapp.fun/en/index.html#/guide/api)
that lets other apps read and write its data, but a few repetitive,
higher-friction workflows aren't covered by LifeUp's own UI. This toolkit
is a small collection of quality-of-life tools built on top of that API to
fill those gaps.

Not affiliated with LifeUp / Ayagikei. This app never talks to any server
of its own — all it does is call into the LifeUp app installed on the same
device.

## Requirements

- LifeUp installed on the device.
- LifeUp's ContentProvider permission granted to this app. The first time
  a screen needs it, use the "Grant Permission" action that appears on
  failure (or `LifeUpBridge.requestContentProviderPermission`), which opens
  LifeUp's own permission screen for you to approve.

## How it talks to LifeUp

`LifeUpBridge.kt` is the single point of contact with LifeUp: it wraps
`ContentResolver.call()`/`query()` against LifeUp's provider
(`net.sarasarasa.lifeup.provider.api`), turns LifeUp's error responses into
a `LifeUpCallException`, and exposes typed helpers (`completeTask`,
`listTasks`, `createCategory`, `createAchievement`, ...) that the rest of
the app builds on. Every screen goes through this bridge rather than
calling the provider directly.

## Tools

### Bulk Complete Tasks

Complete a repeating LifeUp task N times in one go, instead of tapping
"complete" over and over in LifeUp itself.

- Save a task as a profile (label + LifeUp task group id/`gid`), either by
  entering the `gid` manually or picking it from a live list of your
  LifeUp tasks.
- Tap a saved profile to enter a count and complete it that many times,
  with progress shown and a "Stop" option mid-run.
- Add per-task quick-complete shortcuts (e.g. "Quick 5x") for counts you
  use often, so they're one tap away without typing a number each time.

### Achievement Templates

Generate a whole *set* of tiered LifeUp achievements — e.g. "Reading
Rookie" / "Reading Adept" / "Reading Champion" / "Master of the Books" for
completion counts 10 / 50 / 200 / 500 — from a single reusable template,
instead of manually recreating each achievement in LifeUp one at a time.

- Define a template once: an achievement category, a set of `{variable}`
  placeholders (e.g. `{name}`, `{something}`), and a list of tiers, each
  with its own name/description template, unlock condition (task
  completion count, streaks, coins, skill level, and the rest of LifeUp's
  ~20 condition types), target, and coin/EXP reward.
- Save templates locally (on-device only, nothing is sent anywhere) and
  reload them later to reuse the same shape for a different habit — just
  swap the variable values.
- Hit "Generate" to create the achievement category and every tier's
  achievement in LifeUp automatically, in one button press, with per-tier
  progress feedback and a summary of what succeeded.

### Stats & History Export *(planned)*

Export task completion history for a yearly recap. Not implemented yet —
shown as a disabled entry on the home screen for now.

## Project structure

- `LifeUpBridge.kt` — all communication with LifeUp's ContentProvider API.
- `MainActivity.kt` — home screen linking to each tool.
- `BulkCompleteActivity.kt`, `TaskProfile.kt`, `TaskShortcut.kt`,
  `TaskProfileStore.kt` — Bulk Complete Tasks screen and its persistence.
- `AchievementTemplateActivity.kt`, `AchievementTemplate.kt`,
  `ConditionTypes.kt`, `AchievementTemplateStore.kt` — Achievement
  Templates screen, its data model, and its persistence.

Saved data (task profiles, achievement templates) is stored on-device only,
as JSON in `SharedPreferences` — there is no backend and no account system.

## Tech stack

Kotlin, Android Views with ViewBinding, Material Components. No third-party
dependencies beyond AndroidX/Material — templates and task profiles are
persisted with plain `SharedPreferences` + `org.json`.
