package app.jarvis.pantera;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.WebView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Diagnóstico de inicialização: etapas, crash e dados do aparelho, gravados em arquivo. */
public final class Diag {
    public static final String TAG = "JARVIS";
    private static final String BOOT = "boot.log";
    private static final String CRASH = "crash.txt";

    private Diag() { }

    private static String now() { return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date()); }

    /** Marca uma etapa. Vai para o Logcat e para um arquivo que sobrevive ao crash. */
    public static void stage(Context c, String name) {
        String line = now() + "  " + name;
        Log.i(TAG, "BOOT " + name);
        try (FileOutputStream o = new FileOutputStream(new File(c.getFilesDir(), BOOT), true)) {
            o.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    public static void newSession(Context c) {
        File f = new File(c.getFilesDir(), BOOT);
        // guarda a sessão anterior para comparar
        File prev = new File(c.getFilesDir(), "boot-anterior.log");
        if (f.exists()) { prev.delete(); f.renameTo(prev); }
        stage(c, "processo iniciado · " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
    }

    public static void saveCrash(Context c, Thread t, Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String txt = "FATAL EXCEPTION · thread " + (t == null ? "?" : t.getName()) + " · " + now() + "\n" + sw;
        Log.e(TAG, txt);
        try (FileOutputStream o = new FileOutputStream(new File(c.getFilesDir(), CRASH), false)) {
            o.write(txt.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
        stage(c, "CRASH: " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    public static String read(Context c, String name) {
        try {
            File f = new File(c.getFilesDir(), name);
            if (!f.exists()) return "";
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }

    public static String lastCrash(Context c) { return read(c, CRASH); }
    public static String bootLog(Context c) { return read(c, BOOT); }
    public static String prevBootLog(Context c) { return read(c, "boot-anterior.log"); }

    public static void clear(Context c) {
        new File(c.getFilesDir(), CRASH).delete();
        new File(c.getFilesDir(), "boot-anterior.log").delete();
    }

    public static String device(Context c) {
        StringBuilder b = new StringBuilder();
        b.append("Aparelho: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" (").append(Build.DEVICE).append(")\n");
        b.append("Android: ").append(Build.VERSION.RELEASE).append(" · SDK ").append(Build.VERSION.SDK_INT).append('\n');
        b.append("ABIs: ").append(String.join(", ", Build.SUPPORTED_ABIS)).append('\n');
        try {
            PackageInfo me = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            b.append("JARVIS: ").append(me.versionName).append(" (").append(me.versionCode).append(")\n");
        } catch (Exception ignored) { }
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                PackageInfo wv = WebView.getCurrentWebViewPackage();
                b.append("WebView: ").append(wv == null ? "NÃO ENCONTRADO" : wv.packageName + " " + wv.versionName).append('\n');
            }
        } catch (Throwable t) { b.append("WebView: erro ao consultar (").append(t.getClass().getSimpleName()).append(")\n"); }
        try {
            boolean tts = c.getPackageManager().queryIntentServices(new android.content.Intent("android.intent.action.TTS_SERVICE"), 0).size() > 0;
            boolean stt = android.speech.SpeechRecognizer.isRecognitionAvailable(c);
            b.append("Voz (TTS): ").append(tts ? "sim" : "não").append(" · Reconhecimento: ").append(stt ? "sim" : "não").append('\n');
        } catch (Throwable ignored) { }
        return b.toString();
    }

    public static String report(Context c) {
        return "RELATÓRIO JARVIS · " + new Date() + "\n\n" + device(c)
                + "\n--- ÚLTIMO CRASH ---\n" + (lastCrash(c).isEmpty() ? "nenhum" : lastCrash(c))
                + "\n--- ETAPAS DA SESSÃO ANTERIOR ---\n" + prevBootLog(c)
                + "\n--- ETAPAS DESTA SESSÃO ---\n" + bootLog(c);
    }

    /** Salva o relatório em Download/JARVIS para o usuário mandar. */
    public static String saveReport(Context c) {
        String txt = report(c);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, "relatorio-jarvis-" + System.currentTimeMillis() + ".txt");
                v.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                v.put(MediaStore.Downloads.RELATIVE_PATH, "Download/JARVIS");
                Uri u = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u != null) {
                    try (java.io.OutputStream o = c.getContentResolver().openOutputStream(u)) {
                        if (o != null) { o.write(txt.getBytes(StandardCharsets.UTF_8)); return "Salvo em Download/JARVIS"; }
                    }
                }
            }
        } catch (Exception e) { return "Não salvou: " + e.getMessage(); }
        return "Este Android não permite salvar direto; use a foto da tela.";
    }
}
