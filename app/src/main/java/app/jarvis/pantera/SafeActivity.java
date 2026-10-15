package app.jarvis.pantera;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Tela de teste do JARVIS: só componentes nativos simples, sem WebView,
 * sem microfone, sem automações. Mostra o aparelho, o último crash e
 * libera os testes um por vez.
 */
public class SafeActivity extends Activity {
    private TextView status;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Diag.stage(this, "SafeActivity.onCreate");
        build();
        Diag.stage(this, "SafeActivity desenhada");
    }

    @Override protected void onResume() {
        super.onResume();
        build();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private Button button(String label, boolean primary, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(label); btn.setAllCaps(false); btn.setTextSize(18);
        btn.setTextColor(primary ? Color.BLACK : Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(primary ? 0xFFFFB340 : 0xFF1A1A1D);
        bg.setStroke(dp(1), primary ? 0xFFFFB340 : 0xFF3A3A3F);
        btn.setBackground(bg);
        btn.setMinHeight(dp(56));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(l);
        return btn;
    }

    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.BLACK);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(20), dp(24), dp(28));
        sv.addView(col);

        TextView title = text("J A R V I S", 34, Color.WHITE, false);
        title.setGravity(Gravity.CENTER);
        col.addView(title);
        TextView sub = text("Modo de teste · versão segura", 16, 0xFFFFB340, false);
        sub.setGravity(Gravity.CENTER);
        col.addView(sub);
        TextView ok = text("Se você está lendo isto, o app abriu no AutoCast.", 18, 0xFF7FF0B0, true);
        ok.setGravity(Gravity.CENTER);
        col.addView(ok);

        col.addView(text("APARELHO", 13, 0xFF8E8A86, true));
        col.addView(text(Diag.device(this), 16, 0xFFE9E4DC, false));

        String crash = Diag.lastCrash(this);
        col.addView(text("ÚLTIMO ERRO", 13, 0xFF8E8A86, true));
        if (crash.isEmpty()) {
            col.addView(text("Nenhum crash registrado.", 16, 0xFF7FF0B0, false));
        } else {
            TextView c = text(crash.length() > 3500 ? crash.substring(0, 3500) + "\n…" : crash, 13, 0xFFFF6A5A, false);
            c.setTypeface(Typeface.MONOSPACE);
            col.addView(c);
        }
        String prev = Diag.prevBootLog(this);
        if (!prev.isEmpty()) {
            col.addView(text("ETAPAS DA ÚLTIMA ABERTURA", 13, 0xFF8E8A86, true));
            TextView p = text(prev, 13, 0xFFBDB8B1, false);
            p.setTypeface(Typeface.MONOSPACE);
            col.addView(p);
        }

        col.addView(text("TESTES, UM POR VEZ", 13, 0xFF8E8A86, true));
        col.addView(button("1 · Testar WebView simples", true, v -> {
            Diag.stage(this, "botão: teste WebView");
            startActivity(new Intent(this, WebTestActivity.class));
        }));
        col.addView(button("2 · Abrir painel sem voz, sem permissões e sem automações", false, v -> {
            Diag.stage(this, "botão: painel seguro");
            startActivity(new Intent(this, MainActivity.class).putExtra("safe", true));
        }));
        col.addView(button("3 · Abrir painel completo", false, v -> {
            Diag.stage(this, "botão: painel completo");
            startActivity(new Intent(this, MainActivity.class));
        }));
        status = text("", 16, 0xFFFFB340, false);
        col.addView(button("Salvar relatório em Download/JARVIS", false, v -> status.setText(Diag.saveReport(this))));
        col.addView(button("Limpar registros", false, v -> { Diag.clear(this); build(); }));
        col.addView(status);
        setContentView(sv);
    }
}
