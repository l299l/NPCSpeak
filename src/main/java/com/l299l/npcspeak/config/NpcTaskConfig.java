package com.l299l.npcspeak.config;

import java.util.ArrayList;
import java.util.List;

public class NpcTaskConfig {

    private String type = "freeform";
    private String goal;
    private String outcomeCheck;
    private List<String> onSuccess = new ArrayList<>();
    private List<String> onFailure = new ArrayList<>();
    private int maxExchanges = -1;
    private int difficulty = 3; // 1 (easy) – 5 (very hard)

    /** Returns true if this task should be evaluated after each exchange. */
    public boolean isEvaluatable() {
        return !"freeform".equalsIgnoreCase(type)
                && outcomeCheck != null
                && !outcomeCheck.isBlank();
    }

    public String getType() { return type; }
    public String getGoal() { return goal; }
    public String getOutcomeCheck() { return outcomeCheck; }
    public List<String> getOnSuccess() { return onSuccess; }
    public List<String> getOnFailure() { return onFailure; }
    public int getMaxExchanges() { return maxExchanges; }
    public int getDifficulty() { return difficulty; }

    public void setType(String type) { this.type = type; }
    public void setGoal(String goal) { this.goal = goal; }
    public void setOutcomeCheck(String outcomeCheck) { this.outcomeCheck = outcomeCheck; }
    public void setOnSuccess(List<String> onSuccess) { this.onSuccess = onSuccess != null ? new ArrayList<>(onSuccess) : new ArrayList<>(); }
    public void setOnFailure(List<String> onFailure) { this.onFailure = onFailure != null ? new ArrayList<>(onFailure) : new ArrayList<>(); }
    public void setMaxExchanges(int maxExchanges) { this.maxExchanges = maxExchanges; }
    public void setDifficulty(int difficulty) { this.difficulty = Math.max(1, Math.min(5, difficulty)); }
}
