package tv.dumbtv.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Puts the home keeper back after a reboot or an update. A sleep timer does not survive a
 * reboot, but survives an update (its alarm is set again). {@code RELEASE_ADMIN} (sent by install.sh --undo) drops the sleep timer permission,
 * which Android requires before the app can be uninstalled.
 */
public final class BootReceiver extends BroadcastReceiver {
    static final String RELEASE_ADMIN = "tv.dumbtv.home.RELEASE_ADMIN";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (RELEASE_ADMIN.equals(intent.getAction())) {
            Sleep.release(context);
            return;
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) Config.get(context).set(Sleep.AT, null);
        else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) Sleep.rearm(context);
        Keeper.start(context);
    }
}
