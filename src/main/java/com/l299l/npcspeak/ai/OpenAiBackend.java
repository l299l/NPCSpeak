package com.l299l.npcspeak.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.l299l.npcspeak.config.PluginConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class OpenAiBackend implements AiBackend {

    private final String url;
    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;
    private final HttpClient httpClient;

    public OpenAiBackend(PluginConfig config) {
        this.url = config.getOpenAiUrl();
        this.apiKey = config.getOpenAiApiKey();
        this.model = config.getOpenAiModel();
        this.timeoutSeconds = config.getOllamaTimeoutSeconds();
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public CompletableFuture<String> complete(List<AiMessage> messages) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildBody(messages)))
                .timeout(Duration.ofSeconds(timeoutSeconds));

        // Authorization header is optional — local servers (LM Studio, vLLM) often don't need it
        if (!apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        return httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException(
                                "OpenAI-compatible API returned HTTP " + response.statusCode() + ": " + response.body());
                    }
                    return parseContent(response.body());
                });
    }

    private String buildBody(List<AiMessage> messages) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        JsonArray arr = new JsonArray();
        for (AiMessage msg : messages) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.role());
            m.addProperty("content", msg.content());
            arr.add(m);
        }
        body.add("messages", arr);
        return body.toString();
    }

    private String parseContent(String body) {
        return JsonParser.parseString(body)
                .getAsJsonObject()
                .getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content")
                .getAsString()
                .trim();
    }

    @Override
    public String getName() { return "OpenAI-compatible"; }
}
