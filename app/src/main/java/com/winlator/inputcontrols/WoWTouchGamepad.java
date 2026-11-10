package com.winlator.inputcontrols;

import android.graphics.*;
import android.util.Log;
import android.os.SystemClock;
import android.graphics.drawable.Drawable;
import com.winlator.widget.InputControlsView;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.HashMap;

/** A responsive composite element; input still goes through the profile's normal XInput path. */
public final class WoWTouchGamepad {
    private final InputControlsView view;
    private final ArrayList<Button> buttons = new ArrayList<>();
    private final HashMap<String, Drawable> art = new HashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final WoWTouchGeometry.Stick stick = new WoWTouchGeometry.Stick();
    private final WoWPointerGestures pointerGestures;
    private int movementPointer = -1, mouseTogglePointer = -1;
    private float restX, restY, stickSize, zoneRight, zoneTop;
    private int width, height, hingeTop = -1, hingeBottom = -1;
    private boolean floatingStick = true, cameraDrag = true;
    private float cameraSensitivity = 1;
    private final WoWBarKeys bars = new WoWBarKeys();
    private WoWBarKeys.Key publishedBar;
    private final Runnable barTimer = () -> { bars.tick(SystemClock.uptimeMillis()); publishBar(); };
    private final HashMap<Button, Runnable> pendingAbilities = new HashMap<>();
    private final HashMap<Button, Runnable> pendingReleases = new HashMap<>();
    private final java.util.HashSet<Button> releasedAbilities = new java.util.HashSet<>();
    private final java.util.HashSet<Button> sentAbilities = new java.util.HashSet<>();
    private static WoWBarKeys.Key barKey(Button b) {
        switch (b.label) {
            case "LT": return WoWBarKeys.Key.LT;
            case "RT": return WoWBarKeys.Key.RT;
            case "LT+RT": return WoWBarKeys.Key.LTRT;
            case "LB+RB": return WoWBarKeys.Key.LBRB;
            default: return null;
        }
    }
    private static boolean ability(Button b) {
        return b.binding == Binding.GAMEPAD_BUTTON_A || b.binding == Binding.GAMEPAD_BUTTON_B
            || b.binding == Binding.GAMEPAD_BUTTON_X || b.binding == Binding.GAMEPAD_BUTTON_Y
            || b.binding == Binding.GAMEPAD_DPAD_UP || b.binding == Binding.GAMEPAD_DPAD_DOWN
            || b.binding == Binding.GAMEPAD_DPAD_LEFT || b.binding == Binding.GAMEPAD_DPAD_RIGHT;
    }
    private void modifier(WoWBarKeys.Key key, boolean down) {
        if (key == null) return;
        if (key == WoWBarKeys.Key.LT || key == WoWBarKeys.Key.LTRT) view.handleInputEvent(Binding.GAMEPAD_BUTTON_L2, down);
        if (key == WoWBarKeys.Key.RT || key == WoWBarKeys.Key.LTRT) view.handleInputEvent(Binding.GAMEPAD_BUTTON_R2, down);
        if (key == WoWBarKeys.Key.LBRB) {
            view.handleInputEvent(Binding.GAMEPAD_BUTTON_L1, down);
            view.handleInputEvent(Binding.GAMEPAD_BUTTON_R1, down);
        }
    }
    private void publishBar() {
        if (publishedBar != bars.armed()) {
            modifier(publishedBar, false); modifier(bars.armed(), true);
            publishedBar = bars.armed(); view.commitGamepadState();
        }
        view.removeCallbacks(barTimer);
        if (bars.deadline() >= 0) view.postDelayed(barTimer, Math.max(1, bars.deadline() - SystemClock.uptimeMillis()));
        view.invalidate();
    }
    private void pressAbility(Button b, int id) {
        if (b.pointer >= 0 || pendingAbilities.containsKey(b) || sentAbilities.contains(b)) return;
        b.pointer = id; bars.pressAbility(); publishBar();
        // The shared-memory pad is polled by the guest: leave a frame for the modifier before the ability.
        Runnable press = () -> {
            pendingAbilities.remove(b); sentAbilities.add(b);
            view.handleInputEvent(b.binding, true); view.commitGamepadState();
            if (releasedAbilities.remove(b)) {
                Runnable release = () -> { pendingReleases.remove(b); releaseAbility(b); };
                pendingReleases.put(b, release); view.postDelayed(release, 24);
            }
        };
        pendingAbilities.put(b, press);
        if (bars.armed() == null) press.run(); else view.postDelayed(press, 24);
    }
    private void releaseAbility(Button b) {
        if (pendingAbilities.containsKey(b)) { releasedAbilities.add(b); return; }
        if (!sentAbilities.remove(b)) return;
        view.handleInputEvent(b.binding, false); view.commitGamepadState();
        bars.releaseAbility(SystemClock.uptimeMillis()); publishBar();
    }

    private static final class Button {
        final float x, y;
        float size;
        final String label, gem;
        final Binding binding;
        int pointer = -1;
        Button(float x, float y, float size, String label, String gem, Binding binding) {
            this.x = x; this.y = y; this.size = size; this.label = label; this.gem = gem; this.binding = binding;
        }
        boolean hit(float px, float py) { return Math.hypot(px-x, py-y) <= size/2; }
    }
    public WoWTouchGamepad(InputControlsView view) { this.view = view; pointerGestures = new WoWPointerGestures(view); }
    public boolean isFloatingStick() { return floatingStick; }
    public boolean isCameraDrag() { return cameraDrag; }
    public float getCameraSensitivity() { return cameraSensitivity; }
    public void setCameraSensitivity(float sensitivity) {
        cameraSensitivity = Float.isFinite(sensitivity) ? Math.max(.1f, Math.min(5, sensitivity)) : 1;
    }
    public void readOptions(JSONObject data) {
        floatingStick = data.optBoolean("floatingStick", true);
        cameraDrag = data.optBoolean("cameraDrag", true);
        setCameraSensitivity((float)data.optDouble("cameraSensitivity", 1));
    }
    public void writeOptions(JSONObject data) throws JSONException {
        data.put("floatingStick", floatingStick);
        data.put("cameraDrag", cameraDrag);
        data.put("cameraSensitivity", cameraSensitivity);
    }
    public void setPanelBounds(int top, int bottom) {
        if (hingeTop == top && hingeBottom == bottom) return;
        cancel(); hingeTop = top; hingeBottom = bottom; pointerGestures.setGameBottom(top); width = 0; view.invalidate();
    }
    private void layout() {
        int w = view.getWidth(), h = view.getHeight();
        if (w == width && h == height) return;
        cancel(); width = w; height = h; buttons.clear();
        if (w == 0 || h == 0) return;
        WoWTouchGeometry.Layout mode = WoWTouchGeometry.layout(w, h, hingeTop >= 0, true);
        boolean cover = mode == WoWTouchGeometry.Layout.COVER, flex = mode == WoWTouchGeometry.Layout.FLEX;
        // Flex coordinates are relative to the actual lower pane, including non-central hinges.
        float panelTop = flex ? hingeBottom : 0;
        float panelScale = flex ? (h-panelTop)/(h*.5f) : 1;
        float unit = h * panelScale;
        float minimum = 44 * view.getResources().getDisplayMetrics().density;
        float faceSize = Math.max(minimum, unit * (cover ? .15f : flex ? .095f : .11f));
        float small = Math.max(minimum, unit * (cover ? .10f : flex ? .06f : .075f));
        float dp = Math.max(minimum, unit * (cover ? .095f : .07f));
        float shift = Math.max(minimum, unit * (cover ? .10f : flex ? .075f : .085f));
        stickSize = unit * (cover ? .38f : flex ? .22f : .27f);
        float[] v = cover ? new float[]{32,45,15,72,68,74,87,62,6,26,15,16,24,26,95,30,95,10,77,10,33,10,40,10,47,10,54,10,61,10,68,10}
            : flex ? new float[]{36,68,18,85,56,87,83,80,8,61,19,57,30,61,95,63.5f,95,54.5f,76,56,44,64,54,64,40,54.5f,48,54.5f,56,54.5f,64,54.5f}
            : new float[]{34,64,17,82,58,87,84,80,7,56,17,52,27,56,94,62,94,52,78,6,30,6,38,6,46,6,54,6,62,6,70,6};
        zoneRight = w*v[0]/100; zoneTop = py(v[1],flex,panelTop,panelScale);
        restX = w*v[2]/100; restY = py(v[3],flex,panelTop,panelScale);
        float fx = w*v[6]/100, fy = py(v[7],flex,panelTop,panelScale), gap = faceSize*.8f;
        add(fx,fy+gap,faceSize,"A","green",Binding.GAMEPAD_BUTTON_A);
        add(fx+gap,fy,faceSize,"B","crimson",Binding.GAMEPAD_BUTTON_B);
        add(fx-gap,fy,faceSize,"X","blue",Binding.GAMEPAD_BUTTON_X);
        add(fx,fy-gap,faceSize,"Y","gold",Binding.GAMEPAD_BUTTON_Y);
        Binding[] directions = {Binding.GAMEPAD_DPAD_UP,Binding.GAMEPAD_DPAD_RIGHT,Binding.GAMEPAD_DPAD_DOWN,Binding.GAMEPAD_DPAD_LEFT};
        String[] arrows = {"▲","▶","▼","◀"};
        float dx = w*v[4]/100, dy = py(v[5],flex,panelTop,panelScale);
        for (int i=0;i<4;i++) add(dx+(i==1?dp*1.02f:i==3?-dp*1.02f:0),dy+(i==2?dp*1.02f:i==0?-dp*1.02f:0),dp,arrows[i],null,directions[i]);
        String[] labels = {"LT","LT+RT","RT","RB","LB","LB+RB","L3","R3","VIEW","⌨","MENU","MOUSE"};
        Binding[] bindings = {Binding.GAMEPAD_BUTTON_L2,Binding.NONE,Binding.GAMEPAD_BUTTON_R2,Binding.GAMEPAD_BUTTON_R1,Binding.GAMEPAD_BUTTON_L1,Binding.NONE,Binding.GAMEPAD_BUTTON_L3,Binding.GAMEPAD_BUTTON_R3,Binding.GAMEPAD_BUTTON_SELECT,Binding.SHOW_KEYBOARD,Binding.GAMEPAD_BUTTON_START,Binding.NONE};
        for (int i=0;i<labels.length;i++) add(w*v[8+i*2]/100,py(v[9+i*2],flex,panelTop,panelScale),i<3?shift:small,labels[i],i==0?"purple":i==1?"gray":i==2?"red-orange":null,bindings[i]);
        // Keep the prototype centers while trimming art/hit circles on narrow aspect ratios.
        for (int i=0;i<buttons.size();i++) for (int j=i+1;j<buttons.size();j++) {
            Button a = buttons.get(i), b = buttons.get(j);
            float distance = (float)Math.hypot(a.x-b.x,a.y-b.y) - 2;
            if ((a.size+b.size)/2 > distance) {
                float available = Math.max(minimum, distance);
                a.size = Math.min(a.size, available); b.size = Math.min(b.size, available);
            }
        }
    }
    private float py(float percent, boolean flex, float top, float scale) {
        return flex ? top+(percent-50)*height/100*scale : percent*height/100;
    }
    private void add(float x,float y,float size,String label,String gem,Binding binding) { buttons.add(new Button(x,y,size,label,gem,binding)); }
    private void image(Canvas canvas,String name,float x,float y,float size,int alpha) {
        Drawable drawable = art.get(name);
        if (drawable == null) {
            int id = view.getResources().getIdentifier("wow_"+name.replace('-','_'),"drawable",view.getContext().getPackageName());
            if (id == 0) return;
            drawable = view.getContext().getDrawable(id).mutate(); art.put(name,drawable);
        }
        drawable.setAlpha(alpha);
        drawable.setBounds((int)(x-size/2),(int)(y-size/2),(int)(x+size/2),(int)(y+size/2)); drawable.draw(canvas);
    }
    public void draw(Canvas canvas) {
        layout();
        if (hingeTop >= 0) { paint.setColor(0xff120d08); canvas.drawRect(0,hingeTop,width,height,paint); }
        for (Button b:buttons) {
            String state = (b.pointer >= 0 || (barKey(b) != null && bars.armed() == barKey(b)) || (b.label.equals("MOUSE") && view.isWoWMouseMode())) ? "pressed" : "normal";
            if (b.gem != null) image(canvas,"gem-"+b.gem+"-"+state,b.x,b.y,b.size*.76f,255);
            image(canvas,"frame-"+(b.gem == null?"menu":"ability")+"-"+state,b.x,b.y,b.size,255);
            if (barKey(b) != null && bars.armed() == barKey(b) && bars.locked()) {
                paint.setColor(0xffffd76e); canvas.drawCircle(b.x,b.y-b.size*.39f,b.size*.065f,paint);
            }
            if (b.label.equals("MOUSE")) { image(canvas,"mouse",b.x,b.y,b.size*.4f,255); continue; }
            if (b.binding == Binding.SHOW_KEYBOARD) { image(canvas,"keyboard",b.x,b.y,b.size*.4f,255); continue; }
            paint.setColor(b.gem == null ? 0xfff0dca6 : 0xfffff7e0);
            paint.setTypeface(Typeface.create(b.gem == null ? "sans-serif" : "serif",Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(b.size*(b.label.length() > 2 ? .20f : b.gem == null ? .25f : .42f));
            paint.setShadowLayer(3,0,1,Color.BLACK);
            canvas.drawText(b.label,b.x,b.y-(paint.ascent()+paint.descent())/2,paint); paint.clearShadowLayer();
        }
        float sx = movementPointer < 0 ? restX : stick.originX, sy = movementPointer < 0 ? restY : stick.originY;
        int alpha = movementPointer < 0 ? 140 : 255;
        image(canvas,"frame-joystick-base-normal",sx,sy,stickSize,alpha);
        image(canvas,"frame-joystick-knob-normal",sx+stick.x*stickSize*.3f,sy+stick.y*stickSize*.3f,stickSize*.44f,alpha);
    }
    RectF buttonBounds(Binding binding) {
        layout();
        for (Button b : buttons) if (b.binding == binding && (binding != Binding.NONE || b.label.equals("MOUSE"))) return new RectF(b.x-b.size/2,b.y-b.size/2,b.x+b.size/2,b.y+b.size/2);
        return null;
    }
    RectF barBounds(WoWBarKeys.Key key) {
        layout();
        for (Button b:buttons) if (barKey(b) == key) return new RectF(b.x-b.size/2,b.y-b.size/2,b.x+b.size/2,b.y+b.size/2);
        return null;
    }
    public boolean down(int id,float x,float y) {
        layout();
        for (Button b:buttons) if (b.hit(x,y)) {
            if (b.label.equals("MOUSE")) { view.setWoWMouseMode(!view.isWoWMouseMode()); mouseTogglePointer=id; Log.i("WoWTouch", "button down " + b.binding.name()); return true; }
            if (barKey(b) != null) { bars.down(barKey(b),id,SystemClock.uptimeMillis()); publishBar(); return true; }
            if (ability(b)) { pressAbility(b,id); view.invalidate(); return true; }
            if (b.binding == Binding.SHOW_KEYBOARD || b.binding == Binding.GAMEPAD_BUTTON_START) cancel();
            if (b.pointer < 0) { b.pointer=id; Log.i("WoWTouch", "button down " + b.binding.name()); view.handleInputEvent(b.binding,true); view.invalidate(); }
            return true;
        }
        boolean inPanel = hingeTop >= 0 && y >= hingeTop;
        if (inPanel) Log.i("WoWTouch", "point in panel y=" + y + " edge=" + hingeTop);
        boolean inGame = !inPanel && view.isWoWGamePoint(x, y);
        if (x <= zoneRight && y >= zoneTop && !(view.isWoWMouseMode() && inGame)) {
            if (movementPointer < 0) { movementPointer=id; stick.begin(floatingStick?x:restX,floatingStick?y:restY); view.invalidate(); }
            return true;
        }
        if (hingeTop >= 0 && y >= hingeTop && y < hingeBottom) return true;
        return pointerGestures.down(id, x, y, inGame, cameraDrag, cameraSensitivity);
    }
    public boolean move(int id,float x,float y) {
        if (id == movementPointer) {
            stick.move(x,y,stickSize*.3f); axis(false,stick.x,stick.y); view.invalidate(); return true;
        }
        for (Button b:buttons) if (b.pointer == id) return true;
        if (id == bars.pointer()) {
            for (Button b:buttons) if (ability(b) && b.hit(x,y)) { pressAbility(b,id); break; }
            return true;
        }
        return pointerGestures.move(id,x,y);
    }
    private void axis(boolean right,float x,float y) {
        view.handleInputEvent(right?Binding.GAMEPAD_RIGHT_THUMB_RIGHT:Binding.GAMEPAD_LEFT_THUMB_RIGHT,true,x);
        view.handleInputEvent(right?Binding.GAMEPAD_RIGHT_THUMB_DOWN:Binding.GAMEPAD_LEFT_THUMB_DOWN,true,y);
    }
    public boolean up(int id) {
        boolean handled=false;
        if (id >= 0 && bars.pointer() == id) { bars.up(id,SystemClock.uptimeMillis()); publishBar(); handled=true; }
        if (id >= 0 && id == mouseTogglePointer) { mouseTogglePointer=-1; Log.i("WoWTouch", "button up " + Binding.NONE.name()); handled=true; }
        if (id == movementPointer && id >= 0) { movementPointer=-1; stick.release(); axis(false,0,0); handled=true; }
        for (Button b:buttons) if (b.pointer == id && id >= 0) { b.pointer=-1; Log.i("WoWTouch", "button up " + b.binding.name()); if (ability(b)) releaseAbility(b); else view.handleInputEvent(b.binding,false); handled=true; }
        if (pointerGestures.up(id)) handled = true;
        view.invalidate(); return handled;
    }
    public boolean cancel() {
        boolean active = movementPointer >= 0 || bars.armed() != null;
        view.removeCallbacks(barTimer);
        for (Runnable task:pendingAbilities.values()) view.removeCallbacks(task);
        for (Runnable task:pendingReleases.values()) view.removeCallbacks(task);
        pendingReleases.clear(); pendingAbilities.clear(); releasedAbilities.clear();
        for (Button b:sentAbilities) view.handleInputEvent(b.binding,false);
        sentAbilities.clear(); bars.reset(); publishBar();
        if (pointerGestures.cancel()) active = true;
        up(mouseTogglePointer);
        up(movementPointer);
        for (Button b:buttons) if (b.pointer >= 0) { up(b.pointer); active=true; }
        view.commitGamepadState();
        return active;
    }
}
