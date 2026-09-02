package net.mcreator.ordeal;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.mcreator.ordeal.network.OrdealModVariables;

public final class OrdealHeavy {

	private OrdealHeavy() {}

	public static final boolean REQUIRE_COMBAT_MODE = true;
	public static int COOLDOWN_TICKS = OrdealTuning.i("heavy.cooldown_ticks", 50);
	public static double CHI_COST = OrdealTuning.d("heavy.chi_cost", 8);
	public static double RANGE = OrdealTuning.d("heavy.range", 4.0);
	public static double DAMAGE_MULT = OrdealTuning.d("heavy.damage_mult", 1.6);
	public static double KB_PER_STR = OrdealTuning.d("heavy.kb_per_str", 0.06);
	public static int WINDUP_TICKS = OrdealTuning.i("heavy.windup_ticks", 4);

	public static final String[] IMPACT_FRAMES = {
			"darknesscore shader play @s 1 zoomblur0 1 blackwhite_ipf0 1 blackwhite_ipf1 2 shake 50 6",
			"darknesscore shader play @s 1 red_zoom 1 blackwhite_red_ipf0 1 blackwhite_red_ipf1 2 shake 50 5",
	};
	public static double IMPACT_CHANCE = OrdealTuning.d("heavy.impact_frame_chance", 0.35);

	public static final String[] HEAVY_ANIMS = { "heavy_punch" };

	private static final String CD_KEY = "ordeal_heavy_cd";

	public static final java.util.Set<String> USE_EXEMPT =
						new java.util.HashSet<>(java.util.Arrays.asList("akontio", "ilios_claymore"));

	public static boolean keepsOwnUse(net.minecraft.world.item.ItemStack st) {
		if (st == null || st.isEmpty()) return false;
		var id = BuiltInRegistries.ITEM.getKey(st.getItem());
		return id != null && "ordeal".equals(id.getNamespace()) && USE_EXEMPT.contains(id.getPath());
	}

	@EventBusSubscriber(modid = "ordeal")
	public static final class Use {
		@SubscribeEvent
		public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
			if (event.getLevel().isClientSide()) return;
			if (!keepsOwnUse(event.getItemStack())) return;
			var id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
			if (AbilityHold.once(event.getEntity(), "use_" + (id == null ? "x" : id.getPath()), 2)) return;
			event.setCanceled(true);
			event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
		}
	}

	public static void execute(ServerPlayer p) {
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		if (REQUIRE_COMBAT_MODE && !v.combatMode) return;

		long now = p.level().getGameTime();
		if (p.getPersistentData().getLong(CD_KEY) > now) return;

		double cost = CHI_COST * OrdealCombo.costMult(p);
		if (v.chi < cost) {
			p.level().playSound(null, p.blockPosition(),
					SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
			return;
		}
		p.getPersistentData().putLong(CD_KEY, now + COOLDOWN_TICKS);
		v.chi -= cost;
		v.markSyncDirty();

		if (HEAVY_ANIMS.length > 0)
			OrdealAnim.play(p, HEAVY_ANIMS[p.getRandom().nextInt(HEAVY_ANIMS.length)], 1);
		p.swing(InteractionHand.MAIN_HAND, true);

		LivingEntity target = findTarget(p);
		OrdealMod.queueServerWork(WINDUP_TICKS, () -> land(p, target));
	}

	private static void land(ServerPlayer p, LivingEntity target) {
		if (!p.isAlive() || !(p.level() instanceof ServerLevel level)) return;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);

		Vec3 at = target != null && target.isAlive()
				? target.position().add(0, target.getBbHeight() * 0.5, 0)
				: p.getEyePosition().add(p.getLookAngle().scale(2.5));

		SoundEvent snd = BuiltInRegistries.SOUND_EVENT
				.getOptional(ResourceLocation.fromNamespaceAndPath("ordeal", "heavy_punch"))
				.orElse(SoundEvents.PLAYER_ATTACK_KNOCKBACK);
		level.playSound(null, at.x, at.y, at.z, snd, SoundSource.PLAYERS, 1.4f, 0.9f);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 3, 0.4, 0.3, 0.4, 0);

		if (target == null || !target.isAlive()) return;

		double dmg = p.getAttributeValue(Attributes.ATTACK_DAMAGE) * DAMAGE_MULT;

		net.mcreator.ordeal.core.OrdealChiRefund.markHeavy(p);
		target.hurt(p.damageSources().playerAttack(p), (float) dmg);

		double dur = target instanceof Player tp
				? tp.getData(OrdealModVariables.PLAYER_VARIABLES).statDurability
				: target.getPersistentData().getDouble(OrdealMobStats.DUR);
		double kb = Math.max(0.25, Math.min(3.2, (v.statStrength - dur) * KB_PER_STR));
		Vec3 dir = p.getLookAngle();
		target.setDeltaMovement(target.getDeltaMovement()
				.add(dir.x * kb, 0.15 + kb * 0.12, dir.z * kb));
		target.hurtMarked = true;
		shake(target, 2, 10);

		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class,
				target.getBoundingBox().inflate(2.5), e -> e != p && e != target && e.isPickable())) {
			Vec3 away = near.position().subtract(target.position()).normalize();
			near.setDeltaMovement(near.getDeltaMovement()
					.add(away.x * kb * 0.4, 0.1, away.z * kb * 0.4));
			near.hurtMarked = true;
		}

		if (IMPACT_FRAMES.length > 0 && p.getRandom().nextDouble() < IMPACT_CHANCE)
			impactFrame(p);
	}

	private static void impactFrame(ServerPlayer p) {
		if (p.getServer() == null) return;
		String cmd = IMPACT_FRAMES[p.getRandom().nextInt(IMPACT_FRAMES.length)];
		p.getServer().getCommands().performPrefixedCommand(
				new net.minecraft.commands.CommandSourceStack(
						net.minecraft.commands.CommandSource.NULL, p.position(), p.getRotationVector(),
						(ServerLevel) p.level(), 4, p.getName().getString(), p.getDisplayName(),
						p.getServer(), p),
				cmd);
	}

	private static LivingEntity findTarget(ServerPlayer p) {
		Vec3 eye = p.getEyePosition(), dir = p.getLookAngle(), end = eye.add(dir.scale(RANGE));
		AABB box = p.getBoundingBox().expandTowards(dir.scale(RANGE)).inflate(1.0);
		LivingEntity best = null;
		double bestD = RANGE * RANGE;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box,
				x -> x != p && x.isAlive() && x.isPickable())) {
			var hit = e.getBoundingBox().inflate(0.35).clip(eye, end);
			if (hit.isPresent()) {
				double d = hit.get().distanceToSqr(eye);
				if (d < bestD) { bestD = d; best = e; }
			}
		}
		return best;
	}

	private static void shake(LivingEntity e, int amplifier, int ticks) {
		var key = ResourceKey.create(Registries.MOB_EFFECT,
				ResourceLocation.fromNamespaceAndPath("ordeal", "screen_shake"));
		BuiltInRegistries.MOB_EFFECT.getHolder(key)
				.ifPresent(h -> e.addEffect(new net.minecraft.world.effect.MobEffectInstance(
						h, ticks, amplifier, false, false)));
	}

	@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
	@OnlyIn(Dist.CLIENT)
	public static final class Client {

		@SubscribeEvent
		public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
			if (!event.isUseItem()) return;
			var pl = Minecraft.getInstance().player;
			if (pl == null || !pl.getData(OrdealModVariables.PLAYER_VARIABLES).combatMode) return;

			if (keepsOwnUse(pl.getMainHandItem()) || keepsOwnUse(pl.getOffhandItem())) return;
			event.setCanceled(true);
			event.setSwingHand(false);
			PacketDistributor.sendToServer(
					new net.mcreator.ordeal.core.OrdealActionMessage("heavy", "", 0));
		}
	}

}