package org.example.controllers;

import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.geometry.Point2D;
import org.example.services.UpdateService;
import org.example.ui.WindowsBackdrop;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.reactfx.Subscription;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditorController {

    private static final String THEME_LIGHT = "/org/example/editor-light.css";
    private static final String THEME_DARK = "/org/example/editor-dark.css";
    private static final String THEME_MICA = "/org/example/editor-mica.css";
    private static final String APP_NAME = "CodePad";
    private static final String DRAFTS_DIR = "CodePad";
    private static final String LEGACY_DRAFTS_DIR = "CodePad";
    private static final String DRAFTS_FILE = "drafts.dat";
    private static final int AUTO_SAVE_SECONDS = 30;
    private static final double BASE_FONT_SIZE = 14.0;
    private static final double MIN_FONT_SIZE = 10.0;
    private static final double MAX_FONT_SIZE = 24.0;
    private static final String DEFAULT_CODE_FONT = "JetBrains Mono";
    private static final String DEFAULT_TEXT_FONT = "Segoe UI";
    private static final List<String> CODE_FONT_CHOICES = List.of(
            "JetBrains Mono", "Fira Code", "Consolas", "Monaco", "Courier New", "Cascadia Mono");
    private static final List<String> TEXT_FONT_CHOICES = List.of(
            "Segoe UI", "Montserrat", "Poppins", "Tahoma", "Arial", "Verdana");
    private static final DateTimeFormatter INSERT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));

    private static final String HELP_UPDATE_TEXT = """
            Como os usuários atualizam o CodePad

            Publicação (você, desenvolvedora):
            • Crie uma release no GitHub com a versão (ex.: v1.2.3).
            • Anexe o instalador Windows (CodePad.exe) e/ou o APK (CodePad.apk).
            • Os nomes dos arquivos devem conter .exe ou .apk para o app encontrá-los.

            Windows (usuários com .exe):
            • Ao abrir o app, ele verifica atualizações automaticamente.
            • Ajuda → Verificar atualizações → Baixar → arquivo em Downloads\\CodePad.
            • Execute o instalador; pode instalar por cima da versão anterior.

            Android (usuários com .apk):
            • Ajuda → Baixar APK (Android) ou use a release no GitHub.
            • Instale o APK por cima do app antigo (mesma assinatura).
            • Se necessário, permita instalar apps de fontes desconhecidas.

            Sem internet ou falha no download:
            • Ajuda → Ver todas as releases e baixe manualmente no navegador.
            """;
    private static final byte[] BOM_UTF8 = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] BOM_UTF16_LE = new byte[]{(byte) 0xFF, (byte) 0xFE};
    private static final byte[] BOM_UTF16_BE = new byte[]{(byte) 0xFE, (byte) 0xFF};

    private enum FileEncoding {
        ANSI("ANSI", java.nio.charset.Charset.defaultCharset(), null),
        UTF8("UTF-8", java.nio.charset.StandardCharsets.UTF_8, null),
        UTF8_BOM("UTF-8 BOM", java.nio.charset.StandardCharsets.UTF_8, BOM_UTF8),
        UTF16_LE_BOM("UTF-16 LE BOM", java.nio.charset.StandardCharsets.UTF_16LE, BOM_UTF16_LE),
        UTF16_BE_BOM("UTF-16 BE BOM", java.nio.charset.StandardCharsets.UTF_16BE, BOM_UTF16_BE);

        final String label;
        final java.nio.charset.Charset charset;
        final byte[] bom;

        FileEncoding(String label, java.nio.charset.Charset charset, byte[] bom) {
            this.label = label;
            this.charset = charset;
            this.bom = bom;
        }
    }

    private enum LineEnding {
        CRLF("Windows (CRLF)", "\r\n"),
        LF("Unix (LF)", "\n"),
        CR("Mac (CR)", "\r");

        final String label;
        final String sequence;

        LineEnding(String label, String sequence) {
            this.label = label;
            this.sequence = sequence;
        }
    }
    private static final String[] KEYWORDS = new String[]{
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while"
    };

    private static final String[] KEYWORDS_JS = new String[]{
            "break", "case", "catch", "class", "const", "continue", "debugger", "default",
            "delete", "do", "else", "export", "extends", "finally", "for", "function", "if",
            "import", "in", "instanceof", "let", "new", "return", "super", "switch", "this",
            "throw", "try", "typeof", "var", "void", "while", "with", "yield", "await"
    };

    private static final String[] KEYWORDS_PY = new String[]{
            "and", "as", "assert", "break", "class", "continue", "def", "del", "elif", "else",
            "except", "False", "finally", "for", "from", "global", "if", "import", "in", "is",
            "lambda", "None", "nonlocal", "not", "or", "pass", "raise", "return", "True",
            "try", "while", "with", "yield"
    };

    private static final Pattern PATTERN_JAVA = buildPattern(KEYWORDS,
            "//[^\\n]*|/\\*(.|\\R)*?\\*/",
            "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'");
    private static final Pattern PATTERN_JS = buildPattern(KEYWORDS_JS,
            "//[^\\n]*|/\\*(.|\\R)*?\\*/",
            "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'|`([^`\\\\]|\\\\.)*`");
    private static final Pattern PATTERN_PY = buildPattern(KEYWORDS_PY,
            "#[^\\n]*",
            "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'");

    /** Destaque leve para anotações, tarefas e datas no modo notas. */
    private static final Pattern PATTERN_NOTES = Pattern.compile(
            "(?m)(?<HEADING>^#{1,3}\\s+.+$)"
                    + "|(?<CHECKBOX>\\[\\s?[xX]?\\s?\\])"
                    + "|(?<NOTEDATE>\\b\\d{2}/\\d{2}/\\d{4}\\b)"
                    + "|(?<TODO>\\b(?:TODO|FIXME|IMPORTANTE|URGENTE)\\b)"
                    + "|(?<TAG>@[\\w]+)"
    );

    private static final String CURSOR = "${cursor}";
    private static final Map<String, String> SNIPPETS = new LinkedHashMap<>();

    static {
        SNIPPETS.put("psvm", "public static void main(String[] args) {\n    " + CURSOR + "\n}");
        SNIPPETS.put("sout", "System.out.println(" + CURSOR + ");");
        SNIPPETS.put("fori", "for (int i = 0; i < " + CURSOR + "; i++) {\n    \n}");
        SNIPPETS.put("if", "if (" + CURSOR + ") {\n    \n}");
    }

    @FXML
    private BorderPane root;
    @FXML
    private TabPane tabPane;
    @FXML
    private Label lblStatus;
    @FXML
    private Label lblStats;
    @FXML
    private Label lblSelection;
    @FXML
    private Label lblLineCol;
    @FXML
    private Label lblEol;
    @FXML
    private Label lblEncoding;
    @FXML
    private Label lblZoom;
    @FXML
    private VBox sidePanel;
    @FXML
    private ComboBox<String> cbCodeFont;
    @FXML
    private ComboBox<String> cbTextFont;
    @FXML
    private Slider sliderFontSize;
    @FXML
    private Label lblFontSizeValue;
    @FXML
    private DatePicker datePicker;
    @FXML
    private Label lblSelectedDate;
    @FXML
    private CheckMenuItem miGlassStyle;
    @FXML
    private CheckMenuItem miSidePanel;

    @FXML
    private MenuItem miNewTab;
    @FXML
    private MenuItem miOpen;
    @FXML
    private MenuItem miSave;
    @FXML
    private MenuItem miSaveAs;
    @FXML
    private MenuItem miCloseTab;
    @FXML
    private MenuItem miExit;

    @FXML
    private MenuItem miUndo;
    @FXML
    private MenuItem miRedo;
    @FXML
    private MenuItem miCut;
    @FXML
    private MenuItem miCopy;
    @FXML
    private MenuItem miPaste;
    @FXML
    private MenuItem miSelectAll;
    @FXML
    private MenuItem miFind;
    @FXML
    private MenuItem miReplace;

    @FXML
    private RadioMenuItem miThemeLight;
    @FXML
    private RadioMenuItem miThemeDark;
    @FXML
    private RadioMenuItem miModeText;
    @FXML
    private RadioMenuItem miModeCode;
    @FXML
    private RadioMenuItem miEncodingAnsi;
    @FXML
    private RadioMenuItem miEncodingUtf8;
    @FXML
    private RadioMenuItem miEncodingUtf8Bom;
    @FXML
    private RadioMenuItem miEncodingUtf16LeBom;
    @FXML
    private RadioMenuItem miEncodingUtf16BeBom;
    @FXML
    private MenuItem miConvertAnsi;
    @FXML
    private MenuItem miConvertUtf8;
    @FXML
    private MenuItem miConvertUtf8Bom;
    @FXML
    private MenuItem miConvertUtf16LeBom;
    @FXML
    private MenuItem miConvertUtf16BeBom;
    @FXML
    private RadioMenuItem miEolWindows;
    @FXML
    private RadioMenuItem miEolUnix;
    @FXML
    private RadioMenuItem miEolMac;
    @FXML
    private MenuItem miZoomIn;
    @FXML
    private MenuItem miZoomOut;
    @FXML
    private MenuItem miZoomReset;

    @FXML
    private MenuItem miAbout;
    @FXML
    private MenuItem miCheckUpdates;
    @FXML
    private MenuItem miUpdateHelp;
    @FXML
    private MenuItem miDownloadApk;
    @FXML
    private MenuItem miOpenReleases;

    private final UpdateService updateService = new UpdateService();
    private int untitledCount = 1;
    private String currentTheme = THEME_DARK;
    private Stage mainStage;
    private String micaStylesheetUrl;
    private boolean micaEnabled = true;
    private Stage findStage;
    private TextField tfFind;
    private TextField tfReplace;
    private Label lblFindStatus;
    private ContextMenu suggestMenu;
    private String appVersion = "0.0.0";
    private boolean draftsDirty = false;
    private Timeline autosaveTimeline;
    private FileEncoding defaultEncoding = FileEncoding.UTF8;
    private LineEnding defaultLineEnding = LineEnding.CRLF;
    private double fontSize = BASE_FONT_SIZE;
    private String codeFontFamily = DEFAULT_CODE_FONT;
    private String textFontFamily = DEFAULT_TEXT_FONT;

    private static class TabData {
        CodeArea area;
        Path filePath;
        boolean dirty;
        boolean loading;
        Subscription highlightSubscription;
        boolean codeMode;
        String language;
        Pattern pattern;
        FileEncoding encoding;
        LineEnding lineEnding;
    }

    private static class DraftEntry {
        String title;
        String filePath;
        boolean codeMode;
        String language;
        String content;
        String encoding;
        String lineEnding;
    }

    @FXML
    public void initialize() {
        ToggleGroup themeGroup = new ToggleGroup();
        miThemeLight.setToggleGroup(themeGroup);
        miThemeDark.setToggleGroup(themeGroup);
        miThemeDark.setSelected(true);

        ToggleGroup modeGroup = new ToggleGroup();
        miModeText.setToggleGroup(modeGroup);
        miModeCode.setToggleGroup(modeGroup);
        miModeText.setSelected(true);

        ToggleGroup encodingGroup = new ToggleGroup();
        miEncodingAnsi.setToggleGroup(encodingGroup);
        miEncodingUtf8.setToggleGroup(encodingGroup);
        miEncodingUtf8Bom.setToggleGroup(encodingGroup);
        miEncodingUtf16LeBom.setToggleGroup(encodingGroup);
        miEncodingUtf16BeBom.setToggleGroup(encodingGroup);
        miEncodingUtf8.setSelected(true);

        ToggleGroup eolGroup = new ToggleGroup();
        miEolWindows.setToggleGroup(eolGroup);
        miEolUnix.setToggleGroup(eolGroup);
        miEolMac.setToggleGroup(eolGroup);
        miEolWindows.setSelected(true);

        setupShortcuts();
        setupSidePanel();
        if (miSidePanel != null && sidePanel != null) {
            miSidePanel.setSelected(true);
            sidePanel.setVisible(true);
            sidePanel.setManaged(true);
        }
        appVersion = loadAppVersion();
        if (!loadDrafts()) {
            createNewTab();
        }
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            updateStatus("Pronto");
            syncModeToggle(newTab);
            syncEncodingToggle(newTab);
            syncLineEndingToggle(newTab);
            updateStats();
            updateLineColStatus();
            updateSelectionStatus();
            updateEncodingStatus();
            updateLineEndingStatus();
            updateZoomStatus();
            syncFontControls(newTab);
        });
        startAutoSave();
        checkForUpdatesAsync();
    }

    private void setupSidePanel() {
        if (cbCodeFont != null) {
            cbCodeFont.getItems().setAll(CODE_FONT_CHOICES);
            cbCodeFont.setValue(codeFontFamily);
            cbCodeFont.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isBlank()) {
                    codeFontFamily = newVal;
                    applyEditorStyleAll();
                }
            });
        }
        if (cbTextFont != null) {
            cbTextFont.getItems().setAll(TEXT_FONT_CHOICES);
            cbTextFont.setValue(textFontFamily);
            cbTextFont.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isBlank()) {
                    textFontFamily = newVal;
                    applyEditorStyleAll();
                }
            });
        }
        if (sliderFontSize != null) {
            sliderFontSize.setValue(fontSize);
            updateFontSizeLabel();
            sliderFontSize.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    setFontSize(newVal.doubleValue());
                    updateFontSizeLabel();
                }
            });
        }
        if (datePicker != null) {
            datePicker.setValue(LocalDate.now());
            datePicker.valueProperty().addListener((obs, oldVal, newVal) -> updateSelectedDateHint(newVal));
            updateSelectedDateHint(datePicker.getValue());
        }
    }

    private void updateFontSizeLabel() {
        if (lblFontSizeValue != null) {
            lblFontSizeValue.setText(String.format(Locale.ROOT, "%.0f px", fontSize));
        }
    }

    private void updateSelectedDateHint(LocalDate date) {
        if (lblSelectedDate == null) {
            return;
        }
        if (date == null) {
            lblSelectedDate.setText("Use o calendário para planejar o dia e inserir a data nas notas.");
            return;
        }
        String formatted = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(
                Locale.forLanguageTag("pt-BR")));
        lblSelectedDate.setText(formatted);
    }

    private void syncFontControls(Tab tab) {
        if (tab == null) {
            return;
        }
        TabData data = (TabData) tab.getUserData();
        if (data == null || cbCodeFont == null || cbTextFont == null) {
            return;
        }
        boolean code = data.codeMode;
        cbCodeFont.setDisable(!code);
        cbTextFont.setDisable(code);
    }

    private void setupShortcuts() {
        miNewTab.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.N, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miOpen.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.O, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miSave.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.S, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miSaveAs.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.S, javafx.scene.input.KeyCombination.CONTROL_DOWN,
                javafx.scene.input.KeyCombination.SHIFT_DOWN));
        miFind.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.F, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miReplace.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.H, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miZoomIn.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.EQUALS, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miZoomOut.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.MINUS, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miZoomReset.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.DIGIT0, javafx.scene.input.KeyCombination.CONTROL_DOWN));
    }

    private void createNewTab() {
        String title = "Sem Titulo " + untitledCount++;
        Tab tab = new Tab(title);
        TabData data = buildCodeTab(tab, "");
        tab.setUserData(data);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        updateStats();
        syncEncodingToggle(tab);
        syncLineEndingToggle(tab);
        updateLineColStatus();
        updateSelectionStatus();
        updateEncodingStatus();
        updateLineEndingStatus();
        updateZoomStatus();
    }

    private TabData buildCodeTab(Tab tab, String content) {
        CodeArea area = new CodeArea();
        boolean codeMode = miModeCode != null && miModeCode.isSelected();
        area.getStyleClass().add(codeMode ? "code-area" : "text-area");
        area.setParagraphGraphicFactory(LineNumberFactory.get(area));

        TabData data = new TabData();
        data.area = area;
        data.loading = true;
        area.replaceText(content == null ? "" : content);
        data.loading = false;
        data.dirty = false;
        data.codeMode = codeMode;
        data.language = "java";
        data.pattern = codeMode ? PATTERN_JAVA : PATTERN_NOTES;
        data.encoding = defaultEncoding;
        data.lineEnding = defaultLineEnding;

        applyEditorStyle(area, codeMode);
        attachHighlight(data);

        area.plainTextChanges().subscribe(ignore -> {
            if (!data.loading) {
                markDirty(tab, true);
                draftsDirty = true;
            }
            updateStats();
        });

        area.caretPositionProperty().addListener((obs, oldPos, newPos) -> updateCaretStatus(area));
        area.selectedTextProperty().addListener((obs, oldText, newText) -> updateSelectionStatus(area));

        setupEditorInteractions(data);
        VirtualizedScrollPane<CodeArea> scroller = new VirtualizedScrollPane<>(area);
        scroller.getStyleClass().add("editor-scroll");
        tab.setContent(scroller);
        tab.setOnCloseRequest(event -> {
            if (!confirmClose(tab)) {
                event.consume();
            }
        });
        tab.setOnClosed(event -> saveDrafts());

        applyHighlight(data);
        return data;
    }

    private void applyHighlight(TabData data) {
        if (data == null || data.pattern == null || data.area == null) {
            return;
        }
        StyleSpans<Collection<String>> spans = computeHighlighting(data.area.getText(), data.pattern);
        data.area.setStyleSpans(0, spans);
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass = styleClassForMatch(matcher);
            if (styleClass == null) {
                continue;
            }
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private static String styleClassForMatch(Matcher matcher) {
        if (matcher.group("COMMENT") != null) {
            return "comment";
        }
        if (matcher.group("STRING") != null) {
            return "string";
        }
        if (matcher.group("ANNOTATION") != null) {
            return "annotation";
        }
        if (matcher.group("KEYWORD") != null) {
            return "keyword";
        }
        if (matcher.group("CONSTANT") != null) {
            return "constant";
        }
        if (matcher.group("TYPE") != null) {
            return "type";
        }
        if (matcher.group("FUNCTION") != null) {
            return "function";
        }
        if (matcher.group("VARIABLE") != null) {
            return "variable";
        }
        if (matcher.group("NUMBER") != null) {
            return "number";
        }
        if (matcher.group("PAREN") != null) {
            return "paren";
        }
        if (matcher.group("BRACE") != null) {
            return "brace";
        }
        if (matcher.group("BRACKET") != null) {
            return "bracket";
        }
        if (matcher.group("SEMICOLON") != null) {
            return "semicolon";
        }
        if (matcher.group("OPERATOR") != null) {
            return "operator";
        }
        if (matcher.group("IDENT") != null) {
            return "identifier";
        }
        if (matcher.group("HEADING") != null) {
            return "note-heading";
        }
        if (matcher.group("CHECKBOX") != null) {
            return "note-checkbox";
        }
        if (matcher.group("NOTEDATE") != null) {
            return "note-date";
        }
        if (matcher.group("TODO") != null) {
            return "note-todo";
        }
        if (matcher.group("TAG") != null) {
            return "note-tag";
        }
        return null;
    }

    private CodeArea getCurrentArea() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab == null) {
            return null;
        }
        TabData data = (TabData) tab.getUserData();
        return data == null ? null : data.area;
    }

    private TabData getCurrentData() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab == null) {
            return null;
        }
        return (TabData) tab.getUserData();
    }

    private void updateCaretStatus(CodeArea area) {
        updateLineColStatus(area);
    }

    private void updateLineColStatus() {
        TabData data = getCurrentData();
        if (data == null) {
            return;
        }
        updateLineColStatus(data.area);
    }

    private void updateLineColStatus(CodeArea area) {
        if (lblLineCol == null || area == null) {
            return;
        }
        int line = area.getCurrentParagraph() + 1;
        int col = area.getCaretColumn() + 1;
        int pos = area.getCaretPosition() + 1;
        lblLineCol.setText("Ln " + line + ", Col " + col + ", Pos " + pos);
    }

    private void updateStatus(String message) {
        if (lblStatus != null) {
            lblStatus.setText(message);
        }
    }

    private void updateSelectionStatus() {
        TabData data = getCurrentData();
        if (data == null) {
            if (lblSelection != null) {
                lblSelection.setText("Seleção: 0");
            }
            return;
        }
        updateSelectionStatus(data.area);
    }

    private void updateSelectionStatus(CodeArea area) {
        if (lblSelection == null || area == null) {
            return;
        }
        String selected = area.getSelectedText();
        int len = selected == null ? 0 : selected.length();
        lblSelection.setText("Seleção: " + len);
    }

    private void updateEncodingStatus() {
        if (lblEncoding == null) {
            return;
        }
        TabData data = getCurrentData();
        FileEncoding encoding = data == null || data.encoding == null ? defaultEncoding : data.encoding;
        lblEncoding.setText(encoding.label);
    }

    private void updateLineEndingStatus() {
        if (lblEol == null) {
            return;
        }
        TabData data = getCurrentData();
        LineEnding lineEnding = data == null || data.lineEnding == null ? defaultLineEnding : data.lineEnding;
        lblEol.setText(lineEnding.label);
    }

    private void updateZoomStatus() {
        if (lblZoom == null) {
            return;
        }
        int percent = (int) Math.round((fontSize / BASE_FONT_SIZE) * 100.0);
        lblZoom.setText(percent + "%");
    }

    private void updateStats() {
        if (lblStats == null) {
            return;
        }
        TabData data = getCurrentData();
        if (data == null) {
            lblStats.setText("Linhas: 1 | Palavras: 0 | Caracteres: 0");
            return;
        }
        String text = data.area.getText();
        int chars = text.length();
        int words = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
        int lines = text.isEmpty() ? 1 : text.split("\\R", -1).length;
        lblStats.setText("Linhas: " + lines + " | Palavras: " + words + " | Caracteres: " + chars);
    }

    private void syncModeToggle(Tab tab) {
        if (tab == null) {
            return;
        }
        TabData data = (TabData) tab.getUserData();
        if (data == null) {
            return;
        }
        if (data.codeMode) {
            miModeCode.setSelected(true);
        } else {
            miModeText.setSelected(true);
        }
    }

    private void syncEncodingToggle(Tab tab) {
        if (tab == null) {
            return;
        }
        TabData data = (TabData) tab.getUserData();
        if (data == null) {
            return;
        }
        FileEncoding encoding = data.encoding == null ? defaultEncoding : data.encoding;
        switch (encoding) {
            case ANSI -> miEncodingAnsi.setSelected(true);
            case UTF8 -> miEncodingUtf8.setSelected(true);
            case UTF8_BOM -> miEncodingUtf8Bom.setSelected(true);
            case UTF16_LE_BOM -> miEncodingUtf16LeBom.setSelected(true);
            case UTF16_BE_BOM -> miEncodingUtf16BeBom.setSelected(true);
        }
    }

    private void syncLineEndingToggle(Tab tab) {
        if (tab == null) {
            return;
        }
        TabData data = (TabData) tab.getUserData();
        if (data == null) {
            return;
        }
        LineEnding lineEnding = data.lineEnding == null ? defaultLineEnding : data.lineEnding;
        switch (lineEnding) {
            case CRLF -> miEolWindows.setSelected(true);
            case LF -> miEolUnix.setSelected(true);
            case CR -> miEolMac.setSelected(true);
        }
    }

    private void attachHighlight(TabData data) {
        if (data.highlightSubscription != null) {
            data.highlightSubscription.unsubscribe();
        }
        data.highlightSubscription = data.area.multiPlainChanges()
                .successionEnds(Duration.ofMillis(200))
                .subscribe(ignore -> applyHighlight(data));
    }

    private void setMode(TabData data, boolean codeMode) {
        data.codeMode = codeMode;
        data.area.getStyleClass().removeAll("code-area", "text-area");
        data.area.getStyleClass().add(codeMode ? "code-area" : "text-area");
        if (codeMode) {
            if (data.language == null || data.language.isBlank()) {
                data.language = "java";
            }
            data.pattern = patternForLanguage(data.language);
        } else {
            data.pattern = PATTERN_NOTES;
        }
        attachHighlight(data);
        applyHighlight(data);
        applyEditorStyle(data.area, codeMode);
        syncFontControls(tabPane.getSelectionModel().getSelectedItem());
    }

    private void applyEditorStyle(CodeArea area, boolean codeMode) {
        if (area == null) {
            return;
        }
        String family = codeMode ? codeFontFamily : textFontFamily;
        String escaped = family.replace("\\", "\\\\").replace("\"", "\\\"");
        area.setStyle(String.format(Locale.ROOT,
                "-fx-font-family: \"%s\"; -fx-font-size: %.0fpx;", escaped, fontSize));
    }

    private void applyEditorStyleAll() {
        for (Tab tab : tabPane.getTabs()) {
            TabData data = (TabData) tab.getUserData();
            if (data != null) {
                applyEditorStyle(data.area, data.codeMode);
            }
        }
    }

    private void setFontSize(double newSize) {
        double clamped = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, newSize));
        if (Math.abs(clamped - fontSize) < 0.01) {
            return;
        }
        fontSize = clamped;
        if (sliderFontSize != null && Math.abs(sliderFontSize.getValue() - clamped) >= 0.01) {
            sliderFontSize.setValue(clamped);
        }
        applyEditorStyleAll();
        updateFontSizeLabel();
        updateZoomStatus();
    }

    private void setupEditorInteractions(TabData data) {
        data.area.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.SPACE) {
                showSuggestions(data);
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.TAB) {
                if (tryExpandSnippet(data)) {
                    event.consume();
                }
            }
        });

        data.area.addEventHandler(KeyEvent.KEY_TYPED, event -> {
            if (data.codeMode && event.getCharacter() != null && event.getCharacter().length() == 1) {
                char ch = event.getCharacter().charAt(0);
                if (handleAutoPair(data.area, ch)) {
                    event.consume();
                    return;
                }
            }
            if (suggestMenu != null && suggestMenu.isShowing()) {
                suggestMenu.hide();
            }
        });
    }

    private boolean handleAutoPair(CodeArea area, char ch) {
        String closing = switch (ch) {
            case '(' -> ")";
            case '[' -> "]";
            case '{' -> "}";
            case '"' -> "\"";
            case '\'' -> "'";
            default -> null;
        };
        if (closing == null) {
            return false;
        }
        String selected = area.getSelectedText();
        if (selected != null && !selected.isEmpty()) {
            area.replaceSelection(ch + selected + closing);
            area.moveTo(area.getCaretPosition() - closing.length());
            return true;
        }
        int caret = area.getCaretPosition();
        area.insertText(caret, String.valueOf(ch) + closing);
        area.moveTo(caret + 1);
        return true;
    }

    private boolean tryExpandSnippet(TabData data) {
        String word = getCurrentWord(data.area);
        if (word == null) {
            return false;
        }
        String snippet = SNIPPETS.get(word);
        if (snippet == null) {
            return false;
        }
        replaceCurrentWordWithSnippet(data.area, snippet);
        return true;
    }

    private void replaceCurrentWordWithSnippet(CodeArea area, String snippet) {
        int[] range = getCurrentWordRange(area);
        if (range == null) {
            return;
        }
        int start = range[0];
        int end = range[1];
        int cursorIndex = snippet.indexOf(CURSOR);
        String cleanSnippet = snippet.replace(CURSOR, "");
        area.replaceText(start, end, cleanSnippet);
        if (cursorIndex >= 0) {
            area.moveTo(start + cursorIndex);
        } else {
            area.moveTo(start + cleanSnippet.length());
        }
    }

    private void showSuggestions(TabData data) {
        if (suggestMenu == null) {
            suggestMenu = new ContextMenu();
        }
        suggestMenu.getItems().clear();
        String prefix = getCurrentWord(data.area);
        for (String suggestion : buildSuggestions(data, prefix)) {
            MenuItem item = new MenuItem(suggestion);
            item.setOnAction(e -> replaceCurrentWord(data.area, suggestion));
            suggestMenu.getItems().add(item);
        }
        if (suggestMenu.getItems().isEmpty()) {
            return;
        }
        data.area.getCaretBounds().ifPresentOrElse(bounds -> {
            Point2D point = data.area.localToScreen(bounds.getMaxX(), bounds.getMaxY());
            suggestMenu.show(data.area, point.getX(), point.getY());
        }, () -> suggestMenu.show(data.area, 0, 0));
    }

    private Set<String> buildSuggestions(TabData data, String prefix) {
        Set<String> result = new LinkedHashSet<>();
        String norm = prefix == null ? "" : prefix.trim();
        if (data.codeMode) {
            for (String key : getKeywordsForLanguage(data.language)) {
                if (norm.isEmpty() || key.startsWith(norm)) {
                    result.add(key);
                }
            }
        }
        for (String key : SNIPPETS.keySet()) {
            if (norm.isEmpty() || key.startsWith(norm)) {
                result.add(key);
            }
        }
        String text = data.area.getText();
        Matcher matcher = Pattern.compile("\\b[a-zA-Z_][\\w]*\\b").matcher(text);
        while (matcher.find()) {
            String word = matcher.group();
            if (norm.isEmpty() || word.startsWith(norm)) {
                result.add(word);
            }
            if (result.size() > 80) {
                break;
            }
        }
        return result;
    }

    private String getCurrentWord(CodeArea area) {
        int[] range = getCurrentWordRange(area);
        if (range == null) {
            return null;
        }
        return area.getText(range[0], range[1]);
    }

    private int[] getCurrentWordRange(CodeArea area) {
        int caret = area.getCaretPosition();
        String text = area.getText();
        if (text.isEmpty() || caret < 0) {
            return null;
        }
        int start = caret;
        int end = caret;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) {
            end++;
        }
        if (start == end) {
            return null;
        }
        return new int[]{start, end};
    }

    private void replaceCurrentWord(CodeArea area, String replacement) {
        int[] range = getCurrentWordRange(area);
        if (range == null) {
            return;
        }
        area.replaceText(range[0], range[1], replacement);
    }

    private void markDirty(Tab tab, boolean dirty) {
        TabData data = (TabData) tab.getUserData();
        if (data != null && data.dirty != dirty) {
            data.dirty = dirty;
            String baseName = tab.getText();
            if (baseName.endsWith("*")) {
                baseName = baseName.substring(0, baseName.length() - 1);
            }
            tab.setText(dirty ? baseName + "*" : baseName);
        }
    }

    private boolean confirmClose(Tab tab) {
        TabData data = (TabData) tab.getUserData();
        if (data == null || !data.dirty) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Alterações não salvas");
        alert.setHeaderText("Salvar alterações antes de fechar?");
        alert.setContentText(tab.getText().replace("*", ""));
        ButtonType btnSave = new ButtonType("Salvar");
        ButtonType btnDont = new ButtonType("Não salvar");
        ButtonType btnCancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnSave, btnDont, btnCancel);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == btnCancel) {
            return false;
        }
        if (result.get() == btnSave) {
            tabPane.getSelectionModel().select(tab);
            return handleSaveInternal(false);
        }
        return true;
    }

    private void setCurrentFile(TabData data, Tab tab, Path path) {
        data.filePath = path;
        String name = path == null ? "Sem Titulo" : path.getFileName().toString();
        tab.setText(name + (data.dirty ? "*" : ""));
    }

    @FXML
    public void handleNewTab() {
        createNewTab();
    }

    @FXML
    public void handleOpen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir arquivo");
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            FileEncoding detected = detectEncoding(bytes);
            int offset = (detected != null && detected.bom != null) ? detected.bom.length : 0;
            Charset charset = detected != null ? detected.charset : defaultEncoding.charset;
            String content = new String(bytes, offset, bytes.length - offset, charset);
            Tab tab = new Tab(file.getName());
            TabData data = buildCodeTab(tab, content);
            data.filePath = file.toPath();
            data.encoding = detected != null ? detected : defaultEncoding;
            data.lineEnding = detectLineEnding(content);
            String language = detectLanguage(file.toPath());
            if ("text".equals(language)) {
                setMode(data, false);
                if (miModeText != null) {
                    miModeText.setSelected(true);
                }
            } else {
                data.language = language;
                setMode(data, true);
                if (miModeCode != null) {
                    miModeCode.setSelected(true);
                }
            }
            tab.setUserData(data);
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
            markDirty(tab, false);
            updateStatus("Arquivo aberto: " + file.getName());
            updateStats();
            syncEncodingToggle(tab);
            syncLineEndingToggle(tab);
            updateEncodingStatus();
            updateLineEndingStatus();
            updateLineColStatus();
            updateSelectionStatus();
        } catch (IOException ex) {
            showError("Não foi possível abrir o arquivo.", ex.getMessage());
        }
    }

    @FXML
    public void handleSave() {
        handleSaveInternal(false);
    }

    @FXML
    public void handleSaveAs() {
        handleSaveInternal(true);
    }

    private boolean handleSaveInternal(boolean forceSaveAs) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        TabData data = getCurrentData();
        if (tab == null || data == null) {
            return false;
        }
        Path path = data.filePath;
        if (forceSaveAs || path == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Salvar arquivo");
            File file = chooser.showSaveDialog(root.getScene().getWindow());
            if (file == null) {
                return false;
            }
            path = file.toPath();
        }
        try {
            String normalized = normalizeLineEndings(data.area.getText(), data.lineEnding);
            byte[] bytes = encodeText(normalized, data.encoding);
            Files.write(path, bytes);
            data.filePath = path;
            markDirty(tab, false);
            setCurrentFile(data, tab, path);
            String language = detectLanguage(path);
            if (data.codeMode && !"text".equals(language)) {
                data.language = language;
                data.pattern = patternForLanguage(language);
                applyHighlight(data);
            }
            updateStatus("Arquivo salvo: " + path.getFileName());
            return true;
        } catch (IOException ex) {
            showError("Não foi possível salvar o arquivo.", ex.getMessage());
            return false;
        }
    }

    @FXML
    public void handleCloseTab() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab != null && confirmClose(tab)) {
            tabPane.getTabs().remove(tab);
            if (tabPane.getTabs().isEmpty()) {
                createNewTab();
            }
            saveDrafts();
        }
    }

    @FXML
    public void handleExit() {
        requestExit();
    }

    public void requestExit() {
        for (Tab tab : tabPane.getTabs()) {
            if (!confirmClose(tab)) {
                return;
            }
        }
        saveDrafts();
        Platform.exit();
    }

    @FXML
    public void handleUndo() {
        CodeArea area = getCurrentArea();
        if (area != null) {
            area.undo();
        }
    }

    @FXML
    public void handleRedo() {
        CodeArea area = getCurrentArea();
        if (area != null) {
            area.redo();
        }
    }

    @FXML
    public void handleCut() {
        CodeArea area = getCurrentArea();
        if (area != null) {
            ClipboardContent content = new ClipboardContent();
            content.putString(area.getSelectedText());
            Clipboard.getSystemClipboard().setContent(content);
            area.replaceSelection("");
        }
    }

    @FXML
    public void handleCopy() {
        CodeArea area = getCurrentArea();
        if (area != null) {
            ClipboardContent content = new ClipboardContent();
            content.putString(area.getSelectedText());
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    @FXML
    public void handlePaste() {
        CodeArea area = getCurrentArea();
        if (area != null && Clipboard.getSystemClipboard().hasString()) {
            area.replaceSelection(Clipboard.getSystemClipboard().getString());
        }
    }

    @FXML
    public void handleSelectAll() {
        CodeArea area = getCurrentArea();
        if (area != null) {
            area.selectAll();
        }
    }

    @FXML
    public void handleFind() {
        showFindReplace(false);
    }

    @FXML
    public void handleReplace() {
        showFindReplace(true);
    }

    @FXML
    public void handleModeText() {
        TabData data = getCurrentData();
        if (data != null) {
            setMode(data, false);
        }
    }

    @FXML
    public void handleModeCode() {
        TabData data = getCurrentData();
        if (data != null) {
            if (data.language == null) {
                data.language = "java";
                data.pattern = PATTERN_JAVA;
            }
            setMode(data, true);
        }
    }

    @FXML
    public void handleEncodingAnsi() {
        applyEncoding(FileEncoding.ANSI, false);
    }

    @FXML
    public void handleEncodingUtf8() {
        applyEncoding(FileEncoding.UTF8, false);
    }

    @FXML
    public void handleEncodingUtf8Bom() {
        applyEncoding(FileEncoding.UTF8_BOM, false);
    }

    @FXML
    public void handleEncodingUtf16LeBom() {
        applyEncoding(FileEncoding.UTF16_LE_BOM, false);
    }

    @FXML
    public void handleEncodingUtf16BeBom() {
        applyEncoding(FileEncoding.UTF16_BE_BOM, false);
    }

    @FXML
    public void handleConvertAnsi() {
        applyEncoding(FileEncoding.ANSI, true);
    }

    @FXML
    public void handleConvertUtf8() {
        applyEncoding(FileEncoding.UTF8, true);
    }

    @FXML
    public void handleConvertUtf8Bom() {
        applyEncoding(FileEncoding.UTF8_BOM, true);
    }

    @FXML
    public void handleConvertUtf16LeBom() {
        applyEncoding(FileEncoding.UTF16_LE_BOM, true);
    }

    @FXML
    public void handleConvertUtf16BeBom() {
        applyEncoding(FileEncoding.UTF16_BE_BOM, true);
    }

    @FXML
    public void handleEolWindows() {
        applyLineEnding(LineEnding.CRLF);
    }

    @FXML
    public void handleEolUnix() {
        applyLineEnding(LineEnding.LF);
    }

    @FXML
    public void handleEolMac() {
        applyLineEnding(LineEnding.CR);
    }

    @FXML
    public void handleZoomIn() {
        setFontSize(fontSize + 1);
    }

    @FXML
    public void handleZoomOut() {
        setFontSize(fontSize - 1);
    }

    @FXML
    public void handleZoomReset() {
        setFontSize(BASE_FONT_SIZE);
    }

    private void applyEncoding(FileEncoding encoding, boolean markDirty) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        TabData data = getCurrentData();
        if (tab == null || data == null) {
            return;
        }
        if (data.encoding == encoding && defaultEncoding == encoding) {
            return;
        }
        data.encoding = encoding;
        defaultEncoding = encoding;
        syncEncodingToggle(tab);
        updateEncodingStatus();
        if (markDirty) {
            markDirty(tab, true);
            draftsDirty = true;
            updateStatus("Converter para " + encoding.label + " (salve para aplicar)");
        } else {
            updateStatus("Codificação: " + encoding.label);
        }
    }

    private void applyLineEnding(LineEnding lineEnding) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        TabData data = getCurrentData();
        if (tab == null || data == null) {
            return;
        }
        if (data.lineEnding == lineEnding && defaultLineEnding == lineEnding) {
            return;
        }
        data.lineEnding = lineEnding;
        defaultLineEnding = lineEnding;
        syncLineEndingToggle(tab);
        updateLineEndingStatus();
        markDirty(tab, true);
        draftsDirty = true;
        updateStatus("Quebra de linha: " + lineEnding.label);
    }

    private void showFindReplace(boolean focusReplace) {
        if (findStage == null) {
            buildFindDialog();
        }
        findStage.show();
        findStage.toFront();
        if (focusReplace) {
            tfReplace.requestFocus();
        } else {
            tfFind.requestFocus();
        }
    }

    private void buildFindDialog() {
        findStage = new Stage();
        findStage.setTitle("Buscar e Substituir");
        findStage.initModality(Modality.NONE);
        findStage.initOwner(root.getScene().getWindow());

        tfFind = new TextField();
        tfReplace = new TextField();
        lblFindStatus = new Label();

        Button btnNext = new Button("Próximo");
        Button btnPrev = new Button("Anterior");
        Button btnReplace = new Button("Substituir");
        Button btnReplaceAll = new Button("Substituir Tudo");

        btnNext.setOnAction(e -> findNext(true));
        btnPrev.setOnAction(e -> findNext(false));
        btnReplace.setOnAction(e -> replaceOnce());
        btnReplaceAll.setOnAction(e -> replaceAll());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label("Buscar:"), 0, 0);
        grid.add(tfFind, 1, 0);
        grid.add(new Label("Substituir:"), 0, 1);
        grid.add(tfReplace, 1, 1);
        GridPane.setHgrow(tfFind, Priority.ALWAYS);
        GridPane.setHgrow(tfReplace, Priority.ALWAYS);

        HBox actions = new HBox(8, btnPrev, btnNext, btnReplace, btnReplaceAll);
        VBox rootBox = new VBox(10, grid, actions, lblFindStatus);
        rootBox.setStyle("-fx-padding: 12;");

        findStage.setScene(new Scene(rootBox, 420, 150));
    }

    private void findNext(boolean forward) {
        CodeArea area = getCurrentArea();
        if (area == null) {
            return;
        }
        String query = tfFind.getText();
        if (query == null || query.isEmpty()) {
            lblFindStatus.setText("Digite algo para buscar.");
            return;
        }
        String text = area.getText();
        int start = area.getCaretPosition();
        int index = forward ? text.indexOf(query, start) : text.lastIndexOf(query, Math.max(0, start - 1));
        if (index == -1 && start != 0) {
            index = forward ? text.indexOf(query) : text.lastIndexOf(query);
        }
        if (index == -1) {
            lblFindStatus.setText("Nenhuma ocorrência encontrada.");
            return;
        }
        area.selectRange(index, index + query.length());
        area.requestFollowCaret();
        lblFindStatus.setText("Ocorrência em " + (index + 1));
    }

    private void replaceOnce() {
        CodeArea area = getCurrentArea();
        if (area == null) {
            return;
        }
        String query = tfFind.getText();
        String replacement = tfReplace.getText();
        if (query == null || query.isEmpty()) {
            lblFindStatus.setText("Digite algo para buscar.");
            return;
        }
        if (area.getSelectedText().equals(query)) {
            area.replaceSelection(replacement == null ? "" : replacement);
        }
        findNext(true);
    }

    private void replaceAll() {
        CodeArea area = getCurrentArea();
        if (area == null) {
            return;
        }
        String query = tfFind.getText();
        String replacement = tfReplace.getText();
        if (query == null || query.isEmpty()) {
            lblFindStatus.setText("Digite algo para buscar.");
            return;
        }
        String text = area.getText();
        String replaced = text.replace(query, replacement == null ? "" : replacement);
        area.replaceText(replaced);
        lblFindStatus.setText("Substituição concluída.");
    }

    public void attachMainWindow(Stage stage, Scene scene) {
        this.mainStage = stage;
        if (scene != null) {
            scene.setFill(Color.TRANSPARENT);
        }
        if (!WindowsBackdrop.isSupported()) {
            if (miGlassStyle != null) {
                miGlassStyle.setDisable(true);
                miGlassStyle.setSelected(false);
            }
            if (root != null) {
                root.getStyleClass().removeAll("mica-window", "mica-light", "glass-chrome");
            }
            return;
        }
        boolean enable = miGlassStyle == null || miGlassStyle.isSelected();
        setMicaEnabled(enable);
    }

    private void setMicaEnabled(boolean enabled) {
        micaEnabled = enabled;
        if (root == null) {
            return;
        }
        if (enabled) {
            if (!root.getStyleClass().contains("mica-window")) {
                root.getStyleClass().add("mica-window");
            }
            if (!root.getStyleClass().contains("glass-chrome")) {
                root.getStyleClass().add("glass-chrome");
            }
            ensureMicaStylesheet();
            syncMicaThemeClass();
            applyWindowsBackdrop();
        } else {
            root.getStyleClass().removeAll("mica-window", "mica-light", "glass-chrome");
            removeMicaStylesheet();
            if (mainStage != null) {
                WindowsBackdrop.apply(mainStage, isDarkTheme(), WindowsBackdrop.Backdrop.NONE);
            }
        }
    }

    private void ensureMicaStylesheet() {
        if (root == null) {
            return;
        }
        java.net.URL url = getClass().getResource(THEME_MICA);
        if (url == null) {
            return;
        }
        micaStylesheetUrl = url.toExternalForm();
        if (!root.getStylesheets().contains(micaStylesheetUrl)) {
            root.getStylesheets().add(micaStylesheetUrl);
        }
    }

    private void removeMicaStylesheet() {
        if (root != null && micaStylesheetUrl != null) {
            root.getStylesheets().remove(micaStylesheetUrl);
        }
    }

    private boolean isDarkTheme() {
        return THEME_DARK.equals(currentTheme);
    }

    private void syncMicaThemeClass() {
        if (root == null) {
            return;
        }
        if (isDarkTheme()) {
            root.getStyleClass().remove("mica-light");
        } else if (!root.getStyleClass().contains("mica-light")) {
            root.getStyleClass().add("mica-light");
        }
    }

    private void applyWindowsBackdrop() {
        if (mainStage == null || !micaEnabled) {
            return;
        }
        WindowsBackdrop.apply(mainStage, isDarkTheme(), WindowsBackdrop.Backdrop.MICA);
    }

    @FXML
    public void handleGlassStyle() {
        if (root == null || miGlassStyle == null) {
            return;
        }
        setMicaEnabled(miGlassStyle.isSelected());
    }

    @FXML
    public void handleSidePanel() {
        if (sidePanel == null || miSidePanel == null) {
            return;
        }
        boolean show = miSidePanel.isSelected();
        sidePanel.setVisible(show);
        sidePanel.setManaged(show);
    }

    @FXML
    public void handleInsertDate() {
        CodeArea area = getCurrentArea();
        if (area == null || datePicker == null) {
            return;
        }
        LocalDate date = datePicker.getValue();
        if (date == null) {
            updateStatus("Selecione uma data no calendário.");
            return;
        }
        String heading = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy - EEEE",
                Locale.forLanguageTag("pt-BR")));
        insertNoteLine(area, "## " + heading + "\n");
        updateStatus("Data inserida nas notas.");
    }

    @FXML
    public void handleInsertTask() {
        CodeArea area = getCurrentArea();
        if (area == null) {
            return;
        }
        insertNoteLine(area, "- [ ] ");
        updateStatus("Tarefa inserida.");
    }

    private void insertNoteLine(CodeArea area, String text) {
        int pos = area.getCaretPosition();
        String full = area.getText();
        int lineStart = full.lastIndexOf('\n', Math.max(0, pos - 1)) + 1;
        boolean atLineStart = pos == lineStart;
        String toInsert = atLineStart ? text : "\n" + text;
        area.insertText(pos, toInsert);
        area.moveTo(pos + toInsert.length());
        TabData data = getCurrentData();
        if (data != null) {
            applyHighlight(data);
        }
    }

    @FXML
    public void handleThemeLight() {
        switchTheme(THEME_LIGHT);
    }

    @FXML
    public void handleThemeDark() {
        switchTheme(THEME_DARK);
    }

    private void switchTheme(String theme) {
        if (root == null) {
            return;
        }
        root.getStylesheets().clear();
        java.net.URL url = getClass().getResource(theme);
        if (url == null) {
            showError("Tema não encontrado", "Não foi possível carregar " + theme);
            return;
        }
        root.getStylesheets().add(url.toExternalForm());
        currentTheme = theme;
        if (micaEnabled) {
            ensureMicaStylesheet();
            syncMicaThemeClass();
            applyWindowsBackdrop();
        }
    }

    @FXML
    public void handleAbout() {
        UpdateService.Platform platform = UpdateService.detectPlatform();
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Sobre o CodePad");
        alert.setHeaderText(APP_NAME + "  v" + appVersion);
        alert.setContentText("""
                Bloco de notas com calendário para planejar tarefas e o dia.
                Modo Código com destaque de sintaxe (variáveis, funções, etc.).

                Atualizações: %s
                O app verifica novas releases no GitHub ao iniciar.

                Repositório: github.com/juliareboucasleite/CodePad
                """.formatted(UpdateService.platformLabel(platform)));
        alert.showAndWait();
    }

    @FXML
    public void handleCheckUpdates() {
        runUpdateCheck(true);
    }

    @FXML
    public void handleUpdateHelp() {
        showScrollableHelp("Como atualizar", HELP_UPDATE_TEXT);
    }

    @FXML
    public void handleOpenReleases() {
        openInBrowser(UpdateService.GITHUB_RELEASES_URL);
    }

    @FXML
    public void handleDownloadApk() {
        new Thread(() -> {
            try {
                Optional<UpdateService.ReleaseInfo> release = updateService.fetchLatestApkRelease();
                Platform.runLater(() -> {
                    if (release.isEmpty()) {
                        showError("APK não encontrado",
                                "Não há arquivo .apk na última release.\n"
                                        + "Publique CodePad.apk na release do GitHub.");
                        return;
                    }
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("Baixar APK");
                    confirm.setHeaderText("Release " + release.get().tag());
                    confirm.setContentText("Arquivo: " + release.get().assetName() + "\n\n"
                            + "O APK será salvo em Downloads\\CodePad.\n"
                            + "Transfira para o celular e instale por cima da versão antiga.");
                    confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
                    if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                        startDownload(release.get());
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> showError("Falha ao obter APK", ex.getMessage()));
            }
        }, "apk-download").start();
    }

    private void showScrollableHelp(String title, String body) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        TextArea area = new TextArea(body);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefWidth(520);
        area.setPrefHeight(360);
        alert.getDialogPane().setContent(area);
        alert.showAndWait();
    }

    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erro");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private static Pattern buildPattern(String[] keywords, String commentRegex, String stringRegex) {
        String keywordPattern = "\\b(" + String.join("|", keywords) + ")\\b";
        return Pattern.compile(
                "(?<COMMENT>" + commentRegex + ")"
                        + "|(?<STRING>" + stringRegex + ")"
                        + "|(?<ANNOTATION>@[A-Za-z][\\w]*)"
                        + "|(?<KEYWORD>" + keywordPattern + ")"
                        + "|(?<CONSTANT>\\b[A-Z][A-Z0-9_]*\\b)"
                        + "|(?<TYPE>\\b[A-Z][\\w]*\\b)"
                        + "|(?<FUNCTION>\\b[a-zA-Z_][\\w]*(?=\\s*\\())"
                        + "|(?<VARIABLE>\\b[a-z][a-zA-Z0-9]*\\b)"
                        + "|(?<NUMBER>\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[dDfFlL]?\\b)"
                        + "|(?<PAREN>\\(|\\))"
                        + "|(?<BRACE>\\{|\\})"
                        + "|(?<BRACKET>\\[|\\])"
                        + "|(?<SEMICOLON>[;,])"
                        + "|(?<OPERATOR>[=+\\-*/%&|^<>!?:]+)"
                        + "|(?<IDENT>\\b_[a-zA-Z][\\w]*\\b)"
        );
    }

    private static String detectLanguage(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".java")) {
            return "java";
        }
        if (name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".ts")) {
            return "js";
        }
        if (name.endsWith(".py")) {
            return "py";
        }
        if (name.endsWith(".html") || name.endsWith(".css")) {
            return "text";
        }
        if (name.endsWith(".txt") || name.endsWith(".md")) {
            return "text";
        }
        return "java";
    }

    private static Pattern patternForLanguage(String language) {
        if ("js".equals(language)) {
            return PATTERN_JS;
        }
        if ("py".equals(language)) {
            return PATTERN_PY;
        }
        return PATTERN_JAVA;
    }

    private static String[] getKeywordsForLanguage(String language) {
        if ("js".equals(language)) {
            return KEYWORDS_JS;
        }
        if ("py".equals(language)) {
            return KEYWORDS_PY;
        }
        return KEYWORDS;
    }

    private FileEncoding detectEncoding(byte[] bytes) {
        if (startsWith(bytes, BOM_UTF8)) {
            return FileEncoding.UTF8_BOM;
        }
        if (startsWith(bytes, BOM_UTF16_LE)) {
            return FileEncoding.UTF16_LE_BOM;
        }
        if (startsWith(bytes, BOM_UTF16_BE)) {
            return FileEncoding.UTF16_BE_BOM;
        }
        return null;
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes == null || prefix == null || bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private LineEnding detectLineEnding(String text) {
        if (text == null || text.isEmpty()) {
            return defaultLineEnding;
        }
        if (text.contains("\r\n")) {
            return LineEnding.CRLF;
        }
        if (text.contains("\r")) {
            return LineEnding.CR;
        }
        return LineEnding.LF;
    }

    private String normalizeLineEndings(String text, LineEnding lineEnding) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").replace("\r", "\n");
        LineEnding target = lineEnding == null ? defaultLineEnding : lineEnding;
        return normalized.replace("\n", target.sequence);
    }

    private byte[] encodeText(String text, FileEncoding encoding) {
        FileEncoding enc = encoding == null ? defaultEncoding : encoding;
        byte[] content = (text == null ? "" : text).getBytes(enc.charset);
        if (enc.bom == null || enc.bom.length == 0) {
            return content;
        }
        byte[] out = new byte[enc.bom.length + content.length];
        System.arraycopy(enc.bom, 0, out, 0, enc.bom.length);
        System.arraycopy(content, 0, out, enc.bom.length, content.length);
        return out;
    }

    private String loadAppVersion() {
        try (InputStream in = getClass().getResourceAsStream("/org/example/app.properties")) {
            if (in == null) {
                return "0.0.0";
            }
            Properties props = new Properties();
            props.load(in);
            String v = props.getProperty("app.version");
            return v == null ? "0.0.0" : v.trim();
        } catch (IOException ex) {
            return "0.0.0";
        }
    }

    private void checkForUpdatesAsync() {
        runUpdateCheck(false);
    }

    private void runUpdateCheck(boolean manual) {
        new Thread(() -> {
            try {
                Optional<UpdateService.ReleaseInfo> update = updateService.findUpdate(appVersion);
                Platform.runLater(() -> {
                    if (update.isPresent()) {
                        showUpdateDialog(update.get());
                    } else if (manual) {
                        showUpToDateMessage();
                    }
                });
            } catch (Exception ex) {
                if (manual) {
                    Platform.runLater(() -> showError(
                            "Não foi possível verificar atualizações", ex.getMessage()));
                }
            }
        }, manual ? "update-check-manual" : "update-check").start();
    }

    private void showUpToDateMessage() {
        String latestTag = appVersion;
        try {
            Optional<UpdateService.ReleaseInfo> latest = updateService.fetchLatestRelease();
            if (latest.isPresent()) {
                latestTag = latest.get().tag();
            }
        } catch (Exception ignored) {
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Atualizações");
        alert.setHeaderText("Você está na versão mais recente");
        alert.setContentText("Versão instalada: " + appVersion
                + "\nÚltima release no GitHub: " + latestTag);
        alert.showAndWait();
    }

    private void showUpdateDialog(UpdateService.ReleaseInfo release) {
        UpdateService.Platform platform = UpdateService.detectPlatform();
        String assetName = release.assetName() != null ? release.assetName() : "atualização";
        StringBuilder content = new StringBuilder();
        content.append("Versão instalada: ").append(appVersion).append('\n');
        content.append("Nova versão: ").append(release.tag()).append('\n');
        content.append("Arquivo: ").append(assetName).append("\n\n");
        content.append(updateStepsForPlatform(platform));
        String notes = release.releaseNotes();
        if (notes != null && !notes.isBlank()) {
            String shortNotes = notes.length() > 500 ? notes.substring(0, 500) + "…" : notes;
            content.append("\n\nNotas da release:\n").append(shortNotes.trim());
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Atualização disponível");
        alert.setHeaderText("Nova versão " + release.tag());
        alert.setContentText(content.toString());
        ButtonType btnDownload = new ButtonType("Baixar " + assetName);
        ButtonType btnBrowser = new ButtonType("Abrir no GitHub");
        ButtonType btnLater = new ButtonType("Mais tarde", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnDownload, btnBrowser, btnLater);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty()) {
            return;
        }
        if (result.get() == btnDownload) {
            startDownload(release);
        } else if (result.get() == btnBrowser) {
            openInBrowser(release.releaseUrl());
        }
    }

    private static String updateStepsForPlatform(UpdateService.Platform platform) {
        return switch (platform) {
            case ANDROID -> """
                    O APK será baixado para Downloads/CodePad.
                    Abra o arquivo no celular e instale por cima do app antigo.""";
            case WINDOWS -> """
                    O instalador será salvo em Downloads\\CodePad.
                    Execute o .exe para atualizar (pode instalar por cima).""";
            case MAC -> """
                    O pacote será salvo em Downloads/CodePad.
                    Abra o .dmg ou .pkg e siga o assistente.""";
            case LINUX -> """
                    O pacote será salvo em Downloads/CodePad.
                    Instale o .deb ou execute o AppImage.""";
            case UNKNOWN -> """
                    O arquivo será salvo em Downloads/CodePad.
                    Siga as instruções da release no GitHub.""";
        };
    }

    private void startDownload(UpdateService.ReleaseInfo release) {
        Dialog<ButtonType> progressDialog = new Dialog<>();
        progressDialog.setTitle("Baixando atualização");
        progressDialog.setHeaderText("Baixando " + release.assetName());
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(360);
        Label statusLabel = new Label("Iniciando…");
        VBox box = new VBox(10, statusLabel, progressBar);
        box.setStyle("-fx-padding: 12;");
        progressDialog.getDialogPane().setContent(box);
        ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        progressDialog.getDialogPane().getButtonTypes().add(cancel);

        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                Path dir = updateService.defaultDownloadDir();
                return updateService.downloadAsset(release, dir, (downloaded, total) -> {
                    if (total > 0) {
                        updateProgress(downloaded, total);
                        updateMessage(formatBytes(downloaded) + " / " + formatBytes(total));
                    } else {
                        updateMessage(formatBytes(downloaded) + " baixados");
                    }
                });
            }
        };
        statusLabel.textProperty().bind(task.messageProperty());
        progressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(event -> {
            progressDialog.close();
            Path path = task.getValue();
            if (path != null) {
                showDownloadCompleteDialog(release, path);
            }
        });
        task.setOnFailed(event -> {
            progressDialog.close();
            Throwable ex = task.getException();
            showError("Falha no download",
                    ex == null ? "Erro desconhecido." : ex.getMessage());
        });
        task.setOnCancelled(event -> progressDialog.close());

        progressDialog.setOnCloseRequest(event -> task.cancel());
        Button cancelButton = (Button) progressDialog.getDialogPane().lookupButton(cancel);
        cancelButton.setOnAction(event -> {
            task.cancel();
            progressDialog.close();
        });

        Thread downloadThread = new Thread(task, "release-download");
        downloadThread.setDaemon(true);
        downloadThread.start();
        progressDialog.show();
    }

    private void showDownloadCompleteDialog(UpdateService.ReleaseInfo release, Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Download concluído");
        alert.setHeaderText(release.tag() + " — " + release.assetName());
        alert.setContentText("Salvo em:\n" + path.toAbsolutePath());
        ButtonType openFolder = new ButtonType("Abrir pasta");
        ButtonType runInstaller = null;
        if (fileName.endsWith(".exe")) {
            runInstaller = new ButtonType("Executar instalador");
        } else if (fileName.endsWith(".apk")) {
            runInstaller = new ButtonType("Abrir arquivo");
        }
        if (runInstaller != null) {
            alert.getButtonTypes().setAll(runInstaller, openFolder, ButtonType.OK);
        } else {
            alert.getButtonTypes().setAll(openFolder, ButtonType.OK);
        }
        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isEmpty()) {
            return;
        }
        ButtonType selected = choice.get();
        if (selected == openFolder) {
            openFolder(path.getParent());
        } else if (runInstaller != null && selected == runInstaller) {
            runDownloadedFile(path);
        }
        updateStatus("Atualização baixada: " + path.getFileName());
    }

    private void runDownloadedFile(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                showError("Arquivo não encontrado", path.toString());
                return;
            }
            if (UpdateService.detectPlatform() == UpdateService.Platform.WINDOWS
                    && path.toString().toLowerCase(Locale.ROOT).endsWith(".exe")) {
                new ProcessBuilder(path.toAbsolutePath().toString()).start();
                return;
            }
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(path.toFile());
            }
        } catch (IOException ex) {
            showError("Não foi possível abrir o arquivo", ex.getMessage());
        }
    }

    private void openFolder(Path folder) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(folder.toFile());
            }
        } catch (IOException ex) {
            showError("Não foi possível abrir a pasta", ex.getMessage());
        }
    }

    private void openInBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ex) {
            showError("Não foi possível abrir o navegador", ex.getMessage());
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private Path getDraftFile() {
        return buildDraftPath(DRAFTS_DIR);
    }

    private Path getLegacyDraftFile() {
        return buildDraftPath(LEGACY_DRAFTS_DIR);
    }

    private Path buildDraftPath(String dirName) {
        String base = System.getenv("LOCALAPPDATA");
        if (base == null || base.isBlank()) {
            base = System.getProperty("user.home");
        }
        return Paths.get(base, dirName, DRAFTS_FILE);
    }

    private void saveDrafts() {
        try {
            List<DraftEntry> entries = new ArrayList<>();
            for (Tab tab : tabPane.getTabs()) {
                TabData data = (TabData) tab.getUserData();
                if (data == null) {
                    continue;
                }
                String text = data.area.getText();
                if ((text == null || text.isEmpty()) && data.filePath == null) {
                    continue;
                }
                DraftEntry e = new DraftEntry();
                e.title = tab.getText();
                e.filePath = data.filePath == null ? "" : data.filePath.toString();
                e.codeMode = data.codeMode;
                e.language = data.language == null ? "java" : data.language;
                e.content = text == null ? "" : text;
                e.encoding = (data.encoding == null ? defaultEncoding : data.encoding).name();
                e.lineEnding = (data.lineEnding == null ? defaultLineEnding : data.lineEnding).name();
                entries.add(e);
            }

            Path file = getDraftFile();
            if (entries.isEmpty()) {
                Files.deleteIfExists(file);
                draftsDirty = false;
                return;
            }
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("DRAFTS_V2").append("\n");
            for (DraftEntry e : entries) {
                sb.append(b64(e.title)).append("\n");
                sb.append(b64(e.filePath)).append("\n");
                sb.append(e.codeMode ? "1" : "0").append("\n");
                sb.append(b64(e.language)).append("\n");
                sb.append(b64(e.encoding)).append("\n");
                sb.append(b64(e.lineEnding)).append("\n");
                sb.append(b64(e.content)).append("\n");
                sb.append("---").append("\n");
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            draftsDirty = false;
        } catch (IOException ignored) {
        }
    }

    private boolean loadDrafts() {
        Path file = getDraftFile();
        if (!Files.exists(file)) {
            Path legacy = getLegacyDraftFile();
            if (!Files.exists(legacy)) {
                return false;
            }
            file = legacy;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return false;
            }
            String header = lines.get(0);
            boolean v2 = "DRAFTS_V2".equals(header);
            boolean v1 = "DRAFTS_V1".equals(header);
            if (!v1 && !v2) {
                return false;
            }
            boolean created = false;
            int i = 1;
            while (i < lines.size()) {
                if (v2) {
                    if (i + 6 >= lines.size()) {
                        break;
                    }
                    String title = fromB64(lines.get(i++));
                    String path = fromB64(lines.get(i++));
                    String codeMode = lines.get(i++);
                    String language = fromB64(lines.get(i++));
                    String encoding = fromB64(lines.get(i++));
                    String lineEnding = fromB64(lines.get(i++));
                    String content = fromB64(lines.get(i++));
                    if (i < lines.size() && "---".equals(lines.get(i))) {
                        i++;
                    }
                    created |= restoreDraft(title, path, codeMode, language, encoding, lineEnding, content);
                } else {
                    if (i + 4 >= lines.size()) {
                        break;
                    }
                    String title = fromB64(lines.get(i++));
                    String path = fromB64(lines.get(i++));
                    String codeMode = lines.get(i++);
                    String language = fromB64(lines.get(i++));
                    String content = fromB64(lines.get(i++));
                    if (i < lines.size() && "---".equals(lines.get(i))) {
                        i++;
                    }
                    created |= restoreDraft(title, path, codeMode, language, null, null, content);
                }
            }
            if (created) {
                tabPane.getSelectionModel().selectFirst();
                Tab selected = tabPane.getSelectionModel().getSelectedItem();
                syncModeToggle(selected);
                syncEncodingToggle(selected);
                syncLineEndingToggle(selected);
                updateStats();
                updateLineColStatus();
                updateSelectionStatus();
                updateEncodingStatus();
                updateLineEndingStatus();
                updateZoomStatus();
            }
            draftsDirty = false;
            return created;
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean restoreDraft(String title, String path, String codeMode, String language,
                                 String encoding, String lineEnding, String content) {
        Tab tab = new Tab(title == null || title.isBlank() ? "Sem Titulo" : title);
        TabData data = buildCodeTab(tab, content == null ? "" : content);
        data.filePath = (path == null || path.isBlank()) ? null : Paths.get(path);
        data.language = (language == null || language.isBlank()) ? "java" : language;
        data.pattern = patternForLanguage(data.language);
        data.encoding = parseEncoding(encoding);
        data.lineEnding = parseLineEnding(lineEnding);
        if ("0".equals(codeMode)) {
            setMode(data, false);
        } else {
            setMode(data, true);
        }
        tab.setUserData(data);
        tabPane.getTabs().add(tab);
        markDirty(tab, data.filePath == null && content != null && !content.isEmpty());
        return true;
    }

    private FileEncoding parseEncoding(String value) {
        if (value == null || value.isBlank()) {
            return defaultEncoding;
        }
        try {
            return FileEncoding.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return defaultEncoding;
        }
    }

    private LineEnding parseLineEnding(String value) {
        if (value == null || value.isBlank()) {
            return defaultLineEnding;
        }
        try {
            return LineEnding.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return defaultLineEnding;
        }
    }

    private void startAutoSave() {
        autosaveTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(AUTO_SAVE_SECONDS), event -> {
            if (draftsDirty) {
                saveDrafts();
            }
        }));
        autosaveTimeline.setCycleCount(Timeline.INDEFINITE);
        autosaveTimeline.play();
    }

    private String b64(String s) {
        if (s == null) {
            return "";
        }
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private String fromB64(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }
}
