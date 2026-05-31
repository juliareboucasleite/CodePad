package org.example.ui;

import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Grade mensal com dias clicáveis e indicador de eventos (ponto colorido).
 */
public class MonthCalendarPane extends VBox {

    private static final DateTimeFormatter MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy",
            Locale.forLanguageTag("pt-BR"));
    private static final String[] WEEKDAYS = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};

    private final Label lblMonth = new Label();
    private final GridPane grid = new GridPane();
    private YearMonth displayed = YearMonth.now();
    private LocalDate selected = LocalDate.now();
    private Function<LocalDate, Integer> eventCountForDate = d -> 0;
    private Consumer<LocalDate> onDateSelected = d -> {
    };
    private Consumer<LocalDate> onAddEventOnDate = d -> {
    };

    public MonthCalendarPane() {
        setSpacing(6);
        HBox header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        Button btnPrev = new Button("‹");
        btnPrev.getStyleClass().add("calendar-nav");
        Button btnNext = new Button("›");
        btnNext.getStyleClass().add("calendar-nav");
        btnPrev.setOnAction(e -> changeMonth(-1));
        btnNext.setOnAction(e -> changeMonth(1));
        lblMonth.getStyleClass().add("calendar-month-title");
        HBox.setHgrow(lblMonth, Priority.ALWAYS);
        lblMonth.setMaxWidth(Double.MAX_VALUE);
        header.getChildren().addAll(btnPrev, lblMonth, btnNext);

        grid.setHgap(2);
        grid.setVgap(2);
        grid.getStyleClass().add("calendar-grid");
        for (int c = 0; c < 7; c++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(100.0 / 7.0);
            col.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(col);
        }

        getChildren().addAll(header, grid);
        rebuild();
    }

    public void setEventCountForDate(Function<LocalDate, Integer> provider) {
        this.eventCountForDate = provider == null ? d -> 0 : provider;
        rebuild();
    }

    public void setOnDateSelected(Consumer<LocalDate> handler) {
        this.onDateSelected = handler == null ? d -> {
        } : handler;
    }

    public void setOnAddEventOnDate(Consumer<LocalDate> handler) {
        this.onAddEventOnDate = handler == null ? d -> {
        } : handler;
    }

    public LocalDate getSelectedDate() {
        return selected;
    }

    public void setSelectedDate(LocalDate date) {
        if (date == null) {
            return;
        }
        selected = date;
        displayed = YearMonth.from(date);
        rebuild();
    }

    public void refresh() {
        rebuild();
    }

    private void changeMonth(int delta) {
        displayed = displayed.plusMonths(delta);
        rebuild();
    }

    private void rebuild() {
        lblMonth.setText(capitalize(MONTH_TITLE.format(displayed.atDay(1))));
        grid.getChildren().clear();
        for (int i = 0; i < 7; i++) {
            Label wd = new Label(WEEKDAYS[i]);
            wd.getStyleClass().add("calendar-weekday");
            wd.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHalignment(wd, HPos.CENTER);
            grid.add(wd, i, 0);
        }
        LocalDate first = displayed.atDay(1);
        int offset = weekdayIndex(first.getDayOfWeek());
        LocalDate start = first.minusDays(offset);
        LocalDate today = LocalDate.now();
        int row = 1;
        int col = 0;
        for (int i = 0; i < 42; i++) {
            LocalDate day = start.plusDays(i);
            grid.add(dayCell(day, displayed, today), col, row);
            col++;
            if (col >= 7) {
                col = 0;
                row++;
            }
        }
    }

    private StackPane dayCell(LocalDate day, YearMonth month, LocalDate today) {
        StackPane cell = new StackPane();
        cell.getStyleClass().add("calendar-day");
        if (!YearMonth.from(day).equals(month)) {
            cell.getStyleClass().add("calendar-day-other");
        }
        if (day.equals(today)) {
            cell.getStyleClass().add("calendar-day-today");
        }
        if (day.equals(selected)) {
            cell.getStyleClass().add("calendar-day-selected");
        }
        Label num = new Label(String.valueOf(day.getDayOfMonth()));
        num.getStyleClass().add("calendar-day-num");
        int count = eventCountForDate.apply(day);
        if (count > 0) {
            Region dot = new Region();
            dot.getStyleClass().add("calendar-event-dot");
            if (count > 1) {
                dot.getStyleClass().add("calendar-event-dot-busy");
            }
            StackPane.setAlignment(dot, Pos.BOTTOM_CENTER);
            cell.getChildren().addAll(num, dot);
        } else {
            cell.getChildren().add(num);
        }
        cell.setOnMouseClicked(e -> {
            if (e.getClickCount() >= 2) {
                selected = day;
                onAddEventOnDate.accept(day);
                rebuild();
            } else {
                selected = day;
                onDateSelected.accept(day);
                rebuild();
            }
        });
        return cell;
    }

    private static int weekdayIndex(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> 0;
            case TUESDAY -> 1;
            case WEDNESDAY -> 2;
            case THURSDAY -> 3;
            case FRIDAY -> 4;
            case SATURDAY -> 5;
            default -> 6;
        };
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
