package ps.reso.instaeclipse.utils.dialog;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

/**
 * Material 3 Expressive building blocks for the in-Instagram settings sheet.
 *
 * <p>The sheet runs inside Instagram's process, where the Material Components library isn't
 * available, so the pieces are drawn by hand: a dark tonal palette taken from the system's
 * dynamic (wallpaper) colors on Android 12+, a segmented list group (rows separated by 2dp gaps
 * with large outer / small inner corners) and an animated M3 switch.
 */
final class ExpressiveKit {

    private ExpressiveKit() {}

    // ==== Palette (M3 dark scheme roles) ====
    static int surface, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest, pressed;
    static int onSurface, onSurfaceVariant, outline, outlineVariant;
    static int primary, onPrimary, primaryContainer, onPrimaryContainer;
    static int secondaryContainer, onSecondaryContainer, tertiary, error;

    static {
        loadFallback();
    }

    /** Indigo scheme matching the companion app, used before Android 12 or if lookup fails. */
    private static void loadFallback() {
        surface = 0xFF131318;
        surfaceContainer = 0xFF1F1F25;
        surfaceContainerHigh = 0xFF29292F;
        surfaceContainerHighest = 0xFF34343A;
        pressed = 0xFF3A3A44;
        onSurface = 0xFFE4E1E9;
        onSurfaceVariant = 0xFFC7C5D0;
        outline = 0xFF918F9A;
        outlineVariant = 0xFF46464F;
        primary = 0xFFC2C1FF;
        onPrimary = 0xFF2A2A60;
        primaryContainer = 0xFF414178;
        onPrimaryContainer = 0xFFE2DFFF;
        secondaryContainer = 0xFF46465C;
        onSecondaryContainer = 0xFFE3E0F9;
        tertiary = 0xFFEAB9D2;
        error = 0xFFFFB4AB;
    }

    /** Re-reads the wallpaper palette; cheap, called whenever the sheet opens. */
    static void refresh() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            Resources r = Resources.getSystem();
            int n1_900 = r.getColor(android.R.color.system_neutral1_900, null);
            int n1_800 = r.getColor(android.R.color.system_neutral1_800, null);
            int n1_700 = r.getColor(android.R.color.system_neutral1_700, null);
            surface = blend(n1_900, Color.BLACK, 0.35f);
            surfaceContainer = blend(n1_900, n1_800, 0.45f);
            surfaceContainerHigh = n1_800;
            surfaceContainerHighest = blend(n1_800, n1_700, 0.45f);
            pressed = n1_700;
            onSurface = r.getColor(android.R.color.system_neutral1_100, null);
            onSurfaceVariant = r.getColor(android.R.color.system_neutral2_200, null);
            outline = r.getColor(android.R.color.system_neutral2_400, null);
            outlineVariant = r.getColor(android.R.color.system_neutral2_700, null);
            primary = r.getColor(android.R.color.system_accent1_200, null);
            onPrimary = r.getColor(android.R.color.system_accent1_800, null);
            primaryContainer = r.getColor(android.R.color.system_accent1_700, null);
            onPrimaryContainer = r.getColor(android.R.color.system_accent1_100, null);
            secondaryContainer = r.getColor(android.R.color.system_accent2_700, null);
            onSecondaryContainer = r.getColor(android.R.color.system_accent2_100, null);
            tertiary = r.getColor(android.R.color.system_accent3_200, null);
        } catch (Throwable t) {
            loadFallback();
        }
    }

    static int blend(int a, int b, float t) {
        float s = 1f - t;
        return Color.rgb(
                Math.round(Color.red(a) * s + Color.red(b) * t),
                Math.round(Color.green(a) * s + Color.green(b) * t),
                Math.round(Color.blue(a) * s + Color.blue(b) * t));
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    /** Emphasized-decelerate easing from the M3 motion spec. */
    static final PathInterpolator EMPHASIZED = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);

    static GradientDrawable shape(int color, float[] radii) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadii(radii);
        return d;
    }

    static GradientDrawable pill(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(10_000f);
        return d;
    }

    /** Ripple over a filled rounded shape. */
    static Drawable ripple(int fill, float[] radii) {
        GradientDrawable content = shape(fill, radii);
        GradientDrawable mask = shape(Color.WHITE, radii);
        return new RippleDrawable(ColorStateList.valueOf(withAlpha(onSurface, 0x26)), content, mask);
    }

    static float[] radii(float tl, float tr, float br, float bl) {
        return new float[]{tl, tl, tr, tr, br, br, bl, bl};
    }

    // ==== Segmented group ====

    /** Marks a view that must not become its own segment (e.g. old in-card hairlines). */
    static final String TAG_SKIP = "ie_skip_segment";

    /**
     * Vertical list whose rows render as separate rounded segments: 2dp apart, 24dp outer corners
     * on the first/last row and 6dp corners elsewhere (the Android 16 settings list). Row shapes
     * follow their position, so rows can be added, removed or hidden freely.
     */
    static class SegmentedGroup extends LinearLayout {
        private final float outer;
        private final float inner;
        private final int gap;
        /** Last shape applied per row, so backgrounds are only rebuilt when a row's position changes. */
        private final java.util.WeakHashMap<View, String> applied = new java.util.WeakHashMap<>();

        SegmentedGroup(Context c) {
            super(c);
            setOrientation(VERTICAL);
            outer = dp(c, 24);
            inner = dp(c, 6);
            gap = Math.round(dp(c, 2));
        }

        @Override
        public void onViewAdded(View child) {
            super.onViewAdded(child);
            if (TAG_SKIP.equals(child.getTag())) child.setVisibility(GONE);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            restyle();
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        private void restyle() {
            int count = 0;
            for (int i = 0; i < getChildCount(); i++) if (getChildAt(i).getVisibility() != GONE) count++;
            int pos = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                boolean first = pos == 0, last = pos == count - 1;
                String key = "seg:" + first + last;
                if (!key.equals(applied.get(child))) {
                    float top = first ? outer : inner, bottom = last ? outer : inner;
                    int pl = child.getPaddingLeft(), pt = child.getPaddingTop();
                    int pr = child.getPaddingRight(), pb = child.getPaddingBottom();
                    child.setBackground(ripple(surfaceContainer, radii(top, top, bottom, bottom)));
                    child.setPadding(pl, pt, pr, pb);
                    applied.put(child, key);
                }
                ViewGroup.LayoutParams lp = child.getLayoutParams();
                if (lp instanceof MarginLayoutParams m) {
                    int want = first ? 0 : gap;
                    if (m.topMargin != want) m.topMargin = want;
                }
                pos++;
            }
        }
    }

    // ==== M3 switch ====

    /** Animated Material 3 switch: 52×32dp track, thumb grows from 16dp to 24dp with a check mark. */
    static class M3Switch extends View implements Checkable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF track = new RectF();
        private final Path check = new Path();
        private boolean checked;
        private float progress; // 0 = off, 1 = on
        private ValueAnimator anim;
        private CompoundButton.OnCheckedChangeListener listener;

        M3Switch(Context c) {
            super(c);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(Math.round(dp(getContext(), 52)), Math.round(dp(getContext(), 32)));
        }

        @Override
        public boolean isChecked() {
            return checked;
        }

        @Override
        public void setChecked(boolean value) {
            if (checked == value) return;
            checked = value;
            if (anim != null) anim.cancel();
            if (isAttachedToWindow() && isShown()) {
                anim = ValueAnimator.ofFloat(progress, value ? 1f : 0f);
                anim.setDuration(220);
                anim.setInterpolator(EMPHASIZED);
                anim.addUpdateListener(a -> {
                    progress = (float) a.getAnimatedValue();
                    invalidate();
                });
                anim.start();
            } else {
                progress = value ? 1f : 0f;
                invalidate();
            }
            if (listener != null) listener.onCheckedChanged(null, value);
        }

        @Override
        public void toggle() {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            setChecked(!checked);
        }

        void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener l) {
            listener = l;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float d = getResources().getDisplayMetrics().density;
            float w = getWidth(), h = getHeight();
            float stroke = 2 * d;
            track.set(stroke / 2, stroke / 2, w - stroke / 2, h - stroke / 2);
            float r = track.height() / 2;

            int trackOff = surfaceContainerHighest;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(blendArgb(trackOff, primary, progress));
            canvas.drawRoundRect(track, r, r, paint);
            if (progress < 1f) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(stroke);
                paint.setColor(withAlpha(outline, Math.round(255 * (1f - progress))));
                canvas.drawRoundRect(track, r, r, paint);
            }

            float thumbR = (8 + 4 * progress) * d;
            if (isPressed()) thumbR = 14 * d;
            float left = h / 2, right = w - h / 2;
            float cx = left + (right - left) * progress;
            float cy = h / 2;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(blendArgb(outline, onPrimary, progress));
            canvas.drawCircle(cx, cy, thumbR, paint);

            if (progress > 0.5f) {
                float a = (progress - 0.5f) * 2f;
                float s = 5.5f * d;
                check.reset();
                check.moveTo(cx - s, cy);
                check.lineTo(cx - s * 0.3f, cy + s * 0.7f);
                check.lineTo(cx + s, cy - s * 0.6f);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * d);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setColor(withAlpha(onPrimaryContainer, Math.round(255 * a)));
                canvas.drawPath(check, paint);
            }
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }
    }

    /** M3 radio indicator: 20dp ring, filled 10dp dot when selected. */
    static class RadioDot extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean selected;

        RadioDot(Context c) {
            super(c);
        }

        void setSelectedState(boolean value) {
            selected = value;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            int size = Math.round(dp(getContext(), 24));
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float d = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2 * d);
            paint.setColor(selected ? primary : onSurfaceVariant);
            canvas.drawCircle(cx, cy, 9 * d, paint);
            if (selected) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, 5 * d, paint);
            }
        }
    }

    static int blendArgb(int a, int b, float t) {
        float s = 1f - t;
        return Color.argb(
                Math.round(Color.alpha(a) * s + Color.alpha(b) * t),
                Math.round(Color.red(a) * s + Color.red(b) * t),
                Math.round(Color.green(a) * s + Color.green(b) * t),
                Math.round(Color.blue(a) * s + Color.blue(b) * t));
    }
}
