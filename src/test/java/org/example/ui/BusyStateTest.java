package org.example.ui;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The window must always come back from a background job. It used to call busy(false) only on the last line of the
 * result handler, so anything that threw on the way there left Extract and Open XML disabled for
 * good, which is indistinguishable from the application having died.
 * The frame is deliberately never shown here, which also keeps the error dialog of the failing handler off the screen
 * while the tests run.
 */
public class BusyStateTest {

    @Test
    public void aHandlerThatThrowsStillReleasesTheButtons() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        BioTriplExFrame[] holder = new BioTriplExFrame[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new BioTriplExFrame());
        BioTriplExFrame frame = holder[0];
        try {
            Method busy = BioTriplExFrame.class.getDeclaredMethod("busy", boolean.class, String.class);
            Method finish = BioTriplExFrame.class.getDeclaredMethod("finish", Runnable.class, String.class);
            busy.setAccessible(true);
            finish.setAccessible(true);
            SwingUtilities.invokeAndWait(() -> invoke(busy, frame, true, "working"));
            assertFalse(button(frame, "extractButton").isEnabled(), "a running job has to disable Extract");
            assertFalse(button(frame, "loadButton").isEnabled(), "a running job has to disable Open XML");
            assertTrue(button(frame, "cancelButton").isEnabled(), "a running job has to offer Cancel");
            SwingUtilities.invokeAndWait(() -> invoke(finish, frame, (Runnable) () -> {
                throw new IllegalStateException("handler blew up");
            }, "idle"));
            assertTrue(button(frame, "extractButton").isEnabled(), "Extract must come back after a failed handler");
            assertTrue(button(frame, "loadButton").isEnabled(), "Open XML must come back after a failed handler");
            assertFalse(button(frame, "cancelButton").isEnabled(), "Cancel must switch off again");
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    @Test
    public void aHandlerThatSucceedsAlsoReleasesTheButtons() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        BioTriplExFrame[] holder = new BioTriplExFrame[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new BioTriplExFrame());
        BioTriplExFrame frame = holder[0];
        try {
            Method busy = BioTriplExFrame.class.getDeclaredMethod("busy", boolean.class, String.class);
            Method finish = BioTriplExFrame.class.getDeclaredMethod("finish", Runnable.class, String.class);
            busy.setAccessible(true);
            finish.setAccessible(true);
            SwingUtilities.invokeAndWait(() -> invoke(busy, frame, true, "working"));
            SwingUtilities.invokeAndWait(() -> invoke(finish, frame, (Runnable) () -> {
            }, "idle"));
            assertTrue(button(frame, "extractButton").isEnabled());
            assertTrue(button(frame, "loadButton").isEnabled());
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    private static void invoke(Method method, Object target, Object... arguments) {
        try {
            method.invoke(target, arguments);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static JButton button(BioTriplExFrame frame, String name) throws Exception {
        Field field = BioTriplExFrame.class.getDeclaredField(name);
        field.setAccessible(true);
        return (JButton) field.get(frame);
    }
}
