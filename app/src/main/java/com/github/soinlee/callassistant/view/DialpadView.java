package com.github.soinlee.callassistant.view;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.soinlee.callassistant.R;

/**
 * A dial pad with a number-display line, the 12 DTMF keys (0-9, *, #), a
 * backspace key and a green call key. It follows the system light/dark theme
 * through the {@code dialpad_*} semantic colors (day values in
 * {@code res/values/colors.xml}, night overrides in
 * {@code res/values-night/colors.xml}).
 *
 * <p>Layout (rows): display / 1 2 3 / 4 5 6 / 7 8 9 / * 0 # / backspace call.
 *
 * <p>Enhancements over the plain digits-only pad:
 * <ul>
 *   <li>Keys 2-9 carry a small letter sub-label (ABC / DEF / …), key 0 carries
 *       a "+", matching the Google Phone dial pad.</li>
 *   <li>The number line is auto-spaced in the common {@code 3-4-4} layout
 *       (e.g. {@code 138 1234 5678}) for pure digit input; the backing
 *       {@link #getInput()} still returns the raw, space-free digits.</li>
 *   <li>The bottom row uses real vector icons: a backspace icon and a green
 *       call (telephone) icon instead of the previous text/⌫ characters.</li>
 * </ul>
 */
public class DialpadView extends LinearLayout {

    public interface OnDialListener {
        /** Invoked when the user taps the green call key with {@code number}. */
        void onDial(String number);
    }

    public interface OnInputChangedListener {
        /** Invoked whenever the typed number changes (including deletions). */
        void onInputChanged(String number);
    }

    public interface OnToggleListener {
        /** Invoked when the user taps the dial-pad collapse key (bottom row). */
        void onToggle();
    }

    // {digit, letters}. 2-9 carry letters, 0 carries "+"; 1, * and # have none.
    private static final String[][] KEYS = {
            {"1", ""},     {"2", "ABC"}, {"3", "DEF"},
            {"4", "GHI"},  {"5", "JKL"}, {"6", "MNO"},
            {"7", "PQRS"}, {"8", "TUV"}, {"9", "WXYZ"},
            {"*", ""},     {"0", "+"},  {"#", ""}
    };

    private static final int COL_COUNT = 3;

    private final TextView mNumberView;
    private final StringBuilder mNumber = new StringBuilder();
    private OnDialListener mDialListener;
    private OnInputChangedListener mInputListener;
    private OnToggleListener mToggleListener;

    public DialpadView(Context context) {
        this(context, null);
    }

    public DialpadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setBackgroundColor(getColor(R.color.dialpad_bg));

        mNumberView = new TextView(context);
        mNumberView.setGravity(Gravity.CENTER_VERTICAL);
        mNumberView.setPadding(dp(16), 0, dp(16), 0);
        mNumberView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        mNumberView.setTextColor(getColor(R.color.dialpad_digit_text));
        mNumberView.setHint(R.string.dialpad_hint);
        mNumberView.setHintTextColor(getColor(R.color.dialpad_divider));
        mNumberView.setSingleLine(true);
        mNumberView.setEllipsize(android.text.TextUtils.TruncateAt.START);
        addView(mNumberView, new LayoutParams(
                LayoutParams.MATCH_PARENT, dp(64)));

        View divider = new View(context);
        divider.setBackgroundColor(getColor(R.color.dialpad_divider));
        addView(divider, new LayoutParams(
                LayoutParams.MATCH_PARENT, 1));

        for (int s = 0; s < KEYS.length; s += COL_COUNT) {
            LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setOrientation(HORIZONTAL);
            addView(rowLayout, new LayoutParams(
                    LayoutParams.MATCH_PARENT, dp(72)));
            for (int c = 0; c < COL_COUNT; c++) {
                final String digit = KEYS[s + c][0];
                final String letters = KEYS[s + c][1];
                LinearLayout keyView = makeKey(digit, letters);
                keyView.setOnClickListener(v -> append(digit));
                // Long-press 0 inserts a leading/plus '+' (Google Phone style).
                if ("0".equals(digit)) {
                    keyView.setOnLongClickListener(v -> {
                        append("+");
                        return true;
                    });
                }
                rowLayout.addView(keyView, new LayoutParams(
                        0, LayoutParams.MATCH_PARENT, 1f));
            }
        }

        // Bottom row (3 equal cells, 5 columns x 5 rows visually):
        //   [ backspace ] [ call ] [ collapse (toggle) ]
        LinearLayout bottomRow = new LinearLayout(context);
        bottomRow.setOrientation(HORIZONTAL);
        addView(bottomRow, new LayoutParams(
                LayoutParams.MATCH_PARENT, dp(88)));

        ImageButton backspace = makeIconKey(R.drawable.ic_backspace_24dp,
                R.color.dialpad_text);
        backspace.setOnClickListener(v -> delete());
        bottomRow.addView(backspace, new LayoutParams(
                0, LayoutParams.MATCH_PARENT, 1f));

        ImageButton call = makeIconKey(R.drawable.ic_call_white_24dp,
                R.color.white);
        // Rounded-rectangle green button, inset so it reads as a button (not a
        // full-cell ellipse) in the bottom action row.
        call.setBackground(getDrawable(R.drawable.dialpad_call_bg));
        call.setOnClickListener(v -> {
            if (mDialListener != null) {
                mDialListener.onDial(getInput());
            }
        });
        LayoutParams callLp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
        callLp.setMargins(dp(20), dp(10), dp(20), dp(10));
        bottomRow.addView(call, callLp);

        ImageButton toggle = makeIconKey(R.drawable.ic_keyboard_24dp,
                R.color.dialpad_text);
        toggle.setOnClickListener(v -> {
            if (mToggleListener != null) {
                mToggleListener.onToggle();
            }
        });
        bottomRow.addView(toggle, new LayoutParams(
                0, LayoutParams.MATCH_PARENT, 1f));
    }

    /**
     * Builds a digit key matching the native Google Phone dial pad: a larger
     * bold digit above a small secondary-colour letter sub-label, on a
     * transparent key that shows a circular ripple when pressed. Key {@code 1}
     * shows a voicemail icon instead of letters (native look).
     */
    private LinearLayout makeKey(String digit, String letters) {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackground(getDrawable(R.drawable.dialpad_key_ripple));

        TextView digitView = new TextView(getContext());
        digitView.setGravity(Gravity.CENTER);
        digitView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        digitView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        digitView.setTextColor(getColor(R.color.dialpad_text));
        digitView.setText(digit);
        root.addView(digitView, new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        if ("1".equals(digit)) {
            // Native dial pad shows a voicemail icon under the "1" key.
            ImageView vm = new ImageView(getContext());
            vm.setImageResource(R.drawable.ic_voicemail_24dp);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    dp(18), dp(18));
            lp.topMargin = dp(1);
            root.addView(vm, lp);
        } else if (letters != null && !letters.isEmpty()) {
            TextView lettersView = new TextView(getContext());
            lettersView.setGravity(Gravity.CENTER);
            lettersView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            lettersView.setTextColor(getColor(R.color.dialpad_letter_text));
            lettersView.setText(letters);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(2);
            root.addView(lettersView, lp);
        }
        return root;
    }

    /** Builds an icon key (backspace / toggle) centered on the given icon. */
    private ImageButton makeIconKey(int iconRes, int tintRes) {
        ImageButton b = new ImageButton(getContext());
        b.setBackground(getDrawable(R.drawable.dialpad_key_ripple));
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setImageResource(iconRes);
        b.setColorFilter(getColor(tintRes));
        return b;
    }

    /** Appends a single digit/symbol key to the current number. */
    public void append(String key) {
        if (key == null || key.length() != 1) {
            return;
        }
        mNumber.append(key);
        updateDisplay();
    }

    /** Deletes the last character, if any. */
    public void delete() {
        if (mNumber.length() > 0) {
            mNumber.deleteCharAt(mNumber.length() - 1);
            updateDisplay();
        }
    }

    /** Clears the whole number. */
    public void clear() {
        mNumber.setLength(0);
        updateDisplay();
    }

    /** Returns the raw digits (no spaces), for dialing / filtering. */
    public String getInput() {
        return mNumber.toString();
    }

    public void setOnDialListener(OnDialListener listener) {
        mDialListener = listener;
    }

    public void setOnInputChangedListener(OnInputChangedListener listener) {
        mInputListener = listener;
    }

    public void setOnToggleListener(OnToggleListener listener) {
        mToggleListener = listener;
    }

    private void updateDisplay() {
        mNumberView.setText(formatNumber(mNumber.toString()));
        if (mInputListener != null) {
            mInputListener.onInputChanged(mNumber.toString());
        }
    }

    /**
     * Auto-spaces a pure-digit number into the common {@code 3-4-4} layout
     * (e.g. {@code 138 1234 5678}). Non-digit input (e.g. {@code *} / {@code #}
     * codes) is returned untouched so the grouping does not get confusing.
     */
    private String formatNumber(String raw) {
        if (raw.isEmpty() || !isDigits(raw)) {
            return raw;
        }
        int len = raw.length();
        if (len <= 3) {
            return raw;
        }
        String head = raw.substring(0, 3);
        String tail = raw.substring(3);
        if (tail.length() <= 4) {
            return head + " " + tail;
        }
        return head + " " + tail.substring(0, 4) + " " + tail.substring(4);
    }

    private boolean isDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private int getColor(int resId) {
        return androidx.core.content.ContextCompat.getColor(getContext(), resId);
    }

    private android.graphics.drawable.Drawable getDrawable(int resId) {
        return androidx.core.content.ContextCompat.getDrawable(getContext(), resId);
    }
}
