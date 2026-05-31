using Microsoft.UI;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using WinRT.Interop;

namespace CodePad.WinUI;

public sealed partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
        ApplyMicaTitleBar();
    }

    private void ApplyMicaTitleBar()
    {
        var hwnd = WindowNative.GetWindowHandle(this);
        var windowId = Win32Interop.GetWindowIdFromWindow(hwnd);
        var appWindow = AppWindow.GetFromWindowId(windowId);
        if (appWindow?.TitleBar == null)
        {
            return;
        }

        appWindow.TitleBar.ExtendsContentIntoTitleBar = true;
        appWindow.TitleBar.ButtonBackgroundColor = Colors.Transparent;
        appWindow.TitleBar.ButtonInactiveBackgroundColor = Colors.Transparent;

        if (TitleBarHost != null)
        {
            appWindow.TitleBar.SetDragRectangles(new[]
            {
                new Windows.Graphics.RectInt32(0, 0, 10000, (int)TitleBarHost.ActualHeight)
            });
        }
    }
}
