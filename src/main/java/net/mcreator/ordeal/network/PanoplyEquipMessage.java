package net.mcreator.ordeal.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;

import net.mcreator.ordeal.OrdealMod;
import net.mcreator.ordeal.CustomPacketPayload;

@EventBusSubscriber
public record PanoplyEquipMessage(int eventType, int pressedms) implements CustomPacketPayload {
	public static final Type<PanoplyEquipMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OrdealMod.MODID, "key_panoply_equip"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PanoplyEquipMessage> STREAM_CODEC = StreamCodec.of((RegistryFriendlyByteBuf buffer, PanoplyEquipMessage message) -> {
		buffer.writeInt(message.eventType);
		buffer.writeInt(message.pressedms);
	}, (RegistryFriendlyByteBuf buffer) -> new PanoplyEquipMessage(buffer.readInt(), buffer.readInt()));

	@Override
	public Type<PanoplyEquipMessage> type() {
		return TYPE;
	}

	public static void handleData(final PanoplyEquipMessage message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
			}).exceptionally(e -> {
				context.connection().disconnect(Component.literal(e.getMessage()));
				return null;
			});
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		OrdealMod.addNetworkMessage(PanoplyEquipMessage.TYPE, PanoplyEquipMessage.STREAM_CODEC, PanoplyEquipMessage::handleData);
	}
}