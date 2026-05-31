package org.example.ui;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.animation.PauseTransition;

/**
 * Ativa Mica/Acrylic do Windows 11 (blur do papel de parede, como o Explorador de Arquivos).
 */
public final class WindowsBackdrop {

    public enum Backdrop {
        /** Mica — superfícies principais (Explorador, Configurações). */
        MICA(2),
        /** Mica Alt — abas / janelas secundárias. */
        MICA_ALT(4),
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

    /**
     * Aplica o backdrop com novas tentativas até o handle nativo existir (JavaFX demora a criar HWND).
     */
    public static void apply(Stage stage, boolean darkMode, Backdrop backdrop) {
        if (!isSupported() || stage == null) {
            return;
        }
        Runnable task = () -> scheduleApply(stage, darkMode, backdrop, 0);
        if (stage.isShowing()) {
            task.run();
        } else {
            stage.setOnShown(e -> task.run());
        }
    }

    public static boolean hasNativeHandle(Stage stage) {
        return nativeHandle(stage) != 0L;
    }

    public static boolean applyNow(Stage stage, boolean darkMode, Backdrop backdrop) {
        if (!isSupported() || stage == null) {
            return false;
        }
        long hwnd = nativeHandle(stage);
        if (hwnd == 0L) {
            return false;
        }
        WinDef.HWND hWnd = new WinDef.HWND(com.sun.jna.Pointer.createConstant(hwnd));
        setIntAttribute(hWnd, DWMWA_USE_IMMERSIVE_DARK_MODE, darkMode ? 1 : 0);
        int hr = setIntAttribute(hWnd, DWMWA_SYSTEMBACKDROP_TYPE, backdrop.dwmsbtValue);
        if (hr != 0 && backdrop == Backdrop.MICA) {
            setIntAttribute(hWnd, DWMWA_SYSTEMBACKDROP_TYPE, Backdrop.MICA_ALT.dwmsbtValue);
        }
        return true;
    }

    private static void scheduleApply(Stage stage, boolean darkMode, Backdrop backdrop, int attempt) {
        if (applyNow(stage, darkMode, backdrop)) {
            return;
        }
        if (attempt >= 8) {
            return;
        }
        PauseTransition pause = new PauseTransition(Duration.millis(80L * (attempt + 1)));
        pause.setOnFinished(e -> scheduleApply(stage, darkMode, backdrop, attempt + 1));
        pause.play();
    }

    private static int setIntAttribute(WinDef.HWND hwnd, int attribute, int value) {
        Memory mem = new Memory(4);
        mem.setInt(0, value);
        return Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, mem, 4);
    }

    @SuppressWarnings("restriction")
    private static long nativeHandle(Stage stage) {
        try {
            Object peer = com.sun.javafx.stage.WindowHelper.getPeer(stage);
            if (peer instanceof com.sun.javafx.tk.quantum.WindowStage windowStage) {
                com.sun.glass.ui.Window platformWindow = windowStage.getPlatformWindow();
                return handleFromPlatformWindow(platformWindow);
            }
        } catch (Exception | Error ignored) {
            // API interna indisponível
        }
        return 0L;
    }

    private static long handleFromPlatformWindow(com.sun.glass.ui.Window platformWindow) {
        if (platformWindow == null) {
            return 0L;
        }
        try {
            java.lang.reflect.Method method = platformWindow.getClass().getMethod("getNativeHandle");
            Object handle = method.invoke(platformWindow);
            if (handle instanceof Long l) {
                return l;
            }
            if (handle instanceof Number n) {
                return n.longValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        String className = platformWindow.getClass().getName();
        if (className.contains("Win")) {
            try {
                java.lang.reflect.Field field = platformWindow.getClass().getDeclaredField("hwnd");
                field.setAccessible(true);
                Object hwnd = field.get(platformWindow);
                if (hwnd instanceof Number n) {
                    return n.longValue();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return 0L;
    }
}
