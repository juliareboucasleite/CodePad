using System.Text;
using CodePad.WinUI.Models;

namespace CodePad.WinUI.Services;

/// <summary>
/// Mesmo formato DRAFTS_V2 do CodePad Java (%LOCALAPPDATA%\CodePad\drafts.dat).
/// </summary>
public sealed class DraftStore
{
    private const string DraftsFileName = "drafts.dat";
    private const string HeaderV2 = "DRAFTS_V2";

    public string DraftsFilePath
    {
        get
        {
            var baseDir = Environment.GetEnvironmentVariable("LOCALAPPDATA")
                ?? Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            return Path.Combine(baseDir, "CodePad", DraftsFileName);
        }
    }

    public IReadOnlyList<NoteDraft> Load()
    {
        var path = DraftsFilePath;
        if (!File.Exists(path))
        {
            return Array.Empty<NoteDraft>();
        }

        var lines = File.ReadAllLines(path, Encoding.UTF8);
        if (lines.Length == 0 || lines[0] != HeaderV2)
        {
            return Array.Empty<NoteDraft>();
        }

        var result = new List<NoteDraft>();
        var i = 1;
        while (i + 6 < lines.Length)
        {
            var title = FromB64(lines[i++]);
            var filePath = FromB64(lines[i++]);
            _ = lines[i++]; // codeMode — notas WinUI ignoram por agora
            _ = FromB64(lines[i++]); // language
            _ = FromB64(lines[i++]); // encoding
            _ = FromB64(lines[i++]); // lineEnding
            var content = FromB64(lines[i++]);
            if (i < lines.Length && lines[i] == "---")
            {
                i++;
            }

            if (string.IsNullOrEmpty(content) && string.IsNullOrEmpty(filePath))
            {
                continue;
            }

            result.Add(new NoteDraft
            {
                Title = string.IsNullOrWhiteSpace(title) ? "Sem Título" : title,
                FilePath = string.IsNullOrWhiteSpace(filePath) ? null : filePath,
                Content = content,
                IsDirty = false
            });
        }

        return result;
    }

    public void Save(IEnumerable<NoteDraft> drafts)
    {
        var entries = drafts
            .Where(d => !string.IsNullOrEmpty(d.Content) || !string.IsNullOrEmpty(d.FilePath))
            .ToList();

        var path = DraftsFilePath;
        if (entries.Count == 0)
        {
            if (File.Exists(path))
            {
                File.Delete(path);
            }
            return;
        }

        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var sb = new StringBuilder();
        sb.AppendLine(HeaderV2);
        foreach (var e in entries)
        {
            sb.AppendLine(ToB64(e.Title));
            sb.AppendLine(ToB64(e.FilePath ?? ""));
            sb.AppendLine("0"); // modo notas
            sb.AppendLine(ToB64("notes"));
            sb.AppendLine(ToB64("UTF8"));
            sb.AppendLine(ToB64("CRLF"));
            sb.AppendLine(ToB64(e.Content));
            sb.AppendLine("---");
        }

        File.WriteAllText(path, sb.ToString(), Encoding.UTF8);
    }

    private static string ToB64(string? value) =>
        Convert.ToBase64String(Encoding.UTF8.GetBytes(value ?? ""));

    private static string FromB64(string? line)
    {
        if (string.IsNullOrWhiteSpace(line))
        {
            return "";
        }
        try
        {
            return Encoding.UTF8.GetString(Convert.FromBase64String(line));
        }
        catch
        {
            return "";
        }
    }
}
