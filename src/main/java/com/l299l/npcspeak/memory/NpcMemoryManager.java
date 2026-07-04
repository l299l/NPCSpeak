package com.l299l.npcspeak.memory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class NpcMemoryManager {

    private final File baseDir;

    public record PlayerMeta(int interactions, boolean locked) {
        public static final PlayerMeta DEFAULT = new PlayerMeta(0, false);
    }

    public NpcMemoryManager(File dataFolder) {
        this.baseDir = new File(dataFolder, "memory");
    }

    public void save(String npcId, UUID playerUUID, String summary) {
        File dir = npcDir(npcId);
        dir.mkdirs();
        File file = new File(dir, playerUUID + ".txt");
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(System.currentTimeMillis() + "\n");
            writer.write(summary);
        } catch (IOException ignored) {
        }
    }

    public String load(String npcId, UUID playerUUID, int maxAgeDays) {
        File file = new File(npcDir(npcId), playerUUID + ".txt");
        if (!file.exists()) return null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String timestampLine = reader.readLine();
            if (timestampLine == null) return null;
            long savedAt = Long.parseLong(timestampLine.trim());
            if (maxAgeDays > 0) {
                long ageMs = System.currentTimeMillis() - savedAt;
                if (ageMs > TimeUnit.DAYS.toMillis(maxAgeDays)) return null;
            }
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(line);
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (IOException | NumberFormatException ignored) {
            return null;
        }
    }

    public List<UUID> listPlayers(String npcId) {
        File dir = npcDir(npcId);
        if (!dir.exists()) return List.of();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".txt"));
        if (files == null) return List.of();
        List<UUID> result = new ArrayList<>();
        for (File f : files) {
            try {
                result.add(UUID.fromString(f.getName().replace(".txt", "")));
            } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    public boolean clear(String npcId, UUID playerUUID) {
        return new File(npcDir(npcId), playerUUID + ".txt").delete();
    }

    public int clearAll(String npcId) {
        File dir = npcDir(npcId);
        if (!dir.exists()) return 0;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".txt"));
        if (files == null) return 0;
        int count = 0;
        for (File f : files) if (f.delete()) count++;
        return count;
    }

    public PlayerMeta loadMeta(String npcId, UUID playerUUID) {
        File file = new File(npcDir(npcId), playerUUID + ".meta");
        if (!file.exists()) return PlayerMeta.DEFAULT;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line1 = reader.readLine();
            String line2 = reader.readLine();
            if (line1 == null) return PlayerMeta.DEFAULT;
            int interactions = Integer.parseInt(line1.trim());
            boolean locked = line2 != null && Boolean.parseBoolean(line2.trim());
            return new PlayerMeta(interactions, locked);
        } catch (IOException | NumberFormatException ignored) {
            return PlayerMeta.DEFAULT;
        }
    }

    public void saveMeta(String npcId, UUID playerUUID, PlayerMeta meta) {
        File dir = npcDir(npcId);
        dir.mkdirs();
        File file = new File(dir, playerUUID + ".meta");
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(meta.interactions() + "\n");
            writer.write(meta.locked() + "\n");
        } catch (IOException ignored) {}
    }

    public boolean clearMeta(String npcId, UUID playerUUID) {
        return new File(npcDir(npcId), playerUUID + ".meta").delete();
    }

    public int clearAllMeta(String npcId) {
        File dir = npcDir(npcId);
        if (!dir.exists()) return 0;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".meta"));
        if (files == null) return 0;
        int count = 0;
        for (File f : files) if (f.delete()) count++;
        return count;
    }

    private File npcDir(String npcId) {
        return new File(baseDir, npcId);
    }
}
