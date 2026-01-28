package net.dehydrated_pain.turnbasedcombatmod.network;

import net.dehydrated_pain.turnbasedcombatmod.combat.CombatInstanceClient;
import net.dehydrated_pain.turnbasedcombatmod.combat.CombatInstanceServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

@EventBusSubscriber(modid = MODID)
public class NetworkHandler {
    @SubscribeEvent // on the mod event bus
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");


        // server to client
        registrar.playToClient(
                StartCombatPacket.TYPE,
                StartCombatPacket.STREAM_CODEC,
                CombatInstanceClient::startCombatNetworkHandler
        );

        registrar.playToClient(
                EndCombatPacket.TYPE,
                EndCombatPacket.STREAM_CODEC,
                CombatInstanceClient::endCombatNetworkHandler
        );

        registrar.playToClient(
                PlayerTurnPacket.TYPE,
                PlayerTurnPacket.STREAM_CODEC,
                CombatInstanceClient::playerTurnNetworkHandler
        );

        registrar.playToClient(
                QTERequestPacket.TYPE,
                QTERequestPacket.STREAM_CODEC,
                CombatInstanceClient::qteRequesteNetworkHandler
        );

        // client to server
        registrar.playToServer(
                QTEResponsePacket.TYPE,
                QTEResponsePacket.STREAM_CODEC,
                CombatInstanceServer::qteResponseNetworkHandler
        );

        registrar.playToServer(
                EndPlayerTurnPacket.TYPE,
                EndPlayerTurnPacket.STREAM_CODEC,
                CombatInstanceServer::endPlayerTurnNetworkHandler
        );




    }
}


