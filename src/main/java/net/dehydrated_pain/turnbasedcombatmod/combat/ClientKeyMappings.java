package net.dehydrated_pain.turnbasedcombatmod.combat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

@EventBusSubscriber(
        modid = MODID,
        value = Dist.CLIENT
)
public final class ClientKeyMappings {
    // TODO: Change modid things
    public static final KeyMapping MY_KEY = new KeyMapping(
            "key.mymod.my_key",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_E,
            "key.categories.mymod"
    );

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(MY_KEY);
    }
}
