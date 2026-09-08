package com.rgbtv.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;

/** RGBTv Android shell: a full-screen WebView hosting the same HTML5 app as the webOS build. */
public class MainActivity extends Activity {
    /** Many IPTV panels reject browser/WebView user agents (HTTP 512/403). Present ourselves as a media player instead. */
    static final String UA = "RGBTv/2.0 (Android) IPTVSmarters/3.1 ExoPlayerLib/2.18";
    private WebView web;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(UA);
        if (Build.VERSION.SDK_INT >= 16) { s.setAllowFileAccessFromFileURLs(true); s.setAllowUniversalAccessFromFileURLs(true); }
        web.setBackgroundColor(0xFF0B0F19);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.startsWith("file://")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (Exception e) { }
                return true;
            }
        });
        web.addJavascriptInterface(new Host(), "RGBTvHost");
        setContentView(web);
        immersive();
        web.loadUrl("file:///android_asset/www/index.html");
    }

    /** exposed to JS as window.RGBTvHost */
    public class Host {
        @JavascriptInterface public void exit() { runOnUiThread(new Runnable() { public void run() { finishAffinity(); } }); }
        @JavascriptInterface public String platform() { return "android"; }
        @JavascriptInterface public boolean isTv() { return getPackageManager().hasSystemFeature("android.software.leanback"); }
        /** Native HTTP (used by the app for all API calls): custom headers allowed, no CORS, player UA. Returns JSON {status, body}. */
        /** async variant: runs on a background thread, result delivered to window.RGBTvHostCb(id, json) */
        @JavascriptInterface public void fetchAsync(final int id, final String url, final String method, final String headersJson, final String body, final int timeoutMs) {
            new Thread(new Runnable() { public void run() {
                final String r = fetch(url, method, headersJson, body, timeoutMs);
                final String js = "window.RGBTvHostCb&&RGBTvHostCb(" + id + "," + org.json.JSONObject.quote(r) + ")";
                runOnUiThread(new Runnable() { public void run() { if (web != null) web.evaluateJavascript(js, null); } });
            } }).start();
        }
        @JavascriptInterface public String fetch(String url, String method, String headersJson, String body, int timeoutMs) {
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setInstanceFollowRedirects(true); c.setConnectTimeout(timeoutMs > 0 ? timeoutMs : 20000); c.setReadTimeout(timeoutMs > 0 ? timeoutMs : 20000);
                c.setRequestMethod(method == null || method.isEmpty() ? "GET" : method);
                c.setRequestProperty("User-Agent", UA); c.setRequestProperty("Accept", "*/*");
                if (headersJson != null && !headersJson.isEmpty()) { org.json.JSONObject h = new org.json.JSONObject(headersJson); java.util.Iterator<String> it = h.keys(); while (it.hasNext()) { String k = it.next(); c.setRequestProperty(k, h.getString(k)); } }
                if (body != null && !body.isEmpty()) { c.setDoOutput(true); java.io.OutputStream os = c.getOutputStream(); os.write(body.getBytes("UTF-8")); os.close(); }
                int st = c.getResponseCode();
                java.io.InputStream in = st >= 400 ? c.getErrorStream() : c.getInputStream();
                String txt = "";
                if (in != null) { java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] buf = new byte[16384]; int n; while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); in.close(); txt = bo.toString("UTF-8"); }
                org.json.JSONObject r = new org.json.JSONObject(); r.put("status", st); r.put("body", txt); return r.toString();
            } catch (Exception e) {
                try { org.json.JSONObject r = new org.json.JSONObject(); r.put("status", 0); r.put("error", String.valueOf(e.getMessage())); return r.toString(); } catch (Exception e2) { return "{\"status\":0}"; }
            } finally { if (c != null) c.disconnect(); }
        }
    }

    private void immersive() {
        View d = getWindow().getDecorView();
        d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) immersive(); }

    /** Hardware/TV-remote keys → the app's own key engine (same codes as webOS). */
    @Override public boolean onKeyDown(int code, KeyEvent ev) {
        int js = mapKey(code);
        if (js != 0) { web.evaluateJavascript("window.Nav&&Nav.press(" + js + ")", null); return true; }
        return super.onKeyDown(code, ev);
    }
    private int mapKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BACK: return 461;
            case KeyEvent.KEYCODE_MEDIA_PLAY: return 415; case KeyEvent.KEYCODE_MEDIA_PAUSE: return 19; case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return 10252;
            case KeyEvent.KEYCODE_MEDIA_STOP: return 413; case KeyEvent.KEYCODE_MEDIA_REWIND: return 412; case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: return 417;
            case KeyEvent.KEYCODE_MEDIA_NEXT: return 418; case KeyEvent.KEYCODE_MEDIA_PREVIOUS: return 419;
            case KeyEvent.KEYCODE_PROG_RED: return 403; case KeyEvent.KEYCODE_PROG_GREEN: return 404; case KeyEvent.KEYCODE_PROG_YELLOW: return 405; case KeyEvent.KEYCODE_PROG_BLUE: return 406;
            case KeyEvent.KEYCODE_INFO: return 457; case KeyEvent.KEYCODE_CHANNEL_UP: return 33; case KeyEvent.KEYCODE_CHANNEL_DOWN: return 34;
            default: return 0; // arrows / enter / digits reach the WebView natively as keydown events
        }
    }
    @Override protected void onPause() { super.onPause(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); immersive(); }
    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }
}
