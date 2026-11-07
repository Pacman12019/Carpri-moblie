package com.capri.companion;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView wv;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        wv = new WebView(this);
        setContentView(wv);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        wv.addJavascriptInterface(new Bridge(), "Android");
        wv.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (wv.canGoBack()) wv.goBack(); else super.onBackPressed();
    }

    /** Native HTTP so the page isn't limited by CORS or mixed-content rules (LAN servers, Ollama, etc). */
    class Bridge {
        @JavascriptInterface
        public void http(final String id, final String method, final String url,
                         final String headersJson, final String body) {
            new Thread(() -> {
                String result;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestMethod(method);
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(120000);
                    JSONObject h = new JSONObject(headersJson);
                    for (java.util.Iterator<String> it = h.keys(); it.hasNext(); ) {
                        String k = it.next();
                        c.setRequestProperty(k, h.getString(k));
                    }
                    if (body != null && body.length() > 0 && !method.equals("GET")) {
                        c.setDoOutput(true);
                        try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes("UTF-8")); }
                    }
                    int code = c.getResponseCode();
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    if (in != null) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    }
                    result = new JSONObject().put("status", code).put("body", bo.toString("UTF-8")).toString();
                } catch (Exception e) {
                    try { result = new JSONObject().put("status", 0).put("body", String.valueOf(e)).toString(); }
                    catch (Exception ee) { result = "{\"status\":0,\"body\":\"error\"}"; }
                }
                final String js = "window.__nf(" + JSONObject.quote(id) + "," + JSONObject.quote(result) + ")";
                runOnUiThread(() -> wv.evaluateJavascript(js, null));
            }).start();
        }
    }
}
