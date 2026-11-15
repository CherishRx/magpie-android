# 1.4.3 发布记录（2026-11-15）

## 结果

| 项 | 值 |
|---|---|
| Release | https://github.com/yqing004-boop/magpie-android/releases/tag/v1.4.3 |
| tag | `v1.4.3` |
| 源码 commit（tag 指向） | `b8080cb` |
| main 最新 | `e126e2f` |
| APK | `magpie-android-1.4.3.apk`（77,799,370 B） |
| SHA-256 | `f584a5afb05abcc2146658e50cc07ebaf8be4ac5537bb164848e672c02e47563` |
| GitHub 状态 | 已发布（draft=false, prerelease=false） |
| 蓝奏云 | 已上传 |

SHA-256 已核对：本地 `sha256sum` 与 GitHub API 返回的 `digest` 一致。

## 蓝奏云

| 项 | 值 |
|---|---|
| 所在目录 | 「应用 → magpie」（folder_id `14218910`） |
| 文件夹页 | https://wwbcx.lanzout.com/b0rbkmqxa（提取码 `9vgx`） |
| 本文件直链 | https://wwbcx.lanzout.com/i9bhD4g4kbcd |
| 本文件提取码 | `cju0` |

## 本版内容

### 修复：AI 智能体卡在「正在思考」

根因（已在真机抓证）：小米 / 澎湃OS 的 greeze 省电隔离在 App 退到后台约 40 秒后，
把整个 uid 的进程组冻结 —— `cgroup.freeze=1`、线程 `do_freezer_trap`、
`magpie:gateway` 唤醒锁被 `reason: greeze` 禁用。网关端口还在监听、
前台服务通知也还在，但用户态代码不执行，AI 请求就卡住。

新增 `FreezeGuard.java`：
- **A（打开 App）**：小米系 + 未设「无限制」→ 发通知直达电池设置页
- **B（后台回来）**：后台超过 40 秒 → 补一条「刚才可能被冻结」的提醒
- 两类都 7 天最多一次；`App.java` 加了 Activity 计数，避免「跳设置页」被误判成退后台

设置页引导文案把因果关系写清（含不设的后果）。

### 新增：上下文地图

`CtxSheet.ContextMap`（自绘 View）：squarified treemap 两层布局，
内容块按 token 面积铺开、块内再分条目，右侧斜纹「剩余空间」，点块看原文。

### 修正：额度写「剩余」，条画「已用」

- 账号页 / 概览页：`已用 / 总额度` → `剩余 / 总额度`
- 进度条改为已用进度；`Ui.quotaBar(fillPct, remainingPct)` 两参重载让长度与颜色分离
- 账号展开后新加一行 `余额 / 总额度`

### 其他

- 路由页按天记录：最新 50 条置顶
- 上下文窗口：会话轮次最新在上
- 通知栏点击改为进 `NativeHome`（原来进 `MainActivity` 网页）
- 关于页补壳仓库链接 + 酷安作者链接（可点）
- 天枢等带名字的 Key 启停修复（`resolveRef` 同时按 `name` / `masked` 匹配）
- `?last=1` 为空时回退拉全量（`MagpieApi.providersFull`）

## 过程中的坑

**`release.sh` 的 BODY 里不能用反引号。** BODY 是双引号字符串，
反引号会被 shell 当成命令替换执行，导致 `余额: not found`、JSON 里少内容。
这次 commit 和 tag 推上去了，但 Release 创建失败，只能手动补建。
已改成「」并推送（commit `e126e2f`）。

## 待验证

- 冻结提醒两张通知：A 在打开 App 约 3 秒后出现，B 在退后台 40 秒以上回来时出现
- 天枢三个账号（国模 / 福利 / sk-…f13b）停用后能否正常启用
- 上下文地图的分块比例与点击行为
- 额度行是否显示为「剩余 / 总额度」

## 下次发版

```sh
cd ~/workspace/magpie-android
ANDROID_JAR="$HOME/.aimini/android-sdk/android-11/android.jar" \
  sh scripts/release.sh <版本号> <版本码>
```

蓝奏云：

```sh
python3 ~/.aimini/lanzou-upload/lanzou_cli.py upload \
  dist/magpie-android-<版本>.apk --folder-id 14218910 --desc "说明"
```
