package tv.dumbtv.home;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An HDMI (or other) input, full screen. Fire OS keeps its own input switch for Amazon-signed
 * apps (com.amazon.tv.inputpreference.service, permission TUNE_INPUT), and Android's standard
 * "show this input" request goes to Live TV, which refuses it or opens its own screen. So this
 * screen shows the input itself with TvView, the public Android TV API: it asks the input's
 * service for a session and gives it this window to draw in. No permission needed.
 *
 * The antenna works the same way, on a channel: the channels Live TV found in its scan are read
 * from Android's TV listings (READ_TV_LISTINGS, allowed by install.sh or asked for here), and
 * Channel up / down or the up / down arrows change channel; OK lists the channels with what is on
 * now, and ☰ has captions and the audio language. The last channel and the captions setting are
 * remembered. Amazon's own guide (the remote's guide key) still works on top of this screen.
 * Amazon's Live TV app cannot be opened by other apps (TUNE_CHANNEL, SHOW_EPG are Amazon-only).
 *
 * Back, Home or opening anything else closes it, so the home screen is what comes back.
 */
public final class InputScreen extends Activity {
    private static final String EXTRA_INPUT = "input", EXTRA_NAME = "name", EXTRA_TUNER = "tuner";
    private static final String LISTINGS = "android.permission.READ_TV_LISTINGS";
    /** home.conf: the antenna channel watched last (a TV listings channel id). */
    private static final String LAST_CHANNEL = "antenna.channel", CAPTIONS = "antenna.captions";

    /** One antenna channel: its listings id, and "5.1  WEWS" to show. */
    private static final class Channel {
        final long id;
        final String number, title;

        Channel(long id, String number, String name) {
            this.id = id;
            this.number = number;
            this.title = name == null || name.isEmpty() ? number : number + "  " + name;
        }
    }

    private final List<Channel> channels = new ArrayList<>();
    private int at;
    private String inputId;
    private boolean tuner;
    /** The key hint shows with the first channel only. */
    private boolean hinted;
    private static final int MATCH = FrameLayout.LayoutParams.MATCH_PARENT, WRAP = FrameLayout.LayoutParams.WRAP_CONTENT;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable hideNote = this::hideNote;
    private FrameLayout root;
    private TvView tv;
    private TextView note;
    private String name;

    /** Shows {@code input} (a TV input id), titled {@code name}. */
    static void open(Activity from, Item input, CharSequence name) {
        // started plainly: NEW_TASK on our own task, which is already in front, would do nothing
        from.startActivity(new Intent(from, InputScreen.class).putExtra(EXTRA_INPUT, input.id)
                .putExtra(EXTRA_NAME, name.toString()).putExtra(EXTRA_TUNER, input.tuner));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra(EXTRA_INPUT);
        name = getIntent().getStringExtra(EXTRA_NAME);
        tuner = getIntent().getBooleanExtra(EXTRA_TUNER, false);
        inputId = id;
        if (id == null) {
            finish();
            return;
        }
        if (name == null) name = "this input";
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        tv = new TvView(this);
        root.addView(tv, new FrameLayout.LayoutParams(MATCH, MATCH));
        note = Ui.text(this, 22, Ui.TEXT, Ui.MEDIUM);
        note.setBackground(Ui.round(0xE616171B, Ui.dp(this, 28)));
        int pad = Ui.dp(this, 22);
        note.setPadding(pad, Ui.dp(this, 14), pad, Ui.dp(this, 14));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.START);
        lp.leftMargin = lp.topMargin = Ui.dp(this, 48);
        root.addView(note, lp);
        setContentView(root);

        tv.setCallback(new TvView.TvInputCallback() {
            @Override
            public void onVideoAvailable(String inputId) {
                // a picture: the TV stays on while it plays, like Amazon's own input screen
                root.setKeepScreenOn(true);
                if (tuner && !channels.isEmpty()) {
                    String now = onNow(channels.get(at));
                    showNote(showing() + (now == null ? "" : "   ·   " + now)
                            + (hinted ? "" : "\nOK: channels   ·   \u2630: captions and audio"), 4000);
                    hinted = true;
                } else {
                    showNote(showing(), 2500);
                }
            }

            @Override
            public void onVideoUnavailable(String inputId, int reason) {
                root.setKeepScreenOn(false);
                boolean tuning = reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING;
                if (tuner) {
                    showNote(tuning ? "Tuning " + showing() + "…"
                            : "No signal on " + showing() + ". Try another channel, or check the antenna.", 0);
                } else {
                    showNote(tuning ? "Switching to " + name + "…" : "No signal on " + name + ". Is it turned on?", 0);
                }
            }

            @Override
            public void onConnectionFailed(String inputId) {
                failed();
            }

            @Override
            public void onDisconnected(String inputId) {
                failed();
            }
        });
        if (!tuner) {
            showNote("Switching to " + name + "…", 0);
            tv.tune(id, TvContract.buildChannelUriForPassthroughInput(id));
            sound();
        } else if (checkSelfPermission(LISTINGS) == PackageManager.PERMISSION_GRANTED) {
            startAntenna();
        } else {
            showNote("Allow the home screen to see your channels.", 0);
            requestPermissions(new String[] {LISTINGS}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startAntenna();
        } else {
            showNote("Without that the home screen cannot list your channels. Amazon's home screen"
                    + " can: Options › More › Amazon home screen.", 0);
        }
    }

    /** What is on screen: the channel on the antenna, else the input's name. */
    private String showing() {
        return tuner && !channels.isEmpty() ? channels.get(at).title : name;
    }

    /** Reads the antenna's channels and tunes to the one watched last (or the first). */
    private void startAntenna() {
        // Amazon's Live TV leaves every channel's "browsable" flag at 0, so it is no filter here
        String[] columns = {TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NUMBER,
            TvContract.Channels.COLUMN_DISPLAY_NAME};
        try (Cursor c = getContentResolver().query(TvContract.buildChannelsUriForInput(inputId),
                columns, null, null, null)) {
            while (c != null && c.moveToNext()) {
                channels.add(new Channel(c.getLong(0), c.isNull(1) ? "" : c.getString(1), c.getString(2)));
            }
        } catch (RuntimeException e) {
            android.util.Log.w("dumbtv", "antenna channels: " + e);
        }
        if (channels.isEmpty()) {
            showNote("No channels yet. Fire TV finds them: Settings › Live TV › Channel Scan.", 0);
            return;
        }
        Collections.sort(channels, (a, b) -> Catalog.compareChannelNumbers(a.number, b.number));
        String last = Config.get(this).get(LAST_CHANNEL, "");
        for (int i = 0; i < channels.size(); i++) {
            if (String.valueOf(channels.get(i).id).equals(last)) at = i;
        }
        tuneChannel(at);
    }

    /** Tunes to channel number {@code i} of the list, wrapping at either end. */
    private void tuneChannel(int i) {
        int n = channels.size();
        at = ((i % n) + n) % n;
        Channel ch = channels.get(at);
        showNote("Tuning " + ch.title + "…", 0);
        tv.tune(inputId, TvContract.buildChannelUri(ch.id));
        sound();
        tv.setCaptionEnabled(captions());
        Config.get(this).set(LAST_CHANNEL, String.valueOf(ch.id));
    }

    private boolean captions() {
        return "on".equals(Config.get(this).get(CAPTIONS, "off"));
    }

    /** The program on {@code ch} now, from the TV listings; null when the listings have none. */
    private String onNow(Channel ch) {
        long now = System.currentTimeMillis();
        try (Cursor c = getContentResolver().query(TvContract.buildProgramsUriForChannel(ch.id, now, now + 1),
                new String[] {TvContract.Programs.COLUMN_TITLE}, null, null, null)) {
            return c != null && c.moveToFirst() ? c.getString(0) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** OK on the antenna: every channel with what is on now; picking one tunes to it. */
    private void channelList() {
        List<Channel> list = new ArrayList<>(channels);
        new Thread(() -> {
            List<String> rows = new ArrayList<>();
            for (Channel ch : list) {
                String now = onNow(ch);
                rows.add(now == null ? ch.title : ch.title + "   ·   " + now);
            }
            runOnUiThread(() -> {
                if (!isFinishing()) Popup.choose(this, "Channels", rows, at, this::tuneChannel);
            });
        }).start();
    }

    /** ☰ on the antenna: captions on or off, and the audio language when there is a choice. */
    private void options() {
        List<String> rows = new ArrayList<>();
        List<Runnable> picks = new ArrayList<>();
        rows.add("Channel list");
        picks.add(this::channelList);
        rows.add(captions() ? "Captions: On" : "Captions: Off");
        picks.add(() -> {
            Config.get(this).set(CAPTIONS, captions() ? null : "on");
            tv.setCaptionEnabled(captions());
            showNote(captions() ? "Captions on" : "Captions off", 2000);
        });
        List<TvTrackInfo> audio = tv.getTracks(TvTrackInfo.TYPE_AUDIO);
        if (audio != null && audio.size() > 1) {
            rows.add("Audio: " + trackName(audio, tv.getSelectedTrack(TvTrackInfo.TYPE_AUDIO)));
            picks.add(() -> {
                List<String> names = new ArrayList<>();
                for (TvTrackInfo t : audio) names.add(trackName(audio, t.getId()));
                Popup.choose(this, "Audio", names, i -> tv.selectTrack(TvTrackInfo.TYPE_AUDIO, audio.get(i).getId()));
            });
        }
        Popup.choose(this, showing(), rows, i -> picks.get(i).run());
    }

    /** "English", or "Track 2" when a track names no language. */
    private static String trackName(List<TvTrackInfo> tracks, String id) {
        for (int i = 0; i < tracks.size(); i++) {
            TvTrackInfo t = tracks.get(i);
            if (!t.getId().equals(id)) continue;
            String lang = t.getLanguage();
            return lang == null || lang.isEmpty() ? "Track " + (i + 1) : new java.util.Locale(lang).getDisplayLanguage();
        }
        return "Automatic";
    }

    /** The input's service would not show it here: say where it still works. */
    private void failed() {
        root.setKeepScreenOn(false);
        showNote("This TV would not show " + name + " here. Amazon's home screen can:"
                + " Options › More › Amazon home screen.", 0);
    }

    /** Shows {@code text} at the top left; {@code ms} > 0 hides it again after that long. */
    private void showNote(String text, long ms) {
        ui.removeCallbacks(hideNote);
        note.setText(text);
        note.setAlpha(1f);
        note.setVisibility(View.VISIBLE);
        if (ms > 0) ui.postDelayed(hideNote, ms);
    }

    private void hideNote() {
        Tween.run(400, t -> note.setAlpha(1 - t), () -> note.setVisibility(View.GONE));
    }

    /**
     * Back always returns home. TvView would otherwise hand it to the input first, and a device
     * that takes it over HDMI-CEC would keep the viewer here.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) finish();
            return true;
        }
        // the antenna: Channel up / down, or the up / down arrows on remotes without channel keys
        int step = code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_DPAD_UP ? 1
                : code == KeyEvent.KEYCODE_CHANNEL_DOWN || code == KeyEvent.KEYCODE_DPAD_DOWN ? -1 : 0;
        if (tuner && step != 0 && !channels.isEmpty()) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) tuneChannel(at + step);
            return true;
        }
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        if (tuner && !channels.isEmpty() && (ok || code == KeyEvent.KEYCODE_MENU)) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (ok) channelList();
                else options();
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /**
     * The sound. The TV's input service plays at volume 0 until the app showing it says
     * otherwise, the way Live TV does: take the audio focus and set the input's volume.
     */
    private void sound() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) am.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        tv.setStreamVolume(1f);
    }

    /** Another app took the sound (Alexa, say): quiet while it talks, back when it is done. */
    private final AudioManager.OnAudioFocusChangeListener focus = change -> {
        if (tv == null) return;
        if (change == AudioManager.AUDIOFOCUS_LOSS) tv.setStreamVolume(0f);
        else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            tv.setStreamVolume(change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ? 0f : 0.3f);
        } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
            tv.setStreamVolume(1f);
        }
    };

    @Override
    protected void onStop() {
        super.onStop();
        // left for Home or another app: close, so pressing Home never brings the input back
        if (tv != null) tv.reset();
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) am.abandonAudioFocus(focus);
        finish();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
