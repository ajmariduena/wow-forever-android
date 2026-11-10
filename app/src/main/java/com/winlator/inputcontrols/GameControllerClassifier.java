package com.winlator.inputcontrols;

import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

public final class GameControllerClassifier {
    private GameControllerClassifier() {}

    public static boolean isGameController(InputDevice device) {
        if (device == null) return false;

        // Built-in handheld controls may be virtual; capabilities determine acceptance.
        boolean isGamepad = device.supportsSource(InputDevice.SOURCE_GAMEPAD);
        boolean isJoystick = device.supportsSource(InputDevice.SOURCE_JOYSTICK);

        // Composite keyboards can claim joystick sources but expose only mouse axes.
        // Query both controller sources because drivers attach sticks to either one.
        boolean hasAxes =
                hasControllerAxis(device, MotionEvent.AXIS_X) ||
                        hasControllerAxis(device, MotionEvent.AXIS_Y);

        boolean[] hasGamepadKeysArray = device.hasKeys(
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.KEYCODE_BUTTON_Y
        );

        boolean hasGamepadKeys = false;
        for (boolean hasKey : hasGamepadKeysArray) {
            if (hasKey) {
                hasGamepadKeys = true;
                break;
            }
        }

        boolean accepted = (isGamepad && hasGamepadKeys) || (isJoystick && hasAxes);
        String reason = isGamepad && hasGamepadKeys ? "gamepad buttons"
                : isJoystick && hasAxes ? "controller axes"
                : !isGamepad && !isJoystick ? "no controller source"
                : "no controller buttons or axes";
        Log.i("GameControllerClassifier", "name=" + device.getName()
                + " vid=" + device.getVendorId() + " pid=" + device.getProductId()
                + " sources=0x" + Integer.toHexString(device.getSources())
                + " " + (accepted ? "accepted" : "rejected") + " reason=" + reason);
        return accepted;
    }

    private static boolean hasControllerAxis(InputDevice device, int axis) {
        return device.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK) != null ||
                device.getMotionRange(axis, InputDevice.SOURCE_GAMEPAD) != null;
    }
}
