# Bundled skills

Skills that ship inside the APK, listed in 扩展 → 技能 after the first launch.

- Assets: `app/src/main/assets/skills/<name>/` (one folder per skill, `SKILL.md` at its root).
- Seeded by `core/skills/BundledSkills.kt` into `$DSH_HOME/skills/` (`files/dsh-home/skills`) on app
  start, on a background thread, before the engine boots.
- `.bundled-skills` in that folder records every name ever seeded, so:
  - a skill the user deletes stays deleted (the app never resurrects it);
  - a skill added by a later app version is installed on the next launch;
  - a bundled skill never overwrites a folder already on disk — a hand edit or an import under the
    same name wins.
- To add one: drop the folder into `assets/skills/`, keep the frontmatter `name` equal to the folder
  name, and bump nothing — the marker is name-based. Rewrites of an already-seeded skill do not
  propagate (that would overwrite user edits); rename the skill if a rewrite must reach everyone.

## The set (20)

Written by the project author (original, no upstream licence needed):

| Skill | What it does |
| --- | --- |
| `cat-file-tidy` | File housekeeping: inventory, dry-run, then move/rename/archive |
| `cold-triad` | Doomsday prophet / budget dissection / relationship prism |
| `dice-code-triad` | Decision dice, code annotator, plain-language translator |
| `defense-assistant` | Thesis defence prep: fragile slides, hostile committee, counter-scripts |
| `fantasy-shredder` | Breaks idealized futures and "when I'm ready" delay |
| `knowledge-gap-scanner` | Maps the dark corners of a field before you start learning it |
| `resume-autopsy` | Five-cut CV diagnosis |

General-purpose (MIT as declared in the file, or no licence declared — see the note below):

| Skill | What it does | Licence |
| --- | --- | --- |
| `anti-sycophancy` | Removes agreement-for-agreement's-sake from replies | MIT |
| `anti-deception` | Holds a position under pressure, urgency and authority appeals | MIT |
| `ask-questions-if-underspecified` | Asks before building when the request is ambiguous | — |
| `concise-planning` | Short, atomic, verifiable plans | — |
| `decision-navigator` | Branching questions until a decision is concrete | — |
| `deep-research` | Multi-source research with triangulation and saved sources | — |
| `avoid-ai-writing` | Strips 21 categories of AI-writing patterns | — |
| `professional-proofreader` | Grammar and clarity pass that keeps the author's voice | — |
| `short` | Rewrites the previous answer shorter, substance intact | MIT |
| `dsh-deepread` | Evidence-first reading of long articles, books, PDFs | MIT |
| `meeting-insights-analyzer` | Behaviour and decisions from a meeting transcript | — |
| `time-ledger` | Natural-language time tracking | MIT |
| `file-organizer` | Sorts a folder by content, not by extension | — |

## Licence note (read before publishing a release)

Five skills declare MIT in their frontmatter. The other eight carry no licence line, and were taken
from public community skill collections. Before shipping a public build, either confirm each
upstream licence and record it above, or drop the unlicensed ones from `assets/skills/` — deleting a
folder there removes it from the next install (already-seeded copies stay on devices that have them).
