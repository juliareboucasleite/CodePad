package org.example.ui;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;
import javafx.stage.Stage;

/**
 * Ativa Mica/Acrylic do Windows 11 (blur do papel de parede, como o Explorador de Arquivos).
 */
public final class WindowsBackdrop {

    public enum Backdrop {
        /** Mica — superfícies principais (Explorador, Configurações). */
        MICA(2),
        /** Acrylic — mais translúcido / blur visível. */
        ACRYLIC(3),
        /** Desliga o efeito. */
        NONE(1);

        final int dwmsbtValue;

        Backdrop(int value) {
            this.dwmsbtValue = value;
        }
    }

    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private static final int DWMWA_SYSTEMBACKDROP_TYPE = 38;

    private interface Dwmapi extends StdCallLibrary {
        Dwmapi INSTANCE = Native.load("dwmapi", Dwmapi.class);

        int DwmSetWindowAttribute(WinDef.HWND hwnd, int dwAttribute, Memory pvAttribute, int cbAttribute);
    }

    private WindowsBackdrop() {
    }

    public static boolean isSupported() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    public static void apply(Stage stage, boolean darkMode, Backdrop backdrop) {
        if (!isSupported() || stage == null) {
            return;
        }
        Runnable task = () -> applyNow(stage, darkMode, backdrop);
        if (stage.isShowing()) {
            task.run();
        } else {
            stage.setOnShown(e -> task.run());
        }
    }

    private static void applyNow(Stage stage, boolean darkMode, Backdrop backdrop) {
        long hwnd = nativeHandle(stage);
        if (hwnd == 0L) {
            return;
        }
        WinDef.HWND hWnd = new WinDef.HWND(com.sun.jna.Pointer.createConstant(hwnd));
        setIntAttribute(hWnd, DWMWA_USE_IMMERSIVE_DARK_MODE, darkMode ? 1 : 0);
        setIntAttribute(hWnd, DWMWA_SYSTEMBACKDROP_TYPE, backdrop.dwmsbtValue);
    }

    private static void setIntAttribute(WinDef.HWND hwnd, int attribute, int value) {
        Memory mem = new Memory(4);
        mem.setInt(0, value);
        Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, mem, 4);
    }

    @SuppressWarnings("restriction")
    private static long nativeHandle(Stage stage) {
        try {
            Object peer = com.sun.javafx.stage.WindowHelper.getPeer(stage);
            if (peer instanceof com.sun.javafx.tk.quantum.WindowStage windowStage) {
                com.sun.glass.ui.Window platformWindow = windowStage.getPlatformWindow();
                java.lang.reflect.Method method = platformWindow.getClass().getMethod("getNativeHandle");
                Object handle = method.invoke(platformWindow);
                if (handle instanceof Long l) {
                    return l;
                }
                if (handle instanceof Number n) {
                    return n.longValue();
                }
            }
        } catch (ReflectiveOperationException | Error ignored) {
            // API interna indisponível
        }
        return 0L;
    }
}
