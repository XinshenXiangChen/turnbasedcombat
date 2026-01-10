package net.dehydrated_pain.turnbasedcombatmod.network;

import io.netty.buffer.ByteBuf;
import net.dehydrated_pain.turnbasedcombatmod.utils.combatresponse.DodgeTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

public record QTERequestPacket(DodgeTypes dodgeType) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QTERequestPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "qte_request"));

    // StreamCodec for serializing/deserializing the packet with DodgeTypes enum
    public static final StreamCodec<ByteBuf, QTERequestPacket> STREAM_CODEC = StreamCodec.composite(
            // Encode/decode DodgeTypes by ordinal (as byte, since we have < 256 enum values)
            ByteBufCodecs.BYTE.map(
                    ordinal -> DodgeTypes.values()[ordinal & 0xFF],
                    dodgeType -> (byte) dodgeType.ordinal()
            ),
            QTERequestPacket::dodgeType,
            QTERequestPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
