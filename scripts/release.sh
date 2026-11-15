#!/bin/sh
# 一键发版：构建 → 验签 → 同步源码到开源 repo → commit/tag/push → 建 GitHub Release 传 APK。
#
# 用法：
#   scripts/release.sh <version_name> <version_code>
#   例：scripts/release.sh 1.0.27 27
#
# 依赖（都在容器里，一般不用动）：
#   ANDROID_JAR=/tmp/plat30/android-11/android.jar
#   ANDROID_SDK=/opt/android-sdk
#   开源 repo 默认在 ../magpie-android-repo（可用 REPO_DIR 覆盖）
#
# 说明：
#   - 不导出到手机 /sdcard/Download：容器里没有 /sdcard，那步由 App 内的 Root 工具做。
#   - GitHub 凭据走 ~/.aimini/git-credential-aimini（git credential fill 动态取，不落盘到脚本）。
#   - keystore / 内核二进制 / aapt2 不会进 repo（.gitignore 已排除）。
set -euo pipefail

VERSION_NAME="${1:-}"
VERSION_CODE="${2:-}"

if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
    echo "用法：$0 <version_name> <version_code>" >&2
    exit 2
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export ANDROID_JAR="${ANDROID_JAR:-$HOME/.aimini/android-sdk/android-11/android.jar}"
export ANDROID_SDK="${ANDROID_SDK:-/opt/android-sdk}"

REPO_DIR="${REPO_DIR:-$ROOT/../magpie-android-repo}"
APK="$ROOT/dist/magpie-android-$VERSION_NAME.apk"
TAG="v$VERSION_NAME"

# 用哪个 build-tools 里的 apksigner/d8（挑第一个带 lib/apksigner.jar 的）
bt_dir() {
    for d in "$ANDROID_SDK"/build-tools/* "$ANDROID_SDK"/android-*; do
        [ -d "$d" ] || continue
        [ -f "$d/lib/apksigner.jar" ] && echo "$d" && return 0
    done
    return 1
}
BT="$(bt_dir || true)"

echo "==> 构建 $VERSION_NAME (code $VERSION_CODE)"
VERSION_NAME="$VERSION_NAME" VERSION_CODE="$VERSION_CODE" sh scripts/build-apk-local.sh

echo "==> 验签"
if [ -n "$BT" ]; then
    java -jar "$BT/lib/apksigner.jar" verify "$APK"
else
    echo "警告：没找到 apksigner，跳过验签" >&2
fi

echo "==> SHA-256"
sha256sum "$APK"

echo "==> 同步源码到 $REPO_DIR"
[ -d "$REPO_DIR/.git" ] || { echo "找不到开源 repo：$REPO_DIR" >&2; exit 1; }
rm -rf "$REPO_DIR/app/src/main"
cp -r app/src/main "$REPO_DIR/app/src/main"
rm -rf "$REPO_DIR/app/src/main/assets/bin"
cp -f scripts/build-apk-local.sh scripts/build-magpie.sh scripts/sync-binary.sh \
    "$REPO_DIR/scripts/" 2>/dev/null || true

# README / 开发文档也一起同步，别让发布页说反了
if [ -f README.github.md ]; then cp -f README.github.md "$REPO_DIR/README.md"; fi
if [ -f README.md ]; then cp -f README.md "$REPO_DIR/README.dev.md"; fi

cd "$REPO_DIR"

echo "==> commit + tag + push"
git add -A
if ! git diff --cached --quiet; then
    git commit -q -m "发布 $VERSION_NAME"
fi
if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo "tag $TAG 已存在；如要重发请先 git tag -d $TAG && git push origin :refs/tags/$TAG" >&2
    exit 1
fi
git tag "$TAG"
git push -q origin main
git push -q origin "$TAG"

echo "==> 取 GitHub 凭据"
TOKEN="$(printf "protocol=https\nhost=github.com\n\n" | git credential fill | sed -n 's/^password=//p')"
if [ -z "$TOKEN" ]; then
    echo "拿不到 GitHub 凭据（credential helper 没返回 token）" >&2
    exit 1
fi
OWNER="$(git remote get-url origin | sed -E 's#.*github.com[/:]([^/]+)/.*#\1#')"
REPO="$(git remote get-url origin | sed -E 's#.*github.com[/:][^/]+/([^/]+?)(\.git)?$#\1#')"
[ -n "$OWNER" ] && [ -n "$REPO" ] || { echo "解析 owner/repo 失败" >&2; exit 1; }

BODY="Magpie 的 Android 壳（非官方移植）。

本版：$VERSION_NAME

## 本次更新

### 修复：AI 智能体卡在「正在思考」

小米 / 澎湃OS 的省电策略（greeze）会在 App 退到后台约 40 秒后冻结整个进程组：网关端口还在监听、前台服务通知也还在，但用户态代码一行都不执行，AI 请求就卡在那里，直到你手动点开 App 才恢复。

这一版加了提醒：

- **打开 App 时**：如果是小米系且还没把 Magpie 设成「无限制」，会发一条通知，点它直达系统设置页。
- **从后台回来时**：如果这次在后台待了 40 秒以上（超过 greeze 的下手时间），补一条通知，告诉你刚才很可能是被系统冻住了。
- 两类提醒都是 7 天最多一次，不刷屏、不弹框。

设置页的后台保活引导也把这层因果关系写清了，不再只说「减少熄屏后冻结的概率」。

### 新增：上下文地图

上下文窗口里补上了网页那张矩形分区图：各类 Token 按面积占比铺开，块内再分具体条目，右侧是带斜纹的「剩余空间」。点有内容的块可以直接看那一项的原文。

### 修正：额度写「剩余」，条画「已用」

- 账号页与概览页的额度窗口，数字从「已用 / 总额度」改成「剩余 / 总额度」。
- 下面的进度条改成已用进度（越满越危险），颜色仍按剩余判定。
- 账号展开后，在窗口列表上方多一行「余额 / 总额度」。

### 其他

- 路由页的请求记录改为最新在上（按天记录原来是从最早一条开始列）。
- 上下文窗口的会话轮次改为最新一轮在上。
- 通知栏点进去是原生界面，不再直接落 magpie 网页。
- 关于页补上本外壳源码仓库与作者信息，链接可点。
- 修复：带名字的 API Key（比如天枢那几个）点了「停用」后再也点不回去，一直提示「这个账号没在供应商列表里找到」。
- 修复：从没打开过网页的供应商页时，?last=1 是空的，导致所有 Key 型供应商都启停不了。现在会自动拉一次全量。

## 安装
下载本 Release 的 APK 直接安装。升级须用同一签名的 APK 覆盖安装；不要先卸载，否则本地配置、账号和数据会被清除。

当前只提供 Android arm64 包，最低 Android 7.0（API 24）。

## 说明
本项目基于 magpie（MIT）制作，仅供学习交流；内核版权归 magpie 原作者所有，如涉及侵权请联系删除。"

echo "==> 创建 Release"
# JSON 必须先落文件再用 @ 传给 curl。
# 直接 -d "$JSON" 会让 shell/echo 层吃掉反斜杠转义，GitHub 回 400 Problems parsing JSON。
REL_JSON_FILE="$(mktemp)"
trap 'rm -f "$REL_JSON_FILE"' EXIT
python3 - "$REL_JSON_FILE" "$TAG" "$VERSION_NAME" "$BODY" <<'PY'
import json, sys
path, tag, ver, body = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
with open(path, 'w', encoding='utf-8') as f:
    json.dump({"tag_name": tag, "name": "Magpie for Android " + ver,
               "body": body, "draft": False, "prerelease": False},
              f, ensure_ascii=False)
PY

curl -sS -X POST \
    -H "Authorization: token $TOKEN" \
    -H "Accept: application/vnd.github+json" \
    -H "Content-Type: application/json" \
    "https://api.github.com/repos/$OWNER/$REPO/releases" \
    -d @"$REL_JSON_FILE" \
    -o "$REL_JSON_FILE.out" -w '%{http_code}' > "$REL_JSON_FILE.code"

RELEASE_ID="$(python3 -c '
import json,sys
p=sys.argv[1]
try:
    d=json.load(open(p))
except Exception as e:
    print(""); print("    解析回包失败:", e, file=sys.stderr); sys.exit(0)
if d.get("id") is None:
    print("", file=sys.stdout)
    print("    HTTP %s: %s" % (open(p+".code").read().strip(), d.get("message")), file=sys.stderr)
else:
    print(d["id"])
' "$REL_JSON_FILE.out")"
rm -f "$REL_JSON_FILE.out" "$REL_JSON_FILE.code"

[ -n "$RELEASE_ID" ] || { echo "创建 Release 失败" >&2; exit 1; }
echo "    release id = $RELEASE_ID"

echo "==> 上传 APK（约 69MB，耐心等）"
curl -sS -X POST \
    -H "Authorization: token $TOKEN" \
    -H "Content-Type: application/vnd.android.package-archive" \
    --data-binary "@$APK" \
    "https://uploads.github.com/repos/$OWNER/$REPO/releases/$RELEASE_ID/assets?name=$(basename "$APK")" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); print("    asset:", d.get("name"), d.get("size",""), "bytes"); print("    error:", d.get("message"))'

echo
echo "完成："
echo "  源码:   https://github.com/$OWNER/$REPO"
echo "  Release: https://github.com/$OWNER/$REPO/releases/tag/$TAG"
echo "  APK:    $APK"
echo "  SHA-256: $(sha256sum "$APK" | cut -d' ' -f1)"
echo
echo "下一步：在 App 内用 Root 工具把 APK 导出到 /sdcard/Download（容器里没有 /sdcard）。"
