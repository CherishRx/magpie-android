# magpie 手机版（APK）

把 [yetone/magpie](https://github.com/yetone/magpie) 塞进一个 Android App：点图标就有，
不用 Termux，不用刷 Magisk。以后上游发新版，直接在 App 里拉下来换掉，不用重新装 APK。

```
┌─ App（这个仓库的 app/）────────────────────────────┐
│  MainActivity ── WebView ──→ http://127.0.0.1:3430 │
│       ⋮ 菜单 → 设置 / 检查更新                     │
│  GatewayService（前台服务）                        │
│       └─ exec filesDir/magpie/magpie serve         │
└────────────────────────────────────────────────────┘
                     ↑
         filesDir/magpie/magpie  ← 更新就是换掉这个 ELF
```

## 这是个壳，更新的是里面那个 ELF

magpie 本体是一个 Go 程序（`magpie serve`）：网关在 3425，Web UI 在网关 +5（3430），
页面是它自己内嵌的。App 只负责三件事——把 ELF 放出来、把进程拉起来、把 WebView 指过去。
所以：

- **功能更新、界面更新都来自上游**，壳几乎不用跟着改；
- **“在 App 里拉更新”= 换掉 `filesDir/magpie/magpie` 这个文件**，不是装新 APK。

## 在 App 里更新（重点）

入口：主界面右上角 **⋮ → 检查更新**，或 **设置 → 检查 magpie 更新**。
启动时还会静默查一次（一天最多一次），有新版本弹一句提示——只提示，不自动下载，
70 MB 别在你不知道的时候吃掉流量。

### 源

| 顺序 | 地址 | 说明 |
| --- | --- | --- |
| 1 | `https://usemagpie.ai/api/latest` | 官方清单：版本、更新说明、每个文件的 SHA-256。也是 `magpie update` 用的那个 |
| 2 | `https://api.github.com/repos/yetone/magpie-releases/releases/latest` | GitHub 官方 API，官方清单挂了才用（未认证限流 60 次/小时） |

设置页的「更新源」可以改成任何同格式的地址（自建反代、镜像站都行）。
资产固定取 **`magpie-cli-android-arm64`**（armv7 机器取 `magpie-cli-android-arm`）。

### 五步，一步都不省

1. **问版本**：清单里 `version` 跟本机 `magpie version` 比。本机是自建的 `android-1.0`
   这种非 semver 版本时，任何官方发布都算“有新版本”。
2. **下载**：写到 `filesDir/magpie/update/<asset>.part`，支持 `Range` 断点续传，
   下到一半断了下次接着下；进度条按字节走。
3. **校验**：边下边算 SHA-256，跟清单里的 `sha256` 比；再比 `size`。
   不对就删文件重来，绝不落盘。
4. **冒烟**：`magpie.<ver>.new version` 真跑一次。直接 exec 被系统拒就走
   `/system/bin/linker64`（和 GatewayService 启动时同一套回退）。跑不起来就放弃这次更新，
   旧版本原封不动。
5. **换位**：旧文件复制成 `magpie.bak`，新版 `rename` 覆盖。Linux 上 rename 覆盖正在执行的
   ELF 是原子的：老进程继续跑老 inode，重启后的新进程跑新文件，中间不空窗。
   然后 `GatewayService.restart()`，WebView 自己重新加载。

出问题就在更新页点 **回滚到上一个版本**：`magpie.bak` 换回来，再重启。

### 为什么不直接调 `magpie update`

本工程自建的二进制是 `GOOS=linux` 编的静态 ELF（为了不含 `/system/bin/linker64` 依赖）。
magpie 自己算资产名用的是 `runtime.GOOS`，于是会挑 **`magpie-cli-linux-arm64`**——
那是桌面 GUI 版，动态链接 glibc + GTK3 + WebKitGTK，Android 上直接段错误。
所以资产名必须由壳按 ABI 写死成 `magpie-cli-android-arm64`（官方那个是 `GOOS=android`
编译的 PIE，interpreter 是 `/system/bin/linker64`，壳的回退路径正好接住）。

### 更新过的二进制不会被 APK 冲掉

首启会把 `assets/bin/magpie.bin` 释放到 `filesDir/magpie/magpie`，并记下大小。
如果只比大小，用户更新完下次启动就会被“重新释放”覆盖回旧版。
所以 `GatewayService.ensureBinary()` 拿 **APK 自己的 `lastUpdateTime`** 当新鲜度标记：

- App 没升级过 → 不碰 `filesDir` 里的二进制（那是用户刚拉的新版）；
- App 升级过（换了 assets）→ 清掉记录，重新从 assets 释放。

## 关键参数

| 项 | 值 | 为什么 |
| --- | --- | --- |
| `targetSdk` | **28** | Android 10+ 对 `targetSdk≥29` 的 App 禁止 exec 私有目录里的文件（W^X）。magpie 是服务器，必须 exec 自己的 ELF。Termux 的 F-Droid 版也钉在 28。同时 28 还能省掉 Android 14 的前台服务类型声明 |
| `minSdk` | 24 | WebView + `startForegroundService` |
| 网关端口 | 3425（可改） | `MAGPIE_ADDR` |
| Web UI | 网关 + 5 = 3430 | `--web=127.0.0.1:3430`，WebView 带 `?k=<webKey>` 打开 |
| 数据目录 | `filesDir/magpie/data` | 二进制旁边放 `.portable`，magpie 就进 portable 模式 |
| HOME | `filesDir/home` | agent 的 `~/.claude`、`~/.codex` 都在这 |
| 二进制 | `assets/bin/magpie.bin`，不压缩 | 首启是拷贝不是解压 |

## 真机上踩到的六个坑（都已修在代码里）

套壳看着简单，跑起来才知道 Android 每个版本都在门口拦一道。按踩到的顺序：

| # | 现象 | 原因 | 修法 |
| --- | --- | --- | --- |
| 1 | 点图标秒退 | `activity_main.xml` 里那个 ⋮ 按钮只有 `minWidth`，没有 `layout_width` | 补上 `layout_width/height="48dp"`（`setContentView` 抛 `InflateException`） |
| 2 | 启动就崩，`SecurityException: startForeground … requires FOREGROUND_SERVICE` | Android 9+ 前台服务要声明权限，targetSdk 28 也躲不掉 | manifest 加 `FOREGROUND_SERVICE`（顺带 `POST_NOTIFICATIONS`） |
| 3 | 网页白屏，`net::ERR_CLEARTEXT_NOT_PERMITTED` | targetSdk 28 默认禁止明文 HTTP，而 WebView 打开的是 `http://127.0.0.1:3430` | `res/xml/network_security_config.xml` + `usesCleartextTraffic` |
| 4 | 网关起来了但一片 `lookup xxx on [::1]:53: connection refused` | CGO 关掉的 Go 读 `/etc/resolv.conf`，Android 没有；退回 `127.0.0.1:53`/`::1:53` 又没人监听 | App 内起本机 CONNECT 代理（`LocalProxy`），把 `HTTPS_PROXY` 塞进子进程环境，域名交给 Java → netd |
| 5 | 编译期 `onJSAlert does not override` | `WebChromeClient` 的方法名是 `onJsAlert` / `onJsConfirm` / `onJsPrompt`（小写 s），参数是 `JsResult` / `JsPromptResult` | 改签名 |
| 6 | `getMimeTypes()` / `setVerticalScrollbarEnabled()` 找不到 | 真名是 `getAcceptTypes()` / `setVerticalScrollBarEnabled()` | 改调用 |

第 4 条值得多说一句：为什么不用 root 起个 DNS 转发？`/proc/sys/net/ipv4/ip_unprivileged_port_start`
是 1024，普通 App 绑不了 53；也不能改 `/etc/resolv.conf`（只读 system 分区）。
代理是唯一不动系统、也不改 magpie 源码的路子，而且 magpie 更新到任何一版都继续有效。

---

## 界面（1.0.12）

顶栏是一张**矮**卡片（34dp 图标 / 16sp 标题 / 上下各 8dp 内边距），不是网页：鸟图标 + 名字 + 状态点（绿＝运行中 / 灰＝已停止）+ 网关胶囊 + 三个点。整个壳 `paddingTop = status_bar_height`，卡片不会钻进状态栏；卡片与内容之间只留 6dp。

下面那块 Web UI 是**四角 20dp 圆弧**：容器和 WebView 各自挂一个圆角 `ViewOutlineProvider` + `setClipToOutline`，布局变化时 `invalidateOutline()` —— 只裁容器的话，WebView 的滚动条和浮动按钮会画到圆角外面。

- **点胶囊** → 复制 `http://127.0.0.1:3425/v1`；长按 → 用浏览器打开
- **点三个点** → 底部弹层（重新加载界面 / 用浏览器打开 / 复制网关地址 / 重启网关 / 停止网关 / 查看日志 / 设置 / 检查 magpie 更新 / 忽略电池优化 / 关于）
- **关于**：外壳版本 + 内核版本、magpie 官方仓库（`github.com/yetone/magpie`）、APK 移植制作者（酷安 @图图没耳朵，`coolapk.com/u/1642567`）、MIT 许可与非官方声明；链接行点一下直接开浏览器
- **设置 → 外观**：莫奈取色（Android 12+，从系统壁纸取强调色）、**顶栏卡片颜色** 与 **内容区背景颜色** 两组 8 色板（第一块＝跟随系统/莫奈），换了立刻生效；状态栏图标会按背景深浅自动翻黑/翻白

> 内容区变色为什么要动页面的 CSS：那页 magpie 的每一个白块（顶栏、工具栏、卡片、底部条、输入框）都是**自己带背景色**的，只把 html/body 弄透明，它们照样白。所以壳是直接覆盖它自己的主题变量：
>
> ```css
> :root,html,body{
>   --bg: 你选的颜色;            /* 页面底 */
>   --card: 底色×0.14 + 白×0.86; /* 卡片，留一点白才有层次 */
>   --card-2 / --bar / --pill    /* 次级块、工具栏、胶囊 */
>   --fg / --muted / --faint / --line  /* 文字与分割线按底色深浅翻 */
> }
> ```
>
> 变量名是从二进制里的 CSS 里抠出来的（`grep -- '--[a-z]*:#'`）。选「跟随系统」就把注入的 `<style>` 摘掉，页面恢复原样。
>
> 另外 WebView 里的浮动按钮会画到圆角外面，壳在容器上盖了一层 `CornerMask`（`Path.Op.DIFFERENCE` 画四个角），用底色把溢出的部分压住。
- **沉浸**：状态栏、导航栏都透明，`SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|LAYOUT_HIDE_NAVIGATION` + 顶栏自己让出 `status_bar_height`，所以上下没有黑边；弹层底部还会让出 `navigation_bar_height`

外观只改外壳（顶栏、弹层、设置页）。**页面本身的深浅色跟随 magpie 自己的 Settings → 外观**，壳不越权去改它。

---

## 构建

### 路 A：没有 Gradle 也能出包（arm64 Linux / 本容器）

```sh
ANDROID_JAR=/path/to/android-30/android.jar ./scripts/build-apk-local.sh
# → dist/magpie-android-1.0.apk
```

手工五步：`aapt2 compile` → `aapt2 link` → `javac` → `d8` → `zipalign` → `apksigner`。

- `scripts/tools/aarch64/` 里带了 aarch64 的 `aapt2`、`zipalign`（Google 只发 x86_64 的，
  arm64 机器上 AGP 会 `exec format error`）；
- `d8`、`apksigner` 是纯 Java，直接 `java -jar`；
- 要一份**带 `resources.arsc` 且能被 build-tools 33 的 aapt2 读**的 `android.jar`：
  API 30 那份可以，API 35 那份 arsc 版本太新，`aapt2 link` 会报
  `failed to load include path`；
- 签名默认用 `scripts/debug.keystore`（没有就自动生成，口令都是 `android`）。

### 路 B：有 x86_64 机器和 Android Studio 的 SDK

```sh
LANG=en_US.UTF-8 ./gradlew :app:assembleDebug    # 需要 JDK 17 + SDK 34 + gradle-wrapper.jar
```

> 仓库里没放 `gradle-wrapper.jar`（二进制），第一次跑要 `gradle wrapper` 生成，或直接用路 A。

---

# 另一条路：Magisk / KernelSU 模块（不装 APK 也能跑）

把 [magpie](https://github.com/yetone/magpie) 打包成 Magisk / KernelSU / APatch 模块，
开机自启本地 AI 网关（`magpie serve`，默认监听 `0.0.0.0:3425`，局域网可访问），供手机上的 AI 客户端
（Claude Code、Codex、OpenCode 等）统一调用。

模块设计参考了设备上成熟的 Magisk/KernelSU 模块做法（进程管理、WebUI 跳转等）。

## 二进制说明

`bin/magpie` 是 **`magpie-cli-linux-arm64`**（v0.1.1504）：

```
ELF 64-bit LSB executable, ARM aarch64, statically linked, Go BuildID
```

**静态链接**，不依赖 glibc / GTK / WebKitGTK，可直接在 Android（bionic）上运行。
实测在 Android 16 / arm64-v8a 上正常启动并监听 3425 端口。

> ⚠️ 不要用 `magpie-linux-arm64`：那是桌面 GUI 版，动态链接 glibc + GTK3 + WebKitGTK，
> Android 上没有 `/lib/ld-linux-aarch64.so.1`，会直接段错误。
> 也可以用官方的 `magpie-cli-android-arm64`，效果等价。

## 模块结构

```
magpie-android/
├── module.prop                          # 模块信息
├── META-INF/com/google/android/         # Magisk 安装入口（官方模板，勿改）
├── customize.sh                         # 安装：建目录、拷二进制、写默认配置
├── service.sh                           # 开机启动（等 boot_completed 后拉起）
├── action.sh                            # [执行] 按钮：启动服务并打开 WebUI
├── uninstall.sh                         # 卸载：停服务、清理数据
├── webroot/index.html                   # KernelSU / APatch 的 WebUI 面板
├── scripts/
│   ├── magpie.config                    # 配置（路径 + 监听地址）
│   ├── magpie.service                   # start|stop|restart|status
│   └── status.sh                        # 更新管理器里显示的模块状态
└── bin/magpie                           # 二进制（安装时拷到 /data/adb/magpie/bin）
```

## 运行时目录

安装后数据在 **`/data/adb/magpie/`**（`/data/adb` 为 root 专属区域，App 无法访问）：

| 路径 | 用途 |
| --- | --- |
| `bin/magpie` | 可执行文件 |
| `scripts/magpie.config` | 配置（0600，root 专属） |
| `run/magpie.pid`、`run/magpie.log` | 进程 PID 与日志 |
| `data/` | magpie 自身数据（`MAGPIE_HOME`） |

## Android 网络适配（重要）

`magpie-cli-linux-arm64` 是**静态链接的 Go 程序**，在 Android 上有两个坑，本模块自动处理：

### 1. DNS 解析失败 → DNS_LOCAL_HOSTNAMES 指向公网 DNS

Android 没有 `/etc/resolv.conf`（用 netd 代理），静态 Go 会退回 `::1:53`，那个端口没人监听，
于是所有外部请求都报：

```
lookup models.dev on [::1]:53: read udp ...: connection refused
```

**解决**：启动时设置 Go 官方的 `DNS_LOCAL_HOSTNAMES` 环境变量，值为 `MAGPIE_DNS`
（默认 `223.5.5.5 119.29.29.29 8.8.8.8`），让 magpie 直接查询这些公网 DNS。

> ### ⚠️ 为什么不用本地 dnsmasq（v0.4 变更，重要）
>
> v0.2 及以前用「自带 dnsmasq 监听 `::1:53`」来解决上面的 DNS 问题。**这是错的**，
> 它会导致**打开热点后立刻关闭**：
>
> - 系统热点(tethering)由 netd 拉起 `/system/bin/dnsmasq`，其监听 socket 地址族是
>   `ANY`（即同时绑 `0.0.0.0:53` 和 `[::]:53`）。
> - 模块的 dnsmasq 抢先占了 `[::]:53`，系统 dnsmasq 于是起不来：
>   ```
>   E dnsmasq : failed to create listening socket: Address already in use
>   E netd    : Failed to send update command to dnsmasq (Broken pipe)
>   E Tethering: ERROR setting DNS forwarders failed ... Remote I/O error (code 121)
>   ```
> - `ap0` 进入 `error=5`，系统随即 `Canceling WiFi tethering request`，热点被取消。
>
> 也试过把模块 dnsmasq 改绑 `0.0.0.0:53`，**打开热点时一样冲突**（系统 dnsmasq 建
> `0.0.0.0:53` 时撞上）。
>
> **正确做法**：不监听任何本地端口，让 magpie 直接查公网 DNS。Go 的
> `hostLookupOrder` 对「无点的短主机名」走 hosts 文件 + NSS、**不会发 DNS 查询**，
> 因此局域网里 tethering 用短名注册的机器（如 `xxx-PC`）照样能解析，不受影响。
> 副作用只有一个：像 `localhost` 这类本该指向本机/局域网的**带特殊含义的短名**，
> 现在会按公网 DNS 解析（对 magpie 这种纯出站网关无影响）。
>
> 升级到 v0.4 后，`magpie.service` 的 `cleanup_legacy_dnsmasq` **每次 start 都会先跑**
> （在「已在运行就早退」之前），认人只认 `/proc/<pid>/cmdline`：
> 旧版是 `--listen-address=::1`，系统热点那个是 `--listen-mark 0xf0063 --user dns_tether`。
> 命中先 TERM、等 2 秒、再 KILL，并确认进程真的退出。
> **绝不按名字 `pkill dnsmasq`** —— 系统热点那个也叫 dnsmasq，杀了热点直接废。

### 2. HTTPS 证书校验失败 → 合并系统 CA

静态 Go 找不到 Android 的证书库，报：

```
tls: failed to verify certificate: x509: certificate signed by unknown authority
```

**解决**：启动时把 `/system/etc/security/cacerts/*.0`（约 143 个）合并成
`/data/adb/magpie/data/certs/ca-bundle.crt`，通过 `SSL_CERT_FILE` 交给 magpie。

> 修复后日志里**不再有任何 DNS / TLS 报错**，`models.json`（5.9 MB）和 `partners.json`
> 都能正常抓取。

### 3. 插件装不上 → 换成 Android 版 Bun

magpie 的插件跑在 **Bun** 上，而它自己下载的是 `bun-linux-aarch64`：

```
ELF executable, dynamic (/lib/ld-linux-aarch64.so.1)   ← glibc，Android 没有
```

于是执行就报 `No such file or directory`，所有 `bun add` 都以失败告终。

**解决**：模块自带官方 NDK 编译的 **`bun-linux-aarch64-android`**（`bin-bun/magpie-bun`，
链接 `/system/bin/linker64`），安装时放进 Bun 缓存目录，并在启动时通过 `MAGPIE_BUN`
让 magpie 直接用它。

> 实测：`bun 1.4.2` 在设备上正常运行，`magpie plugin add` 可以成功安装插件。

### 4. 局域网地址被 fake-ip 骗到 → 排除 198.18.0.0/15

magpie 自己探测「局域网地址」时，会把代理软件 TUN 的 fake-ip 段当成局域网，日志里打出：

```
on the network http://198.18.0.0:3430/?k=…
```

`198.18.0.0/15` 是 mihomo / Clash 这类 TUN 模式用的保留网段，**其它设备根本连不上**，
真实可用地址是 `wlan0` 上的那个（如 `192.168.1.11`）。

**解决**：模块自己算局域网 IP（`scripts/magpie.service` 的 `lan_ip()` 与 `scripts/status.sh`
里同一套规则），只认真实网卡（`wlan0` → `wlan1` → `ccmni*` → 任意网卡），并排除
`127.*` / `169.254.*` / `198.18.*` / `198.19.*`。

- 启动日志会多打一行 `局域网 http://192.168.1.11:3430`，同时写入 `run/lan.url`
- 控制台面板的「局域网地址」走同一套规则（`status.json` 的 `lanIp` / `lanApiUrl` / `lanWebUrl`）
- 本机访问仍然用 `127.0.0.1`，不受影响

## 关键设计

- **root 运行**：数据目录在 `/data/adb` 下，服务以 root 启动。
- **PID 校验**：从 pidfile 读 PID 后 `kill -0` 验证，进程不在了就清掉 pidfile。
- **优雅停止**：先 `SIGTERM`，2 秒未退出再 `SIGKILL`。
- **开机启动**：`service.sh` 等 `sys.boot_completed` 后拉起。**不需要等用户解锁** —— magpie
  只使用 `/data/adb`，该目录在 `boot_completed` 时早已就绪；等 `/sdcard` 反而可能在
  某些 ROM 上无限循环卡住启动。
- **不覆盖用户配置**：升级时 `magpie.config` 已存在就保留。

## 日志自动清理

magpie 的日志会一直增长，所以 `service.sh` 和 `magpie.service` 都会在**启动前**检查：

```
超过 5 MB  →  mv magpie.log magpie.log.1  →  建新的空文件
```

- 只保留**一份**归档 `.1`，不会无限堆积
- 只处理 `magpie.log`（不再有 dnsmasq.log）
- **必须在进程启动前做**：magpie 一旦打开日志 fd，`mv` 后新写入仍会进旧文件，清理就等于白做
- 触发时机：开机启动、以及每次 `magpie.service start/restart`

阈值在 `service.sh` / `magpie.service` 里的 `LOG_MAX_BYTES=5242880`（5 MB），要改就同时改两处。

## 版本号说明

| 位置 | 值 | 含义 |
| --- | --- | --- |
| `module.prop` 的 `version` | `v0.4` | 模块版本，固定不跟随 magpie |
| `module.prop` 的 `versionCode` | `1004` | 整数，模块升级时递增即可 |
| WebUI 面板「版本」 | `0.1.1517` | **magpie 二进制真实版本** |

magpie **自带自动更新**，会自己从 GitHub 拉新版本替换二进制（实测从 0.1.1504 自动升到
0.1.1517）。所以 WebUI 面板的版本一栏直接读 `magpie --version`，跟着上游走，
不需要模块这边做任何检测。

## 模块状态显示（管理器里的一行字）

`scripts/status.sh` 会把状态写进模块描述，在管理器模块卡片上直接看到：

```
magpie 运行中 | WebUI: http://127.0.0.1:3430/?k=<key>
magpie 已停止 | 点 [执行] 打开 WebUI
```

- **KernelSU / APatch**：通过 `ksud module config set override.description` 动态覆盖，状态实时变化
- **Magisk**：不支持该机制，显示 `module.prop` 里的静态文字（同样可读）
- 更新时机：安装时、开机启动后、每次启停后

## 两个端口（重要）

| 端口 | 用途 | 说明 |
| --- | --- | --- |
| **3425** | API 网关 | OpenAI / Anthropic / Gemini 协议，给 AI 客户端用 |
| **3430** | 网页界面 WebUI | 浏览器打开的管理页面 |

> ⚠️ **`magpie serve` 默认只提供 API，没有网页。**
> 必须加 `--web` 才会在同一进程里起网页（本模块已自动加上）。
> 不加时访问 3425 只会返回一段 JSON（API 索引），不是界面。

> ⚠️ **WebUI 需要带密钥访问**：`http://127.0.0.1:3430/?k=<key>`
> 直接开 `http://127.0.0.1:3430` 会返回 **401**。
> 密钥保存在 `/data/adb/magpie/data/web.key`（固定不变，重启不会变），
> 带密钥的完整地址写在 `/data/adb/magpie/run/web.url`。

## 执行按钮与 WebUI

**管理器内 [执行] 按钮**（`action.sh`）：

点一下直接完成三件事，不需要按任何键：

1. 服务没在跑 → 自动启动
2. 用浏览器打开带密钥的 WebUI 地址（从 `run/web.url` 读取）
3. 同步模块状态描述

启动失败会打印日志路径，不会静默失败。

**KernelSU / APatch 的 WebUI**（`webroot/index.html`）：

管理器检测到 `webroot/` 后会在模块卡片上显示 **WebUI** 按钮，点开是状态面板。
面板数据来自同目录的 `status.json`（由 `scripts/status.sh` 生成），纯静态、无外部依赖。
界面分两页，底部导航切换：

| 页面 | 内容 |
| --- | --- |
| 首页 | 状态条 + 「API 接入」卡片 + 「控制台」卡片 |
| 日志 | 最近 30 行运行日志（`lastlog.txt`） |

**API 接入** 卡片：

| 字段 | 示例 | 复制 |
| --- | --- | --- |
| 接口地址（OpenAI 兼容） | `http://127.0.0.1:3425/v1` | ✓ |
| API Key | `sk-magpie-key-…` | ✓ |
| 局域网地址（其它设备用） | `http://192.168.1.9:3425/v1` | ✓ |

API Key 用 `magpie gateway-key add <名字>` 创建，存在
`data/.config/magpie/caller-keys.json` 的 `secret` 字段。
**没有 key 时面板显示「未创建」并隐藏复制按钮**，不会再显示一个假的 `magpie`。
回环地址（127.0.0.1）不校验 key，只有开放局域网后才必须带 key（否则 401）。

**控制台** 卡片：

| 字段 | 示例 | 复制 |
| --- | --- | --- |
| 访问地址 | `http://127.0.0.1:3430` | ✓ |
| 访问密码 | `fa6b0812…`（默认隐藏，点「显示」） | ✓ |
| 一键打开（免输密码） | `http://127.0.0.1:3430/?k=…` | 按钮 |

其它行为：

- 每个地址下方都有 **复制** 按钮（优先用 Clipboard API，失败退回 `execCommand`）
- 访问密码默认打码显示，防止截图泄露
- 长地址 / 长 Key 自动换行，不会横向溢出
- 每 5 秒自动刷新状态

> `webroot/` 的权限与 SELinux context 由 KernelSU 自动设置，`customize.sh` 不会去改动它的 context。
> Magisk 不支持 `webroot`，因此 Magisk 下只有 [执行] 按钮可用。

## 打包

zip 根目录必须是上面这些文件：

```sh
cd Magisk模块
(cd magpie-android && zip -r ../build/magpie-android.zip .)
```

然后在 Magisk / KernelSU / APatch 管理器里「从本地安装」选该 zip。

## 使用

1. 装好后重启，或用管理器 [执行] 菜单启动。
2. 在手机终端配置 provider：`magpie provider add deepseek sk-…`
3. 把手机上的 AI 客户端指向 `http://127.0.0.1:3425/v1`。
4. 手动控制：`su -c "sh /data/adb/modules/magpie/scripts/magpie.service {start|stop|restart|status}"`

### 终端里直接用 `magpie` 命令

Android 的终端环境有两个坑，直接用原始二进制会出问题：

- `HOME=/`（根目录只读），magpie 会把配置往 `/.config` 里写，报
  `mkdir /.config: read-only file system`
- `/data/adb/magpie/bin` 不在 `PATH` 里，敲 `magpie` 找不到命令

**解决**：模块提供了一个包装脚本 `scripts/magpie-cli.sh`，安装时链接到
`/data/adb/ksu/bin/magpie`（该目录本来就在 PATH 中）。它会自动设置：

| 变量 | 值 | 作用 |
| --- | --- | --- |
| `HOME` | `/data/adb/magpie/data` | 让 magpie 找到自己的配置 |
| `SSL_CERT_FILE` | 合并后的系统 CA | 修复 HTTPS 证书校验 |
| `MAGPIE_BUN` | Android 版 Bun | 让插件能安装 |

所以在新终端里（root shell）直接敲就行：

```sh
magpie --version
magpie providers
magpie plugin list
magpie plugin add @magpie-community/opencode-joycode-auth
magpie plugin login joycode
```

> 注意：需要 root shell（`su`）。普通 app 终端没有权限读 `/data/adb`。

> `service.sh` 与 `magpie.service` 使用 `magpie serve`；具体参数以 `magpie serve --help` 为准。
