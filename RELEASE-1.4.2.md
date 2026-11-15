# 1.4.2 发布记录（2026-11-15）

## 结果

| 项 | 值 |
|---|---|
| Release | https://github.com/yqing004-boop/magpie-android/releases/tag/v1.4.2 |
| tag | `v1.4.2` |
| 源码 commit（tag 指向） | `30b9602` |
| main 最新 | `85056a6` |
| APK | `magpie-android-1.4.2.apk`（77,791,178 B） |
| SHA-256 | `c5f5b43d978583fff31030a89efe50046acbfc0c584c6bd8a60778c96a571e56` |
| GitHub 状态 | 已发布（draft=false, prerelease=false） |
| 蓝奏云 | 已上传 |

SHA-256 已双向核对：本地 `sha256sum` 输出与 GitHub API 返回的 `digest` 完全一致。

## 蓝奏云

| 项 | 值 |
|---|---|
| 所在目录 | 「应用 → magpie」（folder_id `14218910`） |
| 文件夹页 | https://wwbcx.lanzout.com/b0rbkmqxa（提取码 `9vgx`） |
| 本文件直链 | https://wwbcx.lanzout.com/i1ayA4g1idib |
| 本文件提取码 | `b9ua` |
| 文件 id | `331002811` |

说明：

- 该目录就是 App 内「设置 → 蓝奏云更新」指向的那个分享（`strings.xml` 的
  `shell_lanzou_url` = `b0rbkmqxa`，`shell_lanzou_password` = `9vgx`），
  已核实目录分享信息与 App 内置值一致 → **不需要改 App**，用户点进去就能看到 1.4.2。
- 每个 APK 都带独立提取码是蓝奏云的默认行为（非会员不允许关闭），
  历史 7 个版本全都如此（1.0.22/25/28/35/39/48 + 1.4.2），不是这次才有的。

## 本次改了什么

### 1. `scripts/release.sh`

**a) Release body 重写**
原 body 末尾写着「构建由 AI 辅助完成」——用户明确禁止。已删除，替换为 1.4.2 的实际更新内容
（四个原生标签页、顶栏胶囊、单位切换、后台零唤醒等）。

**b) 加 README 同步**
脚本原来只同步 `app/src/main` 和三个构建脚本，不改 README。
结果 repo 的 README 一直停留在「WebView 展示 magpie 原生管理界面」的老说法。
现加两行：

```sh
if [ -f README.github.md ]; then cp -f README.github.md "$REPO_DIR/README.md"; fi
if [ -f README.md ]; then cp -f README.md "$REPO_DIR/README.dev.md"; fi
```

**c) 修 Release 创建 400 错误 ★ 根因**
原脚本：

```sh
RELEASE_JSON="$(python3 -c 'print(json.dumps({...}))' ...)"
curl ... -d "$RELEASE_JSON"
```

**Alpine 的 `/bin/sh`（ash）内置 `echo` 会把 `\n` 当转义序列解释**，
Python 输出的 `\u...` 和 `\n` 在命令替换那一层就被破坏，
GitHub 收到含裸换行的 JSON → `400 Problems parsing JSON`。

实测确认：`json.loads()` 直接报 `Invalid control character at: line 1 column 145`。

改成先落文件再 `-d @file`：

```sh
REL_JSON_FILE="$(mktemp)"
python3 - "$REL_JSON_FILE" "$TAG" "$VERSION_NAME" "$BODY" <<'PY'
import json, sys
path, tag, ver, body = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
with open(path, 'w', encoding='utf-8') as f:
    json.dump({...}, f, ensure_ascii=False)
PY
curl ... -d @"$REL_JSON_FILE" -o "$REL_JSON_FILE.out" -w '%{http_code}' > "$REL_JSON_FILE.code"
```

同时加了 HTTP 状态码输出，失败时能看到 GitHub 的具体 message 而不是只有一句
「创建 Release 失败」。

### 2. `README.github.md`（发布页 README）

整篇重写。原文十几处把产品描述成「WebView 打开 magpie 自带管理界面」，
与 1.4.2 的实际情况（原生主界面 + 网页作为深度功能入口）完全不符。改动点：

- 开头定位：不说「打开即用 Web 管理界面」，改成「外壳界面是原生绘制的，不套 WebView」
- 核心特性新增「原生主界面」条目，分四个标签页列出
- 「更新入口」从「设置 → 检查 magpie 更新」改成「更新页 → 检查 App 更新」
- 工作原理架构图加上 `NativeHome`
- 界面预览加注：现有三张截图是**网页界面**，原生界面截图待补
- 快速开始第 3 步补一句：订阅类账号登录仍在网页里做

### 3. `README.md`（开发文档，repo 里叫 `README.dev.md`）

- 架构 ASCII 图：`MainActivity → WebView` 改成 `NativeHome（原生主界面）+ MainActivity（网页，⋮ 进）`
- 「App 负责三件事」改成四件事（多一个「用原生界面把网关的数据画出来」）
- 「设置页的更新源」改成「更新页的更新源」
- 更新入口表述对齐 1.4.2

## 待办

- **原生界面截图**：`repo/screenshots/` 现有 3 张全是网页界面。
  要补概览 / 用量 / 账号 / 路由四张原生截图，否则 README 里那句「截图待补」一直挂着。
  截图时记得打码账号名、密钥、局域网 IP。
- 发版脚本现在能一条命令跑通了：
  ```sh
  cd ~/workspace/magpie-android
  ANDROID_JAR="$HOME/.aimini/android-sdk/android-11/android.jar" sh scripts/release.sh <ver> <code>
  ```
