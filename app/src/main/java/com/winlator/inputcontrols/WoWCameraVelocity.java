package com.winlator.inputcontrols;

public final class WoWCameraVelocity {
    public static final long IDLE_MS = 60;
    // At yaw=3 rad/s with linear stick response, 40% of screen width gives roughly 90° before acceleration/clamping.
    private static final float GAIN = (float)(Math.PI / (2 * 3 * .4));
    private float lastX, lastY, width, sensitivity, dx, dy, sampleMs, filteredX, filteredY;
    private long lastSample, lastMotion = -1, lastFrame;

    public void begin(float x, float y, long now, float width, float sensitivity) {
        stop();
        lastX = x; lastY = y; lastSample = lastFrame = now;
        this.width = Math.max(1, width); this.sensitivity = sensitivity;
    }

    public void move(float x, float y, long now) {
        float deltaX = x - lastX, deltaY = y - lastY;
        if (deltaX != 0 || deltaY != 0) {
            dx += deltaX; dy += deltaY;
            sampleMs += Math.max(1, Math.min(32, now - lastSample));
            lastMotion = now;
        }
        lastX = x; lastY = y; lastSample = now;
    }

    public float[] frame(long now) {
        if (!isActive(now)) { stop(); return new float[]{0, 0}; }
        if (sampleMs > 0) {
            float vx = dx * 1000 / (width * sampleMs), vy = dy * 1000 / (width * sampleMs);
            float gain = GAIN * sensitivity * (1 + .15f * Math.min(1, (float)Math.hypot(vx, vy)));
            float alpha = 1 - (float)Math.exp(-Math.max(1, now - lastFrame) / 8.0);
            filteredX += alpha * (vx * gain - filteredX);
            filteredY += alpha * (vy * gain - filteredY);
            float length = Math.max(1, (float)Math.hypot(filteredX, filteredY));
            filteredX /= length; filteredY /= length;
            dx = dy = sampleMs = 0;
            lastFrame = now;
            return new float[]{filteredX, filteredY};
        }
        lastFrame = now;
        float decay = Math.max(0, 1 - (now - lastMotion) / (float)IDLE_MS);
        return new float[]{filteredX * decay, filteredY * decay};
    }

    public boolean isActive(long now) { return lastMotion >= 0 && now - lastMotion < IDLE_MS; }
    public long nextFrameDelay(long now) { return Math.max(1, Math.min(16, IDLE_MS - (now - lastMotion))); }
    public void stop() { dx = dy = sampleMs = filteredX = filteredY = 0; lastMotion = -1; }
}
