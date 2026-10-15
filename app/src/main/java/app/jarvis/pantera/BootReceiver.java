package app.jarvis.pantera;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Abre o JARVIS assim que o Ottocast liga com o carro. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        i.putExtra("fromBoot", true);
        try { ctx.startActivity(i); } catch (Exception ignored) { }
    }
}
