package com.benim.codemate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.ConnectionSpec;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.TlsVersion;
import okio.BufferedSource;

public class MainActivity extends Activity {
    private static final int BG = 0xFFFAF9F5, INK = 0xFF1F1E1D, MUT = 0xFF83827D, ACC = 0xFFD97757, UB = 0xFFF0EEE6, CODE = 0xFF1F1E1D;
    private static final String[] MODELS = {"claude-opus-5-5", "claude-sonnet-5-5"};
    private static final String[] MLABEL = {"Opus 5.5 (güçlü)", "Sonnet 5.5 (hızlı)"};
    private static final String SYS =
        "Adin CodeMate. Sen deneyimli bir yazilim muhendisi ve kod asistanisin. Kullanici C, C++, Python ve Java ile calisiyor; "
        + "takildigi veya kafasinin karistigi yerlerde sana geliyor. Turkce cevap ver, kod ve tanimlayicilar Ingilizce kalsin.\n"
        + "Kurallar:\n"
        + "1) Hata aramada once en olasi kok nedeni soyle, ilgili satiri/ifadeyi goster, sonra duzeltilmis kodu eksiksiz ver. Sadece belirtiyi degil nedeni acikla.\n"
        + "2) Kendi yazdigin kodu vermeden once zihninde derle ve calistir: sinir durumlari, tur ve tasma hatalari, bellek "
        + "(C/C++: tanimsiz davranis, sizinti, dangling pointer, buffer tasmasi, off-by-one; Python: mutable default, surum farki, indent; "
        + "Java: NPE, equals/hashCode, thread guvenligi) ve derleme hatalarini kontrol et; buldugun hatayi duzelt.\n"
        + "3) C/C++ icin uygun derleme bayraklarini oner (-Wall -Wextra -fsanitize=address,undefined).\n"
        + "4) Eksik bilgi (derleyici, surum, tam hata mesaji) varsa varsayimini belirt, en iyi cevabi yine ver; gerekirse tek kisa soru sor.\n"
        + "5) Kod bloklari ```dil bicimi ile olsun. Gereksiz giris ve sus yok, kisa ve net ol. Guncel API/surum bilgisi gerekirse web aramasini kullan.";

    private LinearLayout log;
    private ScrollView scroll;
    private EditText input;
    private Button send, modelBtn;
    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ArrayList<String[]> hist = new ArrayList<String[]>();
    private boolean busy = false;
    private int mi = 0;
    private OkHttpClient client;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("cm", MODE_PRIVATE);
        client = buildClient();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(8), dp(8), dp(8));
        TextView title = new TextView(this);
        title.setText("* CodeMate");
        title.setTextSize(20);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setTextColor(INK);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button keyBtn = new Button(this);
        keyBtn.setText("Anahtar");
        keyBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { askKey(); } });
        top.addView(keyBtn);
        Button newBtn = new Button(this);
        newBtn.setText("Yeni");
        newBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (!busy) { hist.clear(); showEmpty(); } } });
        top.addView(newBtn);
        root.addView(top);

        scroll = new ScrollView(this);
        log = new LinearLayout(this);
        log.setOrientation(LinearLayout.VERTICAL);
        log.setPadding(dp(14), dp(6), dp(14), dp(14));
        scroll.addView(log, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(10), dp(6), dp(10), dp(8));
        input = new EditText(this);
        input.setHint("Kodunu yapıştır veya sorunu yaz...");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setMaxLines(6);
        input.setTextColor(INK);
        input.setHintTextColor(MUT);
        bottom.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        modelBtn = new Button(this);
        modelBtn.setText(MLABEL[0]);
        modelBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { mi = 1 - mi; modelBtn.setText(MLABEL[mi]); }
        });
        row.addView(modelBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        send = new Button(this);
        send.setText("Gönder");
        send.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { ask(); } });
        row.addView(send);
        bottom.addView(row);
        root.addView(bottom);

        setContentView(root);
        showEmpty();
    }

    private OkHttpClient buildClient() {
        OkHttpClient.Builder cb = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS);
        if (Build.VERSION.SDK_INT < 21) {
            try {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init((KeyStore) null);
                TrustManager[] tms = tmf.getTrustManagers();
                X509TrustManager tm = (X509TrustManager) tms[0];
                SSLContext sc = SSLContext.getInstance("TLSv1.2");
                sc.init(null, new TrustManager[]{tm}, null);
                cb.sslSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()), tm);
                ConnectionSpec cs = new ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2).build();
                cb.connectionSpecs(Collections.singletonList(cs));
            } catch (Exception e) {
                // eski telefonda TLS 1.2 acilamadiysa varsayilanla devam
            }
        }
        return cb.build();
    }

    private void askKey() {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setText(prefs.getString("ak", ""));
        new AlertDialog.Builder(this)
            .setTitle("API anahtarı")
            .setMessage("Anthropic API anahtarını (sk-ant-...) yapıştır. Sadece bu telefonda saklanır.")
            .setView(et)
            .setPositiveButton("Kaydet", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { prefs.edit().putString("ak", et.getText().toString().trim()).commit(); }
            })
            .setNegativeButton("İptal", null)
            .show();
    }

    private void showEmpty() {
        log.removeAllViews();
        TextView t = new TextView(this);
        t.setText("*\nHangi kodla uğraşıyoruz?");
        t.setGravity(Gravity.CENTER);
        t.setTextSize(24);
        t.setTypeface(Typeface.SERIF);
        t.setTextColor(INK);
        t.setPadding(0, dp(80), 0, dp(8));
        log.addView(t);
        TextView s = new TextView(this);
        s.setText("C, C++, Python, Java: hata bul, düzelt, açıkla.");
        s.setGravity(Gravity.CENTER);
        s.setTextColor(MUT);
        log.addView(s);
    }

    private void scrollDown() {
        scroll.post(new Runnable() { public void run() { scroll.fullScroll(View.FOCUS_DOWN); } });
    }

    private void addUser(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(INK);
        tv.setTextSize(16);
        tv.setPadding(dp(14), dp(9), dp(14), dp(9));
        GradientDrawable g = new GradientDrawable();
        g.setColor(UB);
        g.setCornerRadius(dp(16));
        tv.setBackgroundDrawable(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.RIGHT;
        lp.topMargin = dp(14);
        log.addView(tv, lp);
    }

    private LinearLayout addAssistantBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        log.addView(box, lp);
        return box;
    }

    private void thinking(LinearLayout box) {
        box.removeAllViews();
        TextView t = new TextView(this);
        t.setText("* Düşünüyor...");
        t.setTextColor(MUT);
        t.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        box.addView(t);
    }

    private CharSequence inline(String s) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int i = 0;
        while (i < s.length()) {
            if (s.startsWith("**", i)) {
                int e = s.indexOf("**", i + 2);
                if (e > i + 2) {
                    int st = sb.length();
                    sb.append(s.substring(i + 2, e));
                    sb.setSpan(new StyleSpan(Typeface.BOLD), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = e + 2;
                    continue;
                }
            }
            if (s.charAt(i) == '`') {
                int e = s.indexOf('`', i + 1);
                if (e > i + 1) {
                    int st = sb.length();
                    sb.append(s.substring(i + 1, e));
                    sb.setSpan(new TypefaceSpan("monospace"), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new BackgroundColorSpan(0xFFEDEAE0), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = e + 1;
                    continue;
                }
            }
            sb.append(s.charAt(i));
            i++;
        }
        return sb;
    }

    private void prose(LinearLayout box, String s) {
        String[] blocks = s.split("\n{2,}");
        for (int k = 0; k < blocks.length; k++) {
            String b = blocks[k].trim();
            if (b.length() == 0) continue;
            boolean head = b.startsWith("#");
            if (head) b = b.replaceFirst("^#+\\s*", "");
            TextView tv = new TextView(this);
            tv.setText(inline(b));
            tv.setTextColor(INK);
            tv.setTextSize(16);
            if (head) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tv.setTextIsSelectable(true);
            tv.setPadding(0, 0, 0, dp(8));
            box.addView(tv);
        }
    }

    private void code(LinearLayout box, String s) {
        int nl = s.indexOf('\n');
        String lang = nl > -1 ? s.substring(0, nl).trim() : "";
        String c = nl > -1 ? s.substring(nl + 1) : s;
        if (c.endsWith("\n")) c = c.substring(0, c.length() - 1);
        final String text = c;

        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CODE);
        g.setCornerRadius(dp(10));
        w.setBackgroundDrawable(g);

        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setPadding(dp(12), dp(6), dp(12), dp(2));
        TextView l = new TextView(this);
        l.setText(lang.length() > 0 ? lang : "kod");
        l.setTextColor(0xFFA9A69D);
        l.setTextSize(12);
        h.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView cp = new TextView(this);
        cp.setText("Kopyala");
        cp.setTextColor(0xFFE5E1D6);
        cp.setTextSize(13);
        cp.setPadding(dp(8), dp(4), dp(4), dp(4));
        cp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("kod", text));
                cp.setText("Kopyalandı");
            }
        });
        h.addView(cp);
        w.addView(h);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        TextView body = new TextView(this);
        body.setText(text);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextSize(13);
        body.setTextColor(0xFFECE9E1);
        body.setTextIsSelectable(true);
        body.setPadding(dp(12), dp(4), dp(12), dp(12));
        hs.addView(body);
        w.addView(hs);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(8);
        box.addView(w, lp);
    }

    private void render(LinearLayout box, String text) {
        box.removeAllViews();
        String[] parts = text.split("```", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i % 2 == 0) prose(box, parts[i]);
            else code(box, parts[i]);
        }
        if (box.getChildCount() == 0) thinking(box);
        scrollDown();
    }

    private static String errMsg(String raw) {
        try {
            JSONObject o = new JSONObject(raw);
            JSONObject e = o.optJSONObject("error");
            if (e != null) return e.optString("message", raw);
        } catch (JSONException ignored) { }
        return raw;
    }

    private void ask() {
        final String t = input.getText().toString().trim();
        if (t.length() == 0 || busy) return;
        final String key = prefs.getString("ak", "");
        if (key.length() == 0) { askKey(); return; }
        if (hist.size() == 0) log.removeAllViews();
        busy = true;
        send.setEnabled(false);
        input.setText("");
        addUser(t);
        final LinearLayout box = addAssistantBox();
        thinking(box);
        scrollDown();

        final String model = MODELS[mi];
        final ArrayList<String[]> snapshot = new ArrayList<String[]>(hist);
        final StringBuilder sb = new StringBuilder();
        final boolean[] pending = {false};
        final Runnable paint = new Runnable() {
            public void run() {
                String snap;
                synchronized (sb) { snap = sb.toString(); }
                pending[0] = false;
                render(box, snap);
            }
        };

        new Thread(new Runnable() {
            public void run() {
                String error = null;
                try {
                    JSONArray ms = new JSONArray();
                    int from = Math.max(0, snapshot.size() - 20);
                    for (int i = from; i < snapshot.size(); i++) {
                        JSONObject m = new JSONObject();
                        m.put("role", snapshot.get(i)[0]);
                        m.put("content", snapshot.get(i)[1]);
                        ms.put(m);
                    }
                    JSONObject um = new JSONObject();
                    um.put("role", "user");
                    um.put("content", t);
                    ms.put(um);

                    JSONObject body = new JSONObject();
                    body.put("model", model);
                    body.put("max_tokens", 16000);
                    body.put("stream", true);
                    body.put("system", SYS);
                    body.put("messages", ms);
                    JSONArray tools = new JSONArray();
                    JSONObject ws = new JSONObject();
                    ws.put("type", "web_search_20250305");
                    ws.put("name", "web_search");
                    ws.put("max_uses", 3);
                    tools.put(ws);
                    body.put("tools", tools);

                    Request req = new Request.Builder()
                        .url("https://api.anthropic.com/v1/messages")
                        .header("x-api-key", key)
                        .header("anthropic-version", "2023-06-01")
                        .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body.toString()))
                        .build();
                    Response resp = client.newCall(req).execute();
                    try {
                        if (!resp.isSuccessful()) {
                            throw new Exception("HTTP " + resp.code() + ": " + errMsg(resp.body().string()));
                        }
                        BufferedSource src = resp.body().source();
                        String line;
                        while ((line = src.readUtf8Line()) != null) {
                            if (!line.startsWith("data: ")) continue;
                            JSONObject ev;
                            try { ev = new JSONObject(line.substring(6)); } catch (JSONException e) { continue; }
                            String type = ev.optString("type");
                            if ("error".equals(type)) {
                                JSONObject eo = ev.optJSONObject("error");
                                throw new Exception(eo != null ? eo.optString("message", "akış hatası") : "akış hatası");
                            }
                            if ("content_block_delta".equals(type)) {
                                JSONObject d = ev.optJSONObject("delta");
                                if (d != null && "text_delta".equals(d.optString("type"))) {
                                    synchronized (sb) { sb.append(d.optString("text")); }
                                    if (!pending[0]) { pending[0] = true; ui.postDelayed(paint, 150); }
                                }
                            }
                        }
                    } finally {
                        resp.close();
                    }
                } catch (Exception e) {
                    error = e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                final String err = error;
                ui.post(new Runnable() {
                    public void run() {
                        String all;
                        synchronized (sb) { all = sb.toString(); }
                        if (err != null && all.length() == 0) {
                            box.removeAllViews();
                            TextView et = new TextView(MainActivity.this);
                            et.setText("Hata: " + err + "\n\nTelefonun saati/tarihi doğru mu? İnternet var mı? Anahtar doğru mu?");
                            et.setTextColor(0xFFB3261E);
                            et.setTextIsSelectable(true);
                            box.addView(et);
                        } else {
                            render(box, all.length() > 0 ? all : "(boş cevap)");
                            if (err != null) Toast.makeText(MainActivity.this, "Bağlantı kesildi: " + err, Toast.LENGTH_LONG).show();
                            hist.add(new String[]{"user", t});
                            hist.add(new String[]{"assistant", all.length() > 0 ? all : "(boş)"});
                        }
                        busy = false;
                        send.setEnabled(true);
                    }
                });
            }
        }).start();
    }
}
