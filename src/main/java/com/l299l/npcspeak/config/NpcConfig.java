package com.l299l.npcspeak.config;

public class NpcConfig {

    private static final String DEFAULT_GREETING = "Greetings! What would you like to know?";

    private String greeting;
    private String systemPrompt;
    private String topic;
    private String avoid;
    private NpcTaskConfig task;
    private Boolean memoryEnabled;
    private int maxInteractions = -1;
    private boolean lockAfterTask = false;
    private boolean logConversations = false;

    public String buildSystemPrompt(String npcName) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are ").append(npcName).append(", an NPC in a Minecraft world.");
        if (topic != null && !topic.isBlank()) {
            sb.append(" Topic: ").append(topic).append(".");
        }
        if (avoid != null && !avoid.isBlank()) {
            sb.append(" Avoid: ").append(avoid).append(".");
        }
        if (task != null && task.getGoal() != null && !task.getGoal().isBlank()) {
            sb.append(" ").append(task.getGoal());
            sb.append(" ").append(difficultyHint(task.getDifficulty()));
            if ("negotiate".equalsIgnoreCase(task.getType())) {
                if (task.isIntelligentQuantity()) {
                    sb.append(" You CAN sell multiple units in a single transaction — do not refuse bulk orders.");
                } else {
                    sb.append(" You negotiate one item per transaction only." +
                            " If the player tries to buy or trade multiple items at once, politely decline" +
                            " and redirect to a single-item deal.");
                }
                sb.append(" When a deal is reached, confirm it VERBALLY (e.g. 'We have a deal!' or 'Sold!')." +
                        " Do NOT act out physically handing items or taking money — the server handles that automatically.");
            }
        }
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sb.append(" ").append(systemPrompt);
        }
        sb.append(" Keep responses brief (1-3 sentences) and stay in character." +
                " Do not mention that you are an AI or a language model.");
        return sb.toString();
    }

    private static String difficultyHint(int d) {
        return switch (Math.max(1, Math.min(5, d))) {
            case 1 -> "You are very accommodating and quickly cooperate or yield with little effort from the other party.";
            case 2 -> "You are easy-going and can be swayed with a reasonable argument or modest effort.";
            case 3 -> "You require clear, genuine effort before you cooperate or change your position.";
            case 4 -> "You are stubborn and resistant; only solid, persistent reasoning will move you.";
            default -> "You are extremely difficult to sway — only truly exceptional arguments will persuade you, and you push back hard on everything else.";
        };
    }

    public String getGreetingOrDefault() {
        return (greeting != null && !greeting.isBlank()) ? greeting : DEFAULT_GREETING;
    }

    public boolean isMemoryEnabledFor(boolean globalEnabled) {
        return memoryEnabled != null ? memoryEnabled : globalEnabled;
    }

    public String getGreeting() { return greeting; }
    public String getSystemPrompt() { return systemPrompt; }
    public String getTopic() { return topic; }
    public String getAvoid() { return avoid; }
    public NpcTaskConfig getTask() { return task; }
    public Boolean getMemoryEnabled() { return memoryEnabled; }
    public int getMaxInteractions() { return maxInteractions; }
    public boolean isLockAfterTask() { return lockAfterTask; }
    public boolean isLogConversations() { return logConversations; }

    public void setGreeting(String greeting) { this.greeting = greeting; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public void setTopic(String topic) { this.topic = topic; }
    public void setAvoid(String avoid) { this.avoid = avoid; }
    public void setTask(NpcTaskConfig task) { this.task = task; }
    public void setMemoryEnabled(Boolean memoryEnabled) { this.memoryEnabled = memoryEnabled; }
    public void setMaxInteractions(int maxInteractions) { this.maxInteractions = maxInteractions; }
    public void setLockAfterTask(boolean lockAfterTask) { this.lockAfterTask = lockAfterTask; }
    public void setLogConversations(boolean logConversations) { this.logConversations = logConversations; }
}
