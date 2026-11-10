package com.winlator.inputcontrols;

import android.os.SystemClock;
import android.util.SparseArray;
import android.util.Log;
import com.winlator.widget.InputControlsView;

final class WoWPointerGestures {
    private static final class Touch {
        float x, y;
        final boolean inGame;
        Touch(float x, float y, boolean inGame) { this.x = x; this.y = y; this.inGame = inGame; }
    }
    private final InputControlsView view;
    private final SparseArray<Touch> touches = new SparseArray<>();
    private final WoWTouchGeometry.TapOrDrag gesture = new WoWTouchGeometry.TapOrDrag();
    private final WoWTouchGeometry.Wheel wheel = new WoWTouchGeometry.Wheel();
    private int owner = -1, gameBottom = -1;
    void setGameBottom(int bottom) { gameBottom = bottom; }
    private boolean inGamePoint(float x, float y) {
        return (gameBottom < 0 || y < gameBottom) && view.isWoWGamePoint(x, y);
    }
    private boolean wheelDropLogged;
    private boolean scrolling, leftDown, rightStickDown, longClicked;
    private boolean cameraEnabled = true;
    private float sensitivity = 1, radius;
    private final Runnable longPress = this::onLongPress;
    private void onLongPress() {
        Touch touch = touches.get(owner);
        if (touch != null && view.isWoWMouseMode() && !scrolling && !gesture.isDragging()) {
            view.clickWoWPointer(touch.x, touch.y, true);
            longClicked = true;
        }
    }

    WoWPointerGestures(InputControlsView view) { this.view = view; }
    private float density() { return view.getResources().getDisplayMetrics().density; }
    private void axis(float x, float y) {
        view.handleInputEvent(Binding.GAMEPAD_RIGHT_THUMB_RIGHT, true, x);
        view.handleInputEvent(Binding.GAMEPAD_RIGHT_THUMB_DOWN, true, y);
    }
    private void releaseOutputs() {
        view.removeCallbacks(longPress);
        if (leftDown) { view.setWoWMouseButton(false, false); leftDown = false; }
        if (rightStickDown) { axis(0, 0); rightStickDown = false; }
    }
    private boolean eligiblePair() {
        return touches.size() == 2 && WoWTouchGeometry.Wheel.eligible(2,
            touches.valueAt(0).inGame && inGamePoint(touches.valueAt(0).x, touches.valueAt(0).y),
            touches.valueAt(1).inGame && inGamePoint(touches.valueAt(1).x, touches.valueAt(1).y));
    }
    private float midpointY() { return (touches.valueAt(0).y + touches.valueAt(1).y) / 2; }
    boolean down(int id, float x, float y, boolean inGame, boolean cameraEnabled, float sensitivity) {
        this.cameraEnabled = cameraEnabled; this.sensitivity = sensitivity;
        if (view.isWoWMouseMode() && !inGame) { Log.i("WoWTouch", "mouse gesture drop=outside game or in panel"); return true; }
        touches.put(id, new Touch(x, y, inGame));
        if (eligiblePair()) {
            Log.i("WoWTouch", "gesture=two-finger wheel");
            releaseOutputs(); gesture.cancel(); scrolling = true;
            wheelDropLogged = false; wheel.begin(midpointY()); return true;
        }
        if (scrolling) wheel.reset();
        if (scrolling || touches.size() > 1) return true;
        owner = id; longClicked = false;
        radius = Math.min(view.getWidth(), view.getHeight()) * .12f;
        gesture.begin(x, y, SystemClock.uptimeMillis(), 4 * density());
        if (view.isWoWMouseMode()) { view.moveWoWPointer(x, y); view.postDelayed(longPress, 500); }
        return true;
    }
    boolean move(int id, float x, float y) {
        Touch touch = touches.get(id);
        if (touch == null) return false;
        touch.x = x; touch.y = y;
        if (scrolling) {
            if (!eligiblePair()) {
                if (!wheelDropLogged) Log.i("WoWTouch", "scroll drop=requires two touches inside game count=" + touches.size());
                wheelDropLogged = true; wheel.reset(); return true;
            }
            wheelDropLogged = false;
            if (!wheel.isActive()) wheel.begin(midpointY());
            int steps = wheel.move(midpointY(), 24 * density());
            if (steps != 0) view.scrollWoWPointer((touches.valueAt(0).x + touches.valueAt(1).x) / 2, midpointY(), steps);
            return true;
        }
        if (id != owner) return true;
        boolean dragging = gesture.move(x, y);
        if (view.isWoWMouseMode()) {
            if (!inGamePoint(x, y)) { releaseOutputs(); gesture.cancel(); return true; }
            if (dragging) {
                view.removeCallbacks(longPress);
                if (!leftDown && !longClicked) {
                    view.moveWoWPointer(gesture.startX, gesture.startY);
                    view.setWoWMouseButton(false, true); leftDown = true;
                }
            }
            view.moveWoWPointer(x, y);
        } else if (dragging && cameraEnabled) {
            float[] deflection = WoWTouchGeometry.rightStick(x, y, gesture.startX, gesture.startY, radius / sensitivity);
            axis(deflection[0], deflection[1]); rightStickDown = true;
        }
        return true;
    }
    boolean up(int id) {
        Touch touch = touches.get(id);
        if (touch == null) return false;
        touches.remove(id);
        if (id == owner) {
            releaseOutputs();
            boolean tap = gesture.isTap(SystemClock.uptimeMillis());
            String reason = scrolling ? "wheel" : longClicked ? "long press" : !touch.inGame ? "outside game or in panel"
                : !inGamePoint(touch.x, touch.y) ? "release outside game" : !tap ? "drag or timeout" : null;
            Log.i("WoWTouch", reason == null ? "tap=click" : "tap drop=" + reason);
            if (reason == null) view.performWoWGameClick(gesture.startX, gesture.startY);
            gesture.cancel(); owner = -1;
        }
        if (touches.size() == 0) { scrolling = false; wheel.reset(); }
        return true;
    }
    boolean cancel() {
        boolean active = touches.size() > 0;
        releaseOutputs(); gesture.cancel(); wheel.reset(); touches.clear(); owner = -1; scrolling = false;
        return active;
    }
}
