package com.winlator.inputcontrols;

public final class WoWTouchGeometry {
    public enum Layout { COVER, OPEN, FLEX }
    public static Layout layout(float width, float height, boolean halfOpened, boolean horizontal) {
        if (halfOpened && horizontal) return Layout.FLEX;
        return width / height > 1.7f ? Layout.COVER : Layout.OPEN;
    }

    public static final class Stick {
        public float originX, originY, x, y;
        public void begin(float x, float y) { originX = x; originY = y; this.x = this.y = 0; }
        public void move(float x, float y, float radius) {
            float dx = (x - originX) / Math.max(1, radius);
            float dy = (y - originY) / Math.max(1, radius);
            float length = Math.max(1, (float)Math.hypot(dx, dy));
            this.x = dx / length;
            this.y = dy / length;
        }
        public void release() { x = y = 0; }
    }

    public static float camera(float delta, float radius) {
        return Math.max(-1, Math.min(1, delta / Math.max(1, radius)));
    }
    private WoWTouchGeometry() {}
}
