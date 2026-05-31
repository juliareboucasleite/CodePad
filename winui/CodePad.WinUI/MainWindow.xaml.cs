using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using CodePad.WinUI.Models;
using CodePad.WinUI.Services;
using Windows.Storage;
using Windows.Storage.Pickers;
using WinRT.Interop;

namespace CodePad.WinUI;

public sealed partial class MainWindow : Window
{
    private readonly DraftStore _drafts = new();
    private readonly DispatcherTimer _autosaveTimer;
    private bool _draftsDirty;
    private int _untitled = 1;
    private TextBlock? _caretText;
    private bool _initialized;

    public MainWindow()
    {
        InitializeComponent();
        SystemBackdrop = new MicaBackdrop();

        _autosaveTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(30) };
        _autosaveTimer.Tick += (_, _) => { if (_draftsDirty) PersistDrafts(); };
        _autosaveTimer.Start();

        WireChrome();
        Activated += MainWindow_Activated;
        Closed += MainWindow_Closed;
    }

    private void MainWindow_Activated(object sender, WindowActivatedEventArgs args)
    {
        if (_initialized || args.WindowActivationState == WindowActivationState.Deactivated)
        {
            return;
        }
        _initialized = true;
        MainWindow_Loaded();
    }

    private void WireChrome()
    {
        var file = new MenuBarItem { Title = "Arquivo" };
        var newTab = new MenuFlyoutItem { Text = "Nova aba" };
        newTab.Click += (_, _) => AddNewTab();
        var open = new MenuFlyoutItem { Text = "Abrir…" };
        open.Click += async (_, _) => await OpenFileAsync();
        var save = new MenuFlyoutItem { Text = "Salvar" };
        save.Click += async (_, _) => await SaveAsync();
        var saveAs = new MenuFlyoutItem { Text = "Salvar como…" };
        saveAs.Click += async (_, _) => await SaveAsAsync();
        file.Items.Add(newTab);
        file.Items.Add(open);
        file.Items.Add(new MenuFlyoutSeparator());
        file.Items.Add(save);
        file.Items.Add(saveAs);
        MainMenu.Items.Add(file);

        NotesTabView.AddTabButtonClick += (_, _) => AddNewTab();
        NotesTabView.TabCloseRequested += (_, args) =>
        {
            NotesTabView.TabItems.Remove(args.Tab);
            EnsureOneTab();
            _draftsDirty = true;
        };
        NotesTabView.SelectionChanged += (_, _) =>
        {
            if (CurrentEditor() is TextBox box)
            {
                UpdateCaretFrom(box);
            }
        };

        var statusRow = new Grid { Padding = new Thickness(12, 6, 12, 6) };
        statusRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        statusRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        Grid.SetRow(statusRow, 2);
        Grid.SetColumnSpan(statusRow, 1);
        ((Grid)Content).Children.Remove(StatusText);
        statusRow.Children.Add(StatusText);
        _caretText = new TextBlock { Opacity = 0.75, VerticalAlignment = VerticalAlignment.Center };
        Grid.SetColumn(_caretText, 1);
        statusRow.Children.Add(_caretText);
        ((Grid)Content).Children.Add(statusRow);
    }

    private void MainWindow_Loaded()
    {
        var loaded = _drafts.Load();
        if (loaded.Count > 0)
        {
            foreach (var draft in loaded)
            {
                AddTab(draft);
            }
            NotesTabView.SelectedIndex = 0;
            UpdateStatus("Rascunhos restaurados.");
        }
        else
        {
            AddTab(new NoteDraft { Title = $"Sem Título {_untitled++}" });
        }
    }

    private void MainWindow_Closed(object sender, WindowEventArgs args) => PersistDrafts();

    private void AddNewTab()
    {
        AddTab(new NoteDraft { Title = $"Sem Título {_untitled++}" });
        UpdateStatus("Nova aba.");
    }

    private TextBox? CurrentEditor() =>
        (NotesTabView.SelectedItem as TabViewItem)?.Content as TextBox;

    private NoteDraft? CurrentDraft() =>
        (NotesTabView.SelectedItem as TabViewItem)?.Tag as NoteDraft;

    private IEnumerable<NoteDraft> AllDrafts()
    {
        foreach (var item in NotesTabView.TabItems)
        {
            if (item is TabViewItem { Tag: NoteDraft draft, Content: TextBox box })
            {
                draft.Content = box.Text;
                yield return draft;
            }
        }
    }

    private void PersistDrafts()
    {
        _drafts.Save(AllDrafts());
        _draftsDirty = false;
    }

    private TabViewItem AddTab(NoteDraft draft)
    {
        var editor = new TextBox
        {
            AcceptsReturn = true,
            TextWrapping = TextWrapping.Wrap,
            PlaceholderText = "Escreva as suas notas…",
            FontSize = 14,
            BorderThickness = new Thickness(0),
            Text = draft.Content
        };

        editor.TextChanged += (_, _) =>
        {
            draft.Content = editor.Text;
            draft.IsDirty = true;
            _draftsDirty = true;
            if (NotesTabView.SelectedItem is TabViewItem tab && tab.Tag == draft)
            {
                tab.Header = draft.Title + " *";
            }
            UpdateCaretFrom(editor);
        };
        editor.KeyUp += (_, _) => UpdateCaretFrom(editor);

        var tab = new TabViewItem
        {
            Header = draft.Title,
            Content = editor,
            Tag = draft
        };
        NotesTabView.TabItems.Add(tab);
        NotesTabView.SelectedItem = tab;
        return tab;
    }

    private async Task OpenFileAsync()
    {
        var picker = new FileOpenPicker();
        InitPicker(picker);
        picker.FileTypeFilter.Add(".txt");
        picker.FileTypeFilter.Add(".md");
        var file = await picker.PickSingleFileAsync();
        if (file == null) return;
        var text = await FileIO.ReadTextAsync(file);
        AddTab(new NoteDraft { Title = file.Name, FilePath = file.Path, Content = text });
        UpdateStatus($"Aberto: {file.Name}");
    }

    private async Task SaveAsync()
    {
        var editor = CurrentEditor();
        var draft = CurrentDraft();
        if (editor == null || draft == null) return;
        if (string.IsNullOrEmpty(draft.FilePath))
        {
            await SaveAsAsync();
            return;
        }
        await File.WriteAllTextAsync(draft.FilePath, editor.Text);
        draft.IsDirty = false;
        _draftsDirty = true;
        RefreshHeader();
        UpdateStatus("Salvo.");
    }

    private async Task SaveAsAsync()
    {
        var editor = CurrentEditor();
        var draft = CurrentDraft();
        if (editor == null || draft == null) return;
        var picker = new FileSavePicker();
        InitPicker(picker);
        picker.SuggestedFileName = draft.Title;
        picker.FileTypeChoices.Add("Texto", new List<string> { ".txt", ".md" });
        var file = await picker.PickSaveFileAsync();
        if (file == null) return;
        await FileIO.WriteTextAsync(file, editor.Text);
        draft.FilePath = file.Path;
        draft.Title = file.Name;
        draft.IsDirty = false;
        _draftsDirty = true;
        RefreshHeader();
        UpdateStatus($"Salvo: {file.Name}");
    }

    private void EnsureOneTab()
    {
        if (NotesTabView.TabItems.Count == 0)
        {
            AddTab(new NoteDraft { Title = $"Sem Título {_untitled++}" });
        }
    }

    private void RefreshHeader()
    {
        if (NotesTabView.SelectedItem is TabViewItem tab && tab.Tag is NoteDraft d)
        {
            tab.Header = d.Title + (d.IsDirty ? " *" : "");
        }
    }

    private void UpdateStatus(string msg) => StatusText.Text = msg;

    private void UpdateCaretFrom(TextBox editor)
    {
        if (_caretText == null) return;
        var text = editor.Text ?? "";
        var pos = Math.Clamp(editor.SelectionStart, 0, text.Length);
        var line = 1;
        var col = 1;
        for (var i = 0; i < pos && i < text.Length; i++)
        {
            if (text[i] == '\n') { line++; col = 1; } else col++;
        }
        _caretText.Text = $"Ln {line}, Col {col}";
    }

    private void InitPicker(object picker)
    {
        var hwnd = WindowNative.GetWindowHandle(this);
        InitializeWithWindow.Initialize(picker, hwnd);
    }
}
