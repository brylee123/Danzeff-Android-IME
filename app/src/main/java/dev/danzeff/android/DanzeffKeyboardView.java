package dev.danzeff.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** Touch: expand a locked group, then swipe or tap. Controller: original face panel. */
public final class DanzeffKeyboardView extends View {
    public interface Listener {
        void onChooseGroup(int row, int col);
        void onChooseSlot(int slot);
        void onBackspace(); void onSpace(); void onEnter();
        void onShift(); void onNumbers(); void onSwitchKeyboard();
    }
    private static final int BG = 0xFF181818, PANEL = 0xFF555555, CELL = 0xFF777777;
    private static final int AMBER = 0xFFF3C35A, INK = 0xFF181818, WHITE = 0xFFFFFFFF;
    private static final String[] FOOTER = {"?123", "⇧", "⌫", "SPACE", "↵", ""};
    private static final String[] FACE_GLYPHS = {"△", "□", "×", "○"};
    private static final int NONE = 0, GRID = 1, FACES = 2, HEADER = 3, FOOTER_ZONE = 4, POPUP = 5;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path sector = new Path();
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Listener listener;
    private final SharedPreferences preferences;
    private final SparseIntArray controllerPointers = new SparseIntArray();
    private final RectF grid = new RectF(), popup = new RectF();
    private float headerHeight, footerHeight, bodyTop, bodyBottom, controlsLeft, controlsRight;
    private int layer, selectedRow = 1, selectedCol = 1;
    private boolean controllerConnected, controllerDirect;
    private int expandedRow = -1, expandedCol = -1, activePointer = -1;
    private int touchZone, footerPressed = -1, swipeSlot = -1;
    private float downX, downY;
    private final float swipeThreshold;

    public DanzeffKeyboardView(Context context, Listener callback) {
        super(context); listener = callback;
        preferences = context.getSharedPreferences("danzeff_preferences", Context.MODE_PRIVATE);
        controllerDirect = preferences.getBoolean("direct_touch", false);
        swipeThreshold = Math.max(d(14), ViewConfiguration.get(context).getScaledTouchSlop());
        setClickable(true); setBackgroundColor(BG);
    }
    public void updateState(int newLayer, int row, int col) {
        layer = newLayer; selectedRow = row; selectedCol = col; invalidate();
    }
    public void setControllerConnected(boolean connected) {
        if (controllerConnected == connected) return;
        controllerConnected = connected; resetTouchSelection(); controllerPointers.clear();
        geometry(getWidth(), getHeight()); requestLayout(); invalidate();
    }
    public void resetTouchSelection() {
        expandedRow = expandedCol = -1; activePointer = -1; swipeSlot = -1;
        touchZone = NONE; footerPressed = -1; invalidate();
    }
    private float d(float value) { return value * getResources().getDisplayMetrics().density; }
    private float font(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        setMeasuredDimension(resolveSize(Math.round(d(360)), widthSpec),
            resolveSize(Math.round(d(landscape ? 242 : 300)), heightSpec));
    }
    private void geometry(float width, float height) {
        headerHeight = d(39); footerHeight = d(56);
        bodyTop = headerHeight + d(5); bodyBottom = height - footerHeight - d(8);
        float faceWidth = controllerConnected ? Math.min(d(150), Math.max(d(94), width * .27f)) : 0;
        grid.set(d(8), bodyTop, controllerConnected ? width - faceWidth - d(17) : width - d(8), bodyBottom);
        controlsLeft = grid.right + d(9); controlsRight = width - d(8);
        if (expandedRow >= 0) popupGeometry();
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh); resetTouchSelection(); geometry(w, h);
    }
    private void popupGeometry() {
        float cw = grid.width() / 3, ch = grid.height() / 3;
        float pw = Math.min(grid.width(), cw * 1.65f), ph = Math.min(grid.height(), ch * 1.65f);
        float cx = grid.left + (expandedCol + .5f) * cw, cy = grid.top + (expandedRow + .5f) * ch;
        float left = Math.max(grid.left, Math.min(grid.right - pw, cx - pw / 2));
        float top = Math.max(grid.top, Math.min(grid.bottom - ph, cy - ph / 2));
        popup.set(left, top, left + pw, top + ph);
    }
    private void box(Canvas canvas, int color, RectF rect, float radius) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); canvas.drawRoundRect(rect, radius, radius, paint);
    }
    private void box(Canvas canvas, int color, float left, float top, float right, float bottom, float radius) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
    }
    private void label(Canvas canvas, String value, float x, float y, float size, int color, boolean heavy) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); paint.setTextSize(font(size));
        paint.setTypeface(heavy ? bold : regular); paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(value, x, y - (paint.ascent() + paint.descent()) / 2, paint);
    }
    private void characters(Canvas canvas, RectF bounds, int row, int col, float size, int color) {
        float cx = bounds.centerX(), cy = bounds.centerY();
        label(canvas, KeyboardLayout.display(KeyboardLayout.at(layer, row, col, 0)), cx, cy - bounds.height() * .29f, size, color, true);
        label(canvas, KeyboardLayout.display(KeyboardLayout.at(layer, row, col, 1)), cx - bounds.width() * .30f, cy, size, color, true);
        label(canvas, KeyboardLayout.display(KeyboardLayout.at(layer, row, col, 2)), cx, cy + bounds.height() * .29f, size, color, true);
        label(canvas, KeyboardLayout.display(KeyboardLayout.at(layer, row, col, 3)), cx + bounds.width() * .30f, cy, size, color, true);
    }
    private final RectF cellBounds = new RectF();
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); if (getWidth() <= 0 || getHeight() <= 0) return;
        geometry(getWidth(), getHeight());
        label(canvas, "DANZEFF", d(61), headerHeight / 2, 13, AMBER, true);
        if (controllerConnected) {
            box(canvas, PANEL, getWidth() * .43f, d(6), getWidth() - d(8), headerHeight - d(4), d(6));
            label(canvas, controllerDirect ? "DIRECT TAP  ⇄" : "TWO-STEP  ⇄", (getWidth() * .43f + getWidth() - d(8)) / 2, headerHeight / 2, 11, WHITE, true);
        } else {
            String hint = swipeSlot >= 0 ? "RELEASE TO TYPE" : expandedRow >= 0 ? "SWIPE OR TAP A CHARACTER" : "HOLD & SWIPE · OR TAP";
            label(canvas, hint, getWidth() * .67f, headerHeight / 2, 10, WHITE, false);
        }
        float cw = grid.width() / 3, ch = grid.height() / 3;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            cellBounds.set(grid.left + col * cw + d(1), grid.top + row * ch + d(1),
                grid.left + (col + 1) * cw - d(1), grid.top + (row + 1) * ch - d(1));
            boolean selected = row == selectedRow && col == selectedCol;
            box(canvas, selected ? AMBER : CELL, cellBounds, d(1));
            characters(canvas, cellBounds, row, col, ch < d(55) ? 11 : 15, selected ? INK : WHITE);
        }
        if (controllerConnected) drawFaces(canvas);
        drawFooter(canvas);
        if (!controllerConnected && expandedRow >= 0) drawPopup(canvas);
    }
    private void drawPopup(Canvas canvas) {
        box(canvas, 0x99000000, grid, 0);
        box(canvas, INK, popup.left - d(2), popup.top - d(2), popup.right + d(2), popup.bottom + d(2), d(5));
        box(canvas, AMBER, popup, d(4));
        if (swipeSlot >= 0) {
            sector.reset(); sector.moveTo(popup.centerX(), popup.centerY());
            switch (swipeSlot) {
                case 0: sector.lineTo(popup.left, popup.top); sector.lineTo(popup.right, popup.top); break;
                case 1: sector.lineTo(popup.left, popup.bottom); sector.lineTo(popup.left, popup.top); break;
                case 2: sector.lineTo(popup.right, popup.bottom); sector.lineTo(popup.left, popup.bottom); break;
                default: sector.lineTo(popup.right, popup.top); sector.lineTo(popup.right, popup.bottom); break;
            }
            sector.close(); paint.setColor(0xFFE1AA36); canvas.drawPath(sector, paint);
        }
        paint.setColor(0xFFAC8535); paint.setStrokeWidth(d(1));
        canvas.drawLine(popup.left + d(4), popup.top + d(4), popup.right - d(4), popup.bottom - d(4), paint);
        canvas.drawLine(popup.left + d(4), popup.bottom - d(4), popup.right - d(4), popup.top + d(4), paint);
        characters(canvas, popup, expandedRow, expandedCol, 22, INK);
    }
    private void drawFaces(Canvas canvas) {
        float cx = (controlsLeft + controlsRight) / 2, cy = (bodyTop + bodyBottom) / 2;
        float fw = controlsRight - controlsLeft, fh = bodyBottom - bodyTop;
        float radius = Math.min(d(27), Math.min(fw * .25f, fh * .16f));
        for (int slot = 0; slot < 4; slot++) {
            float x = cx + (slot == 1 ? -fw * .26f : slot == 3 ? fw * .26f : 0);
            float y = cy + (slot == 0 ? -fh * .29f : slot == 2 ? fh * .29f : 0);
            paint.setColor(PANEL); canvas.drawCircle(x, y, radius, paint);
            label(canvas, FACE_GLYPHS[slot], x, y - d(7), 17, AMBER, true);
            label(canvas, KeyboardLayout.display(KeyboardLayout.at(layer, selectedRow, selectedCol, slot)), x, y + d(12), 12, WHITE, true);
        }
    }
    private void drawFooter(Canvas canvas) {
        float width = getWidth() / 6f, top = getHeight() - footerHeight + d(3), bottom = getHeight() - d(5);
        for (int i = 0; i < 6; i++) {
            float left = i * width + d(2), right = (i + 1) * width - d(2), cx = (left + right) / 2, cy = (top + bottom) / 2;
            box(canvas, i == 3 ? AMBER : PANEL, left, top, right, bottom, d(6));
            if (i == 5) {
                // A monochrome globe keeps the solid palette and avoids color emoji fonts.
                paint.setStyle(Paint.Style.STROKE); paint.setColor(WHITE); paint.setStrokeWidth(d(1.3f));
                float radius = d(10); canvas.drawCircle(cx, cy, radius, paint);
                canvas.drawOval(cx - radius * .45f, cy - radius, cx + radius * .45f, cy + radius, paint);
                canvas.drawLine(cx - radius, cy, cx + radius, cy, paint); paint.setStyle(Paint.Style.FILL);
            } else {
                String text = i == 0 ? (layer >= 2 ? "ABC" : "?123") : i == 1 && layer % 2 == 1 ? "⇧ ON" : FOOTER[i];
                label(canvas, text, cx, cy, i == 3 ? 11 : 13, i == 3 ? INK : WHITE, true);
            }
        }
    }
    private int cellRow(float y) { return Math.max(0, Math.min(2, (int) ((y - grid.top) * 3 / grid.height()))); }
    private int cellCol(float x) { return Math.max(0, Math.min(2, (int) ((x - grid.left) * 3 / grid.width()))); }
    private int direction(float dx, float dy) { return Math.abs(dy) >= Math.abs(dx) ? (dy < 0 ? 0 : 2) : (dx < 0 ? 1 : 3); }
    private int popupSlot(float x, float y) {
        if (!popup.contains(x, y)) return -1;
        float dx = x - popup.centerX(), dy = y - popup.centerY();
        if (Math.hypot(dx, dy) < d(12)) return -1;
        return direction(dx / popup.width(), dy / popup.height());
    }
    private int zone(float x, float y) {
        if (y >= getHeight() - footerHeight && y <= getHeight() && x >= 0 && x < getWidth()) return FOOTER_ZONE;
        if (controllerConnected && y < headerHeight && x >= getWidth() * .43f) return HEADER;
        if (grid.contains(x, y)) return GRID;
        if (controllerConnected && x >= controlsLeft && x <= controlsRight && y >= bodyTop && y <= bodyBottom) return FACES;
        return NONE;
    }
    private void expand(float x, float y) {
        expandedRow = cellRow(y); expandedCol = cellCol(x); swipeSlot = -1;
        listener.onChooseGroup(expandedRow, expandedCol); popupGeometry(); invalidate();
    }
    private void resetAfterCharacter() {
        resetTouchSelection(); listener.onChooseGroup(1, 1);
    }
    private void commitSlot(int slot) {
        if (slot < 0 || KeyboardLayout.at(layer, expandedRow, expandedCol, slot).isEmpty()) return;
        listener.onChooseGroup(expandedRow, expandedCol); listener.onChooseSlot(slot); resetAfterCharacter();
    }
    private void footerAction(int column) {
        switch (column) {
            case 0: listener.onNumbers(); break; case 1: listener.onShift(); break;
            case 2: listener.onBackspace(); break; case 3: listener.onSpace(); break;
            case 4: listener.onEnter(); break; case 5: listener.onSwitchKeyboard(); break;
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        geometry(getWidth(), getHeight());
        if (controllerConnected) return controllerTouch(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) { resetAfterCharacter(); return true; }
        if (action == MotionEvent.ACTION_DOWN) {
            activePointer = event.getPointerId(0); downX = event.getX(); downY = event.getY(); swipeSlot = -1;
            if (expandedRow >= 0 && popup.contains(downX, downY)) touchZone = POPUP;
            else {
                touchZone = zone(downX, downY);
                if (touchZone == GRID) expand(downX, downY);
                else if (expandedRow >= 0) { expandedRow = expandedCol = -1; listener.onChooseGroup(1, 1); invalidate(); }
            }
            footerPressed = touchZone == FOOTER_ZONE ? (int) (6 * downX / getWidth()) : -1;
            return true;
        }
        int index = event.findPointerIndex(activePointer);
        if (index < 0) return true;
        float x = event.getX(index), y = event.getY(index);
        if (action == MotionEvent.ACTION_MOVE && (touchZone == GRID || touchZone == POPUP)) {
            float dx = x - downX, dy = y - downY;
            swipeSlot = Math.hypot(dx, dy) >= swipeThreshold ? direction(dx, dy) : -1;
            invalidate(); return true;
        }
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                && event.getPointerId(event.getActionIndex()) == activePointer) {
            if (touchZone == GRID || touchZone == POPUP) {
                float dx = x - downX, dy = y - downY;
                if (Math.hypot(dx, dy) >= swipeThreshold) {
                    if (x >= 0 && x < getWidth() && y >= 0 && y < bodyBottom) commitSlot(direction(dx, dy));
                    else resetAfterCharacter();
                } else if (touchZone == POPUP) commitSlot(popupSlot(x, y));
                // A first tap leaves the expanded cell open for the second character tap.
            } else if (touchZone == FOOTER_ZONE && zone(x, y) == FOOTER_ZONE
                    && (int) (6 * x / getWidth()) == footerPressed) {
                footerAction(footerPressed);
            }
            activePointer = -1; swipeSlot = -1; touchZone = NONE; invalidate(); performClick();
        }
        return true;
    }
    private boolean controllerTouch(MotionEvent event) {
        int action = event.getActionMasked(), index = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            float x = event.getX(index), y = event.getY(index); int area = zone(x, y);
            controllerPointers.put(event.getPointerId(index), area);
            if (area == GRID) listener.onChooseGroup(cellRow(y), cellCol(x));
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) if (controllerPointers.get(event.getPointerId(i), NONE) == GRID && grid.contains(event.getX(i), event.getY(i)))
                listener.onChooseGroup(cellRow(event.getY(i)), cellCol(event.getX(i)));
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int started = controllerPointers.get(event.getPointerId(index), NONE);
            float x = event.getX(index), y = event.getY(index); int ended = zone(x, y);
            if (started == GRID && ended == GRID && controllerDirect) {
                listener.onChooseGroup(cellRow(y), cellCol(x));
                float cx = grid.left + (cellCol(x) + .5f) * grid.width() / 3, cy = grid.top + (cellRow(y) + .5f) * grid.height() / 3;
                listener.onChooseSlot(direction((x - cx) / grid.width(), (y - cy) / grid.height()));
            } else if (started == FACES && ended == FACES) listener.onChooseSlot(direction((x - (controlsLeft + controlsRight) / 2) / (controlsRight - controlsLeft), (y - (bodyTop + bodyBottom) / 2) / (bodyBottom - bodyTop)));
            else if (started == HEADER && ended == HEADER) { controllerDirect = !controllerDirect; preferences.edit().putBoolean("direct_touch", controllerDirect).apply(); invalidate(); }
            else if (started == FOOTER_ZONE && ended == FOOTER_ZONE) footerAction((int) (6 * x / getWidth()));
            controllerPointers.delete(event.getPointerId(index));
            if (action == MotionEvent.ACTION_UP) { controllerPointers.clear(); performClick(); }
        } else if (action == MotionEvent.ACTION_CANCEL) controllerPointers.clear();
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
