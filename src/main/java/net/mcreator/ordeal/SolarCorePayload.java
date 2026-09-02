package net.mcreator.ordeal;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = "ordeal", bus = EventBusSubscriber.Bus.MOD)
public record SolarCorePayload(int kind, int entityId, double x, double y, double z,
		float intensity, float radius, int ticks) implements CustomPacketPayload {

	public static final int HELD  = 0;
	public static final int HANG  = 1;
	public static final int BURST = 2;
	public static final int FLY   = 3;

	public static final Type<SolarCorePayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath("ordeal", "solar_core"));

	// composite() tops out at six fields in this version - eight fields go
	// through a hand-rolled codec instead.
	public static final StreamCodec<RegistryFriendlyByteBuf, SolarCorePayload> STREAM_CODEC =
			StreamCodec.of(SolarCorePayload::write, SolarCorePayload::read);

	private static void write(RegistryFriendlyByteBuf buf, SolarCorePayload p) {
		buf.writeVarInt(p.kind());
		buf.writeVarInt(p.entityId());
		buf.writeDouble(p.x());
		buf.writeDouble(p.y());
		buf.writeDouble(p.z());
		buf.writeFloat(p.intensity());
		buf.writeFloat(p.radius());
		buf.writeVarInt(p.ticks());
	}

	private static SolarCorePayload read(RegistryFriendlyByteBuf buf) {
		return new SolarCorePayload(buf.readVarInt(), buf.readVarInt(),
				buf.readDouble(), buf.readDouble(), buf.readDouble(),
				buf.readFloat(), buf.readFloat(), buf.readVarInt());
	}

	@Override
	public Type<SolarCorePayload> type() {
		return TYPE;
	}

	public static void held(Player p, float intensity) {
		held(p, intensity, 0, 0);
	}

	/** radius = the orb's real display size in blocks; ticks carries the charge stage. */
	public static void held(Player p, float intensity, float radius, int stage) {
		if (p == null || p.level().isClientSide()) return;
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(p,
				new SolarCorePayload(HELD, p.getId(), 0, 0, 0, intensity, radius, stage));
	}

	public static void hang(ServerLevel sl, Vec3 at, int stage, int ticks, float radius) {
		if (sl == null || at == null) return;
		PacketDistributor.sendToPlayersNear(sl, null, at.x, at.y, at.z, 160,
				new SolarCorePayload(HANG, -1, at.x, at.y, at.z, stage / 5f, radius, ticks));
	}

	/** A sun in flight - id keys the client-side orb, intensity < 0 removes it. */
	public static void fly(ServerLevel sl, int id, Vec3 at, float intensity, float radius) {
		if (sl == null || at == null) return;
		PacketDistributor.sendToPlayersNear(sl, null, at.x, at.y, at.z, 260,
				new SolarCorePayload(FLY, id, at.x, at.y, at.z, intensity, radius, 0));
	}

	public static void burst(ServerLevel sl, Vec3 at, int stage, double radius) {
		if (sl == null || at == null) return;
		PacketDistributor.sendToPlayersNear(sl, null, at.x, at.y, at.z, 240,
				new SolarCorePayload(BURST, -1, at.x, at.y, at.z, stage / 5f, (float) radius, 0));
	}

	@SubscribeEvent
	public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
		event.registrar("ordeal").playToClient(TYPE, STREAM_CODEC, SolarCorePayload::handle);
	}

	private static void handle(SolarCorePayload p, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (FMLEnvironment.dist.isClient()) Client.accept(p);
		});
	}

	@OnlyIn(Dist.CLIENT)
	private static final class Client {
		private static void accept(SolarCorePayload p) {
			SolarCore.accept(p);
		}
	}
}