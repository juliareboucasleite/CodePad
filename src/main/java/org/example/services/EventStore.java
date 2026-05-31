package org.example.services;

import org.example.model.CalendarEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persistência simples de eventos em JSON (sem dependências extras).
 */
public class EventStore {

    private static final String FILE_NAME = "events.json";
    private static final Pattern OBJECT = Pattern.compile(
            "\\{\\s*\"id\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"date\"\\s*:\\s*\"([^\"]*)\""
                    + "\\s*,\\s*\"time\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"title\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
                    + "\\s*,\\s*\"color\"\\s*:\\s*\"([^\"]*)\"\\s*\\}");

    private final Path file;
    private final List<CalendarEvent> events = new ArrayList<>();

    public EventStore() {
        this(defaultPath());
    }

    public EventStore(Path file) {
        this.file = file;
    }

    public static Path defaultPath() {
        String base = System.getenv("LOCALAPPDATA");
        if (base == null || base.isBlank()) {
            base = System.getProperty("user.home");
        }
        return Path.of(base, "CodePad", FILE_NAME);
    }

    public synchronized List<CalendarEvent> all() {
        return List.copyOf(events);
    }

    public synchronized List<CalendarEvent> onDate(LocalDate date) {
        if (date == null) {
            return List.of();
        }
        return events.stream().filter(e -> date.equals(e.date())).toList();
    }

    public synchronized int countOnDate(LocalDate date) {
        return onDate(date).size();
    }

    public synchronized void load() throws IOException {
        events.clear();
        if (!Files.exists(file)) {
            return;
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        Matcher m = OBJECT.matcher(json);
        while (m.find()) {
            try {
                String id = unescape(m.group(1));
                LocalDate date = LocalDate.parse(m.group(2));
                String timeRaw = m.group(3);
                LocalTime time = timeRaw == null || timeRaw.isBlank()
                        ? null : LocalTime.parse(timeRaw);
                String title = unescape(m.group(4));
                String color = m.group(5);
                events.add(new CalendarEvent(id, date, time, title, color));
            } catch (RuntimeException ignored) {
            }
        }
    }

    public synchronized void save() throws IOException {
        Files.createDirectories(file.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < events.size(); i++) {
            CalendarEvent e = events.get(i);
            sb.append("  {\"id\":\"").append(escape(e.id())).append("\",");
            sb.append("\"date\":\"").append(e.date()).append("\",");
            sb.append("\"time\":\"").append(e.time() == null ? "" : e.timeLabel()).append("\",");
            sb.append("\"title\":\"").append(escape(e.title())).append("\",");
            sb.append("\"color\":\"").append(escape(e.colorKey())).append("\"}");
            if (i < events.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]\n");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    public synchronized void add(CalendarEvent event) throws IOException {
        events.add(event);
        save();
    }

    public synchronized void update(CalendarEvent event) throws IOException {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).id().equals(event.id())) {
                events.set(i, event);
                save();
                return;
            }
        }
        add(event);
    }

    public synchronized void remove(String id) throws IOException {
        events.removeIf(e -> e.id().equals(id));
        save();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
