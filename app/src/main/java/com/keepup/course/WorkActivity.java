package com.keepup.course;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 作业详情 / 作答页：内嵌网页 + 底部「解题助手」。
 * 题型识别、填充逻辑全部在 assets/solver.js 里，这里只负责编排与展示。
 */
public class WorkActivity extends Activity {
    private static final String SYSTEM_PROMPT =
            "你是课程作业的解题助手。用户会给你一份作业的题目，请逐题给出可以直接提交的答案。"
                    + "只输出 JSON 数组，不要任何解释文字。格式：[{\"i\":1,\"answer\":\"B\"}]";

    private WebView web;
    private TextView tvStatus, tvTitle;
    private ProgressBar progress;
    private AuroraView aurora;
    private ProgressDialog solving;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private JSONObject snapshot;       // 最近一次 scan 结果
    private Dialog sheet;
    private LinearLayout sheetList;
    private TextView sheetTitle, sheetHint;
    private Button sheetSolve;
    private View sheetRoot;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_work);

        String url = getIntent().getStringExtra(Notifications.EXTRA_URL);
        String title = getIntent().getStringExtra("title");
        aurora = findViewById(R.id.aurora);
        web = findViewById(R.id.web);
        tvStatus = findViewById(R.id.tvStatus);
        tvTitle = findViewById(R.id.tvTitle);
        progress = findViewById(R.id.progress);

        if (title != null && !title.isEmpty()) tvTitle.setText(title);
        if (url == null || url.isEmpty()) {
            finish();
            return;
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            Ui.tap(v);
            goBack();
        });
        findViewById(R.id.btnReload).setOnClickListener(v -> {
            Ui.tap(v);
            web.reload();
        });
        findViewById(R.id.btnSolve).setOnClickListener(v -> {
            Ui.tap(v);
            openSheet();
        });
        Ui.press(findViewById(R.id.btnSolve));

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);
        Http.copyToWebView(this);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String u) {
                progress.setVisibility(View.GONE);
                injectAndScan();
            }
        });

        progress.setVisibility(View.VISIBLE);
        tvStatus.setText("正在加载作业页…");
        web.loadUrl(url);
    }

    private void goBack() {
        if (web != null && web.canGoBack()) web.goBack();
        else finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (sheet != null && sheet.isShowing()) {
                sheet.dismiss();
                return true;
            }
            goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /* ------------------------------------------------------------ JS 通道 */

    private void js(String code, Consumer<String> cb) {
        web.evaluateJavascript(code, cb::accept);
    }

    /** evaluateJavascript 回调里是「JSON 字符串字面量」，先解转义再解析。 */
    private static String unquote(String raw) {
        if (raw == null) return null;
        if (raw.startsWith("\"") && raw.endsWith("\"")) {
            try {
                Object o = new JSONTokener(raw).nextValue();
                return o instanceof String ? (String) o : raw;
            } catch (Exception ignored) {
                return raw;
            }
        }
        return raw;
    }

    private void injectAndScan() {
        String js = readAsset("solver.js");
        if (js == null) {
            tvStatus.setText("解题脚本缺失");
            return;
        }
        js(js, r -> web.postDelayed(this::scan, 700));
    }

    private String readAsset(String name) {
        try (java.io.InputStream in = getAssets().open(name)) {
            byte[] buf = new byte[in.available()];
            int n = in.read(buf);
            return new String(buf, 0, Math.max(n, 0), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private void scan() {
        js("CRSolver.scan()", raw -> {
            String text = unquote(raw);
            try {
                snapshot = new JSONObject(text == null ? "{}" : text);
            } catch (Exception e) {
                snapshot = null;
            }
            if (snapshot == null || !snapshot.optBoolean("ok")) {
                tvStatus.setText("这个页面没识别到题目");
                return;
            }
            int total = snapshot.optInt("total");
            int solvable = snapshot.optInt("solvable");
            tvStatus.setText("识别到 " + total + " 道题 · 可自动作答 " + solvable + " 道");
            if (sheet != null && sheet.isShowing()) fillSheet(true);
        });
    }

    /* ------------------------------------------------------------ 弹层 */

    private void openSheet() {
        if (snapshot == null || !snapshot.optBoolean("ok")) {
            Toast.makeText(this, "还没识别到题目，等页面加载完再试试", Toast.LENGTH_SHORT).show();
            scan();
            return;
        }
        if (sheet == null) buildSheet();
        fillSheet(true);
        sheet.show();
        if (sheetRoot != null) Ui.sheetIn(sheetRoot);
    }

    private void buildSheet() {
        sheet = new Dialog(this);
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_sheet);
        root.setPadding(dp(22), dp(20), dp(22), dp(18));
        sheetRoot = root;

        // 顶部抓手
        View grip = new View(this);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(38), dp(4));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.bottomMargin = dp(18);
        grip.setLayoutParams(gp);
        grip.setBackgroundColor(getColor(R.color.paper_deep));
        root.addView(grip);

        sheetTitle = new TextView(this);
        sheetTitle.setTextSize(21);
        sheetTitle.setTypeface(Typeface.DEFAULT_BOLD);
        sheetTitle.setTextColor(getColor(R.color.ink));
        root.addView(sheetTitle);

        sheetHint = new TextView(this);
        sheetHint.setTextSize(12.5f);
        sheetHint.setTextColor(getColor(R.color.ink_2));
        sheetHint.setLineSpacing(dp(3), 1f);
        sheetHint.setPadding(0, dp(8), 0, dp(4));
        root.addView(sheetHint);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(336));
        sp.topMargin = dp(10);
        scroll.setLayoutParams(sp);
        scroll.setVerticalScrollBarEnabled(false);
        sheetList = new LinearLayout(this);
        sheetList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(sheetList);
        root.addView(scroll);

        sheetSolve = new Button(this);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        bp.topMargin = dp(14);
        sheetSolve.setLayoutParams(bp);
        sheetSolve.setText("开始解题");
        sheetSolve.setTextColor(Color.WHITE);
        sheetSolve.setTextSize(15);
        sheetSolve.setTypeface(Typeface.DEFAULT_BOLD);
        sheetSolve.setStateListAnimator(null);
        sheetSolve.setBackground(getDrawable(R.drawable.bg_pill));
        sheetSolve.setOnClickListener(v -> {
            Ui.tap(v);
            solve();
        });
        root.addView(sheetSolve);

        TextView tip = new TextView(this);
        tip.setTextSize(11.5f);
        tip.setTextColor(getColor(R.color.ink_3));
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dp(10), 0, 0);
        tip.setText("答案由 AI 生成，填空和简答请自己过一眼再提交");
        root.addView(tip);

        sheet.setContentView(root);
        Window w = sheet.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.45f);
        }
        sheet.setCanceledOnTouchOutside(true);
    }

    private void fillSheet(boolean reset) {
        if (sheetList == null || snapshot == null) return;
        JSONArray qs = snapshot.optJSONArray("questions");
        if (qs == null) return;
        int solvable = snapshot.optInt("solvable");
        sheetTitle.setText(qs.length() + " 道题，可自动作答 " + solvable + " 道");
        sheetHint.setText("只处理选择、判断、填空、简答；要传附件的实践题会跳过。");
        sheetList.removeAllViews();
        for (int i = 0; i < qs.length(); i++) {
            JSONObject q = qs.optJSONObject(i);
            if (q == null) continue;
            View v = row(q);
            sheetList.addView(v);
            Ui.staggerIn(v, i);
        }
    }

    private View row(JSONObject q) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setPadding(0, dp(11), 0, dp(11));

        TextView kind = new TextView(this);
        kind.setLayoutParams(new LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT));
        kind.setTextSize(12);
        kind.setTypeface(Typeface.DEFAULT_BOLD);
        boolean ok = q.optBoolean("solvable");
        String tn = q.optString("typeName");
        kind.setText(tn.replace("题", ""));
        kind.setTextColor(getColor(ok ? R.color.pine : R.color.ink_3));
        box.addView(kind);

        TextView body = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        body.setLayoutParams(lp);
        body.setTextSize(14);
        body.setTextColor(getColor(ok ? R.color.ink : R.color.ink_3));
        body.setLineSpacing(dp(3), 1f);
        body.setMaxLines(3);
        body.setEllipsize(android.text.TextUtils.TruncateAt.END);
        String stem = q.optString("stem");
        body.setText(ok ? stem : stem + "\n" + q.optString("reason"));
        box.addView(body);
        return box;
    }

    /* ------------------------------------------------------------ 解题 */

    private void solve() {
        final JSONObject snap = snapshot;
        if (snap == null) return;

        if (!AiClient.usable(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("先选一个答案来源")
                    .setMessage("默认用免费通道（不需要 Key，速度慢一些）。\n想更快更准可以在「设置 → 解题助手」换成自己的接口。")
                    .setPositiveButton("去设置", (d, w) -> startActivity(new Intent(this, SettingsActivity.class)))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        // ① 先查本地缓存：同一道题只算一次
        List<JSONObject> need = new ArrayList<>();
        JSONArray answers = new JSONArray();
        JSONArray qs = snap.optJSONArray("questions");
        for (int i = 0; i < (qs == null ? 0 : qs.length()); i++) {
            JSONObject q = qs.optJSONObject(i);
            if (q == null || !q.optBoolean("solvable")) continue;
            String hit = Prefs.cacheGet(this, cacheKey(q));
            if (!hit.isEmpty()) {
                try {
                    answers.put(new JSONObject().put("i", q.optInt("i")).put("answer", hit));
                } catch (Exception ignored) {
                }
            } else {
                need.add(q);
            }
        }

        if (need.isEmpty()) {
            fillWith(snap, answers);
            return;
        }

        final List<JSONObject> ask = need;
        final JSONArray acc = answers;
        String userPrompt = buildPrompt(ask);
        solving = ProgressDialog.show(this, "", need.size() < (qs == null ? 0 : qs.length())
                ? "正在解题（" + need.size() + " 道没见过的题）…" : "正在解题，免费通道大约要 20~60 秒…", true, false);
        sheetSolve.setEnabled(false);

        io.execute(() -> {
            try {
                String content = AiClient.chat(this, SYSTEM_PROMPT, userPrompt, true);
                JSONArray got = extractAnswers(content);
                if (got.length() == 0) throw new Exception("AI 没按格式返回答案");
                for (int i = 0; i < got.length(); i++) {
                    JSONObject a = got.optJSONObject(i);
                    if (a == null) continue;
                    acc.put(a);
                    String ans = a.optString("answer");
                    String stem = stemOf(ask, a.optInt("i"));
                    if (!stem.isEmpty() && !ans.isEmpty()) Prefs.cachePut(this, stem, ans);
                }
                runOnUiThread(() -> fillWith(snap, acc));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    dismissSolving();
                    sheetSolve.setEnabled(true);
                    if (isDemo()) {
                        fillWith(snap, demoAnswers());
                        return;
                    }
                    String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    sheetHint.setText("解题失败：" + msg);
                    if (msg.contains("公共通道") || msg.contains("空") || msg.contains("429")
                            || msg.contains("超时") || msg.contains("限流")) {
                        new AlertDialog.Builder(this)
                                .setTitle("这次没拿到答案")
                                .setMessage(msg)
                                .setPositiveButton("重试", (d, w) -> solve())
                                .setNeutralButton("去设置", (d, w) ->
                                        startActivity(new Intent(this, SettingsActivity.class)))
                                .setNegativeButton("关闭", null)
                                .show();
                    }
                });
            }
        });
    }

    private void fillWith(JSONObject snap, JSONArray answers) {
        try {
            JSONObject payload = new JSONObject()
                    .put("questions", snap.getJSONArray("questions"))
                    .put("answers", answers);
            js("CRSolver.fill(" + JSONObject.quote(payload.toString()) + ")", raw -> onFilled(unquote(raw)));
        } catch (Exception e) {
            dismissSolving();
            sheetHint.setText("填充指令发送失败");
        }
    }

    private static String cacheKey(JSONObject q) {
        return q.optString("typeName") + "|" + q.optString("stem");
    }

    /** 缓存 key 与 fill 时的 key 保持一致：按题型+题干。 */
    private static String stemOf(List<JSONObject> list, int i) {
        for (JSONObject q : list) {
            if (q.optInt("i") == i) return cacheKey(q);
        }
        return "";
    }

    private JSONArray demoAnswers() {
        try {
            return new JSONArray()
                    .put(new JSONObject().put("i", 1).put("answer", "B"))
                    .put(new JSONObject().put("i", 2).put("answer", "AB"))
                    .put(new JSONObject().put("i", 3).put("answer", "对"))
                    .put(new JSONObject().put("i", 4).put("answer", "外模式#内模式"))
                    .put(new JSONObject().put("i", 5).put("answer", "外模式/模式映像保证数据的逻辑独立性；模式/内模式映像保证数据的物理独立性。"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void onFilled(String json) {
        dismissSolving();
        sheetSolve.setEnabled(true);
        try {
            JSONObject r = new JSONObject(json == null ? "{}" : json);
            JSONArray results = r.optJSONArray("results");
            int filled = r.optInt("filled");
            if (results != null) {
                sheetList.removeAllViews();
                for (int i = 0; i < results.length(); i++) {
                    JSONObject x = results.optJSONObject(i);
                    if (x == null) continue;
                    View v = resultRow(x);
                    sheetList.addView(v);
                    Ui.staggerIn(v, i);
                }
                sheetHint.setText("已自动填 " + filled + "/" + results.length() + " 题，请自己核对一遍再提交。");
            }
            if (Prefs.autoSubmit(this) && filled > 0) {
                js("CRSolver.submit()", v -> Toast.makeText(this, "已触发提交，若弹出确认框请点确定", Toast.LENGTH_LONG).show());
            } else {
                Toast.makeText(this, "答案已填入，检查后点页面下方「提交」", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            sheetHint.setText("填写结果解析失败");
        }
    }

    private View resultRow(JSONObject x) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setPadding(0, dp(11), 0, dp(11));
        boolean ok = x.optBoolean("ok");

        TextView mark = new TextView(this);
        mark.setLayoutParams(new LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT));
        mark.setTextSize(12);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setText(ok ? "✓ 已填" : "✗ 没填上");
        mark.setTextColor(getColor(ok ? R.color.pine : R.color.alert));
        box.addView(mark);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ans = new TextView(this);
        ans.setTextSize(14);
        ans.setTextColor(getColor(R.color.ink));
        ans.setMaxLines(3);
        ans.setEllipsize(android.text.TextUtils.TruncateAt.END);
        String a = x.optString("answer");
        ans.setText(TextUtils.isEmpty(a) ? "（没有答案）" : shorten(a, 120));
        right.addView(ans);
        if (!ok) {
            TextView why = new TextView(this);
            why.setTextSize(12);
            why.setTextColor(getColor(R.color.ink_3));
            why.setPadding(0, dp(3), 0, 0);
            why.setText(x.optString("why"));
            right.addView(why);
        }
        box.addView(right);
        return box;
    }

    private void dismissSolving() {
        if (solving != null && solving.isShowing()) solving.dismiss();
        solving = null;
    }

    private String buildPrompt(List<JSONObject> solvable) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是作业题目（可能包含单选、多选、判断、填空、简答等类型）。\n\n");
        for (JSONObject q : solvable) {
            sb.append("第 ").append(q.optInt("i")).append(" 题【").append(q.optString("typeName")).append("】")
                    .append(q.optString("stem")).append("\n");
            JSONArray opts = q.optJSONArray("options");
            if (opts != null) {
                for (int j = 0; j < opts.length(); j++) {
                    JSONObject o = opts.optJSONObject(j);
                    if (o == null) continue;
                    sb.append("   ").append(o.optString("key")).append(". ").append(o.optString("text")).append("\n");
                }
            }
            if (q.optInt("blanks") > 0) sb.append("   （共 ").append(q.optInt("blanks")).append(" 个空）\n");
            sb.append("\n");
        }
        sb.append("请只输出 JSON 数组，覆盖上面每一题：\n")
                .append("[{\"i\":1,\"answer\":\"B\"},{\"i\":2,\"answer\":\"AC\"}]\n")
                .append("answer 规则：\n")
                .append("• 单选题：一个选项字母（A/B/C/D…），字母以题目里给出的为准\n")
                .append("• 多选题：多个字母连写，如 ACD\n")
                .append("• 判断题：只写「对」或「错」\n")
                .append("• 填空题：多个空用 # 分隔，顺序与题目一致\n")
                .append("• 简答题/论述题/名词解释：直接给可提交的中文答案正文，不要题号、不要 markdown");
        return sb.toString();
    }

    /** 从模型输出里抠出 JSON 数组，容忍 ```json 包裹和前后废话。 */
    private JSONArray extractAnswers(String content) {
        String s = content == null ? "" : content.trim();
        int a = s.indexOf('[');
        int b = s.lastIndexOf(']');
        if (a >= 0 && b > a) {
            try {
                return new JSONArray(s.substring(a, b + 1));
            } catch (Exception ignored) {
            }
        }
        // 有的模型会包一层 {"answers":[...]}
        int oa = s.indexOf('{');
        int ob = s.lastIndexOf('}');
        if (oa >= 0 && ob > oa) {
            try {
                JSONArray arr = new JSONObject(s.substring(oa, ob + 1)).optJSONArray("answers");
                if (arr != null && arr.length() > 0) return arr;
            } catch (Exception ignored) {
            }
        }
        // 逐条兜底：{"i":1,"answer":"A"}
        JSONArray out = new JSONArray();
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"i\"\\s*:\\s*\"?(\\d+)\"?[^}]*?\"answer\"\\s*:\\s*\"([^\"]*)\"")
                    .matcher(s);
            while (m.find()) {
                out.put(new JSONObject().put("i", Integer.parseInt(m.group(1))).put("answer", m.group(2)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private boolean isDemo() {
        String u = getIntent().getStringExtra(Notifications.EXTRA_URL);
        return u != null && u.contains("test_work.html");
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private static String shorten(String s, int n) {
        String t = s.replaceAll("\\s+", " ");
        return t.length() > n ? t.substring(0, n) + "…" : t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (aurora != null) aurora.resume();
    }

    @Override
    protected void onPause() {
        if (aurora != null) aurora.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        dismissSolving();
        if (sheet != null) {
            sheet.dismiss();
            sheet = null;
        }
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
