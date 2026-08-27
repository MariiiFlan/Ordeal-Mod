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

import net.mcreator.ordeal.procedures.AbilityCallProcedure;
import net.mcreator.ordeal.OrdealMod;
import net.mcreator.ordeal.CustomPacketPayload;

@EventBusSubscriber
public record Ability2Message(int eventType, int pressedms) implements CustomPacketPayload {
	public static final Type<Ability2Message> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OrdealMod.MODID, "key_ability_2"));
	public static final StreamCodec<RegistryFriendlyByteBuf, Ability2Message> STREAM_CODEC = StreamCodec.of((RegistryFriendlyByteBuf buffer, Ability2Message message) -> {
		buffer.writeInt(message.eventType);
		buffer.writeInt(message.pressedms);
	}, (RegistryFriendlyByteBuf buffer) -> new Ability2Message(buffer.readInt(), buffer.readInt()));

	@Override
	public Type<Ability2Message> type() {
		return TYPE;
	}

	public static void handleData(final Ability2Message message, final IPayloadContext context) {
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

			AbilityCallProcedure.execute(world, entity);
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		OrdealMod.addNetworkMessage(Ability2Message.TYPE, Ability2Message.STREAM_CODEC, Ability2Message::handleData);
	}
}