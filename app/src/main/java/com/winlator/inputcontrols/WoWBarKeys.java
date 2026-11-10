package com.winlator.inputcontrols;

/** Clock-driven modifier state; callers publish output changes and schedule the next deadline. */
public final class WoWBarKeys {
    public enum Key { LT, RT, LTRT, LBRB }
    public static final long TAP_MS = 350, EXPIRY_MS = 3000, RELEASE_MS = 60;
    private Key armed;
    private int pointer = -1, abilities;
    private boolean locked, used;
    private long downAt, lastTap = -1, deadline = -1;

    public Key armed() { return armed; }
    public boolean locked() { return locked; }
    public int pointer() { return pointer; }
    public long deadline() { return deadline; }
    public void down(Key key, int id, long now) {
        tick(now);
        if (armed == key && pointer < 0) {
            if (!locked && lastTap >= 0 && now - lastTap < TAP_MS) {
                locked = true; deadline = -1;
            } else clear();
            return;
        }
        clear(); armed = key; pointer = id; downAt = now; used = abilities > 0;
    }
    public void up(int id, long now) {
        if (id != pointer) return;
        pointer = -1;
        if (armed == null || locked || abilities > 0) return;
        if (used || now - downAt >= TAP_MS) clear();
        else { lastTap = now; deadline = now + EXPIRY_MS; }
    }
    public void pressAbility() {
        abilities++; deadline = -1;
        if (armed != null) used = true;
    }
    public void releaseAbility(long now) {
        if (abilities > 0) abilities--;
        if (armed != null && !locked && pointer < 0 && abilities == 0) deadline = now + RELEASE_MS;
    }
    public void tick(long now) { if (deadline >= 0 && now >= deadline) clear(); }
    private void clear() { armed = null; locked = used = false; pointer = -1; lastTap = deadline = -1; }
    public void reset() { clear(); abilities = 0; }
}
