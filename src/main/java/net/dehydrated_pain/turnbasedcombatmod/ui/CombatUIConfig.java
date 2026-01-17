package net.dehydrated_pain.turnbasedcombatmod.ui;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.LOGGER;
import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

public class CombatUIConfig implements ResourceManagerReloadListener {
    private static final ResourceLocation UI_CONFIG_LOCATION = ResourceLocation.fromNamespaceAndPath(MODID, "ui/combat_ui.json");

    private static TurnIndicatorConfig turnIndicator = new TurnIndicatorConfig("turnbasedcombatmod:textures/gui/combat_skill_button.png", 64, 64);

    public static class TurnIndicatorConfig {
        public final ResourceLocation image;
        public final int width;
        public final int height;

        public TurnIndicatorConfig(String imagePath, int width, int height) {
            this.image = ResourceLocation.parse(imagePath);
            this.width = width;
            this.height = height;
        }
    }

    @Override
    public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
        try {
            var resource = resourceManager.getResource(UI_CONFIG_LOCATION);
            if (resource.isPresent()) {
                try (InputStream inputStream = resource.get().open()) {
                    JsonObject json = GsonHelper.parse(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

                    if (json.has("combat_skill_button")) {
                        JsonObject turnIndicatorJson = json.getAsJsonObject("combat_skill_button");
                        String imagePath = GsonHelper.getAsString(turnIndicatorJson, "image", "turnbasedcombatmod:textures/gui/combat_skill_button.png");
                        int width = GsonHelper.getAsInt(turnIndicatorJson, "width", 64);
                        int height = GsonHelper.getAsInt(turnIndicatorJson, "height", 64);

                        turnIndicator = new TurnIndicatorConfig(imagePath, width, height);
                        LOGGER.info("Loaded combat UI config from {}", UI_CONFIG_LOCATION);
                    }
                }
            } else {
                LOGGER.warn("Combat UI config not found at {}, using defaults", UI_CONFIG_LOCATION);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load combat UI config from {}", UI_CONFIG_LOCATION, e);
        }
    }

    public static TurnIndicatorConfig getTurnIndicator() {
        return turnIndicator;
    }
}
