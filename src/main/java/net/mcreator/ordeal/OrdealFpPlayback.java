package net.mcreator.ordeal;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * First-person playback — a port of Invincible's FpPlayback.
 *
 * The reason first person felt stiff is that it was sampling the clip straight
 * onto the arms. This does what Invincible does instead:
 *
 *   · every switch SNAPSHOTS the pose on screen and cross-fades into the new
 *     clip over BLEND_TICKS, so a clip never jumps to its own frame 0
 *   · the displayed pose EASES toward its target every frame, so even a hard
 *     cut arrives smoothly
 *   · a looping clip is remembered, and a one-shot played over it hands back
 *     when it finishes - start the idle, throw a punch, the idle returns
 *   · with nothing playing the arms sit BELOW the view, so the first animation
 *     rises up into frame instead of dropping in from nowhere
 *
 * One player - yourself. First person only ever draws your own arms.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class OrdealFpPlayback {

	private OrdealFpPlayback() {}

	/** Cross-fade length when switching clips, in ticks. Lower = snappier. */
	public static int BLEND_TICKS = 4;
	/** How fast the drawn pose chases its target each frame. */
	public static float EASE = 0.35f;

	/** Off-screen low: the arms wait below the view so they rise up into shot. */
	private static OrdealAnimData.Pose lowRest(float x) {
		OrdealAnimData.Pose p = new OrdealAnimData.Pose();
		p.x = x;
		p.y = -1.7f;
		p.z = -0.15f;
		p.rx = -8f;
		return p;
	}

	private static final OrdealAnimData.Pose REST_R = lowRest(0.25f);
	private static final OrdealAnimData.Pose REST_L = lowRest(-0.25f);

	// what the renderer draws
	public static OrdealAnimData.Pose dispR = new OrdealAnimData.Pose();
	public static OrdealAnimData.Pose dispL = new OrdealAnimData.Pose();
	public static OrdealAnimData.Pose dispCam = new OrdealAnimData.Pose();
	public static int activeNoise = 1;

	// what it is easing toward
	private static OrdealAnimData.Pose tgtR = new OrdealAnimData.Pose();
	private static OrdealAnimData.Pose tgtL = new OrdealAnimData.Pose();
	private static OrdealAnimData.Pose tgtCam = new OrdealAnimData.Pose();

	// where the current cross-fade started from
	private static OrdealAnimData.Pose fromR = new OrdealAnimData.Pose();
	private static OrdealAnimData.Pose fromL = new OrdealAnimData.Pose();
	private static OrdealAnimData.Pose fromCam = new OrdealAnimData.Pose();
	private static int blendTicks;

	private static OrdealAnimData anim;
	private static float playTicks;
	private static int loopsLeft;
	private static OrdealAnimData loopFallback;

	private static String[] chain;
	private static int chainIdx, chainLoops, chainBlend = -1;

	private static boolean active, deactivating;
	public static boolean editorPreview = false;

	public static boolean isActive() { return active || deactivating || editorPreview; }

	public static boolean isAnimating() { return active; }

	// ---- entry points -------------------------------------------------------

	public static void play(String names, int loops, int blend) {
		if (names == null || names.isEmpty()) return;
		if (names.indexOf(',') >= 0) { playChain(names.split(","), loops, blend); return; }
		chain = null;
		OrdealAnimData a = OrdealAnimStore.load(names.trim());
		if (a == null) return;
		start(a, loops, blend);
	}

	public static void playChain(String[] names, int loops, int blend) {
		if (names == null || names.length == 0) return;
		java.util.List<String> clean = new java.util.ArrayList<>();
		for (String n : names) if (n != null && !n.trim().isEmpty()) clean.add(n.trim());
		if (clean.isEmpty()) return;
		chain = clean.toArray(new String[0]);
		chainLoops = loops == 0 ? 1 : loops;
		chainBlend = blend;
		if (!restartChain()) chain = null;
	}

	public static void stop() {
		deactivating = true;
		chain = null;
		loopFallback = null;
	}

	/** Editor: hold a single pose on the arms, no clock running. */
	public static void setPreview(OrdealAnimData.Pose r, OrdealAnimData.Pose l, OrdealAnimData.Pose cam) {
		editorPreview = true;
		active = false;
		deactivating = false;
		anim = null;
		chain = null;
		loopFallback = null;
		dispR = copy(r); dispL = copy(l); dispCam = copy(cam);
		tgtR = dispR; tgtL = dispL; tgtCam = dispCam;
	}

	public static void clearPreview() { editorPreview = false; }

	// ---- internals ----------------------------------------------------------

	private static void start(OrdealAnimData a, int loops, int blend) {
		if (a == null) return;

		// coming from fully off: drop invisibly to the low pose first, so the
		// clip rises up from the bottom rather than appearing mid-air
		if (!active && !deactivating && !editorPreview) {
			dispR = copy(REST_R);
			dispL = copy(REST_L);
			dispCam = new OrdealAnimData.Pose();
		}
		fromR = copy(dispR); fromL = copy(dispL); fromCam = copy(dispCam);
		blendTicks = blend < 0 ? BLEND_TICKS : blend;

		editorPreview = false;
		anim = a;
		activeNoise = a.noise;
		playTicks = 0;
		// a clip marked loop in its json loops, whatever the command asked for -
		// otherwise the default "loops 1" would end an idle after one pass
		loopsLeft = a.loop ? -1 : (loops == 0 ? 1 : loops);
		active = true;
		deactivating = false;

		// a clip that repeats is what a later one-shot comes back to
		if (a.loop || loops < 0) loopFallback = a;
	}

	private static boolean advanceChain() {
		while (chain != null && chainIdx + 1 < chain.length) {
			chainIdx++;
			OrdealAnimData next = OrdealAnimStore.load(chain[chainIdx]);
			if (next != null) { start(next, 1, chainBlend); return true; }
		}
		return false;
	}

	private static boolean restartChain() {
		if (chain == null) return false;
		for (chainIdx = 0; chainIdx < chain.length; chainIdx++) {
			OrdealAnimData a = OrdealAnimStore.load(chain[chainIdx]);
			if (a != null) { start(a, 1, chainBlend); return true; }
		}
		return false;
	}

	private static void toIdleOrStop() {
		chain = null;
		if (loopFallback != null) { start(loopFallback, -1, -1); return; }
		deactivating = true;
		anim = null;
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		if (editorPreview) return;
		if (Minecraft.getInstance().isPaused()) return;
		if (!active && !deactivating) return;

		if (deactivating) {
			tgtR = REST_R; tgtL = REST_L; tgtCam = new OrdealAnimData.Pose();
		} else if (anim != null) {
			playTicks += Math.max(0.01f, anim.speed);
			float dur = anim.length();

			if (dur > 0 && playTicks >= dur) {
				if (chain != null) {
					if (!advanceChain()) {
						boolean repeat = chainLoops < 0 || chainLoops > 1;
						if (chainLoops > 1) chainLoops--;
						if (!(repeat && restartChain())) toIdleOrStop();
					}
				} else if (anim.loop || loopsLeft < 0) {
					// A loop wrapping back to frame 0 is a jump from the last
					// pose to the first one in a single tick, which reads as the
					// animation stopping and starting again. Cross-fade the wrap
					// with the same machinery a clip switch uses.
					fromR = copy(dispR);
					fromL = copy(dispL);
					fromCam = copy(dispCam);
					blendTicks = BLEND_TICKS;
					playTicks = 0;
				} else {
					loopsLeft--;
					if (loopsLeft > 0) playTicks = 0;
					// hold last frame: sit on the final pose until something stops it
					else if (anim.holdLast) playTicks = dur;
					else toIdleOrStop();
				}
			}

			if (!deactivating && anim != null) {
				OrdealAnimData.Pose r = sample(anim, "fp_right");
				OrdealAnimData.Pose l = sample(anim, "fp_left");
				OrdealAnimData.Pose c = sample(anim, "fp_cam");
				if (blendTicks > 0) {
					float t = 1f - (blendTicks / (float) Math.max(1, BLEND_TICKS));
					float e = t * t * (3f - 2f * t);   // smoothstep
					tgtR = lerp(fromR, r, e);
					tgtL = lerp(fromL, l, e);
					tgtCam = lerp(fromCam, c, e);
					blendTicks--;
				} else {
					tgtR = r; tgtL = l; tgtCam = c;
				}
			}
		}

		// eased all the way down: hand the arms back to vanilla
		if (deactivating && dispR.y <= -1.4f && dispL.y <= -1.4f) {
			active = false;
			deactivating = false;
			anim = null;
			chain = null;
			loopFallback = null;
			dispCam = new OrdealAnimData.Pose();
			tgtCam = new OrdealAnimData.Pose();
		}
	}

	/** Called once a frame by the renderer, before it draws. */
	public static void advanceVisual(float rate) {
		dispR = lerp(dispR, tgtR, rate);
		dispL = lerp(dispL, tgtL, rate);
		dispCam = lerp(dispCam, tgtCam, rate);
	}

	/**
	 * One place owns the clock, and it is this class.
	 *
	 * OrdealAnimData.sample wraps the time itself when the clip is marked loop.
	 * With the playback ALSO resetting playTicks, a clip was being wrapped twice
	 * - the pose jumped back before the playback had finished the pass, which
	 * looked like the animation restarting early. Clamping the time here leaves
	 * exactly one thing in charge of when a loop comes round.
	 */
	private static OrdealAnimData.Pose sample(OrdealAnimData d, String bone) {
		float len = d.length();
		float t = len > 0 ? Math.min(playTicks, len) : playTicks;
		OrdealAnimData.Pose p = d.sample(bone, t);
		return p == null ? new OrdealAnimData.Pose() : p;
	}

	private static OrdealAnimData.Pose lerp(OrdealAnimData.Pose a, OrdealAnimData.Pose b, float t) {
		OrdealAnimData.Pose o = new OrdealAnimData.Pose();
		o.rx = a.rx + (b.rx - a.rx) * t;
		o.ry = a.ry + (b.ry - a.ry) * t;
		o.rz = a.rz + (b.rz - a.rz) * t;
		o.x = a.x + (b.x - a.x) * t;
		o.y = a.y + (b.y - a.y) * t;
		o.z = a.z + (b.z - a.z) * t;
		return o;
	}

	private static OrdealAnimData.Pose copy(OrdealAnimData.Pose s) {
		if (s == null) return new OrdealAnimData.Pose();
		OrdealAnimData.Pose p = new OrdealAnimData.Pose();
		p.rx = s.rx; p.ry = s.ry; p.rz = s.rz;
		p.x = s.x; p.y = s.y; p.z = s.z;
		return p;
	}
}