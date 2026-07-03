package com.l299l.npcspeak.ai;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AiBackend {

    CompletableFuture<String> complete(List<AiMessage> messages);

    String getName();

    // Validate configuration on startup. Implementations may be lazy (no-op here)
    // and surface errors only on the first complete() call instead.
    default void validate() throws IllegalStateException {}
}
