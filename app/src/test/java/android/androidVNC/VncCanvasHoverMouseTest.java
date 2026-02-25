package android.androidVNC;

import android.content.Context;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import com.vectras.qemu.Config;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class VncCanvasHoverMouseTest {

    @Test
    public void hoverMoveWithHistoryUsesHistoricalCoordinatesAndNoClickMask() {
        boolean previousHistoricalEvents = Config.processMouseHistoricalEvents;
        Config.MouseMode previousMouseMode = Config.mouseMode;

        try {
            Config.processMouseHistoricalEvents = true;
            Config.mouseMode = Config.MouseMode.External;

            Context context = RuntimeEnvironment.getApplication();
            RecordingVncCanvas canvas = new RecordingVncCanvas(context);
            VncCanvas.VNCGenericMotionListener_API12 listener = canvas.new VNCGenericMotionListener_API12();

            long downTime = SystemClock.uptimeMillis();
            MotionEvent event = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_HOVER_MOVE, 100f, 200f, 0);
            event.setSource(InputDevice.SOURCE_MOUSE);
            event.addBatch(downTime + 16, 110f, 210f, 0.8f, 1.0f, 0);

            boolean handled = listener.onGenericMotion(canvas, event);

            assertTrue(handled);
            assertEquals(2, canvas.pointerCalls.size());

            PointerCall historicalCall = canvas.pointerCalls.get(0);
            assertEquals(100, historicalCall.x);
            assertEquals(200, historicalCall.y);
            assertEquals(MotionEvent.ACTION_HOVER_MOVE, historicalCall.action);
            assertFalse(historicalCall.mouseIsDown);
            assertFalse(historicalCall.useRightButton);
            assertFalse(historicalCall.useMiddleButton);
            assertFalse(historicalCall.scrollUp);

            PointerCall currentCall = canvas.pointerCalls.get(1);
            assertEquals(110, currentCall.x);
            assertEquals(210, currentCall.y);
            assertEquals(MotionEvent.ACTION_HOVER_MOVE, currentCall.action);
            assertFalse(currentCall.mouseIsDown);
            assertFalse(currentCall.useRightButton);
            assertFalse(currentCall.useMiddleButton);
            assertFalse(currentCall.scrollUp);

            event.recycle();
        } finally {
            Config.processMouseHistoricalEvents = previousHistoricalEvents;
            Config.mouseMode = previousMouseMode;
        }
    }

    private static class RecordingVncCanvas extends VncCanvas {
        final List<PointerCall> pointerCalls = new ArrayList<>();

        RecordingVncCanvas(Context context) {
            super(context);
        }

        @Override
        boolean processPointerEvent(int x, int y, int action, int modifiers, boolean mouseIsDown,
                                    boolean useRightButton, boolean useMiddleButton, boolean scrollUp) {
            pointerCalls.add(new PointerCall(x, y, action, mouseIsDown, useRightButton, useMiddleButton, scrollUp));
            return true;
        }
    }

    private static class PointerCall {
        final int x;
        final int y;
        final int action;
        final boolean mouseIsDown;
        final boolean useRightButton;
        final boolean useMiddleButton;
        final boolean scrollUp;

        PointerCall(int x, int y, int action, boolean mouseIsDown, boolean useRightButton,
                    boolean useMiddleButton, boolean scrollUp) {
            this.x = x;
            this.y = y;
            this.action = action;
            this.mouseIsDown = mouseIsDown;
            this.useRightButton = useRightButton;
            this.useMiddleButton = useMiddleButton;
            this.scrollUp = scrollUp;
        }
    }
}
