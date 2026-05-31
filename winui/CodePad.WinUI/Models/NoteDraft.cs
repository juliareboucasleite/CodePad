namespace CodePad.WinUI.Models;

public sealed class NoteDraft
{
    public string Title { get; set; } = "Sem Título";
    public string? FilePath { get; set; }
    public string Content { get; set; } = "";
    public bool IsDirty { get; set; }
}
