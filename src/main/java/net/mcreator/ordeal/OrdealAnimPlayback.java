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
	}

	// ---- entry points -------------------------------------------------------

	public static void play(Player p, String names, int loops, int blend) {
		// first person runs on its own clock so it can cross-fade and hand back
		// to a loop the way Invincible does - see OrdealFpPlayback
		if (p.level().isClientSide() && isSelf(p))
			OrdealFpPlayback.play(names, loops, blend);

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
		if (p.level().isClientSide() && isSelf(p)) {
			if (endClip != null && !endClip.isEmpty()) OrdealFpPlayback.play(endClip, 1, blend);
			else OrdealFpPlayback.stop();
		}
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

	/** Blended pose for one bone, or null when nothing is playing. */
	public static OrdealAnimData.Pose pose(Player p, String bone) {
		State s = STATES.get(p.getUUID());
		if (s == null || s.weight <= 0.001f || s.clips.isEmpty()) return null;
		OrdealAnimData d = s.clips.get(Math.min(s.index, s.clips.size() - 1));
		return d.sample(bone, s.time);
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
	}
}