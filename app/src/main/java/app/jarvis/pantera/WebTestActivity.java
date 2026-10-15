package app.jarvis.pantera;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Teste 1: só uma WebView com uma página mínima. */
public class WebTestActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Diag.stage(this, "WebTest: criando WebView");
        WebView w = new WebView(this);
        Diag.stage(this, "WebTest: WebView criada");
        w.getSettings().setJavaScriptEnabled(true);
        w.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) { Diag.stage(WebTestActivity.this, "WebTest: página carregada"); }
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                Diag.stage(WebTestActivity.this, "WebTest: renderizador da WebView caiu (crash=" + d.didCrash() + ")");
                finish();
                return true;
            }
        });
        setContentView(w);
        String html = "<html><body style='background:#000;color:#fff;font-family:sans-serif;text-align:center;padding-top:15vh'>"
                + "<h1 style='letter-spacing:.4em'>WEBVIEW OK</h1><p id=j style='color:#ffb340;font-size:22px'>JavaScript: não executou</p>"
                + "<p style='color:#888'>Toque em voltar para o teste seguinte.</p>"
                + "<script>document.getElementById('j').textContent='JavaScript: funcionando · '+navigator.userAgent.match(/Chrome\\/[\\d.]+/)</script></body></html>";
        w.loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "UTF-8", null);
        Diag.stage(this, "WebTest: carregando página");
    }
}
