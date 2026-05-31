package org.example.services;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consulta releases no GitHub e baixa o instalador Windows (EXE).
 */
public class UpdateService {

    public static final String GITHUB_RELEASES_URL =
            "https://github.com/juliareboucasleite/CodePad/releases";
    private static final String UPDATE_API =
            "https://api.github.com/repos/juliareboucasleite/CodePad/releases/latest";
    private static final String USER_AGENT = "CodePad-Updater";

    public record ReleaseAsset(String name, String downloadUrl) {
    }

    public record ReleaseInfo(
            String tag,
            String releaseUrl,
            ReleaseAsset asset,
            String releaseNotes
    ) {
        public String assetName() {
            return asset == null ? null : asset.name();
        }

        public String downloadUrl() {
            return asset == null ? null : asset.downloadUrl();
        }
    }

    public enum Platform {
        WINDOWS, LINUX, MAC, UNKNOWN
    }

    @FunctionalInterface
    public interface DownloadProgress {
        void onProgress(long downloaded, long total);
    }

    private final HttpClient client;

    public UpdateService() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public UpdateService(HttpClient client) {
        this.client = client;
    }

    public static Platform detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return Platform.WINDOWS;
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return Platform.MAC;
        }
        if (os.contains("linux") || os.contains("nix")) {
            return Platform.LINUX;
        }
        return Platform.UNKNOWN;
    }

    public Optional<ReleaseInfo> fetchLatestRelease() throws IOException, InterruptedException {
        return fetchLatestRelease(null);
    }

    public Optional<ReleaseInfo> fetchLatestRelease(Platform forcePlatform)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(UPDATE_API))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("GitHub API respondeu " + res.statusCode());
        }
        return parseReleaseResponse(res.body(), forcePlatform);
    }

    public Optional<ReleaseInfo> findUpdate(String currentVersion) throws IOException, InterruptedException {
        Optional<ReleaseInfo> latest = fetchLatestRelease();
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        ReleaseInfo info = latest.get();
        if (compareVersions(info.tag(), currentVersion) > 0) {
            return Optional.of(info);
        }
        return Optional.empty();
    }

    public Optional<ReleaseAsset> findAssetForPlatform(List<ReleaseAsset> assets, Platform platform) {
        if (assets == null || assets.isEmpty()) {
            return Optional.empty();
        }
        List<String> priorities = assetPriorities(platform);
        Map<String, ReleaseAsset> byLowerName = new LinkedHashMap<>();
        for (ReleaseAsset asset : assets) {
            if (asset.name() != null && asset.downloadUrl() != null) {
                byLowerName.put(asset.name().toLowerCase(Locale.ROOT), asset);
            }
        }
        for (String candidate : priorities) {
            ReleaseAsset hit = byLowerName.get(candidate.toLowerCase(Locale.ROOT));
            if (hit != null) {
                return Optional.of(hit);
            }
        }
        for (String candidate : priorities) {
            String suffix = candidate.contains(".") ? candidate.substring(candidate.lastIndexOf('.')) : candidate;
            for (ReleaseAsset asset : assets) {
                String name = asset.name();
                if (name == null || isExcludedAsset(name, platform)) {
                    continue;
                }
                if (name.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) {
                    return Optional.of(asset);
                }
            }
        }
        return Optional.empty();
    }

    public Path defaultDownloadDir() throws IOException {
        Path base = Path.of(System.getProperty("user.home"), "Downloads", "CodePad");
        Files.createDirectories(base);
        return base;
    }

    public Path downloadAsset(ReleaseInfo release, Path targetDir, DownloadProgress progress)
            throws IOException, InterruptedException {
        if (release == null || release.asset() == null || release.downloadUrl() == null) {
            throw new IOException("Release sem arquivo para download.");
        }
        Files.createDirectories(targetDir);
        String fileName = sanitizeFileName(release.asset().name());
        if (fileName.isBlank()) {
            fileName = "CodePad-update.bin";
        }
        Path target = targetDir.resolve(fileName);
        return downloadUrl(release.downloadUrl(), target, progress);
    }

    public Path downloadUrl(String url, Path target, DownloadProgress progress)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(15))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() >= 400) {
            throw new IOException("Download falhou (HTTP " + res.statusCode() + ")");
        }
        long total = res.headers().firstValueAsLong("Content-Length").orElse(-1L);
        try (InputStream in = res.body();
             OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
                downloaded += read;
                if (progress != null) {
                    progress.onProgress(downloaded, total);
                }
            }
        }
        return target;
    }

    public static int compareVersions(String a, String b) {
        List<Integer> va = parseVersionNumbers(a);
        List<Integer> vb = parseVersionNumbers(b);
        int max = Math.max(va.size(), vb.size());
        for (int i = 0; i < max; i++) {
            int ai = i < va.size() ? va.get(i) : 0;
            int bi = i < vb.size() ? vb.get(i) : 0;
            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
        }
        return 0;
    }

    public static String platformLabel(Platform platform) {
        return switch (platform) {
            case WINDOWS -> "Windows (.exe)";
            case LINUX -> "Linux";
            case MAC -> "macOS";
            case UNKNOWN -> "sistema atual";
        };
    }

    private Optional<ReleaseInfo> parseReleaseResponse(String json, Platform forcePlatform) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        String tag = extractJsonString(json, "tag_name");
        String url = extractJsonString(json, "html_url");
        String notes = extractJsonString(json, "body");
        List<ReleaseAsset> assets = parseAssets(json);
        if (tag == null || url == null) {
            return Optional.empty();
        }
        Platform platform = forcePlatform != null ? forcePlatform : detectPlatform();
        Optional<ReleaseAsset> asset = findAssetForPlatform(assets, platform);
        if (asset.isEmpty()) {
            asset = assets.stream()
                    .filter(a -> a.name() != null && !isExcludedAsset(a.name(), platform))
                    .findFirst();
        }
        if (asset.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ReleaseInfo(tag, url, asset.get(), notes));
    }

    private static boolean isExcludedAsset(String name, Platform platform) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (platform == Platform.WINDOWS && lower.endsWith(".apk")) {
            return true;
        }
        return false;
    }

    private static List<String> assetPriorities(Platform platform) {
        return switch (platform) {
            case WINDOWS -> List.of(
                    "CodePad.exe", "CodePad-Setup.exe", "CodePad-setup.exe", "codepad.exe");
            case LINUX -> List.of(
                    "CodePad.deb", "codepad.deb", "CodePad.AppImage", "CodePad.tar.gz");
            case MAC -> List.of(
                    "CodePad.dmg", "CodePad.pkg", "CodePad-mac.dmg");
            case UNKNOWN -> List.of(
                    "CodePad.exe", "CodePad.deb", "CodePad.dmg");
        };
    }

    private static List<ReleaseAsset> parseAssets(String json) {
        List<ReleaseAsset> assets = new ArrayList<>();
        Pattern block = Pattern.compile(
                "\"name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"[\\s\\S]*?"
                        + "\"browser_download_url\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher m = block.matcher(json);
        while (m.find()) {
            String name = unescapeJson(m.group(1));
            String download = unescapeJson(m.group(2));
            if (name != null && download != null) {
                assets.add(new ReleaseAsset(name, download));
            }
        }
        return assets;
    }

    private static List<Integer> parseVersionNumbers(String v) {
        List<Integer> out = new ArrayList<>();
        if (v == null) {
            return out;
        }
        Matcher m = Pattern.compile("\\d+").matcher(v);
        while (m.find()) {
            try {
                out.add(Integer.parseInt(m.group()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static String extractJsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([\\s\\S]*?)\"\\s*,");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([\\s\\S]*?)\"\\s*\\}");
            m = p.matcher(json);
            if (!m.find()) {
                return null;
            }
        }
        return unescapeJson(m.group(1));
    }

    private static String unescapeJson(String s) {
        if (s == null) {
            return null;
        }
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String sanitizeFileName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
