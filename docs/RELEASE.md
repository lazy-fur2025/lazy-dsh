# 发布流程

DSH-Android 通过 GitHub Releases 分发（不在 Google Play 上架）。发布物只有一个正式签名的 APK；App 内没有更新检查，用户自行下载新版本覆盖安装。

## 步骤

1. **改版本号**：`app/build.gradle.kts` 里 `versionCode` 加 1（不能复用），`versionName` 改成新版本。
2. **测试并构建正式签名的 APK**：

   ```bash
   ./gradlew testDebugUnitTest -Pdsh.engineOut=<engineOut>
   ./gradlew assembleRelease -Pdsh.engineOut=<engineOut> \
     -Pdsh.signing.storeFile=<dsh-release.jks> -Pdsh.signing.storePassword=… \
     -Pdsh.signing.keyAlias=dsh-android -Pdsh.signing.keyPassword=…
   cp app/build/outputs/apk/release/app-release.apk DSH-Android-vX.Y.Z-arm64.apk
   ```

   签名参数只从 Gradle 属性读取（例如 `~/.gradle/gradle.properties` 或命令行 `-P`），keystore 和密码都不要提交进仓库。

3. **写发布说明**：`notes-zh.md`（GitHub Release 的正文）和 `notes-en.md`（英文）。末尾可以附 `APK SHA-256: …`，方便用户核对下载。
4. **发布 GitHub Release**（tag `vX.Y.Z`），资产是 APK：

   ```bash
   gh release create vX.Y.Z DSH-Android-vX.Y.Z-arm64.apk -t "vX.Y.Z" -F notes-zh.md
   ```

5. **验证**：从 Release 下载 APK，覆盖安装到装着上一版本的手机上，确认能正常打开且版本号已更新。

## 约定

- **v1+v2+v3 签名**：`app/build.gradle.kts` 的正式签名同时开了三种方案。minSdk 29 时构建工具默认不签 v1，而部分国内手机的安装器和应用商店只认 v1，会报“安装包没有签名文件”。
- **签名不能换**：换签名后，老用户无法覆盖安装，只能卸载重装（数据会丢）。release keystore 要长期备份。
- **版本号只升不降**：Android 不允许安装 versionCode 更低、包名相同的应用。
- **预发布**：GitHub 的 pre-release 可以照常使用；`releases/latest` 会自动跳过预发布。
