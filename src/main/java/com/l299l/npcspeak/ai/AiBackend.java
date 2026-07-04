package com.l299l.npcspeak.ai;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface AiBackend {

    CompletableFuture<String> complete(List<AiMessage> messages);

    default CompletableFuture<String> streamComplete(List<AiMessage> messages, Consumer<String> onToken) {
        return complete(messages);
    }

    String getName();

    default void validate() throws IllegalStateException {}
}
