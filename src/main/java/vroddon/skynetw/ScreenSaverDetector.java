package vroddon.skynetw;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.BOOLByReference;

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
}