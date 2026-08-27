package net.mcreator.ordeal.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.SectionPos;

import net.mcreator.ordeal.procedures.OpenCodeFieldTerminalProcedure;
import net.mcreator.ordeal.OrdealMod;
import net.mcreator.ordeal.CustomPacketPayload;

@EventBusSubscriber
public record KodeFieldTerminalMessage(int eventType, int pressedms) implements CustomPacketPayload {
	public static final Type<KodeFieldTerminalMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OrdealMod.MODID, "key_kode_field_terminal"));
	public static final StreamCodec<RegistryFriendlyByteBuf, KodeFieldTerminalMessage> STREAM_CODEC = StreamCodec.of((RegistryFriendlyByteBuf buffer, KodeFieldTerminalMessage message) -> {
		buffer.writeInt(message.eventType);
		buffer.writeInt(message.pressedms);
	}, (RegistryFriendlyByteBuf buffer) -> new KodeFieldTerminalMessage(buffer.readInt(), buffer.readInt()));

	@Override
	public Type<KodeFieldTerminalMessage> type() {
		return TYPE;
	}

	public static void handleData(final KodeFieldTerminalMessage message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
				pressAction(context.player(), message.eventType, message.pressedms);
			}).exceptionally(e -> {
				context.connection().disconnect(Component.literal(e.getMessage()));
				return null;
			});
		}
	}

	public static void pressAction(Player entity, int type, int pressedms) {
		Level world = entity.level();
		double x = entity.getX();
		double y = entity.getY();
		double z = entity.getZ();
		// security measure to prevent arbitrary chunk generation
		if (!world.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)))
			return;
		if (type == 0) {

			OpenCodeFieldTerminalProcedure.execute(world, x, y, z, entity);
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		OrdealMod.addNetworkMessage(KodeFieldTerminalMessage.TYPE, KodeFieldTerminalMessage.STREAM_CODEC, KodeFieldTerminalMessage::handleData);
	}
}