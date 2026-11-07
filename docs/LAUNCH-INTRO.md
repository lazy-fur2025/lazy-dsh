# 冷启动片头

两个调色版本，同一套构图与时间轴：

| 文件 | 起点 | 终点 | 用在 |
|---|---|---|---|
| `GIF/dsh-splash/out/final.mp4` | 薄荷 `#D1E6C6` | 画布 `#F5F7FC` | 浅色主题 |
| `GIF/dsh-splash/out/final-dark.mp4` | 画布 `#0B0C10` | 画布 `#0B0C10` | 深色主题 |

两版都是 1080×2372, 60 fps, 2.4 s, H.264 High, yuv420p。App 里分别是
`res/raw/splash.mp4` 和 `res/raw/splash_dark.mp4`，由 `feature/launch/LaunchIntro.kt` 按当前主题选择。

深色版不是给浅色版加一层滤镜：它由 `index.html?theme=dark` 重新逐帧渲染（`final-dark.mp4`），
换了背景渐变、光晕、环带与扫光的曝光，标志本体保持不变。这样做是因为浅色版的开场是一大块
薄荷白，夜里点开等于闪一下手电筒；而直接压暗又会把标志的黄色染成土色。

## How it is wired

1. **System splash** (`Theme.Dsh.Splash`): the icon on `@color/splash_bg` — 浅色 `#D1E6C6`
   (= 浅色片头第一帧角落), 深色 `#0B0C10` (= 深色片头第一帧与画布). 冷启动第一帧不跳色。
   它只等到主题加载，从不等导航。
2. **The film** (`SplashFilm`): `VideoView` fed by `android.resource://…/raw/splash[_dark]`, muted,
   no controls, 1.03× overscan so the 9:19.77 film covers a 9:20/9:21 panel without letterbox bars.
3. **The hold**: after the film ends, its own last frame (`drawable-nodpi/splash_still.png` /
   `splash_still_dark.png`) is drawn until the app is ready, so a finished `VideoView` can never
   blink black.
4. **The hand-off**: a 340 ms fade (`Motion.standard`) into the already-composed home page. 浅色版末帧
   是画布 `#F5F7FC`，深色版末帧是 `#0B0C10`，交接处只有淡出。

The intro is an **overlay above the live app**, not a gate: `AppNav` composes underneath from the
first frame, so the engine start and the home layout are never delayed by it, and no page can cover
it. It leaves when both the film's beat is over *and* navigation is placed.

## Rules

- **Cold start only.** `MainActivity.IntroPlayed` is process-scoped (`markIntroPlayed()` on first
  composition); rotation, theme change and returning from recents never replay it.
- **降低动态效果 skips the film.** It falls back to `QuietIntro` — the emblem on the canvas with only
  the halo rings turning at 15°/s (one turn per 24 s), 900 ms, then the same fade.
- **Tap to skip.** Any touch finishes the beat immediately (the exit still waits for `ready`).
- **No audio, no alpha.** The source MP4s have neither; the background is always an opaque colour,
  which is why it must be a full-screen overlay rather than a layer over the UI.

## Regenerating the dark cut

`GIF/dsh-splash/index.html` takes `?theme=dark`; every colour that needs to change is in the
`C` / `P` objects at the top. The render is frame-by-frame (`window.render(t)`, 144 frames):

```python
# playwright + chromium, viewport 1080x2372, --allow-file-access-from-files
# (the flag is needed for the mark's alpha fix-up: the PNG's fully transparent pixels are
#  near-white, and blurring them bleeds a grey plate onto a dark background)
for i in range(144):
    page.evaluate("t => window.render(t)", i / 60)
    canvas.screenshot(path=f"dark_frames/{i:04d}.png")
```

then

```bash
ffmpeg -framerate 60 -i dark_frames/%04d.png -c:v libx264 -profile:v high \
  -pix_fmt yuv420p -crf 18 -maxrate 10M -bufsize 20M -movflags +faststart out/final-dark.mp4
```

## Checking it without a device

`ScreenshotTest` renders `QuietIntro` in light and dark (`launch-quiet-light.png`,
`launch-quiet-dark.png`). The film itself needs a device or an emulator: `VideoView` + a hardware
H.264 decoder are not available in Robolectric.

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop io.github.lazy.dsh
adb shell am start -n io.github.lazy.dsh/.MainActivity
```
