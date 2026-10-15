package app.jarvis.pantera;

import android.app.Application;

/** Primeira coisa que roda: registra o capturador de crash antes de qualquer tela. */
public class JarvisApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Diag.newSession(this);
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Diag.saveCrash(this, t, e);
            if (previous != null) previous.uncaughtException(t, e);
        });
        Diag.stage(this, "Application.onCreate ok");
    }
}
