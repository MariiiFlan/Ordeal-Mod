package net.mcreator.ordeal;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.mcreator.ordeal.core.OrdealCombat;

/**
 * Mobs get Ordeal stats too, rolled once on spawn and stored on the entity.
 * Tier scales with distance from world spawn, so the further out you fight the
 * harder things hit — and the more they're worth.
 *
 * Read any of these from a procedure with {@code entity.getPersistentData().getDouble("ordeal_str")}.
 */
@EventBusSubscriber(modid = "ordeal")
public final class OrdealMobStats {

	private OrdealMobStats() {}

	// ---- DEBUG TOGGLE -------------------------------------------------------
	// While true, every Warden, Iron Golem and Zombified Piglin spawns as a
	// Kimyo with a random talent, boosted stats and a guard. Flip to false
	// to turn the test mobs off.
	public static final boolean DEBUG_KIMYO_MOBS = true;
	// -------------------------------------------------------------------------

	public static final String STR   = "ordeal_str";
	public static final String DUR   = "ordeal_dur";
	public static final String XP    = "ordeal_xp";
	public static final String TIER  = "ordeal_tier";
	public static final String RACE   = "ordeal_race";
	public static final String TALENT = "ordeal_talent";
	private static final String DONE = "ordeal_stats_rolled";

	/** Blocks from spawn per tier step. */
	public static final double TIER_DISTANCE = 1200.0;
	public static final int MAX_TIER = 6;

	private static final ResourceLocation HP  = ResourceLocation.fromNamespaceAndPath("ordeal", "mob_health");
	private static final ResourceLocation ATK = ResourceLocation.fromNamespaceAndPath("ordeal", "mob_attack");

	@SubscribeEvent
	public static void onJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide()) return;
		if (!(event.getEntity() instanceof LivingEntity le) || le instanceof Player) return;
		if (le.getPersistentData().getBoolean(DONE)) return;

		int tier = tierAt(le);
		double baseHp = le.getMaxHealth();

		// The body decides. Vanilla health sets the physical base, tier
		// stretches it, then +/-15 randomises the individual — a piglin can
		// never roll Warden numbers.
		double j1 = (le.getRandom().nextDouble() * 2 - 1) * 15;
		double j2 = (le.getRandom().nextDouble() * 2 - 1) * 15;
		double str = Math.min(150, Math.max(0, Math.round(baseHp * 0.30 * (1 + tier * 0.35) + j1)));
		double dur = Math.min(150, Math.max(0, Math.round(baseHp * 0.22 * (1 + tier * 0.35) + j2)));
		double xp = Math.max(4, baseHp * 0.5 * (1 + tier * 0.6) + j1);

		if (DEBUG_KIMYO_MOBS && isDebugKimyo(le)) {
			String talent = TALENTS[le.getRandom().nextInt(TALENTS.length)];
			le.getPersistentData().putString(RACE, "kimyo");
			le.getPersistentData().putString(TALENT, talent);
			// a Kimyo is its body plus a talent — the body still sets the floor
			str = Math.min(150, str + 10);
			dur = Math.min(150, dur + 12);
			xp *= 2.5;
		} else {
			le.getPersistentData().putString(RACE, "human");
		}

		le.getPersistentData().putDouble(STR, str);
		le.getPersistentData().putDouble(DUR, dur);
		le.getPersistentData().putDouble(XP, Math.round(xp));
		le.getPersistentData().putInt(TIER, tier);
		le.getPersistentData().putBoolean(DONE, true);

		if (tier > 0) mod(le, Attributes.MAX_HEALTH, HP, baseHp * tier * 0.35);
		if (str > 0) mod(le, Attributes.ATTACK_DAMAGE, ATK, str * OrdealCombat.AP_PER_STR);
		if (tier > 0) le.setHealth(le.getMaxHealth());
	}

	// ---- presence fear ------------------------------------------------------
	// Villagers can feel a monster in the room. Only an EFFECTIVE presence in
	// the DANGEROUS band or above (raw presence scaled down by concealment)
	// scares them - conceal yourself and the village stays calm.
	public static final double FEAR_PRESENCE = net.mcreator.ordeal.OrdealTuning.d("fear.presence_threshold", 350);
	public static final double FEAR_RADIUS   = net.mcreator.ordeal.OrdealTuning.d("fear.radius", 10);

	@SubscribeEvent
	public static void onFearTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
		Player p = event.getEntity();
		if (p.level().isClientSide() || p.tickCount % 30 != 0) return;
		var v = p.getData(net.mcreator.ordeal.network.OrdealModVariables.PLAYER_VARIABLES);
		double presence = v.spLifetime + v.talentSP_Lifetime + v.talent1_strength + v.talent2_strength;
		double effective = presence * (1.0 - v.ChiConcealed);
		if (effective < FEAR_PRESENCE) return;

		for (net.minecraft.world.entity.npc.Villager vil : p.level().getEntitiesOfClass(
				net.minecraft.world.entity.npc.Villager.class,
				p.getBoundingBox().inflate(FEAR_RADIUS))) {
			var away = vil.position().subtract(p.position());
			away = new net.minecraft.world.phys.Vec3(away.x, 0, away.z);
			if (away.lengthSqr() < 0.01) away = new net.minecraft.world.phys.Vec3(1, 0, 0);
			away = away.normalize().scale(12);
			vil.getNavigation().moveTo(vil.getX() + away.x, vil.getY(), vil.getZ() + away.z, 1.1);
		}
	}

	private static boolean isDebugKimyo(LivingEntity le) {
		var t = le.getType();
		return t == EntityType.WARDEN || t == EntityType.IRON_GOLEM || t == EntityType.ZOMBIFIED_PIGLIN;
	}

	private static final String[] TALENTS = {
			"ilios", "kataigida", "hide_forge", "kirin", "chichioya_no_hai", "kong", "weapon_mastery" };

	/**
	 * Mob persistentData never leaves the server on its own, so sense, the
	 * silhouette and the debug panel would all read zeroes. When a player
	 * starts tracking a rolled mob, its numbers are mirrored down once.
	 */
	@SubscribeEvent
	public static void onStartTracking(PlayerEvent.StartTracking event) {
		if (!(event.getEntity() instanceof ServerPlayer sp)) return;
		if (!(event.getTarget() instanceof LivingEntity le) || le instanceof Player) return;
		var tag = le.getPersistentData();
		if (!tag.getBoolean(DONE)) return;
		PacketDistributor.sendToPlayer(sp, new Sync(le.getId(),
				tag.getString(RACE), tag.getString(TALENT),
				(float) tag.getDouble(STR), (float) tag.getDouble(DUR), (float) tag.getDouble(XP)));
	}

	/** Client mirror of one mob's rolled data. */
	@EventBusSubscriber(modid = "ordeal", bus = EventBusSubscriber.Bus.MOD)
	public record Sync(int entityId, String race, String talent, float str, float dur, float xp)
			implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

		public static final Type<Sync> TYPE =
				new Type<>(ResourceLocation.fromNamespaceAndPath("ordeal", "mob_stats"));

		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = new StreamCodec<>() {
			@Override
			public void encode(RegistryFriendlyByteBuf buf, Sync p) {
				buf.writeVarInt(p.entityId).writeUtf(p.race).writeUtf(p.talent);
				buf.writeFloat(p.str);
				buf.writeFloat(p.dur);
				buf.writeFloat(p.xp);
			}

			@Override
			public Sync decode(RegistryFriendlyByteBuf buf) {
				return new Sync(buf.readVarInt(), buf.readUtf(), buf.readUtf(),
						buf.readFloat(), buf.readFloat(), buf.readFloat());
			}
		};

		@Override
		public Type<Sync> type() {
			return TYPE;
		}

		@SubscribeEvent
		public static void onRegister(RegisterPayloadHandlersEvent event) {
			event.registrar("ordeal").playToClient(TYPE, STREAM_CODEC, Sync::handle);
		}

		private static void handle(Sync p, IPayloadContext ctx) {
			ctx.enqueueWork(() -> {
				if (FMLEnvironment.dist.isClient()) Client.accept(p);
			});
		}

		@OnlyIn(Dist.CLIENT)
		private static final class Client {
			private static void accept(Sync p) {
				var level = net.minecraft.client.Minecraft.getInstance().level;
				if (level == null) return;
				var e = level.getEntity(p.entityId());
				if (e == null) return;
				var tag = e.getPersistentData();
				tag.putString(RACE, p.race());
				tag.putString(TALENT, p.talent());
				tag.putDouble(STR, p.str());
				tag.putDouble(DUR, p.dur());
				tag.putDouble(XP, p.xp());
			}
		}
	}

	public static int tierAt(LivingEntity le) {
		if (le.level().isClientSide()) return 0;
		var spawn = le.level().getSharedSpawnPos();
		double d = Math.sqrt(le.distanceToSqr(spawn.getX() + 0.5, le.getY(), spawn.getZ() + 0.5));
		return (int) Math.min(MAX_TIER, d / TIER_DISTANCE);
	}

	public static double guardMaxOf(LivingEntity le) {
		double dur = le.getPersistentData().getDouble(DUR);
		return dur <= 0 ? 0 : OrdealCombat.GUARD_BASE + dur * OrdealCombat.GUARD_PER_DUR;
	}

	private static void mod(LivingEntity le, Holder<Attribute> attr, ResourceLocation id, double value) {
		AttributeInstance inst = le.getAttribute(attr);
		if (inst == null || value == 0) return;
		if (inst.getModifier(id) != null) inst.removeModifier(id);
		inst.addPermanentModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
	}
}