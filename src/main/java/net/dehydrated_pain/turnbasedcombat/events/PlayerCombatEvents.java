package net.dehydrated_pain.turnbasedcombat.events;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import static net.dehydrated_pain.turnbasedcombat.TurnBasedCombatMod.LOGGER;
import static net.dehydrated_pain.turnbasedcombat.TurnBasedCombatMod.MODID;

@EventBusSubscriber(modid = MODID)
public class PlayerCombatEvents {

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);

    }


    /*
    setCombatEnvironment should create a carbon copy of the surroundings of the player, then teleport the player and the enemies into the world
     */
    private void setCombatEnvironment() {

    }



}
