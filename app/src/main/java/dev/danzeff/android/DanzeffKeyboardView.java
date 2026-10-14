package dev.danzeff.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.util.SparseIntArray;

/**
 * Original PSP-inspired 3x3/4-button visual input surface.
 * - TWO-STEP: tap/hold a group on the left, then tap a face button on the right.
 * - DIRECT: tap top / left / bottom / right inside any cell to enter immediately.
 * Does not collect or persist typed text.
 */
public final class DanzeffKeyboardView extends View {
    public interface Listener {
        void onChooseGroup(int row, int col);
        void onChooseSlot(int slot);
        void onBackspace();
        void onSpace();
        void onEnter();
        void onShift();
        void onNumbers();
        void onSwitchKeyboard();
    }

    private static final int BG = 0xFF0D1721;
    private static final int PANEL = 0xFF172634;
    private static final int CELL = 0xFF203140;
    private static final int SELECTED = 0xFF31594E;
    private static final int ACCENT = 0xFF55D6C2;
    private static final int WARM = 0xFFF5B943;
    private static final int FG = 0xFFF1F6F9;
    private static final int MUTED = 0xFF9EB0BD;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Listener listener;
    private final SharedPreferences preferences;
    private final SparseIntArray pointerStarts = new SparseIntArray();
    private final RectF grid = new RectF();
    private float controlsLeft;
    private float controlsRight;
    private float bodyTop;
    private float bodyBottom;
    private float headerHeight;
    private float footerHeight;
    private int layer = KeyboardLayout.LOWER;
    private int selectedRow = 1;
    private int selectedCol = 1;
    private boolean direct;

    // Pointer zones, distinct from keyboard character slots.
    private static final int ZONE_NONE = 0;
    private static final int ZONE_GRID = 1;
    private static final int ZONE_FACES = 2;
    private static final int ZONE_HEADER = 3;
    private static final int ZONE_FOOTER = 4;
    private static final int[] FACES = {KeyboardLayout.TOP, KeyboardLayout.LEFT,
        KeyboardLayout.BOTTOM, KeyboardLayout.RIGHT};
    private static final String[] FOOTER = {"?123", "⇧", "⌫", "SPACE", "↵", "🌐"};

    public DanzeffKeyboardView(Context context, Listener callback) {
        super(context);
        listener = callback;
        preferences = context.getSharedPreferences("danzeff_preferences", Context.MODE_PRIVATE);
        direct = preferences.getBoolean("direct_touch", false);
        setClickable(true);
        setBackgroundColor(BG);
    }

    public void updateState(int newLayer, int row, int col) {
        layer = newLayer;
        selectedRow = row;
        selectedCol = col;
        invalidate();
    }

    private float d(float dp) { return dp * getResources().getDisplayMetrics().density; }
    private float font(float sp) { return sp * getResources().getDisplayMetrics().scaledDensity; }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int orientation = getResources().getConfiguration().orientation;
        int desiredHeight = Math.round(d(orientation == Configuration.ORIENTATION_LANDSCAPE ? 242 : 296));
        int desiredWidth = Math.round(d(360));
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    private void geometry(float width, float height) {
        headerHeight = d(39);
        footerHeight = d(52);
        bodyTop = headerHeight + d(5);
        bodyBottom = height - footerHeight - d(6);
        float controlWidth = Math.min(d(150), Math.max(d(94), width * 0.27f));
        grid.set(d(8), bodyTop, Math.max(d(75), width - controlWidth - d(17)), bodyBottom);
        controlsLeft = grid.right + d(9);
        controlsRight = width - d(8);
    }

    private void round(Canvas canvas, int color, float left, float top, float right,
                       float bottom, float radius) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
    }

    private void line(Canvas canvas, int color, float x1, float y1, float x2, float y2, float width) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(width);
        canvas.drawLine(x1, y1, x2, y2, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void label(Canvas canvas, String value, float x, float y, float sp, int color, boolean bold) {
        paint.setColor(color);
        paint.setTextSize(font(sp));
        paint.setTypeface(bold ? android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                : android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(value, x, y - (fm.ascent + fm.descent) / 2f, paint);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        geometry(w, h);

        label(canvas, "DANZEFF", d(61), headerHeight / 2f, 13, ACCENT, true);
        round(canvas, PANEL, w * 0.43f, d(6), w - d(8), headerHeight - d(4), d(8));
        label(canvas, direct ? "DIRECT TAP   ⇄" : "TWO-STEP   ⇄", (w * 0.43f + w - d(8)) / 2f,
                headerHeight / 2f, 11, FG, true);

        float cellW = grid.width() / 3f;
        float cellH = grid.height() / 3f;
        float pad = d(2.1f);
        float cellRadius = d(6);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                float x = grid.left + c * cellW;
                float y = grid.top + r * cellH;
                boolean active = r == selectedRow && c == selectedCol;
                round(canvas, active ? SELECTED : CELL,
                        x + pad, y + pad, x + cellW - pad, y + cellH - pad, cellRadius);
                if (active) {
                    paint.setColor(ACCENT);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(d(1.5f));
                    canvas.drawRoundRect(x + pad, y + pad, x + cellW - pad,
                            y + cellH - pad, cellRadius, cellRadius, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                float cx = x + cellW / 2f;
                float cy = y + cellH / 2f;
                float horiz = cellW * 0.30f;
                float vert = cellH * 0.28f;
                float typeSize = cellW < d(63) || cellH < d(55) ? 10.5f : 13.5f;
                int keyColor = active ? FG : MUTED;
                label(canvas, KeyboardLayout.display(KeyboardLayout.at(layer, r, c, 0)),
                        cx, cy - vert, typeSize, keyColor, true);
                label(canvas, KeyboardLayout.display(KeyboardLayout.at(layer, r, c, 1)),
                        cx - horiz, cy, typeSize, keyColor, true);
                label(canvas, KeyboardLayout.display(KeyboardLayout.at(layer, r, c, 2)),
                        cx, cy + vert, typeSize, keyColor, true);
                label(canvas, KeyboardLayout.display(KeyboardLayout.at(layer, r, c, 3)),
                        cx + horiz, cy, typeSize, keyColor, true);
            }
        }

        float faceMiddle = (controlsLeft + controlsRight) / 2f;
        float faceW = controlsRight - controlsLeft;
        float faceMiddleY = (bodyTop + bodyBottom) / 2f;
        float faceH = bodyBottom - bodyTop;
        float[] x = {faceMiddle, faceMiddle - faceW * 0.26f,
            faceMiddle, faceMiddle + faceW * 0.26f};
        float[] y = {faceMiddleY - faceH * 0.29f, faceMiddleY,
            faceMiddleY + faceH * 0.29f, faceMiddleY};
        String[] glyph = {"△", "□", "×", "○"};
        float radius = Math.min(d(27), Math.min(faceW * 0.25f, faceH * 0.16f));
        for (int i = 0; i < FACES.length; i++) {
            paint.setColor(PANEL);
            canvas.drawCircle(x[i], y[i], radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(i == 0 ? WARM : 0xFF476174);
            paint.setStrokeWidth(d(1.2f));
            canvas.drawCircle(x[i], y[i], radius, paint);
            paint.setStyle(Paint.Style.FILL);
            label(canvas, glyph[i], x[i], y[i] - d(7), 17, i == 0 ? WARM : ACCENT, true);
            String chosen = KeyboardLayout.at(layer, selectedRow, selectedCol, FACES[i]);
            label(canvas, KeyboardLayout.display(chosen), x[i], y[i] + d(12),
                    12, FG, true);
        }

        String[] footer = FOOTER.clone();
        footer[0] = layer >= KeyboardLayout.DIGITS ? "ABC" : "?123";
        footer[1] = (layer % 2 == 1) ? "⇧ ON" : "⇧";
        float itemWidth = w / footer.length;
        float footerTop = h - footerHeight + d(3);
        for (int i = 0; i < footer.length; i++) {
            float l = i * itemWidth + d(2);
            float rr = (i + 1) * itemWidth - d(2);
            round(canvas, i == 3 ? SELECTED : PANEL, l, footerTop,
                    rr, h - d(5), d(7));
            label(canvas, footer[i], (l + rr) / 2f, (footerTop + h - d(5)) / 2f,
                    i == 3 ? 11 : 13, i == 1 && layer % 2 == 1 ? WARM : FG, true);
        }
    }

    private int zone(float x, float y) {
        if (y < headerHeight && x >= getWidth() * 0.43f) return ZONE_HEADER;
        if (grid.contains(x, y)) return ZONE_GRID;
        if (x >= controlsLeft && x <= controlsRight && y >= bodyTop && y <= bodyBottom)
            return ZONE_FACES;
        if (y >= getHeight() - footerHeight && y <= getHeight()) return ZONE_FOOTER;
        return ZONE_NONE;
    }

    private void selectGrid(float x, float y) {
        if (!grid.contains(x, y)) return;
        int c = Math.min(2, Math.max(0, (int) ((x - grid.left) * 3f / grid.width())));
        int r = Math.min(2, Math.max(0, (int) ((y - grid.top) * 3f / grid.height())));
        if (r != selectedRow || c != selectedCol) listener.onChooseGroup(r, c);
    }

    private int quadrant(float x, float y) {
        float cw = grid.width() / 3f;
        float ch = grid.height() / 3f;
        float cellCenterX = grid.left + (selectedCol + 0.5f) * cw;
        float cellCenterY = grid.top + (selectedRow + 0.5f) * ch;
        float nx = (x - cellCenterX) / cw;
        float ny = (y - cellCenterY) / ch;
        if (Math.abs(ny) >= Math.abs(nx)) return ny < 0 ? KeyboardLayout.TOP : KeyboardLayout.BOTTOM;
        return nx < 0 ? KeyboardLayout.LEFT : KeyboardLayout.RIGHT;
    }

    private int closestFace(float tx, float ty) {
        float cx = (controlsLeft + controlsRight) / 2f;
        float cy = (bodyTop + bodyBottom) / 2f;
        float dx = tx - cx;
        float dy = ty - cy;
        // Four equal-angle sectors; slot order top, left, bottom, right.
        if (Math.abs(dy / (bodyBottom - bodyTop)) >
                Math.abs(dx / (controlsRight - controlsLeft))) {
            return dy < 0 ? KeyboardLayout.TOP : KeyboardLayout.BOTTOM;
        }
        return dx < 0 ? KeyboardLayout.LEFT : KeyboardLayout.RIGHT;
    }

    private void footerAction(float x) {
        int column = Math.max(0, Math.min(5, (int)(6f * x / getWidth())));
        switch (column) {
            case 0: listener.onNumbers(); break;
            case 1: listener.onShift(); break;
            case 2: listener.onBackspace(); break;
            case 3: listener.onSpace(); break;
            case 4: listener.onEnter(); break;
            case 5: listener.onSwitchKeyboard(); break;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int id = event.getPointerId(index);
            float x = event.getX(index), y = event.getY(index);
            int start = zone(x, y);
            pointerStarts.put(id, start);
            if (start == ZONE_GRID) selectGrid(x, y);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (pointerStarts.get(event.getPointerId(i), ZONE_NONE) == ZONE_GRID)
                    selectGrid(event.getX(i), event.getY(i));
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int id = event.getPointerId(index);
            int started = pointerStarts.get(id, ZONE_NONE);
            float x = event.getX(index), y = event.getY(index);
            int ended = zone(x, y);
            if (started == ZONE_GRID && ended == ZONE_GRID) {
                selectGrid(x, y);
                if (direct) listener.onChooseSlot(quadrant(x, y));
            } else if (started == ZONE_FACES && ended == ZONE_FACES) {
                listener.onChooseSlot(closestFace(x, y));
            } else if (started == ZONE_HEADER && ended == ZONE_HEADER) {
                direct = !direct;
                preferences.edit().putBoolean("direct_touch", direct).apply();
                invalidate();
            } else if (started == ZONE_FOOTER && ended == ZONE_FOOTER) {
                footerAction(x);
            }
            pointerStarts.delete(id);
            if (action == MotionEvent.ACTION_UP) {
                pointerStarts.clear();
                performClick();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            pointerStarts.clear();
            return true;
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
