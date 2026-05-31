package org.example.ui;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

/**
 * Detecta tema claro/escuro do Windows (AppsUseLightTheme).
 */
public final class WindowsTheme {

    private static final String REG_PATH =
            "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";
    private static final String REG_VALUE = "AppsUseLightTheme";

    private WindowsTheme() {
    }

    public static boolean isAppsLightTheme() {
        if (!WindowsBackdrop.isSupported()) {
            return false;
        }
        try {
            if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, REG_PATH)) {
                return true;
            }
            int value = Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, REG_PATH, REG_VALUE);
            return value != 0;
        } catch (RuntimeException ex) {
            return true;
        }
    }
}
