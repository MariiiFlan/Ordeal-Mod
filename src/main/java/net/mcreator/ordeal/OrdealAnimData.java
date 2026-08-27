package net.mcreator.ordeal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrdealAnimData {

	/** Canonical bone ids, in display order. */
	public static final String[] BONES = {
			"head", "body", "right_arm", "left_arm", "right_leg", "left_leg", "root"
	};

	/**
	 * First person is its own track set, not a scaled copy of the body.
	 *
	 * Invincible authored first person separately for a reason: a hand six
	 * inches from your face wants completely different numbers from a shoulder
	 * swing seen from behind. These three channels are what its fpanim files
	 * hold - the two hands in view space, and the camera itself.
	 *
	 * Rotations are degrees. Translations are model pixels here like every
	 * other channel, and get divided back to blocks at render.
	 */
	public static final String[] FP_BONES = { "fp_right", "fp_left", "fp_cam" };

	public static boolean isFp(String bone) {
		return bone != null && bone.startsWith("fp_");
	}

	public static final int FORMAT = 2;

	// ------------------------------------------------------------------
	// Easing
	// ------------------------------------------------------------------

	public enum Ease {
		LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, SMOOTH, BACK, HOLD;

		public static Ease fromId(String id) {
			if (id == null)
				return LINEAR;
			switch (id.toLowerCase()) {
				case "ease_in": case "in": return EASE_IN;
				case "ease_out": case "out": return EASE_OUT;
				case "ease_in_out": case "inout": case "in_out": return EASE_IN_OUT;
				case "smooth": return SMOOTH;
				case "back": return BACK;
				case "hold": case "cut": return HOLD;
				default: return LINEAR;
			}
		}

		public String id() {
			return name().toLowerCase();
		}

		/** Maps linear progress 0..1 to eased progress. SMOOTH is handled in sample() (needs neighbors). */
		public float apply(float x) {
			switch (this) {
				case EASE_IN: return x * x;
				case EASE_OUT: return 1f - (1f - x) * (1f - x);
				case EASE_IN_OUT: return x < 0.5f ? 2f * x * x : 1f - 2f * (1f - x) * (1f - x);
				case BACK: {
					// easeOutBack — overshoots the target then settles.
					float c1 = 1.70158f, c3 = c1 + 1f;
					float p = x - 1f;
					return 1f + c3 * p * p * p + c1 * p * p;
				}
				case HOLD: return 0f;
				case SMOOTH: // fall through — smoothstep as the pairwise fallback
					return x * x * (3f - 2f * x);
				default: return x;
			}
		}
	}

	// ------------------------------------------------------------------
	// Key + Pose
	// ------------------------------------------------------------------

	public static class Key {
		public float t;
		public float[] rot = new float[3]; // pitch, yaw, roll (degrees)
		public float[] pos = new float[3]; // x, y, z (pixels, model space)
		public Ease ease = Ease.LINEAR;

		public Key() {}

		public Key(float t) {
			this.t = t;
		}

		public Key copy() {
			Key k = new Key(t);
			k.rot = rot.clone();
			k.pos = pos.clone();
			k.ease = ease;
			return k;
		}
	}

	/** A sampled bone transform at a point in time. */
	public static class Pose {
		public float rx, ry, rz, x, y, z;
	}

	// ------------------------------------------------------------------
	// Clip data
	// ------------------------------------------------------------------

	public float fps = 20f;
	public float speed = 1f;
	public boolean loop = false;
	/** Living-motion level baked into the clip: 0 Off, 1 Low, 2 Med, 3 High. */
	public int noise = 1;
	/** WASD lean level while this clip plays: 0 Off, 1 Low, 2 Med, 3 High. */
	public int wobble = 1;
	/** bone id -> keys sorted by t. Only bones with keys are present. */
	public final Map<String, List<Key>> bones = new LinkedHashMap<>();

	public List<Key> channel(String bone) {
		return bones.computeIfAbsent(bone, b -> new ArrayList<>());
	}

	/** Clip length in ticks = time of the last key across all channels. */
	public float length() {
		float max = 0f;
		for (List<Key> keys : bones.values())
			for (Key k : keys)
				if (k.t > max)
					max = k.t;
		return max;
	}

	public int keyCount() {
		int n = 0;
		for (List<Key> keys : bones.values())
			n += keys.size();
		return n;
	}

	/** Insert or replace a key at time t (within half a tick) on a bone, keeping the channel sorted. */
	public Key putKey(String bone, Key key) {
		List<Key> keys = channel(bone);
		for (int i = 0; i < keys.size(); i++) {
			float dt = keys.get(i).t - key.t;
			if (Math.abs(dt) < 0.5f) {
				keys.set(i, key);
				return key;
			}
			if (keys.get(i).t > key.t) {
				keys.add(i, key);
				return key;
			}
		}
		keys.add(key);
		return key;
	}

	public void sortChannel(String bone) {
		channel(bone).sort((a, b) -> Float.compare(a.t, b.t));
	}

	// ------------------------------------------------------------------
	// Sampling
	// ------------------------------------------------------------------

	/**
	 * Sample a bone at time t (ticks). Returns null when the bone has no keys
	 * (meaning: leave it at vanilla / don't touch it).
	 */
	public Pose sample(String bone, float t) {
		List<Key> keys = bones.get(bone);
		if (keys == null || keys.isEmpty())
			return null;

		float len = length();
		if (loop && len > 0f) {
			t = t % len;
			if (t < 0f)
				t += len;
		}

		// before first key -> hold first; after last -> hold last
		Key first = keys.get(0);
		if (t <= first.t || keys.size() == 1)
			return poseOf(first);
		Key last = keys.get(keys.size() - 1);
		if (t >= last.t)
			return poseOf(last);

		int i = 0;
		while (i < keys.size() - 1 && keys.get(i + 1).t <= t)
			i++;
		Key a = keys.get(i);
		Key b = keys.get(i + 1);

		float span = b.t - a.t;
		float x = span <= 0f ? 1f : (t - a.t) / span;

		if (a.ease == Ease.SMOOTH && keys.size() >= 2) {
			// Catmull-Rom through neighbors for genuinely smooth motion across keys.
			Key p0 = i > 0 ? keys.get(i - 1) : a;
			Key p3 = i + 2 < keys.size() ? keys.get(i + 2) : b;
			Pose p = new Pose();
			p.rx = catmull(p0.rot[0], a.rot[0], b.rot[0], p3.rot[0], x);
			p.ry = catmull(p0.rot[1], a.rot[1], b.rot[1], p3.rot[1], x);
			p.rz = catmull(p0.rot[2], a.rot[2], b.rot[2], p3.rot[2], x);
			p.x = catmull(p0.pos[0], a.pos[0], b.pos[0], p3.pos[0], x);
			p.y = catmull(p0.pos[1], a.pos[1], b.pos[1], p3.pos[1], x);
			p.z = catmull(p0.pos[2], a.pos[2], b.pos[2], p3.pos[2], x);
			return p;
		}

		float e = a.ease.apply(x);
		Pose p = new Pose();
		p.rx = lerp(a.rot[0], b.rot[0], e);
		p.ry = lerp(a.rot[1], b.rot[1], e);
		p.rz = lerp(a.rot[2], b.rot[2], e);
		p.x = lerp(a.pos[0], b.pos[0], e);
		p.y = lerp(a.pos[1], b.pos[1], e);
		p.z = lerp(a.pos[2], b.pos[2], e);
		return p;
	}

	private static Pose poseOf(Key k) {
		Pose p = new Pose();
		p.rx = k.rot[0];
		p.ry = k.rot[1];
		p.rz = k.rot[2];
		p.x = k.pos[0];
		p.y = k.pos[1];
		p.z = k.pos[2];
		return p;
	}

	private static float lerp(float a, float b, float x) {
		return a + (b - a) * x;
	}

	private static float catmull(float p0, float p1, float p2, float p3, float x) {
		float x2 = x * x, x3 = x2 * x;
		return 0.5f * ((2f * p1) + (-p0 + p2) * x
				+ (2f * p0 - 5f * p1 + 4f * p2 - p3) * x2
				+ (-p0 + 3f * p1 - 3f * p2 + p3) * x3);
	}

	// ------------------------------------------------------------------
	// JSON
	// ------------------------------------------------------------------

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public String toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("format", FORMAT);
		root.addProperty("fps", fps);
		root.addProperty("speed", speed);
		root.addProperty("loop", loop);
		root.addProperty("noise", noise);
		root.addProperty("wobble", wobble);
		JsonObject bonesObj = new JsonObject();
		for (Map.Entry<String, List<Key>> e : bones.entrySet()) {
			if (e.getValue().isEmpty())
				continue;
			JsonArray arr = new JsonArray();
			for (Key k : e.getValue()) {
				JsonObject ko = new JsonObject();
				ko.addProperty("t", k.t);
				ko.add("rot", floats(k.rot));
				ko.add("pos", floats(k.pos));
				ko.addProperty("ease", k.ease.id());
				arr.add(ko);
			}
			bonesObj.add(e.getKey(), arr);
		}
		root.add("bones", bonesObj);
		return GSON.toJson(root);
	}

	public static OrdealAnimData fromJson(String json) {
		JsonObject root = GSON.fromJson(json, JsonObject.class);
		OrdealAnimData d = new OrdealAnimData();
		if (root.has("fps"))
			d.fps = root.get("fps").getAsFloat();
		if (root.has("speed"))
			d.speed = root.get("speed").getAsFloat();
		if (root.has("loop"))
			d.loop = root.get("loop").getAsBoolean();
		if (root.has("noise"))
			d.noise = root.get("noise").getAsInt();
		if (root.has("wobble"))
			d.wobble = root.get("wobble").getAsInt();
		if (root.has("bones")) {
			JsonObject bonesObj = root.getAsJsonObject("bones");
			for (Map.Entry<String, JsonElement> e : bonesObj.entrySet()) {
				List<Key> keys = d.channel(e.getKey());
				for (JsonElement el : e.getValue().getAsJsonArray()) {
					JsonObject ko = el.getAsJsonObject();
					Key k = new Key(ko.get("t").getAsFloat());
					readFloats(ko.get("rot"), k.rot);
					readFloats(ko.get("pos"), k.pos);
					k.ease = Ease.fromId(ko.has("ease") ? ko.get("ease").getAsString() : null);
					keys.add(k);
				}
				d.sortChannel(e.getKey());
			}
		}
		return d;
	}

	private static JsonArray floats(float[] v) {
		JsonArray a = new JsonArray();
		for (float f : v)
			a.add(f);
		return a;
	}

	private static void readFloats(JsonElement el, float[] out) {
		if (el == null)
			return;
		JsonArray a = el.getAsJsonArray();
		for (int i = 0; i < out.length && i < a.size(); i++)
			out[i] = a.get(i).getAsFloat();
	}

	public OrdealAnimData copy() {
		return fromJson(toJson());
	}
}