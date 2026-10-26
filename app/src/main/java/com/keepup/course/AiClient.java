package com.keepup.course;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 极简 OpenAI 兼容客户端（DeepSeek / 通义 / 智谱 / 硅基流动 / 自建中转都能用）。
 * 只做一件事：给一段提示词，拿回一段 JSON 文本。
 */
public final class AiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static OkHttpClient client;

    public static class Config {
        public boolean free = true;
        public String base = "";
        public String key = "";
        public String model = "";

        public boolean ready() {
            return free || (!base.isEmpty() && !key.isEmpty() && !model.isEmpty());
        }
    }

    public static Config config(Context c) {
        Config cfg = new Config();
        cfg.free = Prefs.aiFree(c);
        cfg.base = Prefs.aiBase(c).trim();
        cfg.key = Prefs.aiKey(c).trim();
        cfg.model = Prefs.aiModel(c).trim();
        return cfg;
    }

    public static boolean usable(Context c) {
        return config(c).ready();
    }

    private static OkHttpClient freeClient;

    private static synchronized OkHttpClient client() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(180, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();
        }
        return client;
    }

    /** 公共通道：单次最多等 50 秒，避免某个模型卡住把整次解题拖死 */
    private static synchronized OkHttpClient freeClient() {
        if (freeClient == null) {
            freeClient = new OkHttpClient.Builder()
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(50, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .build();
        }
        return freeClient;
    }

    private static String endpoint(String base) {
        String b = base.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (b.endsWith("/chat/completions")) return b;
        if (b.endsWith("/v1")) return b + "/chat/completions";
        return b + "/v1/chat/completions";
    }

    /** 免 Key 公共通道：text.pollinations.ai，OpenAI 兼容、匿名可用，慢一点但不要钱。 */
    public static final String FREE_ENDPOINT = "https://text.pollinations.ai/openai";

    /** 免费通道可轮换的模型：某个模型排队/空返回时换下一个 */
    private static final String[] FREE_MODELS = {"openai", "openai-fast"};

    /** 失败抛 IOException，message 直接可展示给用户。 */
    public static String chat(Context c, String system, String user, boolean jsonMode) throws IOException {
        Config cfg = config(c);
        if (!cfg.ready()) throw new IOException("还没有配置 AI 接口（设置 → 解题助手）");
        return cfg.free ? freeChat(system, user) : paidChat(cfg, system, user, jsonMode);
    }

    /**
     * 免费通道：公共接口经常限流或返回空，这里做「换模型 + 退避重试」，
     * 全部失败才报错，并把原因说清楚，避免用户只看到一句"空答案"。
     */
    private static String freeChat(String system, String user) throws IOException {
        String lastErr = "";
        for (int round = 0; round < 3; round++) {
            for (String model : FREE_MODELS) {
                try {
                    return once(FREE_ENDPOINT, null, model, system, user, false, true);
                } catch (IOException e) {
                    lastErr = e.getMessage() == null ? "请求失败" : e.getMessage();
                    // 429 / 5xx / 空返回 都值得换一个模型再试
                    sleep(round == 0 ? 1200 : (round == 1 ? 2500 : 4000));
                }
            }
        }
        throw new IOException("公共通道繁忙：" + lastErr
                + "。可以过一会儿重试，或在「设置 → 解题助手」填自己的接口（更快更稳）。");
    }

    private static String paidChat(Config cfg, String system, String user, boolean jsonMode) throws IOException {
        try {
            return once(endpoint(cfg.base), cfg.key, cfg.model, system, user, jsonMode, false);
        } catch (IOException first) {
            sleep(800);
            return once(endpoint(cfg.base), cfg.key, cfg.model, system, user, jsonMode, false);
        }
    }

    private static String once(String url, String key, String model, String system, String user,
                               boolean jsonMode, boolean free) throws IOException {
        JSONArray messages = new JSONArray();
        try {
            if (system != null && !system.isEmpty()) {
                messages.put(new JSONObject().put("role", "system").put("content", system));
            }
            messages.put(new JSONObject().put("role", "user").put("content", user));
            JSONObject body = new JSONObject()
                    .put("model", model)
                    .put("messages", messages)
                    .put("temperature", 0.2)
                    .put("stream", false);
            if (free) body.put("private", true);
            if (jsonMode && !free) body.put("response_format", new JSONObject().put("type", "json_object"));

            Request.Builder rb = new Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                    .post(RequestBody.create(body.toString(), JSON));
            if (key != null && !key.isEmpty()) rb.header("Authorization", "Bearer " + key);

            try (Response r = (free ? freeClient() : client()).newCall(rb.build()).execute()) {
                String text = r.body() == null ? "" : r.body().string();
                if (!r.isSuccessful()) {
                    throw new IOException("接口返回 " + r.code() + "：" + brief(text));
                }
                return contentOf(text);
            }
        } catch (org.json.JSONException e) {
            throw new IOException("请求组装失败：" + e.getMessage());
        }
    }

    /** 兼容三种返回：标准 OpenAI JSON、纯文本、以及 pollinations 的错误串 */
    private static String contentOf(String text) throws IOException {
        if (text == null) throw new IOException("接口没有返回内容");
        String t = text.trim();
        if (t.isEmpty()) throw new IOException("接口返回了空内容");

        if (t.startsWith("{")) {
            try {
                JSONObject o = new JSONObject(t);
                JSONArray choices = o.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
                    String content = msg == null ? "" : msg.optString("content", "");
                    if (!content.isEmpty()) return content;
                    // 有些模型把内容放在 reasoning_content
                    String reason = msg == null ? "" : msg.optString("reasoning_content", "");
                    if (reason.isEmpty() && msg != null) reason = msg.optString("reasoning", "");
                    if (!reason.isEmpty()) return reason;
                }
                String err = o.optString("error", "");
                if (!err.isEmpty()) throw new IOException("接口报错：" + brief(err));
                throw new IOException("接口没有返回答案");
            } catch (org.json.JSONException e) {
                // 不是标准 JSON，当作纯文本处理
                return t;
            }
        }
        // 纯文本：公共接口在限流时常返回一句提示，交给上层判断
        if (t.toLowerCase().contains("rate limit") || t.contains("Too Many Requests")) {
            throw new IOException("公共通道被限流");
        }
        if (t.length() < 8) throw new IOException("接口返回内容太短");
        return t;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String brief(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 160 ? t.substring(0, 160) + "…" : t;
    }
}
