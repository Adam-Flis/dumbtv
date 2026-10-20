package tv.dumbtv.home;

import android.app.admin.DeviceAdminReceiver;

/**
 * Lets the sleep timer turn the TV off (DevicePolicyManager.lockNow). It asks for nothing else.
 * Fire OS shows its own "Activate" screen the first time a timer is set; Options › More ›
 * "Remove sleep timer permission" (or install.sh --undo) takes it away again.
 */
public final class Admin extends DeviceAdminReceiver {}
