# Interactive view (互动窗口)

The model can answer with a small, manipulable scene instead of a paragraph. It writes one
self-contained HTML page and the app renders it directly in the conversation in a sandboxed
WebView. Buttons, dragging and JavaScript work without leaving the conversation; full screen is optional.

## Tools

One device tool, `interactive_view` (`ToolGroup.Interactive`, risk read):

| argument | meaning |
|---|---|
| `html` | the complete HTML document; inline CSS/JS, no external URLs, at most 256 KB |
| `title` | short window title |
| `id` | reuse an artifact id to update an existing scene |
| `open` | optional immediate full-screen window (default `false`); the scene is always shown inline |

The call writes `files/interactive/<id>.html` + `<id>.json` and returns
`{"id","title","bytes","opened"}`. Only an explicit `open=true` launches the activity through
`ActivityStarts.tryStart` (the same PendingIntent path as the floating ball), so a foreground
conversation can open it immediately and a background start is not treated as a failure—the inline
scene still works. Leaving `open` out never navigates away from chat.

## The window

`feature/interactive/InteractiveActivity.kt` is a separate `@AndroidEntryPoint` activity (not a nav
destination), so a tool call opens it without touching the conversation back stack. It uses the
normal `DshTheme`, a glass top bar with Reload/Close, and a full-height WebView.

Sandbox:

- JavaScript and DOM storage on; media gestures required.
- `blockNetworkLoads` / `blockNetworkImage` on, file and content access off, no JavaScript bridge.
- Navigation away from the page (http/https/file/intent…) is refused; `about:`, `data:`, `blob:`,
  `javascript:` and same-document links are allowed.
- `shouldOverrideUrlLoading` uses the modern `WebResourceRequest` overload.

## Viewport units

Some Android WebViews resolve CSS `vh`/`vw` against a 0×0 first layout when the document arrives via
`loadDataWithBaseURL`, even though `window.innerHeight` is correct later. AI pages use `100vh` and
`max-height:74vh` constantly, which collapsed bottom sheets to their padding.

`core/interactive/ViewportUnits.kt` bakes `vh`/`vw` (including `dvh/svh/lvh` and `dvw/svw/lvw`) into
pixels using the current WebView size before loading. `InteractiveActivity` also waits for a real
height and reloads once after layout. Rotation recreates the activity and redoes the rewrite.

## In the conversation

`ToolPresentation` maps `interactive_view` to `BodyKind.Interactive`. `ToolRows` renders the scene
title, an optional **Full screen** button and `InlineInteractive`:

- under the expanded tool row,
- under a collapsed step group (`GroupInlineInteractive`), so the scene is never hidden by a group,
- and below the row when it is collapsed.

The inline viewport is 360.dp high and fills the available card width. HTML is read on the IO
dispatcher; loading and missing content are separate states with stable bounds. A change of id or
tool-result revision recreates that state so old content is not shown for a new scene. The WebView
loads only after a nonzero layout, rewrites viewport units against its actual size, and is stopped
and destroyed when released. It shares the full-screen viewer’s network/file sandbox.

## Tests

- `core/interactive/InteractiveArtifactsTest.kt`: save/read/update, unsafe ids, size cap, intent
  shape, viewport-unit rewriting.
- `CapabilityListTest` includes the tool and its descriptor budget.
- Device: a Snapdragon die diagram opened, its bottom sheet reacted to block taps, and a fresh
  Chinese card rendered 测试卡片 / 你好世界 / 等待操作 / 点我 correctly.
