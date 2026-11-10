package tv.dumbtv.home;

import android.app.Activity;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * An HDMI (or other) input, full screen. Fire OS keeps its own input switch for Amazon-signed
 * apps (com.amazon.tv.inputpreference.service, permission TUNE_INPUT), and Android's standard
 * "show this input" request goes to Live TV, which refuses it or opens its own screen. So this
 * screen shows the input itself with TvView, the public Android TV API: it asks the input's
 * service for a session and gives it this window to draw in. No permission needed.
 *
 * Back, Home or opening anything else closes it, so the home screen is what comes back.
 */
public final class InputScreen extends Activity {
    private static final String EXTRA_INPUT = "input", EXTRA_NAME = "name";
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
        from.startActivity(new Intent(from, InputScreen.class)
                .putExtra(EXTRA_INPUT, input.id).putExtra(EXTRA_NAME, name.toString()));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra(EXTRA_INPUT);
        name = getIntent().getStringExtra(EXTRA_NAME);
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
                showNote(name, 2500);
            }

            @Override
            public void onVideoUnavailable(String inputId, int reason) {
                root.setKeepScreenOn(false);
                boolean tuning = reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING;
                showNote(tuning ? "Switching to " + name + "…" : "No signal on " + name + ". Is it turned on?", 0);
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
        showNote("Switching to " + name + "…", 0);
        tv.tune(id, TvContract.buildChannelUriForPassthroughInput(id));
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
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) finish();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // left for Home or another app: close, so pressing Home never brings the input back
        if (tv != null) tv.reset();
        finish();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
