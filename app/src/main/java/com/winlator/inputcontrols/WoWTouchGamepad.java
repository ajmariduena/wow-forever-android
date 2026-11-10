package com.winlator.inputcontrols;

import android.graphics.*;
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
    private int movementPointer = -1;
    private float restX, restY, stickSize, zoneRight, zoneTop;
    private int width, height, hingeTop = -1, hingeBottom = -1;
    private boolean floatingStick = true, cameraDrag = true;
    private float cameraSensitivity = 1;

    private static final class Button {
        final float x, y, size;
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
    public void readOptions(JSONObject data) {
        floatingStick = data.optBoolean("floatingStick", true);
        cameraDrag = data.optBoolean("cameraDrag", true);
        cameraSensitivity = (float)data.optDouble("cameraSensitivity", 1);
        if (!Float.isFinite(cameraSensitivity)) cameraSensitivity = 1;
        cameraSensitivity = Math.max(.1f, Math.min(5, cameraSensitivity));
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
        float faceSize = unit * (cover ? .15f : flex ? .095f : .11f);
        float small = unit * (cover ? .10f : flex ? .065f : .075f);
        float dp = unit * (cover ? .095f : flex ? .062f : .07f);
        stickSize = unit * (cover ? .38f : flex ? .22f : .27f);
        float[] values = cover ? new float[]{30,30,14,66,39,80,86,60,5,13,13,13,78,30,87,30,41,10,50,10,59,10}
            : flex ? new float[]{34,63,17,82,47,85,80,79,7,58,17,58,83,58,93,58,40,58,50,58,60,58}
            : new float[]{32,54,16,78,42,86,83,74,7,46,17,46,83,46,93,46,40,6,50,6,60,6};
        zoneRight = w*values[0]/100; zoneTop = py(values[1], flex, panelTop, panelScale);
        restX = w*values[2]/100; restY = py(values[3], flex, panelTop, panelScale);
        float fx = w*values[6]/100, fy = py(values[7], flex, panelTop, panelScale), gap = faceSize*.8f;
        add(fx,fy+gap,faceSize,"A","green",Binding.GAMEPAD_BUTTON_A);
        add(fx+gap,fy,faceSize,"B","crimson",Binding.GAMEPAD_BUTTON_B);
        add(fx-gap,fy,faceSize,"X","blue",Binding.GAMEPAD_BUTTON_X);
        add(fx,fy-gap,faceSize,"Y","gold",Binding.GAMEPAD_BUTTON_Y);
        String[] labels = {"▲","▶","▼","◀","LT","LB","RB","RT","VIEW","⌨","MENU"};
        Binding[] bindings = {Binding.GAMEPAD_DPAD_UP,Binding.GAMEPAD_DPAD_RIGHT,Binding.GAMEPAD_DPAD_DOWN,Binding.GAMEPAD_DPAD_LEFT,
            Binding.GAMEPAD_BUTTON_L2,Binding.GAMEPAD_BUTTON_L1,Binding.GAMEPAD_BUTTON_R1,Binding.GAMEPAD_BUTTON_R2,
            Binding.GAMEPAD_BUTTON_SELECT,Binding.SHOW_KEYBOARD,Binding.GAMEPAD_BUTTON_START};
        float dx = w*values[4]/100, dy = py(values[5],flex,panelTop,panelScale);
        for (int i=0;i<4;i++) add(dx+(i==1?dp*1.02f:i==3?-dp*1.02f:0),dy+(i==2?dp*1.02f:i==0?-dp*1.02f:0),dp,labels[i],null,bindings[i]);
        for (int i=4;i<11;i++) {
            int index = 8+(i-4)*2;
            add(w*values[index]/100,py(values[index+1],flex,panelTop,panelScale),small,labels[i],null,bindings[i]);
        }
        float[] extras = cover ? new float[]{25,36,94,44,68,10}
            : flex ? new float[]{28,67,94,69,71,58} : new float[]{27,60,94,58,70,6};
        add(w*extras[0]/100,py(extras[1],flex,panelTop,panelScale),small,"L3",null,Binding.GAMEPAD_BUTTON_L3);
        add(w*extras[2]/100,py(extras[3],flex,panelTop,panelScale),small,"R3",null,Binding.GAMEPAD_BUTTON_R3);
        add(w*extras[4]/100,py(extras[5],flex,panelTop,panelScale),small,"MOUSE",null,Binding.NONE);
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
            String state = (b.pointer >= 0 || (b.label.equals("MOUSE") && view.isWoWMouseMode())) ? "pressed" : "normal";
            if (b.gem != null) image(canvas,"gem-"+b.gem+"-"+state,b.x,b.y,b.size*.76f,255);
            image(canvas,"frame-"+(b.gem == null?"menu":"ability")+"-"+state,b.x,b.y,b.size,255);
            if (b.label.equals("MOUSE")) { image(canvas,"mouse",b.x,b.y,b.size*.4f,255); continue; }
            if (b.binding == Binding.SHOW_KEYBOARD) { image(canvas,"keyboard",b.x,b.y,b.size*.4f,255); continue; }
            paint.setColor(b.gem == null ? 0xfff0dca6 : 0xfffff7e0);
            paint.setTypeface(Typeface.create(b.gem == null ? "sans-serif" : "serif",Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(b.size*(b.gem == null ? .25f : .42f));
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
        for (Button b : buttons) if (b.binding == binding) return new RectF(b.x-b.size/2,b.y-b.size/2,b.x+b.size/2,b.y+b.size/2);
        return null;
    }
    public boolean down(int id,float x,float y) {
        layout();
        for (Button b:buttons) if (b.hit(x,y)) {
            if (b.label.equals("MOUSE")) { view.setWoWMouseMode(!view.isWoWMouseMode()); return true; }
            if (b.pointer < 0) { b.pointer=id; view.handleInputEvent(b.binding,true); view.invalidate(); }
            return true;
        }
        boolean inGame = (hingeTop < 0 || y < hingeTop) && view.isWoWGamePoint(x, y);
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
        return pointerGestures.move(id,x,y);
    }
    private void axis(boolean right,float x,float y) {
        view.handleInputEvent(right?Binding.GAMEPAD_RIGHT_THUMB_RIGHT:Binding.GAMEPAD_LEFT_THUMB_RIGHT,true,x);
        view.handleInputEvent(right?Binding.GAMEPAD_RIGHT_THUMB_DOWN:Binding.GAMEPAD_LEFT_THUMB_DOWN,true,y);
    }
    public boolean up(int id) {
        boolean handled=false;
        if (id == movementPointer && id >= 0) { movementPointer=-1; stick.release(); axis(false,0,0); handled=true; }
        for (Button b:buttons) if (b.pointer == id && id >= 0) { b.pointer=-1; view.handleInputEvent(b.binding,false); handled=true; }
        if (pointerGestures.up(id)) handled = true;
        view.invalidate(); return handled;
    }
    public boolean cancel() {
        boolean active = movementPointer >= 0;
        if (pointerGestures.cancel()) active = true;
        up(movementPointer);
        for (Button b:buttons) if (b.pointer >= 0) { up(b.pointer); active=true; }
        return active;
    }
}
