# 能耗模式 (电荷 / 叶绿)

Two characters on the brand sheet — 电荷猫 (Charge Cat) and 叶绿兽 (Leaf Beast) — two ways of spending the model. The switch sits at the head of
the composer's control row (`EnergySwitch`), and the same choice can be made in 设置 → 通用.

| | 电荷 Electric | 叶绿 Leaf |
|---|---|---|
| Model | **never changed** — the user's choice stands | **never changed** |
| DeepSeek reasoning effort | `High` | `Low` |
| Third-party 推理强度 | nearest of `high · xhigh · max · medium · ultra · enabled` | nearest of `minimal · low · medium · disabled` |
| Skill catalogue in the prompt | kept | dropped (`skills: false`) |
| App 经验 reflection after a device task | on (the 任务结束后自动总结经验 setting) | off — no extra model call |
| System-prompt directive | decide, act, answer in one pass; batch tool calls | shortest correct answer; reuse context; skip optional steps |
| Palette | electric blue-violet + lightning gold | living green |
| Wordmark face | heavy, tight sans (a bolt of type) | light serif with air between the letters |
| Home page | a greeting that fits the hour + one caring line | same pools, same rules |

## The mode owns 思考强度

There is no separate 思考强度 (effort) control in the composer or in 设置 any more: two levers that
both change the same thing read as redundancy. The DeepSeek effort is derived from the mode in
`SettingsRepository.toSettings()` (and written by `setPerformance()`), so an existing install whose
stored effort was `max` reports the mode's own level the moment it starts. The model menu keeps the
third-party 推理强度 levels, because those depend on what each endpoint actually accepts.

## Where it lives

- `core/data/PerformanceMode.kt` — the enum: effort, skills, reflection, the prompt text, and
  `applyTo(SessionMode)`. Everything the mode sets is here.
- `core/data/SettingsRepository.kt` — `Settings.performance` (stored as `performance_mode`),
  `Settings.reflects`, the effort derivation in `toSettings()`, and `setPerformance()`, which writes
  the route the mode implies.
- `core/session/SessionStore.sendMode()` — applies `applyTo` to the conversation's session mode and
  sends the result with `android/session/mode` **before every prompt**, so a switch takes effect on
  the next turn without restarting anything. A turn already running keeps the directive it started
  with.
- `core/capability/AppExperience.kt` — `settings.current().reflects` gates the post-turn call.
- `core/designsystem/Color.kt` — `DshColors.inBrand()` and `modeAccent()`; `Theme.kt` provides the
  mode through `LocalPerformanceMode` and hands the palette to `LocalDshColors`, crossfading the
  brand tokens when it changes.
- `core/designsystem/Brand.kt` — `wordmarkStyle()` / `ModeWordmark`: the "DSH" lettering itself is
  per-mode (heavy sans in 电荷, light serif in 叶绿), used by the drawer, the home kicker and 关于.
  Body Chinese deliberately stays on the platform face (`FontFamily.Default`) and 叶绿's CJK on the
  system serif: Android ships Noto Serif CJK, it matches the wordmark's air, and no font is
  bundled (~0 KB) — do not add a subset "for consistency" without asking.
- `feature/conversation/EnergySwitch.kt` — the compact switch, the settings card, and `PerformanceUi`
  (the two character avatars, `res/drawable-nodpi/mode_electric.png` / `mode_leaf.png`, plus the
  `*_mini` silhouettes the 26 dp switch uses).
- `res/values*/hero.xml` — the greeting pools (`hero_greet_morning|day|evening|night`, `hero_care`);
  `HeroHeading` picks one of each per launch, again by time of day.

## Why the model is not part of it

The model is the one thing a third-party route knows better than a preset, and silently swapping it
would change cost in a way the user did not ask for. So a mode spends effort, skills and extra
calls — the levers that actually track token use — and leaves the model chip alone.

## Palette rules

Both palettes are light-first (the brand was designed on a light canvas), and both have a dark
variant. Only brand-carrying tokens change: `business`, `link`, `info`, `deepDiving`,
`brandGrad*`, the three aurora orbs, `canvas`, `bubble` and (in 叶绿) the glass fill. Neutrals,
borders and status colours stay put, so contrast and every existing screen keep working.

The hero shows the character of the mode in effect, its name beside the wordmark, and crossfades on a
switch; that is the clearest sign of which mode is running.
