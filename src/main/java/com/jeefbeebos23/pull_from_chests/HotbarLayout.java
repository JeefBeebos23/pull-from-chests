package com.jeefbeebos23.pull_from_chests;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HotbarLayout {

    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir()
            .resolve("pull_from_chests_hotbar_layout.json");
    private static final Gson GSON = new Gson();

    public static void save(List<String> layout) {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(layout));
        } catch (IOException ignored) {}
    }

    public static List<String> load() {
        if (!Files.exists(CONFIG_PATH)) return emptyLayout();
        try {
            String json = Files.readString(CONFIG_PATH);
            java.lang.reflect.Type type = new TypeToken<List<String>>() {}.getType();
            List<String> layout = GSON.fromJson(json, type);
            if (layout == null || layout.size() != 9) return emptyLayout();
            return layout;
        } catch (Exception ignored) {
            return emptyLayout();
        }
    }

    private static List<String> emptyLayout() {
        return new ArrayList<>(Arrays.asList(new String[9]));
    }
}
