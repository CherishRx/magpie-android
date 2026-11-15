package com.magpie.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 「上下文窗口」详情卡：一条请求的 prompt 按组成部分摊开。
 *
 * <p>照 magpie 网页点开一条请求后那张卡：
 * <pre>
 * 上下文窗口                    group/xyng  ● 已计数
 * 已用上下文 [健康]
 * 21.1K / 1M token
 * 已用 2% · 剩余 979K
 *
 * 提示词缓存
 * 0% 命中
 * ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬
 * 0 来自缓存 · 21.1K 新增
 *
 * [条形图：系统提示词 15.4K | 剩余空间 979K]
 * ■ 系统提示词 15.4K  ■ 工具 5.6K  ■ 会话内容 37
 * ■ 剩余空间 979K
 *
 * 内容      [全部] [系统提示词] [工具] [会话内容]
 * ● agent 提示词   15.4K
 * ● browser         776
 * ● terminal        534
 * </pre>
 *
 * <p>数据来自 {@code route.prompt}：`window` / `tokens` / `counted` /
 * `parts[{kind, tokens, items[{name, tag, n, tokens}]}]`。
 */
final class CtxSheet {

    /** prompt 的 kind 到显示名。 */
    static String kindNameOf(String kind) {
        switch (kind) {
            case "system":
                return "系统提示词";
            case "tools":
                return "工具";
            case "skills":
                return "技能";
            case "memory":
                return "记忆";
            case "files":
                return "文件";
            case "results":
                return "工具输出";
            case "chat":
                return "会话内容";
            default:
                return kind;
        }
    }

    /** 各 kind 的颜色（跟网页那几颗小圆点对应）。 */
    static int kindColorOf(Ui ui, String kind) {
        switch (kind) {
            case "system":
                return 0xFF8B5CF6;   // 紫
            case "tools":
                return 0xFF38BDF8;   // 蓝
            case "skills":
                return 0xFF34D399;   // 绿
            case "memory":
                return 0xFFFBBF24;   // 黄
            case "files":
                return 0xFFFB7185;   // 粉
            case "results":
                return 0xFFF97316;   // 橙
            case "chat":
                return 0xFF6366F1;   // 靛
            default:
                return ui.col(M3.OUTLINE);
        }
    }

    private CtxSheet() {
    }

    /**
     * 弹出一张上下文窗口详情。
     *
     * @param route 一条 trace/history 的 Route；没有 {@code prompt} 就提示「没有记录」
     */
    static void show(final Activity act, final Ui ui, final JSONObject route) {
        JSONObject prompt = NativeData.obj(route, "prompt");
        if (prompt == null) {
            AlertDialog dlg = new AlertDialog.Builder(act)
                    .setTitle("上下文窗口")
                    .setMessage("这条请求没有留下上下文记录（magpie 只在能读到请求体时才统计）。")
                    .setPositiveButton("知道了", null)
                    .create();
                    styleDialog(act, ui, dlg);
            dlg.show();
            return;
        }

        final int window = NativeData.num(prompt, "window");
        final int tokens = NativeData.num(prompt, "tokens");
        final boolean counted = NativeData.flag(prompt, "counted");
        JSONArray parts = NativeData.arr(prompt, "parts");

        // ---- 外壳：可滚的竖布局 ----
        ScrollView scroll = new ScrollView(act);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = ui.dp(18);
        root.setPadding(pad, ui.dp(4), pad, pad);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- 标题行：上下文窗口 + 组 + 已计数 ----
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = ui.text("上下文窗口", 16, M3.ON_SURFACE, true);
        head.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        JSONObject group = NativeData.obj(route, "group");
        String ref = group == null ? "" : NativeData.str(group, "ref",
                NativeData.str(group, "name", ""));
        if (!ref.isEmpty()) {
            TextView g = ui.mono(ref, 10, M3.ON_SURFACE_VARIANT);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            glp.rightMargin = ui.dp(6);
            head.addView(g, glp);
        }
        if (counted) {
            TextView ok = ui.pill("已计数", M3.TERTIARY_CONTAINER, M3.ON_TERTIARY_CONTAINER);
            head.addView(ok);
        }
        root.addView(head);

        // ---- 已用上下文 ----
        LinearLayout usedRow = new LinearLayout(act);
        usedRow.setOrientation(LinearLayout.HORIZONTAL);
        usedRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ulp.topMargin = ui.dp(14);
        usedRow.setLayoutParams(ulp);
        usedRow.addView(ui.text("已用上下文", 12, M3.ON_SURFACE_VARIANT, false));

        double share = window > 0 ? tokens * 100.0 / window : -1;
        String health;
        int healthRole;
        if (share < 0) {
            health = "未知";
            healthRole = M3.SURFACE_CONTAINER_HIGHEST;
        } else if (share < 50) {
            health = "健康";
            healthRole = M3.TERTIARY_CONTAINER;
        } else if (share < 80) {
            health = "偏多";
            healthRole = M3.SECONDARY_CONTAINER;
        } else {
            health = "快满";
            healthRole = M3.ERROR_CONTAINER;
        }
        TextView h = ui.pill(health, healthRole,
                healthRole == M3.ERROR_CONTAINER ? M3.ON_ERROR_CONTAINER
                        : healthRole == M3.SECONDARY_CONTAINER ? M3.ON_SECONDARY_CONTAINER
                        : M3.ON_TERTIARY_CONTAINER);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.leftMargin = ui.dp(8);
        usedRow.addView(h, hlp);
        root.addView(usedRow);

        // 大数字 21.1K / 1M token
        LinearLayout big = new LinearLayout(act);
        big.setOrientation(LinearLayout.HORIZONTAL);
        big.setGravity(Gravity.BOTTOM);
        big.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView num = ui.text(NativeData.tokens(tokens), 30, M3.ON_SURFACE, true);
        big.addView(num);
        TextView of = ui.text(window > 0 ? " / " + NativeData.tokens(window) + " token"
                : " token", 13, M3.ON_SURFACE_VARIANT, false);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.bottomMargin = ui.dp(5);
        big.addView(of, olp);
        root.addView(big);

        if (share >= 0) {
            String sub = "已用 " + NativeData.percent(share) + " · 剩余 "
                    + NativeData.tokens(Math.max(0, window - tokens));
            TextView s = ui.text(sub, 12, M3.ON_SURFACE_VARIANT, false);
            s.setLayoutParams(mlp(ui, 4));
            root.addView(s);
        }

        // ---- 提示词缓存 ----
        int cacheRead = 0;
        JSONArray usage = NativeData.arr(route, "usage");
        if (usage.length() > 0) {
            JSONObject u = usage.optJSONObject(0);
            cacheRead = NativeData.num(u, "cache_read");
        }
        long promptTokens = tokens > 0 ? tokens : 1;
        double hit = Math.max(0, Math.min(100, cacheRead * 100.0 / promptTokens));

        TextView cacheTitle = ui.text("提示词缓存", 12, M3.ON_SURFACE_VARIANT, false);
        cacheTitle.setLayoutParams(mlp(ui, 18));
        root.addView(cacheTitle);

        LinearLayout cacheBig = new LinearLayout(act);
        cacheBig.setOrientation(LinearLayout.HORIZONTAL);
        cacheBig.setGravity(Gravity.BOTTOM);
        cacheBig.addView(ui.text(NativeData.percent(hit), 30, M3.ON_SURFACE, true));
        TextView hitLabel = ui.text(" 命中", 13, M3.ON_SURFACE_VARIANT, false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = ui.dp(5);
        cacheBig.addView(hitLabel, clp);
        root.addView(cacheBig);

        root.addView(ui.quotaBar(hit));

        TextView cacheSub = ui.text(NativeData.tokens(cacheRead) + " 来自缓存 · "
                        + NativeData.tokens(Math.max(0, tokens - cacheRead)) + " 新增",
                12, M3.ON_SURFACE_VARIANT, false);
        cacheSub.setLayoutParams(mlp(ui, 6));
        root.addView(cacheSub);

        // ---- 条形图：各 kind 的占比 + 剩余 ----
        List<JSONObject> kinds = new ArrayList<>();
        long sumParts = 0;
        for (int i = 0; i < parts.length(); i++) {
            JSONObject p = parts.optJSONObject(i);
            if (p == null || NativeData.num(p, "tokens") <= 0) {
                continue;
            }
            kinds.add(p);
            sumParts += NativeData.num(p, "tokens");
        }

        if (!kinds.isEmpty()) {
            long total = window > 0 ? window : Math.max(1, sumParts);

            // ---- 细条（占比一眼看出）----
            LinearLayout bar = new LinearLayout(act);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(10));
            blp.topMargin = ui.dp(14);
            bar.setLayoutParams(blp);
            bar.setBackground(ui.round(ui.col(M3.SURFACE_CONTAINER_HIGHEST), 999));
            bar.setClipToOutline(true);
            for (JSONObject p : kinds) {
                long n = NativeData.num(p, "tokens");
                View seg = new View(act);
                GradientDrawable d = new GradientDrawable();
                d.setColor(kindColorOf(ui, NativeData.str(p, "kind", "")));
                seg.setBackground(d);
                bar.addView(seg, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(0.004f, (float) n / total)));
            }
            long rest0 = Math.max(0, total - sumParts);
            if (rest0 > 0) {
                View seg = new View(act);
                bar.addView(seg, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.MATCH_PARENT, (float) rest0 / total));
            }
            root.addView(bar);

            // ---- 方块占比图（网页那张：左两块 + 右侧大片「剩余空间」斜线）----
            root.addView(treemap(act, ui, kinds, total, sumParts));

            // 图例：每 kind 一颗点 + 名字 + 数
            LinearLayout legend = new LinearLayout(act);
            legend.setOrientation(LinearLayout.VERTICAL);
            legend.setLayoutParams(mlp(ui, 10));
            for (JSONObject p : kinds) {
                legend.addView(legendRow(ui, kindColorOf(ui, NativeData.str(p, "kind", "")),
                        kindNameOf(NativeData.str(p, "kind", "")),
                        NativeData.tokens(NativeData.num(p, "tokens"))));
            }
            if (window > 0) {
                legend.addView(legendRow(ui, ui.col(M3.SURFACE_CONTAINER_HIGHEST),
                        "剩余空间", NativeData.tokens(Math.max(0, window - tokens))));
            }
            root.addView(legend);
        }

        // ---- 内容：按 kind 分组的明细 ----
        if (!kinds.isEmpty()) {
            TextView contentTitle = ui.text("内容", 12, M3.ON_SURFACE_VARIANT, false);
            contentTitle.setLayoutParams(mlp(ui, 16));
            root.addView(contentTitle);

            for (JSONObject p : kinds) {
                String kind = NativeData.str(p, "kind", "");
                JSONArray items = NativeData.arr(p, "items");

                LinearLayout group2 = new LinearLayout(act);
                group2.setOrientation(LinearLayout.VERTICAL);
                group2.setLayoutParams(mlp(ui, 10));

                LinearLayout gh = new LinearLayout(act);
                gh.setOrientation(LinearLayout.HORIZONTAL);
                gh.setGravity(Gravity.CENTER_VERTICAL);
                View dot = new View(act);
                GradientDrawable dd = new GradientDrawable();
                dd.setShape(GradientDrawable.OVAL);
                dd.setColor(kindColorOf(ui, kind));
                dot.setBackground(dd);
                gh.addView(dot, new LinearLayout.LayoutParams(ui.dp(8), ui.dp(8)));
                TextView gt = ui.text(kindNameOf(kind), 13, M3.ON_SURFACE, true);
                LinearLayout.LayoutParams gtlp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                gtlp.leftMargin = ui.dp(7);
                gh.addView(gt, gtlp);
                gh.addView(ui.text(NativeData.tokens(NativeData.num(p, "tokens")),
                        12.5f, M3.ON_SURFACE, true));
                group2.addView(gh);

                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.optJSONObject(i);
                    if (it == null) {
                        continue;
                    }
                    String name = NativeData.str(it, "name", "?");
                    int n = NativeData.num(it, "n");
                    if (n > 1) {
                        name += "（" + n + "）";
                    }
                    final String fName = name;
                    final String fKind = kind;
                    final int fTokens = NativeData.num(it, "tokens");
                    View row = itemRow(ui, kindColorOf(ui, kind), name, fTokens);
                    row.setClickable(true);
                    row.setFocusable(true);
                    row.setOnClickListener(v -> PartSheet.show(act, ui, route, fKind, fName, fTokens));
                    group2.addView(row);
                }
                root.addView(group2);
            }
        }

        AlertDialog dlg = new AlertDialog.Builder(act)
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .create();
        styleDialog(act, ui, dlg);
        dlg.show();
    }

    /**
     * 方块占比图：网页上那张「系统提示词 / 工具 / 会话内容 + 剩余空间斜线」。
     *
     * <p>做法：左边一列按从大到小叠各成分（至少占 34% 宽），右边把剩余空间
     * 铺满并画斜线。小成分在里面按 token 比例分高度。
     */
    private static View treemap(Activity act, Ui ui, List<JSONObject> kinds,
                                long total, long sumParts) {
        LinearLayout wrap = new LinearLayout(act);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(150));
        wlp.topMargin = ui.dp(8);
        wrap.setLayoutParams(wlp);

        // 左：成分堆叠（每个块直接挂在 wrap 上按权重分宽）
        float usedShare = (float) sumParts / total;
        float leftWeight = Math.max(0.34f, Math.min(0.7f, usedShare));

        for (int i = 0; i < kinds.size(); i++) {
            JSONObject p = kinds.get(i);
            String kind = NativeData.str(p, "kind", "");
            long n = NativeData.num(p, "tokens");
            float share = sumParts > 0 ? (float) n / sumParts : 1f;

            LinearLayout cell = new LinearLayout(act);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setPadding(ui.dp(8), ui.dp(6), ui.dp(6), ui.dp(6));
            int color = kindColorOf(ui, kind);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor((color & 0x00FFFFFF) | 0x33000000);   // 30% 透明
            bg.setCornerRadius(ui.dp(8));
            bg.setStroke(Math.max(1, ui.dp(1)), (color & 0x00FFFFFF) | 0x66000000);
            cell.setBackground(bg);

            // 只给够高的块写字，太小的写不下
            if (share > 0.12f) {
                TextView nm = ui.text(kindNameOf(kind), 10.5f, M3.ON_SURFACE, true);
                nm.setSingleLine(true);
                nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
                cell.addView(nm);
                TextView tk = ui.mono(NativeData.tokens(n), 9.5f, M3.ON_SURFACE_VARIANT);
                cell.addView(tk);
            }

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, Math.max(0.06f, share));
            if (i > 0) {
                clp.topMargin = ui.dp(3);
            }
            wrap.addView(cell, clp);
        }

        // 右：剩余空间（斜线）
        long rest = Math.max(0, total - sumParts);
        if (rest > 0) {
            LinearLayout right = new LinearLayout(act);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x11000000);
            bg.setCornerRadius(ui.dp(8));
            bg.setStroke(Math.max(1, ui.dp(1)), ui.col(M3.OUTLINE_VARIANT));
            right.setBackground(bg);

            TextView nm = ui.text("剩余空间", 11, M3.ON_SURFACE_VARIANT, false);
            right.addView(nm);
            TextView tk = ui.mono(NativeData.tokens(rest) + " · "
                    + NativeData.percent(rest * 100.0 / total), 10,
                    M3.ON_SURFACE_VARIANT);
            tk.setLayoutParams(mlp(ui, 2));
            right.addView(tk);

            wrap.addView(right, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(0.1f, 1f - leftWeight)));
        }
        return wrap;
    }

    private static LinearLayout.LayoutParams mlp(Ui ui, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ui.dp(topDp);
        return lp;
    }

    private static View legendRow(Ui ui, int color, String name, String value) {
        LinearLayout row = new LinearLayout(ui.ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ui.dp(4);
        row.setLayoutParams(lp);

        View dot = new View(ui.ctx);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        dot.setBackground(d);
        row.addView(dot, new LinearLayout.LayoutParams(ui.dp(8), ui.dp(8)));

        TextView t = ui.text(name, 11.5f, M3.ON_SURFACE_VARIANT, false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = ui.dp(7);
        row.addView(t, tlp);

        row.addView(ui.mono(value, 11.5f, M3.ON_SURFACE_VARIANT));
        return row;
    }

    private static View itemRow(Ui ui, int color, String name, int tokens) {
        LinearLayout row = new LinearLayout(ui.ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ui.dp(5);
        lp.leftMargin = ui.dp(2);
        row.setLayoutParams(lp);

        View dot = new View(ui.ctx);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        dot.setBackground(d);
        row.addView(dot, new LinearLayout.LayoutParams(ui.dp(7), ui.dp(7)));

        TextView t = ui.text(name, 12, M3.ON_SURFACE, false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = ui.dp(8);
        row.addView(t, tlp);

        row.addView(ui.mono(NativeData.tokens(tokens), 11.5f, M3.ON_SURFACE_VARIANT));
        return row;
    }

    private static void styleDialog(Activity act, Ui ui, AlertDialog dlg) {
        try {
            if (dlg.getWindow() != null) {
                dlg.getWindow().setBackgroundDrawable(
                        ui.round(ui.col(M3.SURFACE_CONTAINER_HIGH), 24));
            }
        } catch (Exception ignored) {
        }
    }
}
