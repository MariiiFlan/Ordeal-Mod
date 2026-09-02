package net.mcreator.ordeal;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber
public record EnhancementPayload(String action, String talentId, String choice)
		implements CustomPacketPayload {

	public static final String SYNC = "sync";
	public static final String PICK = "pick";
	public static final String OPEN = "open";

	public static final Type<EnhancementPayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(OrdealMod.MODID, "enhancement"));

	public static final StreamCodec<RegistryFriendlyByteBuf, EnhancementPayload> STREAM_CODEC =
			StreamCodec.of((RegistryFriendlyByteBuf buf, EnhancementPayload msg) -> {
				buf.writeUtf(msg.action, 16);
				buf.writeUtf(msg.talentId, 64);
				buf.writeUtf(msg.choice, 512);
			}, (RegistryFriendlyByteBuf buf) ->
					new EnhancementPayload(buf.readUtf(16), buf.readUtf(64), buf.readUtf(512)));

	@Override
	public Type<EnhancementPayload> type() {
		return TYPE;
	}

	public static void sync(Entity e) {
		if (!(e instanceof ServerPlayer sp)) return;
		PacketDistributor.sendToPlayer(sp, new EnhancementPayload(SYNC, "", Enhancements.encode(sp)));
	}

	public static void openFor(Entity e) {
		if (!(e instanceof ServerPlayer sp)) return;
		PacketDistributor.sendToPlayer(sp, new EnhancementPayload(OPEN, "", Enhancements.encode(sp)));
	}

	public static void choose(String talentId, String enhancementId) {
		PacketDistributor.sendToServer(new EnhancementPayload(PICK, talentId, enhancementId));
	}

	public static void handleData(final EnhancementPayload message, final IPayloadContext context) {
		context.enqueueWork(() -> {
			if (context.flow() == PacketFlow.CLIENTBOUND) {
				Enhancements.applyClient(message.choice);
				if (OPEN.equals(message.action)) EnhancementPrompt.openNow();
				return;
			}
			if (!PICK.equals(message.action)) return;
			Player p = context.player();
			if (p == null) return;
			Enhancements.pick(p, message.talentId, message.choice);
		});
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		OrdealMod.addNetworkMessage(TYPE, STREAM_CODEC, EnhancementPayload::handleData);
	}
}