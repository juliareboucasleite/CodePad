package org.example.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

public final class CalendarEvent {

    private final String id;
    private final LocalDate date;
    private final LocalTime time;
    private final String title;
    private final String colorKey;

    public CalendarEvent(String id, LocalDate date, LocalTime time, String title, String colorKey) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.date = Objects.requireNonNull(date, "date");
        this.time = time;
        this.title = title == null ? "" : title.trim();
        this.colorKey = colorKey == null || colorKey.isBlank() ? "blue" : colorKey;
    }

    public static CalendarEvent create(LocalDate date, LocalTime time, String title, String colorKey) {
        return new CalendarEvent(null, date, time, title, colorKey);
    }

    public String id() {
        return id;
    }

    public LocalDate date() {
        return date;
    }

    public LocalTime time() {
        return time;
    }

    public String title() {
        return title;
    }

    public String colorKey() {
        return colorKey;
    }

    public CalendarEvent withDate(LocalDate newDate) {
        return new CalendarEvent(id, newDate, time, title, colorKey);
    }

    public CalendarEvent withTime(LocalTime newTime) {
        return new CalendarEvent(id, date, newTime, title, colorKey);
    }

    public CalendarEvent withTitle(String newTitle) {
        return new CalendarEvent(id, date, time, newTitle, colorKey);
    }

    public CalendarEvent withColorKey(String newColor) {
        return new CalendarEvent(id, date, time, title, newColor);
    }

    public String timeLabel() {
        if (time == null) {
            return "";
        }
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }
}
