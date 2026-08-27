package net.mcreator.ordeal;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> every viewer of targetId: play clip {@code name} for {@code loops}
 * ({@code -1} forever). {@code name} may be comma-separated for a stitched combo.
 * {@code blend} is cross-fade ticks; -1 uses the default, 0 snaps.
 */
@EventBusSubscriber(modid = "ordeal", bus = EventBusSubscriber.Bus.MOD)
public record OrdealAnimPayload(boolean stop, int targetId, String name, int loops, int blend)
		implements CustomPacketPayload {

	public static final Type<OrdealAnimPayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath("ordeal", "anim_play"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OrdealAnimPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.BOOL, OrdealAnimPayload::stop,
					ByteBufCodecs.VAR_INT, OrdealAnimPayload::targetId,
					ByteBufCodecs.STRING_UTF8, OrdealAnimPayload::name,
					ByteBufCodecs.VAR_INT, OrdealAnimPayload::loops,
					ByteBufCodecs.VAR_INT, OrdealAnimPayload::blend,
					OrdealAnimPayload::new);

	@Override
	public Type<OrdealAnimPayload> type() {
		return TYPE;
	}

	@SubscribeEvent
	public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
		event.registrar("ordeal").playToClient(TYPE, STREAM_CODEC, OrdealAnimPayload::handle);
	}

	private static void handle(OrdealAnimPayload p, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (FMLEnvironment.dist.isClient()) Client.accept(p);
		});
	}

	@OnlyIn(Dist.CLIENT)
	private static final class Client {
		private static void accept(OrdealAnimPayload p) {
			var level = net.minecraft.client.Minecraft.getInstance().level;
			if (level == null) return;
			var e = level.getEntity(p.targetId());
			if (!(e instanceof net.minecraft.world.entity.player.Player player)) return;
			if (p.stop()) OrdealAnimPlayback.stop(player, p.name(), p.blend());
			else OrdealAnimPlayback.play(player, p.name(), p.loops(), p.blend());
		}
	}
}
