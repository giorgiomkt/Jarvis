package app.jarvis.pantera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/**
 * JARVIS: painel Pantera em tela cheia.
 * Carrega o painel embutido, liga voz nativa (ouvir e falar) e
 * toca a playlist do Spotify sempre que o carro liga.
 */
public class MainActivity extends Activity {
    private static final String HOST = "https://appassets.androidplatform.net/assets/index.html";
    private static boolean autoplayedThisBoot = false;

    private WebView web;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private SpeechRecognizer recognizer;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("jarvis", MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        web.setBackgroundColor(0xFF000000);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setGeolocationEnabled(true);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setTextZoom(100);

        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                openExternal(u.toString());
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(final PermissionRequest request) {
                ui.post(() -> request.grant(request.getResources()));
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                cb.invoke(origin, true, false);
            }
        });
        web.addJavascriptInterface(new Bridge(), "Native");
        setContentView(web);
        web.loadUrl(HOST);

        askPermissions();
        initTts();

        if (!autoplayedThisBoot) {
            autoplayedThisBoot = true;
            ui.postDelayed(this::autoplay, 4000);
        }
    }

    // ---------- Tela cheia ----------
    private void immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    // ---------- Permissões ----------
    private void askPermissions() {
        ArrayList<String> need = new ArrayList<>();
        need.add(Manifest.permission.RECORD_AUDIO);
        need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 31) {
            need.add(Manifest.permission.BLUETOOTH_SCAN);
            need.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        ArrayList<String> missing = new ArrayList<>();
        for (String p : need) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), 7);

        // Para abrir sozinho quando o carro liga, o Android exige "Sobrepor a outros apps".
        if (!Settings.canDrawOverlays(this) && !prefs.getBoolean("askedOverlay", false)) {
            prefs.edit().putBoolean("askedOverlay", true).apply();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) { }
        }
    }

    // ---------- Voz: falar ----------
    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("pt", "BR"));
                tts.setPitch(0.85f);
                ttsReady = true;
            }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { js("window.__nSpeak&&__nSpeak('start')"); }
            @Override public void onDone(String id) { js("window.__nSpeak&&__nSpeak('end')"); }
            @Override public void onError(String id) { js("window.__nSpeak&&__nSpeak('end')"); }
        });
    }

    // ---------- Voz: ouvir ----------
    private void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            js("window.__nListen&&__nListen('error','5');window.__nListen&&__nListen('end','')");
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle b) { js("__nListen('start','')"); }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float v) { }
                @Override public void onBufferReceived(byte[] b) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onError(int error) {
                    js("__nListen('error'," + JSONObject.quote(String.valueOf(error)) + ");__nListen('end','')");
                }
                @Override public void onResults(Bundle b) {
                    String t = first(b);
                    js("__nListen('final'," + JSONObject.quote(t) + ");__nListen('end','')");
                }
                @Override public void onPartialResults(Bundle b) {
                    js("__nListen('partial'," + JSONObject.quote(first(b)) + ")");
                }
                @Override public void onEvent(int t, Bundle b) { }
            });
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        recognizer.startListening(i);
    }

    private static String first(Bundle b) {
        ArrayList<String> r = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return (r == null || r.isEmpty()) ? "" : r.get(0);
    }

    // ---------- Spotify: playlist ao ligar ----------
    private static String toSpotifyUri(String link) {
        if (link == null) return null;
        link = link.trim();
        if (link.startsWith("spotify:")) return link;
        Uri u = Uri.parse(link);
        if (u.getHost() != null && u.getHost().contains("spotify.com")) {
            java.util.List<String> seg = u.getPathSegments();
            // ex.: /playlist/ID ou /intl-pt/playlist/ID
            for (int k = 0; k + 1 < seg.size(); k++) {
                String t = seg.get(k);
                if (t.equals("playlist") || t.equals("album") || t.equals("track") || t.equals("artist")) {
                    return "spotify:" + t + ":" + seg.get(k + 1);
                }
            }
        }
        return null;
    }

    private void playPlaylist(boolean comeBack) {
        String uri = toSpotifyUri(prefs.getString("playlist", ""));
        if (uri == null) return;
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        i.setPackage("com.spotify.music");
        i.putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://" + getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (ActivityNotFoundException e) { return; }
        ui.postDelayed(() -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY), 3500);
        if (comeBack) {
            ui.postDelayed(() -> {
                Intent back = new Intent(this, MainActivity.class);
                back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                try { startActivity(back); } catch (Exception ignored) { }
            }, 5200);
        }
    }

    private void autoplay() { playPlaylist(true); }

    private void mediaKey(int code) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    // ---------- Abrir apps e links ----------
    private void openExternal(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) { }
    }

    private void js(final String code) {
        ui.post(() -> { if (web != null) web.evaluateJavascript(code, null); });
    }

    /** Ponte chamada pelo painel (window.Native). */
    private class Bridge {
        @JavascriptInterface public boolean isNative() { return true; }
        @JavascriptInterface public void speak(String text, double rate) {
            if (!ttsReady || text == null) { js("window.__nSpeak&&__nSpeak('end')"); return; }
            tts.setSpeechRate((float) rate);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis");
        }
        @JavascriptInterface public void stopSpeak() { if (tts != null) tts.stop(); }
        @JavascriptInterface public void listen() { ui.post(MainActivity.this::startListening); }
        @JavascriptInterface public void stopListen() { ui.post(() -> { if (recognizer != null) recognizer.stopListening(); }); }
        @JavascriptInterface public void open(String url) { openExternal(url); }
        @JavascriptInterface public void setPlaylist(String link) { prefs.edit().putString("playlist", link == null ? "" : link).apply(); }
        @JavascriptInterface public void playPlaylist() { ui.post(() -> playPlaylist(false)); }
    }

    @Override public void onBackPressed() {
        if (web != null) web.evaluateJavascript("document.querySelectorAll('.drawer').forEach(d=>d.hidden=true);var h=document.getElementById('hub');if(h)h.hidden=true;", null);
    }

    @Override protected void onDestroy() {
        if (tts != null) tts.shutdown();
        if (recognizer != null) recognizer.destroy();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
