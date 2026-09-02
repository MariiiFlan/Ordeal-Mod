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
 * The state lives in the player's persistent data, which the server never sends
 * anywhere. Anything on the client asking StageLadder what stage you are on -
 * the debug overlay, the HUD, the terminal - was reading an empty copy and
 * getting "none / UNPICKED" back while the server had it right all along.
 *
 * This is the mirror, exactly the way Passives mirrors its off-list: the server
 * pushes stage and variant on every change and on login, the client keeps them
 * in StageLadder.CLIENT_STAGE / CLIENT_VARIANT, and the readouts stop lying.
 */
@EventBusSubscriber
public record StageLadderPayload(int stage, String variant) implements CustomPacketPayload {

	public static final Type<StageLadderPayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(OrdealMod.MODID, "stage_ladder"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StageLadderPayload> STREAM_CODEC =
			StreamCodec.of((RegistryFriendlyByteBuf buf, StageLadderPayload msg) -> {
				buf.writeVarInt(msg.stage);
				buf.writeUtf(msg.variant, 16);
			}, (RegistryFriendlyByteBuf buf) ->
					new StageLadderPayload(buf.readVarInt(), buf.readUtf(16)));

	@Override
	public Type<StageLadderPayload> type() {
		return TYPE;
	}

	public static void sync(Entity e) {
		if (!(e instanceof ServerPlayer sp)) return;
		PacketDistributor.sendToPlayer(sp,
				new StageLadderPayload(StageLadder.stage(sp), StageLadder.variant(sp)));
	}

	public static void handleData(final StageLadderPayload msg, final IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (ctx.flow() != PacketFlow.CLIENTBOUND) return;
			StageLadder.CLIENT_STAGE = msg.stage();
			StageLadder.CLIENT_VARIANT = msg.variant();
		});
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		OrdealMod.addNetworkMessage(TYPE, STREAM_CODEC, StageLadderPayload::handleData);
	}
}