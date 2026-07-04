package com.l299l.npcspeak.logging;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ConversationLogger {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Path logsDir;
    private final String discordWebhook;
    private final HttpClient http;

    public ConversationLogger(File dataFolder, String discordWebhook) {
        this.logsDir = dataFolder.toPath().resolve("logs");
        this.discordWebhook = discordWebhook;
        this.http = HttpClient.newHttpClient();
    }

    public void log(String npcId, String playerName, String userMessage, String npcResponse) {
        write(npcId, "[" + time() + "] [" + playerName + "] User: " + userMessage + " | NPC: " + npcResponse);
    }

    public void logBlocked(String npcId, String npcDisplayName, String playerName, String blockedMessage) {
        write(npcId, "[" + time() + "] [" + playerName + "] BLOCKED: " + blockedMessage);
        if (!discordWebhook.isBlank()) {
            sendDiscordAlert(npcDisplayName, playerName, blockedMessage);
        }
    }

    public List<String> readToday(String npcId) {
        return read(npcId, LocalDate.now());
    }

    public List<String> read(String npcId, LocalDate date) {
        Path file = logsDir.resolve(date.format(DATE_FMT)).resolve(npcId + ".log");
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            return List.of();
        }
    }

    public List<String> readTodayFiltered(String npcId, String playerName) {
        return readToday(npcId).stream()
                .filter(line -> line.contains("[" + playerName + "]"))
                .toList();
    }

    private void write(String npcId, String line) {
        Path dir = logsDir.resolve(LocalDate.now().format(DATE_FMT));
        Path file = dir.resolve(npcId + ".log");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    private static String time() {
        return LocalTime.now().format(TIME_FMT);
    }

    private void sendDiscordAlert(String npcName, String playerName, String message) {
        String body = "{\"embeds\":[{\"color\":15158332,\"title\":\"Blocked Message\",\"fields\":["
                + "{\"name\":\"Player\",\"value\":\"" + escape(playerName) + "\",\"inline\":true},"
                + "{\"name\":\"NPC\",\"value\":\"" + escape(npcName) + "\",\"inline\":true},"
                + "{\"name\":\"Message\",\"value\":\"" + escape(message) + "\"}"
                + "]}]}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(discordWebhook))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).exceptionally(e -> null);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
