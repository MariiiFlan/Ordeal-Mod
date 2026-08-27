package net.mcreator.ordeal.core;

import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Being in combat, the way Invincible ran it.
 *
 * It is not the combat-mode stance and it is not something you switch on. You
 * are in combat because someone hit you, or because you hit them - the moment a
 * blow lands BOTH sides get the InCombat effect for ten seconds and each one's
 * InCombatWith is set to the other. Every further hit refreshes it. Stop
 * fighting for ten seconds and it lapses on its own.
 *
 * OrdealCombat calls engage() on every landed hit; nothing else has to.
 */
public final class OrdealCombatState {

	private OrdealCombatState() {}

	/**
	 * Effect element worn while in combat. Any of these names is accepted, so it
	 * works whether the element ended up as "incombat" or "in_combat".
	 */
	public static final String[] EFFECT_NAMES = { "incombat", "in_combat", "combat" };

	/** Ten seconds, the same window Invincible used. Refreshed by every hit. */
	public static int TICKS = net.mcreator.ordeal.OrdealTuning.i("combat.in_combat_ticks", 200);

	private static MobEffect cached;
	private static boolean looked;

	/** Key the chi lockout hangs on. Not a player variable - nothing to make. */
	private static final String CHI_LOCK = "ordeal_chiLock";

	/**
	 * You spent chi on something. That puts you in combat for the full window
	 * on its own - casting is fighting, whether or not anyone hit you - and
	 * shuts your chi off for the same window, so an ability is paid for twice:
	 * once in chi, once in the ten seconds you cannot get any back.
	 *
	 * Called automatically when your chi drops; nothing to add to a procedure.
	 */
	public static void usedAbility(Entity e) {
		if (!(e instanceof LivingEntity le) || le.level().isClientSide()) return;
		apply(le);
		if (e instanceof Player p) p.getPersistentData().putInt(CHI_LOCK, TICKS);
	}

	/** Ticks left before chi starts coming back. */
	public static int chiLock(Player p) {
		return p == null ? 0 : Math.max(0, p.getPersistentData().getInt(CHI_LOCK));
	}

	/** Runs the lockout down. Called once a tick from OrdealStats. */
	public static void tickChiLock(Player p) {
		int t = chiLock(p);
		if (t > 0) p.getPersistentData().putInt(CHI_LOCK, t - 1);
	}

	/** A hit landed: lock both sides into combat with each other. */
	public static void engage(LivingEntity victim, Entity attacker) {
		if (victim == null || attacker == null || victim == attacker) return;
		if (victim.level().isClientSide()) return;

		apply(victim);
		if (attacker instanceof LivingEntity la) apply(la);

		mark(victim, attacker.getName().getString());
		mark(attacker, victim.getName().getString());
	}

	/** True while the InCombat effect is on this entity. */
	public static boolean inCombat(Entity e) {
		if (!(e instanceof LivingEntity le)) return false;
		Holder<MobEffect> h = holder();
		if (h == null)
			// no effect element in the build - fall back to the stance so the
			// rules that depend on this still mean something
			return e instanceof Player p && p.getData(OrdealModVariables.PLAYER_VARIABLES).combatMode;
		return le.hasEffect(h);
	}

	/** Who this entity is locked in with, or "none". */
	public static String opponent(Entity e) {
		if (!(e instanceof Player p)) return "none";
		String s = p.getData(OrdealModVariables.PLAYER_VARIABLES).inCombatWith;
		return s == null || s.isEmpty() ? "none" : s;
	}

	/** Drop out of combat early - death, a wipe, a command. */
	public static void disengage(Entity e) {
		if (e instanceof LivingEntity le) {
			Holder<MobEffect> h = holder();
			if (h != null && le.hasEffect(h)) le.removeEffect(h);
		}
		if (e instanceof Player p) {
			p.getPersistentData().putInt(CHI_LOCK, 0);
			OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
			if (!"none".equals(v.inCombatWith)) {
				v.inCombatWith = "none";
				v.markSyncDirty();
			}
		}
	}

	// ---- internals ----------------------------------------------------------

	private static void apply(LivingEntity e) {
		Holder<MobEffect> h = holder();
		if (h == null) return;
		e.addEffect(new MobEffectInstance(h, TICKS, 0, false, false, true));
	}

	private static void mark(Entity e, String other) {
		if (!(e instanceof Player p)) return;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		if (other.equals(v.inCombatWith)) return;
		v.inCombatWith = other;
		v.markSyncDirty();
	}

	/** Resolved by name once, so the build is fine before the element exists. */
	private static Holder<MobEffect> holder() {
		if (!looked) {
			looked = true;
			for (var e : BuiltInRegistries.MOB_EFFECT.entrySet()) {
				if (!e.getKey().location().getNamespace().equals("ordeal")) continue;
				String path = e.getKey().location().getPath().replace("_", "");
				for (String want : EFFECT_NAMES)
					if (path.equalsIgnoreCase(want.replace("_", ""))) { cached = e.getValue(); break; }
				if (cached != null) break;
			}
		}
		return cached == null ? null : BuiltInRegistries.MOB_EFFECT.wrapAsHolder(cached);
	}
}