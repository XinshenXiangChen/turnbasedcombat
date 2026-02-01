package net.dehydrated_pain.turnbasedcombatmod.combat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

/**
 * Centralized key mappings for turn-based combat mod.
 * All combat-related key bindings are defined here.
 */
@EventBusSubscriber(
        modid = MODID,
        value = Dist.CLIENT
)
public final class ClientKeyMappings {
    // Category for all combat key bindings
    private static final String CATEGORY = "key.categories." + MODID + ".combat";
    
    // Ability Selection Keys
    public static final KeyMapping ABILITY_ATTACK = new KeyMapping(
            "key." + MODID + ".ability.attack",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            CATEGORY
    );
    
    public static final KeyMapping ABILITY_SKILL = new KeyMapping(
            "key." + MODID + ".ability.skill",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            CATEGORY
    );
    
    public static final KeyMapping ABILITY_ITEM = new KeyMapping(
            "key." + MODID + ".ability.item",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY
    );
    
    // Skill Selection Keys
    public static final KeyMapping SKILL_1 = new KeyMapping(
            "key." + MODID + ".skill.1",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY
    );
    
    public static final KeyMapping SKILL_2 = new KeyMapping(
            "key." + MODID + ".skill.2",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            CATEGORY
    );
    
    public static final KeyMapping SKILL_3 = new KeyMapping(
            "key." + MODID + ".skill.3",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY
    );
    
    // Enemy Selection Keys
    public static final KeyMapping ENEMY_PREVIOUS = new KeyMapping(
            "key." + MODID + ".enemy.previous",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY
    );
    
    public static final KeyMapping ENEMY_NEXT = new KeyMapping(
            "key." + MODID + ".enemy.next",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY
    );
    
    // Parry/Dodge Keys
    public static final KeyMapping PARRY_E = new KeyMapping(
            "key." + MODID + ".parry.e",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_E,
            CATEGORY
    );
    
    public static final KeyMapping PARRY_SHIFT = new KeyMapping(
            "key." + MODID + ".parry.shift",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            CATEGORY
    );
    
    public static final KeyMapping PARRY_SPACE = new KeyMapping(
            "key." + MODID + ".parry.space",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_SPACE,
            CATEGORY
    );
    
    public static final KeyMapping DODGE_Q = new KeyMapping(
            "key." + MODID + ".dodge.q",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Q,
            CATEGORY
    );

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        // Ability selection
        event.register(ABILITY_ATTACK);
        event.register(ABILITY_SKILL);
        event.register(ABILITY_ITEM);
        
        // Skill selection
        event.register(SKILL_1);
        event.register(SKILL_2);
        event.register(SKILL_3);
        
        // Enemy selection
        event.register(ENEMY_PREVIOUS);
        event.register(ENEMY_NEXT);
        
        // Parry/Dodge
        event.register(PARRY_E);
        event.register(PARRY_SHIFT);
        event.register(PARRY_SPACE);
        event.register(DODGE_Q);
    }
}
