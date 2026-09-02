package net.mcreator.ordeal;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The Ilios State variant pick, on the same rails the enhancement pick runs on:
 * server says OPEN, the client puts up a real screen, the client sends PICK back.
 *
 * The action bar version this replaces was too easy to miss - it flashed once,
 * mid-fight, for a permanent choice.
 */
@EventBusSubscriber
public record IliosStatePayload(String action, String choice) implements CustomPacketPayload {

	public static final String OPEN = "open";
	public static final String PICK = "pick";

	public static final Type<IliosStatePayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(OrdealMod.MODID, "ilios_state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, IliosStatePayload> STREAM_CODEC =
			StreamCodec.of((RegistryFriendlyByteBuf buf, IliosStatePayload msg) -> {
				buf.writeUtf(msg.action, 16);
				buf.writeUtf(msg.choice, 32);
			}, (RegistryFriendlyByteBuf buf) ->
					new IliosStatePayload(buf.readUtf(16), buf.readUtf(32)));

	@Override
	public Type<IliosStatePayload> type() {
		return TYPE;
	}

	public static void openFor(Entity e) {
		if (!(e instanceof ServerPlayer sp)) return;
		PacketDistributor.sendToPlayer(sp, new IliosStatePayload(OPEN, ""));
	}

	/** Called by the screen when a card is confirmed. */
	public static void choose(String variant) {
		PacketDistributor.sendToServer(new IliosStatePayload(PICK, variant));
	}

	public static void handleData(final IliosStatePayload msg, final IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (ctx.flow() == PacketFlow.CLIENTBOUND) {
				if (OPEN.equals(msg.action())) IliosStatePrompt.openNow();
				return;
			}
			if (!PICK.equals(msg.action())) return;
			if (!(ctx.player() instanceof ServerPlayer sp)) return;
			// the confirming press is not wasted - it drops you into stage 1
			StageLadder.confirmVariant(sp, msg.choice());
		});
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		OrdealMod.addNetworkMessage(TYPE, STREAM_CODEC, IliosStatePayload::handleData);
	}
}