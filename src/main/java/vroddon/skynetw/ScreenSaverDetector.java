package vroddon.skynetw;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.BOOLByReference;
import com.sun.jna.platform.win32.WinUser;

public class ScreenSaverDetector {
    // Extend User32 interface to add the missing method for screensaver detection
    public interface ExtendedUser32 extends User32 {
        ExtendedUser32 INSTANCE = Native.load("user32", ExtendedUser32.class);

        boolean SystemParametersInfoA(int uiAction, int uiParam, BOOLByReference pvParam, int fWinIni);
    }

    // SPI constant for getting screensaver running status
    private static final int SPI_GETSCREENSAVERRUNNING = 0x0072;

    /**
     * Checks if the Windows screensaver is currently active
     * @return true if screensaver is running, false otherwise
     */
    public static boolean isScreensaverActive() {
        try {
            BOOLByReference isRunning = new BOOLByReference();
            boolean result = ExtendedUser32.INSTANCE.SystemParametersInfoA(
                SPI_GETSCREENSAVERRUNNING,
                0,
                isRunning,
                0
            );

            if (result) {
                return isRunning.getValue().booleanValue();
            }
        } catch (Exception e) {
            System.err.println("Error detecting screensaver status: " + e.getMessage());
        }

        // Fall back to activity-based detection if API call fails
        return false;
    }

    /**
     * Milliseconds since the last keyboard or mouse input of the user, system wide
     * (Windows GetLastInputInfo). It is not affected by the screensaver.
     * @return the idle time, or 0 (user considered active) if the API call fails
     */
    public static long getIdleMillis() {
        try {
            WinUser.LASTINPUTINFO info = new WinUser.LASTINPUTINFO();
            if (User32.INSTANCE.GetLastInputInfo(info)) {
                // Both values are 32 bit tick counters (they wrap every 49 days), hence the mask.
                return (Kernel32.INSTANCE.GetTickCount() - info.dwTime) & 0xFFFFFFFFL;
            }
        } catch (Exception e) {
            System.err.println("Error detecting idle time: " + e.getMessage());
        }
        return 0;
    }
}
