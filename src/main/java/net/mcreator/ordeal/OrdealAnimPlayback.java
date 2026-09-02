package net.mcreator.ordeal;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client playback. One state per player UUID so everyone in view animates
 * independently, driven by the broadcast payload. Blends in on start, out on
 * stop, and chains comma-separated clips back to back.
 */
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class OrdealAnimPlayback {

	public static int BLEND_TICKS = 6;

	private OrdealAnimPlayback() {}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<String, OrdealAnimData> CACHE = new HashMap<>();

	private static final class State {
		List<OrdealAnimData> clips = new ArrayList<>();
		int index;
		int loops;          // -1 forever
		float time;         // ticks into the current clip
		float weight;       // 0..1
		int blend;
		boolean ending;
		float partial;      // sub-tick accumulator

		/**
		 * The looping clip that was running before this one, if any.
		 *
		 * Invincible's FpPlayback keeps this so a one-shot returns to whatever
		 * was looping instead of dropping the arms - start the idle, throw a
		 * punch, and the idle comes back on its own when the punch ends.
		 */
		List<OrdealAnimData> loopFallback;
		int fallbackLoops;

		/**
		 * The pose actually drawn, per bone, which chases the sampled pose
		 * instead of being it. This is what makes idle -> flight a transition
		 * rather than a jump: when a new clip starts, disp is still holding the
		 * old clip's pose and eases into the new one over a few ticks.
		 *
		 * Invincible calls this disp/tgt with easeVisual(VISUAL_RATE) in
		 * TpPlayback; same idea, per bone name instead of a fixed array.
		 */
		Map<String, OrdealAnimData.Pose> disp = new HashMap<>();
		boolean primed;
	}

	/**
	 * How fast the drawn pose chases the sampled one, per tick. 0.25 is roughly
	 * a quarter-second cross-fade. 1.0 disables the easing entirely (snap).
	 */
	public static float VISUAL_RATE = 0.25f;

	// ---- entry points -------------------------------------------------------

	public static void play(Player p, String names, int loops, int blend) {
		// NOTE: this used to also fire OrdealFpPlayback for your own player, which
		// pushed every THIRD-person clip into the FIRST-person rig as well. That is
		// why a tp animation showed up in fp view. They are two systems with two
		// commands on purpose - fp clips are played through OrdealFpPlayback by the
		// fp command, and nothing here touches them.

		List<OrdealAnimData> clips = new ArrayList<>();
		for (String n : names.split(",")) {
			OrdealAnimData d = clip(n.trim());
			if (d != null) clips.add(d);
		}
		if (clips.isEmpty()) return;

		State s = STATES.computeIfAbsent(p.getUUID(), k -> new State());

		// remember a looping clip so a one-shot played over it can come back
		boolean nowLooping = loops < 0 || clips.get(0).loop;
		if (!nowLooping && s.loops != 0 && isLooping(s) && !s.ending) {
			s.loopFallback = s.clips;
			s.fallbackLoops = s.loops;
		} else if (nowLooping) {
			s.loopFallback = null;
		}

		s.clips = clips;
		s.index = 0;
		s.loops = loops;
		s.time = 0;
		s.partial = 0;
		s.blend = blend < 0 ? BLEND_TICKS : blend;
		s.ending = false;
		if (s.blend <= 0) s.weight = 1f;
	}

	/** Only your own arms are ever drawn in first person. */
	private static boolean isSelf(Player p) {
		try {
			return net.minecraft.client.Minecraft.getInstance().player == p;
		} catch (Throwable t) {
			return false;
		}
	}

	/** True while this state is on a clip that repeats. */
	private static boolean isLooping(State s) {
		if (s.clips.isEmpty()) return false;
		return s.loops < 0 || s.clips.get(Math.min(s.index, s.clips.size() - 1)).loop;
	}

	public static void stop(Player p, String endClip, int blend) {
		// likewise: stopping a third-person clip must not stop the first-person rig
		State s = STATES.get(p.getUUID());
		if (s != null) { s.loopFallback = null; s.fallbackLoops = 0; }
		if (endClip != null && !endClip.isEmpty() && clip(endClip) != null) {
			play(p, endClip, 1, blend);
			State ns = STATES.get(p.getUUID());
			if (ns != null) { ns.loops = 1; ns.loopFallback = null; }
			return;
		}
		if (s != null) s.ending = true;
	}

	public static boolean isAnimating(Player p) {
		State s = STATES.get(p.getUUID());
		return s != null && s.weight > 0.001f;
	}


	/**
	 * True while the MCreator Player Animator plugin is driving this player (the
	 * "play animation" block), so the Ordeal clip system yields the body.
	 *
	 * This is Invincible's TpPlayback.externalAnimActive, ported 1:1. Ordeal has
	 * the same plugin (OrdealModPlayerAnimationAPI) and the same setupAnim mixin,
	 * and without this yield BOTH systems write the same ModelParts in the same
	 * frame - whichever mixin applies last wins, which is why a clip can look
	 * like it plays sometimes and does nothing other times.
	 *
	 * Primary check is the NBT flag the plugin's broadcast packet sets on every
	 * client before any mixin runs, so it is immune to mixin merge order. The
	 * active map is the backup. The entry clears itself when the animation ends,
	 * so this flips back false on its own and flight animation resumes.
	 */
	private static boolean externalAnimActive(Player p) {
		try {
			// ANY non-empty name means the plugin owns the body. No registry
			// lookup: ability animations are namespaced ("ordeal:akonito_left")
			// while the registry is keyed by bare name, so containsKey never
			// matched and the yield never happened - which is why abilities did
			// not override flight.
			//
			// The deadlock this guard was added for is fixed at the source
			// instead: Flight no longer fires a plugin animation of its own, so
			// nothing can leave a flag set for a clip that does not exist.
			if (!p.getPersistentData().getString("PlayerCurrentAnimation").isEmpty())
				return true;
			return OrdealModPlayerAnimationAPI.active_animations.get(p) != null;
		} catch (Throwable t) {
			return false; // never block our own system
		}
	}

	/**
	 * True when a Player Animator clip is currently overriding our pose. The
	 * flight driver reads this so an ability animation wins over the flight
	 * loop, and the flight loop comes straight back when the ability finishes -
	 * the clip never stopped, it was only yielded.
	 */
	public static boolean overriddenByPlugin(Player p) {
		return externalAnimActive(p);
	}

	/** Blended pose for one bone, or null when nothing is playing. */
	public static OrdealAnimData.Pose pose(Player p, String bone) {
		if (externalAnimActive(p)) return null; // PlayerAnimator owns the body
		State s = STATES.get(p.getUUID());
		if (s == null || s.weight <= 0.001f || s.clips.isEmpty()) return null;
		OrdealAnimData.Pose eased = s.disp.get(bone);
		if (eased != null) return eased;
		OrdealAnimData d = s.clips.get(Math.min(s.index, s.clips.size() - 1));
		return d.sample(bone, s.time);
	}

	/**
	 * Ease every bone's drawn pose toward this tick's sampled pose. Called once
	 * per client tick from advance(), so the rate is per tick and does not
	 * change with framerate.
	 *
	 * The first tick of the very first clip snaps into place rather than easing
	 * up from nothing - fading IN is the weight's job, not this one's.
	 */
	private static void ease(State s) {
		if (s.clips.isEmpty()) return;
		OrdealAnimData d = s.clips.get(Math.min(s.index, s.clips.size() - 1));
		float rate = s.primed ? Math.max(0.01f, Math.min(1f, VISUAL_RATE)) : 1f;
		for (String bone : OrdealAnimData.BONES) {
			OrdealAnimData.Pose want = d.sample(bone, s.time);
			if (want == null) want = new OrdealAnimData.Pose();
			OrdealAnimData.Pose cur = s.disp.get(bone);
			if (cur == null) { cur = new OrdealAnimData.Pose(); s.disp.put(bone, cur); }
			cur.rx += (want.rx - cur.rx) * rate;
			cur.ry += (want.ry - cur.ry) * rate;
			cur.rz += (want.rz - cur.rz) * rate;
			cur.x  += (want.x  - cur.x)  * rate;
			cur.y  += (want.y  - cur.y)  * rate;
			cur.z  += (want.z  - cur.z)  * rate;
		}
		s.primed = true;
	}

	/** Living-motion level of whatever is playing on this player. */
	public static int noiseLevel(Player p) {
		State s = STATES.get(p.getUUID());
		if (s == null || s.clips.isEmpty()) return 0;
		return s.clips.get(Math.min(s.index, s.clips.size() - 1)).noise;
	}

	/** Lean level of whatever is playing, or -1 when nothing is. */
	public static int wobbleLevel(Player p) {
		State s = STATES.get(p.getUUID());
		if (s == null || s.clips.isEmpty()) return -1;
		return s.clips.get(Math.min(s.index, s.clips.size() - 1)).wobble;
	}

	public static float weight(Player p) {
		if (externalAnimActive(p)) return 0f; // PlayerAnimator owns the body
		State s = STATES.get(p.getUUID());
		return s == null ? 0f : s.weight;
	}

	// ---- tick ---------------------------------------------------------------

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) { STATES.clear(); return; }
		if (mc.isPaused()) return;

		STATES.entrySet().removeIf(e -> {
			State s = e.getValue();
			advance(s);
			return s.weight <= 0.001f && s.ending;
		});
	}

	private static void advance(State s) {
		if (s.clips.isEmpty()) { s.ending = true; s.weight = 0; return; }

		ease(s); // chase the sampled pose - this is the clip-to-clip cross-fade

		if (s.ending) {
			s.weight = Math.max(0f, s.weight - (s.blend <= 0 ? 1f : 1f / s.blend));
			return;
		}
		if (s.weight < 1f)
			s.weight = Math.min(1f, s.weight + (s.blend <= 0 ? 1f : 1f / s.blend));

		OrdealAnimData d = s.clips.get(s.index);
		s.time += Math.max(0.01f, d.speed);

		float len = d.length();
		if (len <= 0 || s.time < len) return;

		// clip finished
		if (s.index < s.clips.size() - 1) {
			s.index++;
			s.time = 0;
			return;
		}
		if (s.loops < 0 || d.loop) { s.time = 0; s.index = 0; return; }
		if (s.loops > 1) { s.loops--; s.time = 0; s.index = 0; return; }

		// HOLD LAST FRAME: park on the final pose and stay there. sample()
		// already holds the last key past the end of the channel, so freezing
		// the clock is all it takes. Nothing releases it but an explicit stop -
		// that is the point of a stance.
		if (d.holdLast) { s.time = len; return; }

		// a one-shot over a loop hands back to the loop instead of ending
		if (s.loopFallback != null && !s.loopFallback.isEmpty()) {
			s.clips = s.loopFallback;
			s.loops = s.fallbackLoops == 0 ? -1 : s.fallbackLoops;
			s.loopFallback = null;
			s.index = 0;
			s.time = 0;
			s.blend = BLEND_TICKS;
			return;
		}
		s.ending = true;
	}

	// ---- clip cache ---------------------------------------------------------

	private static OrdealAnimData clip(String name) {
		if (name == null || name.isEmpty()) return null;
		if (CACHE.containsKey(name)) return CACHE.get(name);
		OrdealAnimData d = OrdealAnimStore.load(name);
		CACHE.put(name, d);
		return d;
	}

	/** Drop the cache so the editor's saves are picked up without a restart. */
	public static void invalidate() {
		CACHE.clear();
		OrdealAnimStore.forgetKinds();
	}
}