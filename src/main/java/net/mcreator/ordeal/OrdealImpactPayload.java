package net.mcreator.ordeal;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A big comic word over a hit — "KA-BAM!", "STAK!". The word itself is picked on
 * the client from a table, so the packet only carries which weight class the hit
 * was and a seed to vary the pick; editing the words never touches the protocol.
 *
 * Nothing else in the mod has to call this. The damage hook at the bottom watches
 * every LivingIncomingDamageEvent at LOWEST priority, so it reads the number
 * AFTER OrdealCombat has finished with guard, chip and absorption.
 */
@EventBusSubscriber(modid = "ordeal", bus = EventBusSubscriber.Bus.MOD)
public record OrdealImpactPayload(double x, double y, double z, float amount, int tier, int seed)
		implements CustomPacketPayload {

	/** Weight classes. NONE never leaves the server. */
	public static final int NONE = 0, SOLID = 1, HEAVY = 2, MASSIVE = 3;

	public static final Type<OrdealImpactPayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath("ordeal", "impact_word"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OrdealImpactPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.DOUBLE, OrdealImpactPayload::x,
					ByteBufCodecs.DOUBLE, OrdealImpactPayload::y,
					ByteBufCodecs.DOUBLE, OrdealImpactPayload::z,
					ByteBufCodecs.FLOAT, OrdealImpactPayload::amount,
					ByteBufCodecs.VAR_INT, OrdealImpactPayload::tier,
					ByteBufCodecs.VAR_INT, OrdealImpactPayload::seed,
					OrdealImpactPayload::new);

	@Override
	public Type<OrdealImpactPayload> type() {
		return TYPE;
	}

	@SubscribeEvent
	public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
		event.registrar("ordeal").playToClient(TYPE, STREAM_CODEC, OrdealImpactPayload::handle);
	}

	private static void handle(OrdealImpactPayload p, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (FMLEnvironment.dist.isClient()) Client.accept(p);
		});
	}

	@OnlyIn(Dist.CLIENT)
	private static final class Client {
		private static void accept(OrdealImpactPayload p) {
			OrdealImpactWords.spawn(p.x(), p.y(), p.z(), p.amount(), p.tier(), p.seed());
		}
	}

	// ---- server side --------------------------------------------------------

	/** Flat damage floors. A hit only needs to clear ONE of the two tests. */
	public static final float SOLID_FLAT = 5f, HEAVY_FLAT = 10f, MASSIVE_FLAT = 20f;
	/** Fraction of the victim's max health. */
	public static final float SOLID_PCT = 0.10f, HEAVY_PCT = 0.20f, MASSIVE_PCT = 0.40f;
	/** How far away the word is visible, in blocks. */
	public static final double RANGE = 48;

	public static int tierFor(LivingEntity victim, float damage) {
		if (damage <= 0) return NONE;
		float max = Math.max(1f, victim.getMaxHealth());
		float pct = damage / max;
		if (damage >= MASSIVE_FLAT || pct >= MASSIVE_PCT) return MASSIVE;
		if (damage >= HEAVY_FLAT || pct >= HEAVY_PCT) return HEAVY;
		if (damage >= SOLID_FLAT || pct >= SOLID_PCT) return SOLID;
		return NONE;
	}

	/** Fire a word by hand, e.g. from a procedure or an ability's own code. */
	public static void send(LivingEntity victim, float damage, int tier) {
		if (tier == NONE || victim == null) return;
		if (!(victim.level() instanceof ServerLevel sl)) return;
		int seed = (int) ((victim.getId() * 31L + sl.getGameTime()) & 0x7FFFFFFF);
		OrdealImpactPayload p = new OrdealImpactPayload(
				victim.getX(), victim.getY() + victim.getBbHeight() * 0.85, victim.getZ(),
				damage, tier, seed);
		for (ServerPlayer sp : sl.players())
			if (sp.distanceToSqr(victim) <= RANGE * RANGE)
				PacketDistributor.sendToPlayer(sp, p);
	}

	@EventBusSubscriber(modid = "ordeal")
	public static final class Hook {
		private Hook() {}

		/**
		 * LOWEST so every other handler — guard, chip, absorption — has already
		 * had its say and the number we shout is the number that lands.
		 */
		@SubscribeEvent(priority = EventPriority.LOWEST)
		public static void onIncoming(LivingIncomingDamageEvent event) {
			if (event.isCanceled()) return;
			LivingEntity victim = event.getEntity();
			if (victim.level().isClientSide()) return;

			float dmg = event.getAmount();
			int tier = tierFor(victim, dmg);
			if (tier == NONE) return;

			// only hits somebody dealt - fall damage and cactus get no comic panel
			Entity src = event.getSource().getEntity();
			if (!(src instanceof LivingEntity)) return;

			send(victim, dmg, tier);
		}
	}
}