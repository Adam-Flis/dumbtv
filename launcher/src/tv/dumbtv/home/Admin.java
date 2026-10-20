package tv.dumbtv.home;

import android.app.admin.DeviceAdminReceiver;

/**
 * Lets the sleep timer turn the TV off (DevicePolicyManager.lockNow). It asks for nothing else.
 * Fire OS 6 answers the standard "Activate device admin" screen with a dummy, so the owner turns
 * it on once with `adb shell dpm set-active-admin tv.dumbtv.home/.Admin`. Options › More ›
 * "Remove sleep timer permission" (or install.sh --undo) takes it away again.
 */
public final class Admin extends DeviceAdminReceiver {}
