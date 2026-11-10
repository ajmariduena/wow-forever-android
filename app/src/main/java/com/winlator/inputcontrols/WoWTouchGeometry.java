package com.winlator.inputcontrols;

public final class WoWTouchGeometry {
    public enum Layout { COVER, OPEN, FLEX }
    public static Layout layout(float width, float height, boolean halfOpened, boolean horizontal) {
        if (halfOpened && horizontal) return Layout.FLEX;
        return width / height > 1.7f ? Layout.COVER : Layout.OPEN;
    }

    public static final class TapOrDrag {
        public float startX, startY;
        private long started;
        private float slop;
        private boolean active, dragging;
        public void begin(float x, float y, long now, float slop) {
            startX = x; startY = y; started = now; this.slop = slop;
            active = true; dragging = false;
        }
        public boolean move(float x, float y) {
            if (active && Math.hypot(x - startX, y - startY) > slop) dragging = true;
            return dragging;
        }
        public boolean isDragging() { return dragging; }
        public boolean isTap(long now) { return active && !dragging && now >= started && now - started <= 250; }
        public void cancel() { active = false; dragging = false; }
    }

    public static float[] gamePoint(float x, float y, int left, int top, int width, int height,
                                    int gameWidth, int gameHeight, int scalingMode) {
        if (width <= 0 || height <= 0 || gameWidth <= 0 || gameHeight <= 0) return null;
        x -= left; y -= top;
        if (x < 0 || y < 0 || x >= width || y >= height) return null;
        float gx, gy;
        if (scalingMode == 1) {
            gx = x * gameWidth / width; gy = y * gameHeight / height;
        } else if (scalingMode == 2) {
            float scale = Math.max((float)width / gameWidth, (float)height / gameHeight);
            gx = (x - Math.round((width - Math.round(gameWidth * scale)) * .5f)) / scale;
            gy = (y - Math.round((height - Math.round(gameHeight * scale)) * .5f)) / scale;
        } else {
            com.winlator.renderer.ViewTransformation transform = new com.winlator.renderer.ViewTransformation();
            transform.update(width, height, gameWidth, gameHeight);
            gx = (x - transform.viewOffsetX) / transform.aspect;
            gy = (y - transform.viewOffsetY) / transform.aspect;
        }
        return gx >= 0 && gy >= 0 && gx < gameWidth && gy < gameHeight ? new float[]{gx, gy} : null;
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

    public static final class Wheel {
        private float lastY, remainder;
        private boolean active;
        public static boolean eligible(int count, boolean firstInGame, boolean secondInGame) {
            return count == 2 && firstInGame && secondInGame;
        }
        public void begin(float midpointY) { lastY = midpointY; remainder = 0; active = true; }
        public int move(float midpointY, float stepSize) {
            if (!active || stepSize <= 0) return 0;
            remainder += midpointY - lastY; lastY = midpointY;
            int steps = (int)(remainder / stepSize);
            remainder -= steps * stepSize;
            return steps;
        }
        public void reset() { active = false; remainder = 0; }
        public boolean isActive() { return active; }
    }

    private WoWTouchGeometry() {}
}
