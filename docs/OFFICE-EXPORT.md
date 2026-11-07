# 导出为 Office 文件

把一条回复变成真正的 `.docx` / `.pptx` / `.xlsx`，保存在 App 里，点开就能用 WPS 或 Word 继续编辑。

## 为什么在 App 里生成，而不是在引擎里

引擎自带的 `office-*` 技能依赖 Python（`python-docx` / `python-pptx` / `openpyxl`），通过
`load_workspace_dependencies` 取一个预置运行时。那个工具的载荷只有 `win32` / `darwin` / `linux`
三个平台，引擎包里也没有任何 Python 二进制，所以在 Android 上它跑不起来；PDF 导出还要
LibreOffice Kit，更远。

docx / pptx / xlsx 本质是 ZIP + XML。用 Kotlin 直接写，好处是确定的：

- 不碰引擎，不用重打 `engine.zip`
- 毫秒级，无网络、无额外依赖、无权限
- 纯 JVM 逻辑，可以逐个部件用 XML 解析器验证

## 结构

| 文件 | 职责 |
| --- | --- |
| `core/office/MarkdownBlocks.kt` | Markdown → 块模型（标题/段落/列表/代码/引用/表格/分隔线），行内粗体、斜体、代码 |
| `core/office/Ooxml.kt` | ZIP 容器 + XML 转义（丢弃 XML 1.0 不允许的控制字符） |
| `core/office/DocxWriter.kt` | Word：样式、编号、表格、代码底纹 |
| `core/office/PptxWriter.kt` | PPT：一张标题页 + 每个 H1/H2 一页，正文超 7 行自动续页 |
| `core/office/XlsxWriter.kt` | Excel：每个表格一个工作表（按上方标题命名），无表格时退化为两列清单 |
| `core/office/OfficeExport.kt` | 命名、保存、列出、打开（ACTION_VIEW）、分享 |

## 文件放在哪

`files/exports`（App 私有目录）。`res/xml/file_paths.xml` 只把 `exports/` 这一层交给
FileProvider，其余目录不暴露。没有存储权限，卸载即清除。

## 界面入口

- 回复下方「导出」按钮 → Word / PPT / Excel
- 抽屉 → 文件：列出全部导出，点击打开，右侧菜单可分享或删除

编辑交给已安装的编辑器（WPS、Word、Sheets）。App 不做内置 Office 编辑器——手机上做不出可用的
OOXML 编辑器，也不该做。

## 验证

- 单元测试：`MarkdownBlocksTest`、`OfficeWritersTest`、`OfficeExportTest`。其中
  `OfficeWritersTest` 会把三种格式的**每一个 XML 部件**交给 `DocumentBuilder` 解析，确认没有
  手工拼串产生的非法 XML。
- 第三方交叉验证：生成样本后用 `python-docx` / `openpyxl` / `python-pptx` 打开，确认样式名、
  粗体 run、Consolas 字体、表格行数、工作表数值类型、PPT 页数与形状文本都正确。

## 已知边界

- 表格里的行内格式会退化成纯文本（单元格只保留文本）。
- PPT 里的表格按「列 | 列」的文本行呈现，不是真正的表格对象。
- 一页正文最多 7 行，超出进入「（续）」页，不做自动缩放测量。
- 不支持图片、超链接目标、脚注。
