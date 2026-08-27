package net.mcreator.ordeal;

import net.mcreator.ordeal.core.OrdealInput;
import net.mcreator.ordeal.core.client.OrdealTalents;
import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Held abilities. An ability with a "hold" block in its talent JSON stops
 * firing the instant you press it and instead runs on the key being down:
 *
 *   charge  - climbs 5 levels, 0.5s each; fires on release at that level
 *   channel - re-fires every few ticks for as long as you hold it
 *   toggle  - press to start, press again (or run dry) to stop
 *
 * All three drain chi per second and stop at a cap, so nothing can be held
 * forever. Abilities with no hold block are untouched: ready() returns true
 * the tick you press, and power() returns 1.
 *
 * Two calls to use from a procedure's Java block:
 *
 *   AbilityHold.ready(entity)  - true on the exact tick the ability should go off
 *   AbilityHold.power(entity)  - multiply damage and knockback by this
 */
@EventBusSubscriber(modid = "ordeal")
public final class AbilityHold {

	private AbilityHold() {}

	public static boolean ENABLED = true;

	private static final class State {
		String id = "";
		int ticks;
		int sinceLast;
		boolean wasDown;
		boolean toggled;
		boolean fire;        // true for exactly one tick
		boolean spent;       // a charge that already went off waits for the key to come up
		boolean pressFired;  // fireOnPress already sent the opener this hold
		double power = 1;
		int level;           // charge step reached, 0..levels
		double chiDebt;      // fractional chi carried between ticks
		double drained;      // what this hold has actually cost, for a tap refund
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private static State state(Entity e) {
		return STATES.computeIfAbsent(e.getUUID(), k -> new State());
	}

	/**
	 * Runs the ability's own procedure when the hold says go, so the gate chain
	 * you already wrote is what fires - cooldown, chi and all.
	 *
	 * phoenix_spear -> net.mcreator.ordeal.procedures.PhoenixSpear0Procedure
	 */
	public static String PROC_SUFFIX = "0Procedure";

	private static void dispatch(Player p, String abilityId) {
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(abilityId);
		String id = ab != null ? ab.id : abilityId;
		if (id == null || id.isEmpty()) return;
		StringBuilder cls = new StringBuilder("net.mcreator.ordeal.procedures.");
		for (String part : id.split("[^A-Za-z0-9]+")) {
			if (part.isEmpty()) continue;
			cls.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		cls.append(PROC_SUFFIX);
		try {
			Class.forName(cls.toString())
					.getMethod("execute", net.minecraft.world.level.LevelAccessor.class, Entity.class)
					.invoke(null, p.level(), p);
		} catch (Throwable t) {
			p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
					"§c[ordeal] no procedure " + cls + " for held ability " + id));
		}
	}

	// ---- what a procedure asks -------------------------------------------

	/**
	 * The ability that should go off THIS TICK, or "" for none.
	 *
	 * Use this as the outer condition of the dispatcher instead of "which key
	 * is pressed". A charge fires on RELEASE, when the key is already back up —
	 * a dispatcher that keys off the button being down would never see it.
	 */
	public static String firing(Entity e) {
		if (e == null) return "";
		State s = STATES.get(e.getUUID());
		return s != null && s.fire ? s.id : "";
	}

	/**
	 * Drop-in replacement for GetAbilityPressed.
	 *
	 * Returns the ability under the key, EXCEPT for abilities AbilityHold runs
	 * itself — those come back empty, so a dispatcher built on this can never
	 * double-fire a held ability and needs to know nothing about which is which.
	 * Add a hold block to any ability later and this keeps working untouched.
	 */
	public static String pressed(Entity e) {
		if (e == null) return "";
		String name = pressedAbility(e);
		return isHold(name) ? "" : name;
	}

	/**
	 * Boolean form, for a plain "if" block:
	 *   net.mcreator.ordeal.AbilityHold.pressed(entity, "Phoenix Spear")
	 *
	 * True only on the tick that ability's key goes down, and never for an
	 * ability AbilityHold runs itself - so the dispatcher is one if per
	 * ability with nothing else to guard.
	 */
	public static boolean pressed(Entity e, String abilityName) {
		if (e == null || abilityName == null || abilityName.isEmpty()) return false;
		return abilityName.equalsIgnoreCase(pressed(e));
	}

	/**
	 * True when this ability is driven by AbilityHold rather than by a key
	 * press. A dispatcher should SKIP these - AbilityHold runs them itself, at
	 * the right moment. Instant abilities return false and are untouched.
	 */
	public static boolean isHold(String abilityName) {
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(abilityName);
		return ab != null && ab.hold != null;
	}

	/**
	 * Is this ability chargeable? Straight out of the talent json - an ability
	 * with a "hold" block is chargeable, one without it is not. Use this to set
	 * your local "chargeable" flag instead of hand-writing it per procedure:
	 *   net.mcreator.ordeal.AbilityHold.chargeable("Phoenix Spear")
	 */
	public static boolean chargeable(String abilityName) {
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(abilityName);
		return ab != null && ab.hold != null && ab.hold.isCharge();
	}

	/** Same, for whatever ability this entity is holding right now. */
	public static boolean chargeable(Entity e) {
		if (e == null) return false;
		State s = STATES.get(e.getUUID());
		return s != null && chargeable(s.id);
	}

	/** True on the single tick this entity's held ability should fire. */
	public static boolean ready(Entity e) {
		if (e == null) return false;
		State s = STATES.get(e.getUUID());
		return s != null && s.fire;
	}

	/** Charge multiplier for the shot that is firing now. 1.0 when not a charge. */
	public static double power(Entity e) {
		if (e == null) return 1;
		State s = STATES.get(e.getUUID());
		return s == null ? 1 : s.power;
	}

	/** Charge step reached, 0..levels. 0 for instant and channel abilities. */
	public static int level(Entity e) {
		if (e == null) return 0;
		State s = STATES.get(e.getUUID());
		return s == null ? 0 : s.level;
	}

	/** The step the key is CURRENTLY at while still held - for the HUD. */
	public static int liveLevel(Entity e) {
		if (e == null) return 0;
		State s = STATES.get(e.getUUID());
		if (s == null || s.id.isEmpty()) return 0;
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(s.id);
		return ab == null || ab.hold == null ? 0 : ab.hold.levelAt(s.ticks);
	}

	/** How long the key has been down, in ticks. */
	public static int heldTicks(Entity e) {
		if (e == null) return 0;
		State s = STATES.get(e.getUUID());
		return s == null ? 0 : s.ticks;
	}

	/** 0..1 across the hold window, for a charge bar. */
	public static double chargeFraction(Entity e) {
		if (e == null) return 0;
		State s = STATES.get(e.getUUID());
		if (s == null || s.id.isEmpty()) return 0;
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(s.id);
		if (ab == null || ab.hold == null) return 0;
		int max = ab.hold.maxTicks();
		return max <= 0 ? 0 : Math.max(0, Math.min(1, s.ticks / (double) max));
	}

	public static String heldAbility(Entity e) {
		if (e == null) return "";
		State s = STATES.get(e.getUUID());
		return s == null ? "" : s.id;
	}

	// ---- the loop ---------------------------------------------------------

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		Player p = event.getEntity();
		if (!ENABLED || p.level().isClientSide()) return;

		State s = state(p);
		s.fire = false;

		String pressed = pressedAbility(p);
		boolean down = !pressed.isEmpty();
		// Resolve from what is held now, or from what WAS held. A charge fires on
		// RELEASE - the key is already up by then, so reading the live key state
		// would hand back null and the shot would silently never go off.
		String id = down ? pressed : s.id;
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(id);
		OrdealTalents.Hold h = ab == null ? null : ab.hold;

		// an ability with no hold block keeps the old behaviour exactly
		if (down && h == null) {
			if (!s.wasDown) { s.fire = true; s.power = 1; s.level = 0; s.id = pressed; publish(p, 1); }
			s.wasDown = true;
			s.ticks = 0;
			return;
		}

		if (h != null && h.isToggle()) { toggle(p, s, pressed, ab, h, down); return; }

		if (down) {
			if (!s.wasDown) {
				// a held ability on cooldown must not wind up. Without this the
				// charge drains chi the whole time it is held and then fires
				// nothing, because the procedure's own gate turns it away
				if (onCooldown(p, pressed)) {
					s.wasDown = true;
					s.spent = true;          // nothing charges until the key comes up
					s.id = pressed;
					s.ticks = 0;
					dispatch(p, pressed);    // let the procedure say how long is left
					return;
				}
				s.id = pressed; s.ticks = 0; s.sinceLast = 0;
				s.chiDebt = 0; s.drained = 0; s.spent = false; s.pressFired = false;
				// press-to-fire: the ability goes off the instant you touch the
				// key, and any charge becomes a bonus hit when you let go
				if (h.isCharge() && h.fireOnPress) {
					s.level = 0;
					s.power = h.powerAtLevel(0);
					s.fire = true;
					s.pressFired = true;
					publish(p, s.power);
					dispatch(p, s.id);
				}
			}
			s.wasDown = true;
			// a charge that already went off waits for the key to come up
			if (s.spent) return;

			if (onCooldown(p, s.id)) { release(p, s, h, true); return; }

			int max = h.maxTicks();
			boolean capped = s.ticks >= max;
			if (!capped) s.ticks++;

			// a full charge SITS there. It stops climbing and stops costing chi,
			// and it does not go off until the key comes up - holding past the
			// cap is free, and nothing fires out from under you
			if (capped && h.isCharge()) return;

			if (!drain(p, h, s)) { release(p, s, h, true); return; }

			if (h.isChannel()) {
				s.sinceLast++;
				if (s.sinceLast >= h.tickEvery) {
					s.sinceLast = 0;
					s.power = 1;
					s.level = 0;
					s.fire = true;
					publish(p, 1);
					dispatch(p, s.id);
				}
				if (capped) release(p, s, h, false);
			}
			return;
		}

		if (s.wasDown) release(p, s, h, false);
		s.wasDown = false;
		s.id = "";
	}

	private static void toggle(Player p, State s, String pressed,
			OrdealTalents.Ability ab, OrdealTalents.Hold h, boolean down) {
		if (down && !s.wasDown) {
			s.toggled = !s.toggled;
			s.id = pressed;
			s.ticks = 0;
			s.chiDebt = 0;
			s.drained = 0;
			s.power = 1;
			if (s.toggled) s.fire = true;
		}
		s.wasDown = down;
		if (!s.toggled) return;

		s.ticks++;
		if (!drain(p, h, s) || (h.maxTicks() > 0 && s.ticks >= h.maxTicks())) {
			s.toggled = false;
			s.ticks = 0;
			return;
		}
		s.sinceLast++;
		if (s.sinceLast >= h.tickEvery) {
			s.sinceLast = 0;
			s.fire = true;
			publish(p, 1);
			dispatch(p, s.id);
		}
	}

	/** Fire at whatever level was reached, or drop it if it never reached level 1. */
	private static void release(Player p, State s, OrdealTalents.Hold h, boolean dry) {
		if (h != null && h.isCharge()) {
			// with fireOnPress the opener already went out, so only a real
			// charge earns the second hit - a tap must not fire twice
			boolean earned = !s.pressFired || h.levelAt(s.ticks) >= 1;
			if (!dry && earned) fireCharge(p, s, h);
			else refund(p, s);
		}
		s.pressFired = false;
		s.ticks = 0;
		s.sinceLast = 0;
		s.chiDebt = 0;
		s.drained = 0;
		s.spent = false;
		s.wasDown = false;
	}

	/** Level reached, power, and the chargePower variable your procedures read. */
	private static void fireCharge(Player p, State s, OrdealTalents.Hold h) {
		s.level = h.levelAt(s.ticks);
		s.power = h.powerAtLevel(s.level);
		s.fire = true;
		s.spent = true;
		publish(p, s.power);
		dispatch(p, s.id);
		s.ticks = 0;
		s.chiDebt = 0;
		s.drained = 0;
	}

	/** Mirror the multiplier into the chargePower player variable so blocks can read it. */
	private static void publish(Player p, double power) {
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		v.chargePower = power;
		v.markSyncDirty();
	}

	/** Take the per-tick cost, cut by Chi Control. False when the pool is empty. */
	private static boolean drain(Player p, OrdealTalents.Hold h, State s) {
		OrdealModVariables.PlayerVariables v0 = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		double perTick = h.drainPerTick(v0.statChiControl);
		if (perTick <= 0) return true;
		// a tap is not a charge - the first few ticks are free, so pressing and
		// letting go costs exactly what the ability costs and nothing more
		if (s.ticks <= h.graceTicks) return v0.chi > 0;
		OrdealModVariables.PlayerVariables v = v0;
		s.chiDebt += perTick;
		if (s.chiDebt < 1) return v.chi > 0;
		double take = Math.floor(s.chiDebt);
		s.chiDebt -= take;
		if (v.chi < take) { v.chi = 0; v.markSyncDirty(); return false; }
		v.chi -= take;
		s.drained += take;
		v.markSyncDirty();
		return true;
	}

	/** A tap that never became a charge gives back everything it drained. */
	private static void refund(Player p, State s) {
		if (s.drained <= 0) return;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		v.chi = Math.min(Math.max(1, v.chiMax), v.chi + s.drained);
		s.drained = 0;
		v.markSyncDirty();
	}

	/** Which loadout slot is being held, as an ability name. Empty when none. */
	private static String pressedAbility(Entity e) {
		OrdealModVariables.PlayerVariables v = e.getData(OrdealModVariables.PLAYER_VARIABLES);
		int off = v.ability_Row == 2 ? 5 : 0;
		if (OrdealInput.ability1(e)) return slot(v, off + 1);
		if (OrdealInput.ability2(e)) return slot(v, off + 2);
		if (OrdealInput.ability3(e)) return slot(v, off + 3);
		if (OrdealInput.ability4(e)) return slot(v, off + 4);
		if (OrdealInput.ability5(e)) return slot(v, off + 5);
		return "";
	}

	/**
	 * Ticks left on this ability's cooldown, or 0 when it is ready.
	 *
	 * The cooldown lives on the CD_1..CD_10 effect matching the loadout slot,
	 * the same place your procedures read it from. Resolved by name so this
	 * keeps working whatever the effect elements end up called.
	 */
	public static int cooldownLeft(Entity e, String abilityName) {
		if (!(e instanceof Player p) || abilityName == null || abilityName.isEmpty()) return 0;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		int slot = 0;
		for (int i = 1; i <= 10; i++)
			if (abilityName.equalsIgnoreCase(slot(v, i))) { slot = i; break; }
		if (slot == 0) return 0;
		var fx = cdEffect(slot);
		if (fx == null) return 0;
		var holder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(fx);
		var inst = p.getEffect(holder);
		return inst == null ? 0 : inst.getDuration();
	}

	public static boolean onCooldown(Entity e, String abilityName) {
		return cooldownLeft(e, abilityName) > 0;
	}

	private static final Map<Integer, net.minecraft.world.effect.MobEffect> CD_CACHE = new HashMap<>();

	private static net.minecraft.world.effect.MobEffect cdEffect(int slot) {
		if (CD_CACHE.containsKey(slot)) return CD_CACHE.get(slot);
		net.minecraft.world.effect.MobEffect found = null;
		for (var en : net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.entrySet()) {
			if (!en.getKey().location().getNamespace().equals("ordeal")) continue;
			if (en.getKey().location().getPath().replace("_", "").equalsIgnoreCase("cd" + slot)) {
				found = en.getValue();
				break;
			}
		}
		// only cache a hit - the registry may not be filled the first time this
		// is asked, and caching a null there would disable the check for good
		if (found != null) CD_CACHE.put(slot, found);
		return found;
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