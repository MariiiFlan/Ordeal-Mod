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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The debug overlay lives on the client, so the command has to reach across.
 * One boolean, one packet.
 */
@EventBusSubscriber
public record OrdealDebugPayload(boolean on, String filter, float scale)
		implements CustomPacketPayload {

	public static final Type<OrdealDebugPayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(OrdealMod.MODID, "debug_overlay"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OrdealDebugPayload> STREAM_CODEC =
			StreamCodec.of((RegistryFriendlyByteBuf buf, OrdealDebugPayload msg) -> {
				buf.writeBoolean(msg.on);
				buf.writeUtf(msg.filter, 32);
				buf.writeFloat(msg.scale);
			}, (RegistryFriendlyByteBuf buf) ->
					new OrdealDebugPayload(buf.readBoolean(), buf.readUtf(32), buf.readFloat()));

	@Override
	public Type<OrdealDebugPayload> type() {
		return TYPE;
	}

	/** Server-side memory of who has it up, so the command toggles rather than only turns on. */
	private static final Set<UUID> ON = new HashSet<>();

	/** Sentinel meaning "leave the filter alone", so toggling never resets it. */
	private static final String KEEP = "~keep~";

	public static void toggle(Entity e) {
		if (!(e instanceof ServerPlayer sp)) return;
		boolean now = !ON.contains(sp.getUUID());
		if (now) ON.add(sp.getUUID()); else ON.remove(sp.getUUID());
		PacketDistributor.sendToPlayer(sp, new OrdealDebugPayload(now, KEEP, -1f));
		sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
				now ? "§7debug overlay §aon" : "§7debug overlay §8off"));
	}

	/** Show one section only. Empty string puts everything back. */
	public static void filter(Entity e, String section) {
		if (!(e instanceof ServerPlayer sp)) return;
		ON.add(sp.getUUID());
		PacketDistributor.sendToPlayer(sp, new OrdealDebugPayload(true, section, -1f));
		sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
				section.isEmpty() ? "§7debug: all sections" : "§7debug: §f" + section));
	}

	/** Text size, 0.25 to 1.5. */
	public static void scale(Entity e, float scale) {
		if (!(e instanceof ServerPlayer sp)) return;
		ON.add(sp.getUUID());
		PacketDistributor.sendToPlayer(sp, new OrdealDebugPayload(true, KEEP, scale));
		sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
				"§7debug scale §f" + Math.round(scale * 100) + "%"));
	}



	public static void handleData(final OrdealDebugPayload msg, final IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (ctx.flow() != PacketFlow.CLIENTBOUND) return;
			if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) Client.apply(msg);
		});
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		OrdealMod.addNetworkMessage(TYPE, STREAM_CODEC, OrdealDebugPayload::handleData);
	}

	@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
	private static final class Client {
		private static void apply(OrdealDebugPayload m) {
			OrdealDebugOverlay.ENABLED = m.on();
			if (!KEEP.equals(m.filter())) OrdealDebugOverlay.FILTER = m.filter();
			if (m.scale() > 0) OrdealDebugOverlay.SCALE = m.scale();
		}
	}
}