package net.mcreator.ordeal;

import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = "ordeal")
public final class DecimationClosing {

	public static double PLANT_RANGE  = OrdealTuning.d("decimation.plant_range", 12);
	public static int    DURATION     = OrdealTuning.i("decimation.duration_ticks", 100);
	public static double RADIUS_START = OrdealTuning.d("decimation.radius_start", 7);
	public static double RADIUS_PER_STR = OrdealTuning.d("decimation.radius_per_str", 0.10);
	public static double RADIUS_END   = OrdealTuning.d("decimation.radius_end", 2);
	public static int    PULSE_EVERY  = OrdealTuning.i("decimation.pulse_every_ticks", 10);
	public static double BASE_DMG     = OrdealTuning.d("decimation.base_dmg_per_pulse", 3);
	public static double PER_STR      = OrdealTuning.d("decimation.extra_dmg_per_str", 0.3);
	public static double BURST_MULT   = OrdealTuning.d("decimation.closing_burst_mult", 3.0);
	public static int    SLOW_AMP     = OrdealTuning.i("decimation.slow_amplifier", 1);
	public static double PULL         = OrdealTuning.d("decimation.pull_per_tick", 0.05);
	public static int    IGNITE_SEC   = OrdealTuning.i("decimation.ignite_seconds", 3);
	public static int    FX_PULSE     = OrdealTuning.i("decimation.fx_every_pulse", 0);
	public static double FX_BURST_SCALE = OrdealTuning.d("decimation.fx_burst_scale", 1.5);
	public static String FX = "photon:ilios_decimation";

	private static final List<Vortex> ACTIVE = new ArrayList<>();

	private DecimationClosing() {}

	private static final class Vortex {
		ServerLevel level;
		UUID caster;
		double x, y, z;
		double startRadius;
		int age;
		double str;
		double state;
		double power;
	}

	public static void start(Entity e) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return;
		if (!(p.level() instanceof ServerLevel sl)) return;

		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		double str = 0;
		if ("ilios".equals(v.talent1_id)) str = v.talent1_strength;
		else if ("ilios".equals(v.talent2_id)) str = v.talent2_strength;

		Vec3 eye = p.getEyePosition(1f);
		Vec3 end = eye.add(p.getLookAngle().scale(Math.max(1, PLANT_RANGE)));
		var hit = sl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 pos = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();

		BlockPos bp = BlockPos.containing(pos.x, pos.y, pos.z);
		int drop = 0;
		while (drop < 8 && sl.isEmptyBlock(bp.below())) { bp = bp.below(); drop++; }

		Vortex vx = new Vortex();
		vx.level = sl;
		vx.caster = p.getUUID();
		vx.x = pos.x;
		vx.y = bp.getY();
		vx.z = pos.z;
		vx.str = str;
		vx.startRadius = RADIUS_START + Math.max(0, str) * RADIUS_PER_STR;
		vx.state = v.talentState <= 0 ? 1 : v.talentState;
		vx.power = v.chargePower <= 0 ? 1 : v.chargePower;
		ACTIVE.add(vx);

		fx(vx, vx.startRadius / Math.max(1, RADIUS_START));
	}

	@SubscribeEvent
	public static void onTick(ServerTickEvent.Post event) {
		if (ACTIVE.isEmpty()) return;
		Iterator<Vortex> it = ACTIVE.iterator();
		while (it.hasNext()) {
			Vortex vx = it.next();
			vx.age++;
			double t = Math.min(1.0, vx.age / (double) Math.max(1, DURATION));
			double radius = vx.startRadius + (RADIUS_END - vx.startRadius) * t;

			if (vx.age >= DURATION) {
				pulse(vx, Math.max(RADIUS_END, 0.5), true);
				fx(vx, FX_BURST_SCALE);
				it.remove();
				continue;
			}
			pull(vx, radius);
			if (vx.age % Math.max(1, PULSE_EVERY) == 0) {
				pulse(vx, radius, false);
				if (FX_PULSE != 0) fx(vx, Math.max(0.2, radius / Math.max(1, RADIUS_START)));
			}
		}
	}

	private static void pull(Vortex vx, double radius) {
		if (PULL <= 0) return;
		ServerLevel sl = vx.level;
		Player caster = sl.getPlayerByUUID(vx.caster);
		AABB box = new AABB(vx.x - radius, vx.y - 1, vx.z - radius, vx.x + radius, vx.y + 4, vx.z + radius);
		for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, box)) {
			if (caster != null && le == caster) continue;
			double dx = vx.x - le.getX(), dz = vx.z - le.getZ();
			double d2 = dx * dx + dz * dz;
			if (d2 > radius * radius || d2 < 0.25) continue;
			double d = Math.sqrt(d2);
			le.setDeltaMovement(le.getDeltaMovement().add(dx / d * PULL, 0, dz / d * PULL));
			le.hurtMarked = true;
		}
	}

	private static void pulse(Vortex vx, double radius, boolean burst) {
		ServerLevel sl = vx.level;
		Player caster = sl.getPlayerByUUID(vx.caster);
		double dmg = (BASE_DMG + vx.str * PER_STR) * vx.power * vx.state * (burst ? BURST_MULT : 1);

		var holder = sl.holderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("ordeal:talent")));
		DamageSource src = caster != null ? new DamageSource(holder, caster) : new DamageSource(holder);

		AABB box = new AABB(vx.x - radius, vx.y - 1, vx.z - radius, vx.x + radius, vx.y + 4, vx.z + radius);
		for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, box)) {
			if (caster != null && le == caster) continue;
			double dx = le.getX() - vx.x, dz = le.getZ() - vx.z;
			if (dx * dx + dz * dz > radius * radius) continue;
			le.hurt(src, (float) dmg);
			le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Math.max(1, PULSE_EVERY) + 10, Math.max(0, SLOW_AMP), false, true));
			if (!le.fireImmune() && IGNITE_SEC > 0)
				le.setRemainingFireTicks(Math.max(le.getRemainingFireTicks(), IGNITE_SEC * 20));
		}
	}

	private static void fx(Vortex vx, double scale) {
		ServerLevel sl = vx.level;
		if (sl.getServer() == null) return;
		CommandSourceStack src = new CommandSourceStack(CommandSource.NULL, new Vec3(vx.x, vx.y, vx.z),
				net.minecraft.world.phys.Vec2.ZERO, sl, 4, "decimation",
				net.minecraft.network.chat.Component.literal("decimation"), sl.getServer(), null);
		sl.getServer().getCommands().performPrefixedCommand(src, "photon fx " + FX + " block ^ ^ ^");
	}


	@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
	public static final class Aim {

		@SubscribeEvent
		public static void onHud(RenderGuiEvent.Post event) {
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			net.minecraft.client.player.LocalPlayer p = mc.player;
			if (p == null || mc.screen != null) return;
			if (!holding(p)) return;

			Vec3 eye = p.getEyePosition(1f);
			Vec3 end = eye.add(p.getLookAngle().scale(Math.max(1, PLANT_RANGE)));
			var hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			boolean miss = hit.getType() == HitResult.Type.MISS;
			double d = (miss ? end : hit.getLocation()).distanceTo(eye);

			String text = (int) Math.round(d) + "m" + (miss ? " MAX" : "");
			int w = mc.getWindow().getGuiScaledWidth();
			int h = mc.getWindow().getGuiScaledHeight();
			event.getGuiGraphics().drawCenteredString(mc.font, text, w / 2, h / 2 + 12,
					miss ? 0xFFFF8A5B : 0xFF7ED8F5);
		}

		private static boolean holding(net.minecraft.client.player.LocalPlayer p) {
			OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
			int off = v.ability_Row == 2 ? 5 : 0;
			String name = "";
			if (net.mcreator.ordeal.init.OrdealModKeyMappings.ABILITY_1.isDown()) name = slot(v, off + 1);
			else if (net.mcreator.ordeal.init.OrdealModKeyMappings.ABILITY_2.isDown()) name = slot(v, off + 2);
			else if (net.mcreator.ordeal.init.OrdealModKeyMappings.ABILITY_3.isDown()) name = slot(v, off + 3);
			else if (net.mcreator.ordeal.init.OrdealModKeyMappings.ABILITY_4.isDown()) name = slot(v, off + 4);
			else if (net.mcreator.ordeal.init.OrdealModKeyMappings.ABILITY_5.isDown()) name = slot(v, off + 5);
			if (name == null || name.isEmpty()) return false;
			var ab = net.mcreator.ordeal.core.client.OrdealTalents.abilityByName(name);
			return ab != null && "decimation_closing".equals(ab.id);
		}

		private static String slot(OrdealModVariables.PlayerVariables v, int i) {
			String s = switch (i) {
				case 1 -> v.loadout_1; case 2 -> v.loadout_2; case 3 -> v.loadout_3;
				case 4 -> v.loadout_4; case 5 -> v.loadout_5; case 6 -> v.loadout_6;
				case 7 -> v.loadout_7; case 8 -> v.loadout_8; case 9 -> v.loadout_9;
				case 10 -> v.loadout_10; default -> "";
			};
			return s == null ? "" : s;
		}
	}
}