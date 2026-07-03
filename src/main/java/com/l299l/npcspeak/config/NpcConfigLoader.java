package com.l299l.npcspeak.config;

import com.l299l.npcspeak.npc.NpcData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class NpcConfigLoader {

    private final File npcsFolder;

    public NpcConfigLoader(File npcsFolder) {
        this.npcsFolder = npcsFolder;
        npcsFolder.mkdirs();
    }

    public List<NpcData> loadAll(Logger logger) {
        File[] files = npcsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return List.of();

        List<NpcData> result = new ArrayList<>();
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            try {
                NpcData data = NpcData.fromFile(id, file);
                if (data.getSpawnLocation() == null) {
                    logger.warning("NPC '" + id + "' has no valid location in " + file.getName() + ", skipping.");
                    continue;
                }
                result.add(data);
            } catch (Exception e) {
                logger.warning("Failed to load NPC '" + id + "': " + e.getMessage());
            }
        }
        return result;
    }

    public void save(NpcData data) throws IOException {
        data.saveToFile(getFile(data.getId()));
    }

    public void delete(String id) {
        getFile(id).delete();
    }

    public boolean exists(String id) {
        return getFile(id).exists();
    }

    public File getFile(String id) {
        return new File(npcsFolder, id + ".yml");
    }
}
