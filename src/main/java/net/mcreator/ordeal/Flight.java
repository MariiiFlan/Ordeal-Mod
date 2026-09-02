package net.mcreator.ordeal;

import net.mcreator.ordeal.core.OrdealInput;
import net.mcreator.ordeal.core.OrdealTalentChi;
import net.mcreator.ordeal.core.client.OrdealTalents;
import net.mcreator.ordeal.network.OrdealModVariables;
import net.mcreator.ordeal.network.PlayPlayerAnimationMessage;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = "ordeal")
public final class Flight {

	public static final int VERSION = 10;
	static { System.out.println("[ordeal] Flight v" + VERSION + " loaded (invincible port)"); }

	// ==================== THE DIALS ====================

	/** Tier at which jet propulsion becomes real wings. */
	public static int WINGS_FROM_TIER = OrdealTuning.i("flight.wings_from_tier", 2);

	/** Top boost speed by tier. Tier 1 is index 0. Higher tiers clamp to the last. */
	public static double[] SPEED_BY_TIER = {
			OrdealTuning.d("flight.speed_tier_1", 1.2),
			OrdealTuning.d("flight.speed_tier_2", 2.4),
			OrdealTuning.d("flight.speed_tier_3", 3.4),
			OrdealTuning.d("flight.speed_tier_4", 4.6) };

	/** Below WINGS_FROM_TIER you are on jets - the same ladder, throttled down. */
	public static double JET_SPEED_MULT = OrdealTuning.d("flight.jet_speed_mult", 0.55);

	/** THE SPRINT KEY. "sprint", "sneak", or "" to disable the boost. */
	public static String BOOST_KEY = "sprint";

	/**
	 * The boost key alone is not enough - you have to be driving forward too,
	 * the same as breaking into a run on the ground. Sprint with no W is just
	 * a held key while you hover.
	 *
	 * Set false to go back to the key on its own starting the glide.
	 */
	public static boolean BOOST_NEEDS_FORWARD = true;

	/** Throttle ramp per tick: up while holding forward, down when you let go. */
	public static double THROTTLE_UP    = OrdealTuning.d("flight.throttle_up", 0.017);
	public static double THROTTLE_DOWN  = OrdealTuning.d("flight.throttle_down", 0.034);

	/** How much of last tick's velocity survives. 0 snaps, 0.8 is very floaty. */
	public static double MOMENTUM       = OrdealTuning.d("flight.momentum", 0.35);

	/** Looking down adds this share of speed; looking up takes it away. */
	public static double DIVE_BONUS     = OrdealTuning.d("flight.dive_bonus", 0.35);

	/** Chi per tick. 0.02 idle = 0.4/s; 0.12 boosting = 2.4/s. */
	public static double CHI_IDLE       = OrdealTuning.d("flight.chi_per_tick_idle", 0.02);
	public static double CHI_BOOST      = OrdealTuning.d("flight.chi_per_tick_boost", 0.12);

	/** Chi Control makes flight cheaper: 0.004 off per point, capped here. */
	public static double CHI_CONTROL_MAX = OrdealTuning.d("flight.chi_control_max", 0.40);

	/** Sonic boom once you are genuinely moving. */
	public static double SONIC_THROTTLE = OrdealTuning.d("flight.sonic_throttle", 0.6);
	public static double SONIC_SPEED    = OrdealTuning.d("flight.sonic_speed", 3.0);
	public static String SONIC_FX       = "photon:misc_sonicboom";

	/** Lay the real elytra glide under your clip. Off if it fights them. */
	public static boolean ELYTRA_POSE   = OrdealTuning.i("flight.elytra_pose", 1) != 0;

	/** Clip names. <style>_idle and <style>_flight are ours, from the tp editor. */
	public static String DEFAULT_STYLE  = "default";

	/**
	 * THE LANDING ANIMATION, played through the MCreator Player Animator plugin
	 * rather than our own clip system - that is the "flight_end" you author in
	 * the plugin, not one in the tp editor.
	 *
	 * EMPTY BY DEFAULT, on purpose. The landing is already smooth without it:
	 * OrdealFlightLean holds the elytra angle when the glide ends and eases the
	 * body upright over LAND_TICKS, so nothing snaps.
	 *
	 * Only put a name here once you have actually authored that clip in the
	 * plugin. Asking for one that does not exist sets PlayerCurrentAnimation to
	 * a name nothing will ever clear, and every Ordeal animation stays yielded
	 * behind it - that is what killed the animations before.
	 *
	 * PER STYLE: a style may name its own end animation via StyleFx.endAnim; the
	 * one here is the fallback.
	 */
	public static String END_ANIM = "";

	/** True = the end animation overrides whatever the plugin is already playing. */
	public static boolean END_ANIM_OVERRIDE = true;

	// ==================== FX PER FLIGHT STYLE ====================

	/**
	 * WHERE THE FX GO. One entry per flight style, keyed by the SLUG - the same
	 * lowercase name the clips use, so FlightStyle "Omni Man" is "omni_man" and
	 * looks for omni_man_idle / omni_man_flight / this entry.
	 *
	 * Every field is a photon id and every one is optional - leave it "" and
	 * nothing plays for that beat.
	 *
	 *   enter     once, the moment flight starts
	 *   idleLoop  every IDLE_FX_EVERY ticks while hovering
	 *   boostLoop every BOOST_FX_EVERY ticks while boosting
	 *   sonic     the boom at speed, replacing SONIC_FX for this style
	 *   exit      once, on landing
	 *   endAnim   Player Animator clip on landing, replacing END_ANIM
	 *
	 * ADDING A STYLE is two lines here plus the two clips. Nothing else knows
	 * styles exist - the driver builds the clip names from the string, and this
	 * map is looked up by the same slug. A style with no entry simply plays no
	 * FX and uses the defaults.
	 */
	public static final class StyleFx {
		public String enter = "", idleLoop = "", boostLoop = "", sonic = "", exit = "", endAnim = "";

		public StyleFx enter(String s)     { this.enter = s;     return this; }
		public StyleFx idleLoop(String s)  { this.idleLoop = s;  return this; }
		public StyleFx boostLoop(String s) { this.boostLoop = s; return this; }
		public StyleFx sonic(String s)     { this.sonic = s;     return this; }
		public StyleFx exit(String s)      { this.exit = s;      return this; }
		public StyleFx endAnim(String s)   { this.endAnim = s;   return this; }
	}

	public static final Map<String, StyleFx> STYLE_FX = new LinkedHashMap<>();

	/** Register or replace a style's FX. Call it from anywhere at load time. */
	public static StyleFx fx(String styleSlug) {
		return STYLE_FX.computeIfAbsent(styleSlug, k -> new StyleFx());
	}

	static {
		// EXAMPLE - delete or edit. Hand me the photon ids and I will fill these in.
		// fx("default").enter("photon:flight_start").idleLoop("photon:flight_hover")
		//              .boostLoop("photon:flight_trail").exit("photon:flight_land");
	}

	/** How often the loop FX re-fire, in ticks. 0 = never. */
	public static int IDLE_FX_EVERY  = 20;
	public static int BOOST_FX_EVERY = 10;

	private static final StyleFx NO_FX = new StyleFx();

	private static StyleFx fxFor(OrdealModVariables.PlayerVariables v) {
		StyleFx f = STYLE_FX.get(style(v));
		return f == null ? NO_FX : f;
	}

	// ==================== GRANTS ====================

	/** One source's offer of flight. Strongest tier wins; ties keep the first. */
	public static final class Grant {
		public final int tier;
		public final String talentId;   // whose reserve pays, "" = your own chi bar
		public final String passiveId;  // must be switched on, "" = no gate
		public final double speedMult;
		public final double chiMult;

		Grant(int tier, String talentId, String passiveId, double speedMult, double chiMult) {
			this.tier = tier;
			this.talentId = talentId == null ? "" : talentId;
			this.passiveId = passiveId == null ? "" : passiveId;
			this.speedMult = speedMult;
			this.chiMult = chiMult;
		}

		/** A grant only counts while its passive is switched on. */
		public boolean live(Player p) {
			return passiveId.isEmpty() || Passives.on(p, passiveId);
		}
	}

	private static final Map<UUID, Map<String, Grant>> GRANTS = new HashMap<>();

	public static void grant(Entity e, String source, int tier) {
		grant(e, source, tier, "", "", 1.0, 1.0);
	}

	public static void grant(Entity e, String source, int tier, String talentId) {
		grant(e, source, tier, talentId, "", 1.0, 1.0);
	}

	public static void grant(Entity e, String source, int tier, String talentId, String passiveId) {
		grant(e, source, tier, talentId, passiveId, 1.0, 1.0);
	}

	public static void grant(Entity e, String source, int tier, String talentId,
			String passiveId, double speedMult, double chiMult) {
		if (!(e instanceof Player p) || p.level().isClientSide() || source == null) return;
		if (tier <= 0) { clear(e, source); return; }
		GRANTS.computeIfAbsent(p.getUUID(), k -> new LinkedHashMap<>())
				.put(source, new Grant(tier, talentId, passiveId, speedMult, chiMult));
	}

	public static void clear(Entity e, String source) {
		if (!(e instanceof Player p) || source == null) return;
		Map<String, Grant> m = GRANTS.get(p.getUUID());
		if (m == null) return;
		m.remove(source);
		if (m.isEmpty()) GRANTS.remove(p.getUUID());
	}

	public static void clearAll(Entity e) {
		if (e instanceof Player p) GRANTS.remove(p.getUUID());
	}

	public static Grant best(Player p) {
		if (p == null) return null;
		Map<String, Grant> m = GRANTS.get(p.getUUID());
		if (m == null || m.isEmpty()) return null;
		Grant top = null;
		for (Grant g : m.values()) {
			if (!g.live(p)) continue;
			if (top == null || g.tier > top.tier) top = g;
		}
		return top;
	}

	public static int tier(Player p) {
		Grant g = best(p);
		return g == null ? 0 : g.tier;
	}

	public static boolean granted(Entity e, String source) {
		if (!(e instanceof Player p) || source == null) return false;
		Map<String, Grant> m = GRANTS.get(p.getUUID());
		return m != null && m.containsKey(source);
	}

	// ==================== READOUTS ====================

	public static boolean flying(Player p) {
		if (p == null) return false;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		return v.flightOn && (v.flightIdle || v.flightBoost);
	}

	public static boolean boosting(Player p) {
		return p != null && p.getData(OrdealModVariables.PLAYER_VARIABLES).flightBoost;
	}

	public static boolean hovering(Player p) {
		if (p == null) return false;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		return v.flightOn && v.flightIdle && !v.flightBoost;
	}

	/**
	 * THE MIXIN HOOK. This is what the three elytra mixins ask before they let
	 * the glide flag live. Reads synced variables, so it is correct on both
	 * sides. Wrapped in a catch because mixins run early in entity construction.
	 */
	public static boolean wantsGlide(Player p) {
		if (p == null) return false;
		try {
			OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
			return v.flightOn && v.flightBoost && !p.onGround();
		} catch (Throwable t) {
			return false;
		}
	}

	public static double chiPerTick(OrdealModVariables.PlayerVariables v, Grant g, boolean boosting) {
		if (g == null) return 0;
		double base = (boosting ? CHI_BOOST : CHI_IDLE) * g.chiMult;
		double cut = Math.min(CHI_CONTROL_MAX, v.statChiControl * 0.004);
		return Math.max(0, base * (1.0 - cut));
	}

	public static double chiPerSecond(Player p, boolean boosting) {
		if (p == null) return 0;
		return chiPerTick(p.getData(OrdealModVariables.PLAYER_VARIABLES), best(p), boosting) * 20.0;
	}

	/** Blocks per tick at full throttle right now. */
	public static double topSpeed(Player p) {
		Grant g = best(p);
		if (g == null) return 0;
		double base = SPEED_BY_TIER[Math.max(0, Math.min(SPEED_BY_TIER.length - 1, g.tier - 1))];
		if (g.tier < WINGS_FROM_TIER) base *= JET_SPEED_MULT;
		return base * g.speedMult;
	}

	// ==================== STATE ====================

	private static final String DEBT  = "ordeal_flight_debt";
	private static final String SONIC = "ordeal_flight_sonic";

	private static final Map<UUID, String> CURRENT = new HashMap<>();

	private Flight() {}

	// ==================== THE TICK ====================

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		Player p = event.getEntity();
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);

		// The client owns player movement. Applying the boost on the owning
		// client as well as the server is what stops the rubber-band - both
		// sides arrive at the same velocity instead of fighting.
		if (p.level().isClientSide()) {
			if (isLocal(p) && v.flightOn && v.flightBoost && !p.onGround()) applyBoost(p, v);
			return;
		}
		if (!(p instanceof ServerPlayer sp)) return;

		Grant g = best(p);

		// ---- no grant, no wings ----
		if (g == null) {
			if (v.flightOn) shutDown(sp, v);
			return;
		}
		if (!v.flightOn) { v.flightOn = true; v.markSyncDirty(); }

		// creative fly permission IS the double-tap-space handler
		if (!p.getAbilities().mayfly) {
			p.getAbilities().mayfly = true;
			p.onUpdateAbilities();
		}

		// ---- on the ground nothing is flying ----
		if (p.onGround()) {
			if (v.flightIdle || v.flightBoost || v.flightThrottle != 0) {
				v.flightIdle = false;
				v.flightBoost = false;
				v.flightThrottle = 0;
				v.flightSpeed = 0;
				v.markSyncDirty();
			}
			if (p.isFallFlying()) p.stopFallFlying();
			p.getPersistentData().putBoolean(SONIC, false);
			drive(sp, v);
			return;
		}

		// ---- the double-tap hook: vanilla just turned creative flight on ----
		if (p.getAbilities().flying && !v.flightIdle && !v.flightBoost) {
			v.flightIdle = true;
			v.markSyncDirty();
		}

		// airborne but not flying - an ordinary jump, leave it alone
		if (!v.flightIdle && !v.flightBoost) { drive(sp, v); return; }

		// ---- throttle: winds up on forward, winds down when you let go ----
		double throttle = v.flightThrottle
				+ (OrdealInput.forward(p) ? THROTTLE_UP : -THROTTLE_DOWN);
		throttle = Math.max(0, Math.min(1, throttle));
		if (throttle != v.flightThrottle) { v.flightThrottle = throttle; v.markSyncDirty(); }

		boolean boost = boostHeld(p);

		// ---- pay for it ----
		if (!payChi(p, v, g, chiPerTick(v, g, boost))) {
			p.displayClientMessage(Component.literal("§4the flight gives out"), true);
			shutDown(sp, v);
			return;
		}

		double speed = topSpeed(p);
		if (v.flightSpeed != speed) { v.flightSpeed = speed; v.markSyncDirty(); }

		if (boost) {
			// ================= ELYTRA FLIGHT =================
			// Order matters. flightBoost has to be TRUE before startFallFlying,
			// because the mixins read it to decide whether to let the glide flag
			// live. Set it first, then ask vanilla.
			if (v.flightIdle || !v.flightBoost) {
				v.flightIdle = false;
				v.flightBoost = true;
				v.markSyncDirty();
			}
			// hand movement back to us - creative hover would cancel the dive
			if (p.getAbilities().flying) {
				p.getAbilities().flying = false;
				p.onUpdateAbilities();
			}
			if (ELYTRA_POSE && !p.isFallFlying()) p.startFallFlying();

			applyBoost(p, v);
			sonic(sp, v, speed);

		} else {
			// ================= IDLE HOVER =================
			if (v.flightBoost) {
				// coming off a boost - hand the hover back to vanilla
				v.flightBoost = false;
				v.flightIdle = true;
				v.markSyncDirty();
				if (p.isFallFlying()) p.stopFallFlying();
				p.getAbilities().flying = true;
				p.onUpdateAbilities();

			} else if (!p.getAbilities().flying) {
				// Vanilla creative flight went off under us - that is the player
				// double-tapping to come DOWN. End the flight rather than forcing
				// it back on, which is what used to trap you in the air.
				v.flightIdle = false;
				v.flightThrottle = 0;
				v.markSyncDirty();
				if (p.isFallFlying()) p.stopFallFlying();
				p.getPersistentData().putBoolean(SONIC, false);
				drive(sp, v);
				return;
			}
			p.getPersistentData().putBoolean(SONIC, false);
		}

		drive(sp, v);
	}

	private static boolean isLocal(Player p) {
		try {
			net.minecraft.client.player.LocalPlayer lp = net.minecraft.client.Minecraft.getInstance().player;
			return lp != null && lp.getUUID().equals(p.getUUID());
		} catch (Throwable t) {
			return false;
		}
	}

	/**
	 * Is the boost key down?
	 *
	 * The raw synced key flag comes first on purpose. isSprinting() latches -
	 * anything that calls setSprinting(true) keeps it true after you let go, so
	 * a boost that read it alone would never end. The flag follows the actual
	 * key, and isSprinting is only a fallback for when the input sync has not
	 * arrived yet.
	 */
	private static boolean boostHeld(Player p) {
		boolean key;
		if ("sprint".equalsIgnoreCase(BOOST_KEY))      key = OrdealInput.sprint(p) || p.isSprinting();
		else if ("sneak".equalsIgnoreCase(BOOST_KEY))  key = OrdealInput.sneak(p) || p.isShiftKeyDown();
		else return false;
		if (!key) return false;
		// sprint + W, like breaking into a run. Let go of W and you drop back
		// to the hover with the throttle winding down on its own.
		return !BOOST_NEEDS_FORWARD || OrdealInput.forward(p);
	}

	/** Where you actually move. Look direction, throttle, momentum, dive. */
	private static void applyBoost(Player p, OrdealModVariables.PlayerVariables v) {
		double speed = v.flightSpeed > 0 ? v.flightSpeed : topSpeed(p);
		double power = 1 + speed * v.flightThrottle;

		// looking down trades height for speed, looking up trades it back
		double dive = Math.sin(Math.toRadians(p.getXRot()));
		power *= 1 + dive * DIVE_BONUS;
		if (power < 0.1) power = 0.1;

		Vec3 want = p.getLookAngle().normalize().scale(power);
		Vec3 cur = p.getDeltaMovement();
		double m = Math.max(0, Math.min(0.95, MOMENTUM));
		p.setDeltaMovement(cur.scale(m).add(want.scale(1 - m)));
		p.fallDistance = 0;
		// NOTE: no setSprinting(true) here. Invincible sets it and it latches,
		// which is one reason a boost could never be released cleanly.
	}

	private static void sonic(ServerPlayer p, OrdealModVariables.PlayerVariables v, double speed) {
		boolean armed = p.getPersistentData().getBoolean(SONIC);
		if (v.flightThrottle >= SONIC_THROTTLE && speed >= SONIC_SPEED) {
			if (armed) return;
			p.getPersistentData().putBoolean(SONIC, true);
			StyleFx f = fxFor(v);
			String boom = f.sonic.isEmpty() ? SONIC_FX : f.sonic;
			if (!boom.isEmpty()) Fx.at(p, boom);
		} else if (v.flightThrottle < SONIC_THROTTLE && armed) {
			p.getPersistentData().putBoolean(SONIC, false);
		}
	}

	private static void shutDown(ServerPlayer p, OrdealModVariables.PlayerVariables v) {
		v.flightOn = false;
		v.flightIdle = false;
		v.flightBoost = false;
		v.flightThrottle = 0;
		v.flightSpeed = 0;
		v.markSyncDirty();

		if (p.isFallFlying()) p.stopFallFlying();
		if (!p.isCreative() && !p.isSpectator()) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
		p.getPersistentData().putBoolean(SONIC, false);
		p.getPersistentData().putDouble(DEBT, 0);
		drive(p, v);
	}

	// ==================== ANIMATION ====================

	/** Edge-triggered, exactly like Invincible's FlightPoseDriver. */
	private static void drive(ServerPlayer p, OrdealModVariables.PlayerVariables v) {
		String desired = null;
		if (v.flightOn && v.flightBoost) desired = style(v) + "_flight";
		else if (v.flightOn && v.flightIdle) desired = style(v) + "_idle";

		// AN ABILITY ANIMATION WINS. The procedures set PlayerCurrentAnimation
		// server-side before broadcasting, so this is readable right here. While
		// one is playing the flight clip is stopped outright rather than layered
		// under it - and because CURRENT is cleared with it, the flight clip is
		// re-sent the moment the ability finishes.
		if (desired != null && abilityAnimActive(p)) desired = null;

		UUID id = p.getUUID();
		String now = CURRENT.get(id);

		if (desired == null) {
			if (now != null) {
				OrdealAnim.stop(p);          // plain fade of OUR clip
				CURRENT.remove(id);
				// Only a REAL landing gets the exit beat. An ability taking the
				// body mid-flight stops the clip too, and firing the landing FX
				// there would be wrong - flight has not ended.
				if (!flying(p)) {
					StyleFx f = fxFor(v);
					if (!f.exit.isEmpty()) Fx.at(p, f.exit);
					String end = f.endAnim.isEmpty() ? END_ANIM : f.endAnim;
					if (end != null && !end.isEmpty()) playPluginAnim(p, end);
				}
			}
			return;
		}
		if (!desired.equals(now)) {
			if (now == null) {
				StyleFx f = fxFor(v);
				if (!f.enter.isEmpty()) Fx.at(p, f.enter);
			}
			OrdealAnim.play(p, desired, -1);
			CURRENT.put(id, desired);
		}
		loopFx(p, v);
	}

	/**
	 * Fire the MCreator Player Animator plugin for this player, on every client
	 * that can see them. This is the same packet the "play animation" block
	 * sends, so a clip authored in the plugin behaves exactly as it does there.
	 */
	private static void playPluginAnim(ServerPlayer p, String animation) {
		try {
			// Do not ask for a clip the plugin does not have. The packet sets the
			// PlayerCurrentAnimation flag regardless, and a flag for a missing
			// animation is never cleared - which used to freeze every Ordeal
			// animation permanently. An empty registry means the server has not
			// loaded them, and there we just send it.
			if (!OrdealModPlayerAnimationAPI.animations.isEmpty()
					&& !OrdealModPlayerAnimationAPI.animations.containsKey(animation)) {
				System.out.println("[ordeal] no Player Animator clip named '" + animation
						+ "' - skipping the flight end animation");
				return;
			}
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(p,
					new PlayPlayerAnimationMessage(p.getId(), animation, END_ANIM_OVERRIDE, false));
		} catch (Throwable t) {
			System.err.println("[ordeal] flight end animation '" + animation + "' failed: " + t);
		}
	}

	/** True while a Player Animator clip is driving this player's body. */
	private static boolean abilityAnimActive(ServerPlayer p) {
		try {
			return !p.getPersistentData().getString("PlayerCurrentAnimation").isEmpty();
		} catch (Throwable t) {
			return false;
		}
	}

	/** The hover / boost loop FX, on their own cadence so they do not spam. */
	private static void loopFx(ServerPlayer p, OrdealModVariables.PlayerVariables v) {
		StyleFx f = fxFor(v);
		if (v.flightBoost) {
			if (BOOST_FX_EVERY > 0 && !f.boostLoop.isEmpty() && p.tickCount % BOOST_FX_EVERY == 0)
				Fx.at(p, f.boostLoop);
		} else if (v.flightIdle) {
			if (IDLE_FX_EVERY > 0 && !f.idleLoop.isEmpty() && p.tickCount % IDLE_FX_EVERY == 0)
				Fx.at(p, f.idleLoop);
		}
	}

	/** FlightStyle -> a safe clip stem. "Omni Man" becomes "omni_man". */
	private static String style(OrdealModVariables.PlayerVariables v) {
		String s = v.FlightStyle;
		if (s == null) return DEFAULT_STYLE;
		s = s.replaceAll("§.", "").trim().toLowerCase(Locale.ROOT);
		s = s.replaceAll("[^a-z0-9_-]+", "_");
		s = s.replaceAll("_+", "_").replaceAll("^_|_$", "");
		return s.isEmpty() ? DEFAULT_STYLE : s;
	}

	// ==================== CHI ====================

	/**
	 * WHOSE CHI PAYS.
	 *
	 * This reads the "pays" field off the granting passive in the talent json -
	 * the same field, and the same three modes, that OrdealTalentChi.pay() uses
	 * for every other ability, so flight cannot disagree with what the terminal
	 * shows:
	 *
	 *   0  PLAYER_ONLY   your own chi bar, full stop
	 *   1  PLAYER_FIRST  your own bar first, the talent reserve covers what is left
	 *   2  TALENT_FIRST  the talent reserve first, your bar covers the shortfall
	 *
	 * "flight" is pays: 1, so your own chi goes first. The old version was
	 * hardwired to talent-first and never looked at the json at all, which is
	 * why it was eating the reserve.
	 *
	 * The fractional cost is carried as debt until it is worth a whole point,
	 * the same way the state ladder pays.
	 */
	private static boolean payChi(Player p, OrdealModVariables.PlayerVariables v,
			Grant g, double perTick) {
		if (perTick <= 0) return true;
		double debt = p.getPersistentData().getDouble(DEBT) + perTick;
		if (debt < 1) { p.getPersistentData().putDouble(DEBT, debt); return true; }

		double take = Math.floor(debt);
		p.getPersistentData().putDouble(DEBT, debt - take);

		int mode = payMode(g);
		int slot = g.talentId.isEmpty() ? 0 : OrdealTalentChi.slotOf(v, g.talentId);

		// no reserve to draw on -> it is all on your own bar whatever the mode says
		if (mode == OrdealTalentChi.PLAYER_ONLY || slot == 0 || OrdealTalentChi.max(v, slot) <= 0) {
			if (v.chi < take) return false;
			v.chi -= take;
			v.markSyncDirty();
			return true;
		}

		double reserve = OrdealTalentChi.get(v, slot);
		double fromPlayer, fromTalent;
		if (mode == OrdealTalentChi.TALENT_FIRST) {
			fromTalent = Math.min(reserve, take);
			fromPlayer = take - fromTalent;
		} else {                                   // PLAYER_FIRST - your bar leads
			fromPlayer = Math.min(v.chi, take);
			fromTalent = take - fromPlayer;
		}
		if (fromPlayer > v.chi || fromTalent > reserve) return false;

		v.chi -= fromPlayer;
		if (fromTalent > 0) OrdealTalentChi.set(v, slot, reserve - fromTalent);
		v.markSyncDirty();
		return true;
	}

	/**
	 * The grant's pay mode, straight off its passive's json. Defaults to
	 * PLAYER_FIRST when the passive cannot be found, because your own chi
	 * leading is the sane default - never silently drain a talent reserve.
	 */
	private static int payMode(Grant g) {
		if (g == null) return OrdealTalentChi.PLAYER_FIRST;
		try {
			OrdealTalents.Ability ab = OrdealTalents.ability(g.passiveId);
			if (ab != null) return ab.pays;
		} catch (Throwable ignored) {}
		return OrdealTalentChi.PLAYER_FIRST;
	}

	// ==================== ODDS AND ENDS ====================

	@SubscribeEvent
	public static void onFall(LivingFallEvent event) {
		if (!(event.getEntity() instanceof Player p)) return;
		if (p.getData(OrdealModVariables.PLAYER_VARIABLES).flightOn) event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		UUID id = event.getEntity().getUUID();
		CURRENT.remove(id);
		GRANTS.remove(id);
	}

	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		UUID id = event.getEntity().getUUID();
		CURRENT.remove(id);
		if (!event.isWasDeath()) return;
		GRANTS.remove(id);
		Player p = event.getEntity();
		if (p.level().isClientSide()) return;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		v.flightOn = false;
		v.flightIdle = false;
		v.flightBoost = false;
		v.flightThrottle = 0;
		v.flightSpeed = 0;
		v.markSyncDirty();
	}
}