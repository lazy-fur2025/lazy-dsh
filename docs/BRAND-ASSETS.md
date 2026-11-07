# Brand assets — where every sticker goes

The whole visual identity comes from one sheet (`pictures/Electric Cat and Leaf Spirit Sticker
Sheet.png`, 1536×1024, transparent). Nothing is redrawn by hand: each component is cut out at
`drawable-nodpi/` and used for one job, so the pieces never compete.

| Asset | Cut from | Used by |
|---|---|---|
| `mode_electric.png` | row 1, the cat's pop-out medallion | home hero (144 dp), the card in 设置 → 通用 |
| `mode_leaf.png` | row 1, the spirit's pop-out medallion | same |
| `mode_electric_mini.png` | row 3, cat-head silhouette | the 26 dp composer switch, the 悬浮球 |
| `mode_leaf_mini.png` | row 3, spirit-head silhouette | same |
| `brand_emblem.png` | row 3, the bolt-and-leaf sparkle | drawer/dock chrome, onboarding, 关于, the running indicator |
| `brand_bolt.png` | row 2, the lightning bolt | watermark behind the 电荷 card |
| `brand_leaf.png` | row 2, the leaf | watermark behind the 叶绿 card |
| `brand_seal_electric.png` | row 2, the bolt in a ring | the 使用中 tag's leading mark |
| `brand_seal_leaf.png` | row 2, the leaf in a ring | same |
| `brand_paw.png` | row 2, the paw print | empty session list |
| `brand_branch.png` | row 2, the three-leaf sprig | empty 文件 page |
| `brand_divider_electric.png` | row 3, the wide lightning rule | drawer section hairline |
| `brand_divider_leaf.png` | row 3, the wide leaf rule | same |
| `ic_stat_dsh.xml` | traced from the bolt | status-bar / notification icon, and the ball's fallback |

## Rules that keep it from getting noisy

- **One big thing per screen.** The hero or a settings card gets a medallion; everything else gets a
  mark no larger than 28 dp.
- **Cut-outs stay uncropped.** The medallions are pop-out art (ears, bolts and leaves break the ring),
  so they are drawn with `ContentScale.Fit` and never `CircleShape`-clipped.
- **White silhouettes need a tinted disc.** The mini heads are white with one coloured swipe; on a
  light canvas they sit on their mode's accent at 10–22 % alpha, otherwise they vanish.
- **Watermarks are quiet.** Elements behind copy stay at 8–14 % alpha and never cross the text's
  baseline area by more than a few dp.
- **The rule is a flourish, not a divider.** 148 dp wide and centred, at 50–62 % alpha; stretched
  across a panel it turns into a yellow beam.

## Cutting new pieces

`tools`-free: the sheet's alpha is already correct, so a crop only needs the de-fringe step (pull the
edge pixels' baked-in white out) and a proportional pad. Both live in the throwaway scripts used for
this pass; if more pieces are needed, re-run the same steps rather than hand-editing PNGs, because
`mode_*.png` files are compared by the screenshot tests.
