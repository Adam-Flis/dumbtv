package tv.dumbtv.home;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Fire TV's own settings screens this app may open. Amazon guards most of them with a
 * signature permission (Display & Sounds, Picture, Account, Preferences, Accessibility,
 * Profiles); the ones below are open to any app. Probed on Fire OS 6.7: see the notes in
 * launcher/README.md before adding one.
 */
final class FireSettings {
    /** One entry of the gear's dropdown. */
    static final class Section {
        final String label, hint, action;

        Section(String label, String hint, String action) {
            this.label = label;
            this.hint = hint;
            this.action = action;
        }
    }

    private static final Section[] SECTIONS = {
        new Section("Wi-Fi & network", "Connect, forget a network", Settings.ACTION_WIFI_SETTINGS),
        new Section("Device & Software", "Restart, sleep, storage, about", Settings.ACTION_DEVICE_INFO_SETTINGS),
        new Section("Apps", "Manage, clear data, uninstall", Settings.ACTION_APPLICATION_SETTINGS),
        new Section("Remotes & Bluetooth", "Remotes, headphones, speakers",
                "com.amazon.device.settings.action.CONTROLLERS"),
        // Amazon's settings search: a way into the locked sections
        new Section("Search all settings", "Picture, sound, account, Alexa…",
                "com.amazon.tv.device.settings.action.SEARCH_SETTINGS"),
    };

    private FireSettings() {}

    /** The sections this TV can open, in menu order. */
    static List<Section> sections(Context c) {
        List<Section> out = new ArrayList<>();
        for (Section s : SECTIONS) {
            if (opens(c, new Intent(s.action))) out.add(s);
        }
        return out;
    }

    /**
     * Amazon's full settings grid. Fire OS answers the generic settings action with a stub that
     * reopens Amazon's home; the TV settings entry point opens the real screen.
     */
    static Intent all(Context c) {
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_SETTINGS");
        return opens(c, tv) ? tv : new Intent(Settings.ACTION_SETTINGS);
    }

    /** Wi-Fi, or the full grid on a TV without that screen. */
    static Intent wifi(Context c) {
        Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
        return opens(c, i) ? i : all(c);
    }

    private static boolean opens(Context c, Intent i) {
        return i.resolveActivity(c.getPackageManager()) != null;
    }
}
