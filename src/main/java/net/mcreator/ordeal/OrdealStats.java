package net.mcreator.ordeal.core;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.mcreator.ordeal.OrdealCombo;
import net.mcreator.ordeal.OrdealTuning;
import net.mcreator.ordeal.network.OrdealModVariables;

/**
 * Turns the seven stats into real numbers. Runs once a second and only writes
 * when something actually moved, so it is cheap to leave on every player.
 */
@EventBusSubscriber(modid = "ordeal")
public class OrdealStats {

	public static double CHI_PER_POINT   = OrdealTuning.d("stats.chi_per_point", 4.0);
	public static double SPEED_PER_AGI   = OrdealTuning.d("stats.speed_per_agility", 0.01);
	public static double KB_RESIST_PER_DUR = OrdealTuning.d("stats.kb_resist_per_dur", 0.004);
	public static final double LIMIT_MAX       = 150.0;
	public static final double BLOOD_PER_DOSE  = 10.0;

	/**
	 * Flat chi everybody has before a single point goes into the CHI stat. Without
	 * it a fresh player sits at 1 chi and cannot cast anything at all.
	 */
	public static double CHI_BASE    = OrdealTuning.d("stats.chi_base", 20.0);

	public static double CHI_REGEN   = OrdealTuning.d("stats.chi_regen_rate", 0.015);
	public static double CHI_CHARGE  = OrdealTuning.d("stats.chi_charge_rate", 0.08);


	private static final ResourceLocation HP    = ResourceLocation.fromNamespaceAndPath("ordeal", "stat_health");
	private static final ResourceLocation SPEED = ResourceLocation.fromNamespaceAndPath("ordeal", "stat_agility");
	private static final ResourceLocation KB    = ResourceLocation.fromNamespaceAndPath("ordeal", "stat_durability");

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		Player p = event.getEntity();
		if (p.level().isClientSide()) return;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);

		regenChi(p, v);

		if (p.tickCount % 20 != 0) return;
		boolean dirty = false;

		if (v.spawnRandom <= 0) {
			v.spawnRandom = 5 + Math.floor(p.getRandom().nextDouble() * 46);
			dirty = true;
		}

		// Race keeps itself honest: carrying a talent makes you Kimyo, and once
		// Kimyo it sticks (the blood stays even if the talent is taken).
		boolean hasTalent = (v.talent1_id != null && !v.talent1_id.isEmpty() && !v.talent1_id.equals("none"))
				|| (v.talent2_id != null && !v.talent2_id.isEmpty() && !v.talent2_id.equals("none"));
		if (hasTalent && !"kimyo".equals(v.race)) { v.race = "kimyo"; dirty = true; }
		else if (v.race == null || v.race.isEmpty()) { v.race = "human"; dirty = true; }

		dirty |= levelUp(v);

		// A lone second talent slides into the first slot.
		if ((v.talent1_id == null || v.talent1_id.isEmpty() || v.talent1_id.equals("none"))
				&& v.talent2_id != null && !v.talent2_id.isEmpty() && !v.talent2_id.equals("none")) {
			v.talent1_id = v.talent2_id;
			v.talent1_strength = v.talent2_strength;
			v.talent1_source = v.talent2_source;
			v.talent2_id = "none"; v.talent2_strength = 0; v.talent2_source = "";
			// the reserve has to travel with the talent, or it ends up attached
			// to the wrong one - or simply lost
			net.mcreator.ordeal.core.OrdealTalentChi.slideSlot2ToSlot1(v);
			dirty = true;
		}

		double limit = chiLimit(v) + net.mcreator.ordeal.ChiFruit.limitBonus(p);
		if (v.chiLimit != limit) { v.chiLimit = limit; dirty = true; }

		double chiMax = Math.max(1, (CHI_BASE + v.statChi * CHI_PER_POINT + net.mcreator.ordeal.ChiFruit.chiBonus(p)) * (1.0 - v.ChiConcealed));
		if (v.chiMax != chiMax) {
			v.chiMax = chiMax;
			v.chi = Math.min(v.chi, chiMax);
			dirty = true;
		}

		// talent reserves: size follows strength, and a new talent arrives full
		dirty |= net.mcreator.ordeal.core.OrdealTalentChi.recompute(p, v);

		double xpCap = 100 + v.level * 50;
		if (v.xpCap != xpCap) { v.xpCap = xpCap; dirty = true; }

		attributes(p, v);
		OrdealCombat.recalc(p);

		if (dirty) v.markSyncDirty();
	}

	public static double chiLimit(OrdealModVariables.PlayerVariables v) {
		return 100 + v.bloodConsumed * BLOOD_PER_DOSE;
	}

	private static boolean levelUp(OrdealModVariables.PlayerVariables v) {
		if (v.xpCap <= 0 || v.xp < v.xpCap) return false;
		boolean any = false;
		int guard = 0;
		while (v.xp >= v.xpCap && v.xpCap > 0 && guard++ < 100) {
			v.xp -= v.xpCap;
			v.level++;
			grant(v);
			v.xpCap = 100 + v.level * 50;
			any = true;
		}
		return any;
	}

	/**
	 * Grants derive from the lifetime-cap variables, spread over 100 levels —
	 * change the cap in MCreator and the per-level amounts follow on their own.
	 */
	private static void grant(OrdealModVariables.PlayerVariables v) {
		v.sp += share(v.spLifetime_Cap, v.level);
		v.talentSP += share(v.talentSp_Lifetime_Cap, v.level);
	}

	private static double share(double cap, double level) {
		if (level > 100) return 0;
		return Math.floor(cap * level / 100.0) - Math.floor(cap * (level - 1) / 100.0);
	}

	/** Chi last seen, so a spend can be noticed without touching any procedure. */
	private static final String LAST_CHI = "ordeal_lastChi";

	private static void regenChi(Player p, OrdealModVariables.PlayerVariables v) {
		// spending chi IS using an ability - it puts you in combat and shuts
		// your chi off for the window, wherever the spend came from
		double last = p.getPersistentData().getDouble(LAST_CHI);
		// the reserve is charged BEFORE the procedure runs, in OrdealTalentChi
		// .prefund - by the time a spend shows up here it has already been paid
		// for out of the right pool
		if (v.chi < last - 0.0001)
			net.mcreator.ordeal.core.OrdealCombatState.usedAbility(p);
		p.getPersistentData().putDouble(LAST_CHI, v.chi);

		net.mcreator.ordeal.core.OrdealCombatState.tickChiLock(p);
		net.mcreator.ordeal.core.OrdealTalentChi.reclaim(p, v);
		net.mcreator.ordeal.core.OrdealTalentChi.regen(p, v);

		if (v.chiMax <= 0 || v.chi >= v.chiMax) return;

		// Two states switch natural regen off:
		//   in combat      - the InCombat effect from trading blows, NOT the
		//                    combat-mode stance
		//   ability block  - the window after any chi spend
		// In either one the ONLY way chi comes back is holding sneak to charge,
		// which is a real choice to make while someone is swinging at you. The
		// block used to kill charging as well, which left you with no way at all
		// to get chi back for the whole window.
		boolean locked = net.mcreator.ordeal.core.OrdealCombatState.chiLock(p) > 0
				|| net.mcreator.ordeal.core.OrdealCombatState.inCombat(p);
		if (locked && v.chiCharging <= 0) return;

		double rate = v.chiMax * (v.chiCharging > 0 ? CHI_CHARGE : CHI_REGEN) / 20.0;
		v.chi = Math.min(v.chiMax, v.chi + rate);
		if (p.tickCount % 5 == 0) v.markSyncDirty();
	}

	/** Death costs the fight, not the recovery: you come back at full health and full guard. */
	@SubscribeEvent
	public static void onRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
		Player p = event.getEntity();
		if (p.level().isClientSide()) return;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		attributes(p, v);                         // health modifier back on before we fill it
		OrdealCombat.recalc(p);
		v.guard = v.guardMax;
		v.guardRegenTick = 0;
		v.inCombatWith = "none";
		net.mcreator.ordeal.core.OrdealTalentChi.onRespawn(p, v);
		v.markSyncDirty();
		net.mcreator.ordeal.core.OrdealCombatState.disengage(p);
		OrdealCombo.drop(p);
		p.setHealth(p.getMaxHealth());
	}

	private static void attributes(Player p, OrdealModVariables.PlayerVariables v) {
		// Health stat IS the HP number; 20 is the vanilla baseline.
		// stat 0 = vanilla 20 HP; every point adds a full HP on top
		mod(p, Attributes.MAX_HEALTH, HP, Math.max(0, v.statHealth));
		if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());

		mod(p, Attributes.MOVEMENT_SPEED, SPEED, v.statAgility * SPEED_PER_AGI,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		mod(p, Attributes.KNOCKBACK_RESISTANCE, KB, Math.min(0.4, v.statDurability * KB_RESIST_PER_DUR));
	}

	private static void mod(Player p, Holder<Attribute> attr, ResourceLocation id, double value) {
		mod(p, attr, id, value, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void mod(Player p, Holder<Attribute> attr, ResourceLocation id,
			double value, AttributeModifier.Operation op) {
		AttributeInstance inst = p.getAttribute(attr);
		if (inst == null) return;
		AttributeModifier had = inst.getModifier(id);
		if (had != null && had.amount() == value && had.operation() == op) return;
		if (had != null) inst.removeModifier(id);
		if (value != 0) inst.addPermanentModifier(new AttributeModifier(id, value, op));
	}
}