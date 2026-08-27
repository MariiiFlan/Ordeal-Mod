package net.mcreator.ordeal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import net.mcreator.ordeal.OrdealAnimData.Ease;
import net.mcreator.ordeal.OrdealAnimData.Key;

/**
 * Blockbench ⇄ Ordeal Animator.
 *
 * Reads and writes the Bedrock animation JSON that Blockbench exports
 * (format_version 1.8.0): seconds-keyed rotation/position channels per bone,
 * degrees, model-pixel positions, catmullrom or linear interpolation. Import
 * takes every animation in the file and saves each as an editor clip; export
 * writes the open clip as <name>.animation.json.
 *
 * Bone names: Blockbench player rigs call the whole-body bone "body" and the
 * chest "torso" — those map to the editor's "root" and "body".
 */
@OnlyIn(Dist.CLIENT)
public final class OrdealBlockbench {

	private OrdealBlockbench() {}

	private static final Map<String, String> IMPORT_BONE = Map.of(
			"body", "root", "torso", "body", "head", "head",
			"right_arm", "right_arm", "left_arm", "left_arm",
			"right_leg", "right_leg", "left_leg", "left_leg", "root", "root");

	private static final Map<String, String> EXPORT_BONE = Map.of(
			"root", "body", "body", "torso", "head", "head",
			"right_arm", "right_arm", "left_arm", "left_arm",
			"right_leg", "right_leg", "left_leg", "left_leg");

	// Axis conventions. Blockbench authors Y-up and Bedrock applies rotations
	// with X and Y negated; the vanilla model parts are Y-down, which cancels
	// both — limb values pass through untouched. The root is different: the
	// editor applies it in the un-flipped world frame, so it keeps Bedrock's
	// negation. Flip a sign here if a clip ever imports mirrored.
	private static final float[] BONE_ROT = {1f, 1f, 1f};
	private static final float[] BONE_POS = {1f, 1f, 1f};
	private static final float[] ROOT_ROT = {-1f, -1f, 1f};
	private static final float[] ROOT_POS = {-1f, -1f, 1f};

	private static float[] rotSign(String bone) { return "root".equals(bone) ? ROOT_ROT : BONE_ROT; }

	private static float[] posSign(String bone) { return "root".equals(bone) ? ROOT_POS : BONE_POS; }

	// ==================================================================
	// IMPORT
	// ==================================================================

	/** Returns a flash message, or null when the dialog was cancelled. */
	public static String importFile() {
		String path = dialogOpen();
		if (path == null) return null;
		try {
			JsonObject root = new Gson().fromJson(
					Files.readString(Path.of(path), StandardCharsets.UTF_8), JsonObject.class);

			// Invincible's own clips are a flat "frames" list, not Bedrock's
			// nested "animations" object, so the format is told apart by what
			// is actually in the file rather than by the file extension.
			if (root != null && root.has("frames") && root.get("frames").isJsonArray())
				return importInvincible(root, Path.of(path));

			JsonObject anims = root == null ? null : root.getAsJsonObject("animations");
			if (anims == null || anims.isEmpty()) return "No animations in that file";
			int n = 0;
			String first = null;
			for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
				String name = e.getKey();
				int dot = name.lastIndexOf('.');
				if (dot >= 0) name = name.substring(dot + 1); // "animation.model.idle" -> "idle"
				OrdealAnimData d = fromBedrock(e.getValue().getAsJsonObject());
				if (!OrdealAnimStore.save(name, d)) continue;
				n++;
				if (first == null) first = name;
			}
			if (first == null) return "Import failed - nothing saved";
			OrdealAnimData loaded = OrdealAnimStore.load(first);
			if (loaded != null) {
				OrdealAnimatorClient.data = loaded;
				OrdealAnimatorClient.clipName = first;
				OrdealAnimatorClient.time = 0;
				OrdealAnimatorClient.livePose.clear();
			}
			return "Imported " + n + " animation(s) - now editing " + first;
		} catch (Exception ex) {
			return "Import failed: " + ex.getMessage();
		}
	}

	// ---- Invincible import --------------------------------------------------

	/** tpanim bone -> ours. fpanim uses "right"/"left"/"cam" instead. */
	private static final Map<String, String> IIC_BONE = Map.of(
			"root", "root", "head", "head", "body", "body",
			"rightArm", "right_arm", "leftArm", "left_arm",
			"rightLeg", "right_leg", "leftLeg", "left_leg");

	/**
	 * Invincible's clips, both flavours.
	 *
	 *   tpanim - one entry per bone, {rx,ry,rz,tx,ty,tz}, already in degrees
	 *            and model pixels. A straight rename.
	 *   fpanim - "right"/"left"/"cam", {pitch,yaw,roll,tx,ty,tz}. These land on
	 *            our own first-person tracks, not on the body arms - they were
	 *            authored at arm's length and scaling them into a shoulder
	 *            swing would ruin them. Their translations are in blocks and
	 *            stay that way - the renderer applies them in blocks too.
	 *
	 * "ticks" is a DURATION on each frame, not a timestamp, so the playhead is
	 * built by adding them up.
	 */
	private static String importInvincible(JsonObject root, Path path) {
		OrdealAnimData d = new OrdealAnimData();
		var frames = root.getAsJsonArray("frames");
		if (frames.isEmpty()) return "That file has no frames";

		boolean fp = false;
		for (JsonElement fe : frames)
			if (fe.isJsonObject() && (fe.getAsJsonObject().has("right")
					|| fe.getAsJsonObject().has("cam"))) { fp = true; break; }

		float t = 0;
		int keys = 0;
		for (JsonElement fe : frames) {
			if (!fe.isJsonObject()) continue;
			JsonObject f = fe.getAsJsonObject();
			Ease ease = Ease.fromId(f.has("transition") ? f.get("transition").getAsString() : null);

			for (Map.Entry<String, JsonElement> e : f.entrySet()) {
				String src = e.getKey();
				if (!e.getValue().isJsonObject()) continue;
				String bone = fp ? fpBone(src) : IIC_BONE.get(src);
				if (bone == null) continue;
				JsonObject b = e.getValue().getAsJsonObject();

				Key k = new Key(t);
				k.ease = ease;
				k.rot[0] = num(b, "rx", num(b, "pitch", 0));
				k.rot[1] = num(b, "ry", num(b, "yaw", 0));
				k.rot[2] = num(b, "rz", num(b, "roll", 0));
				// first-person translations stay in BLOCKS, exactly as Invincible
				// stores and applies them; only the body uses model pixels
				k.pos[0] = num(b, "tx", 0);
				k.pos[1] = num(b, "ty", 0);
				k.pos[2] = num(b, "tz", 0);

				d.putKey(bone, k);
				keys++;
			}
			t += Math.max(1, f.has("ticks") ? f.get("ticks").getAsFloat() : 1);
		}
		if (keys == 0) return "Nothing in that file matched a bone";

		if (root.has("loop")) d.loop = root.get("loop").getAsBoolean();
		if (root.has("noise")) d.noise = root.get("noise").getAsInt();

		String name = path.getFileName().toString();
		int dot = name.lastIndexOf('.');
		if (dot > 0) name = name.substring(0, dot);
		// a file that came through a download or an upload often carries a
		// timestamp in front of the real name - drop it
		name = name.replaceFirst("^\\d{6,}[_-]", "");
		if (name.isEmpty()) name = "imported";
		if (!OrdealAnimStore.save(name, d))
			return "Read the file fine but could not save \"" + name + "\" - check the config folder";

		OrdealAnimData loaded = OrdealAnimStore.load(name);
		if (loaded == null) return "Saved \"" + name + "\" but could not read it back";

		OrdealAnimatorClient.data = loaded;
		OrdealAnimatorClient.clipName = name;
		OrdealAnimatorClient.time = 0;
		OrdealAnimatorClient.livePose.clear();

		// A first-person clip has nothing on the body tracks, so landing in the
		// third-person view would show an empty timeline and look like the
		// import did nothing. Switch to the view the clip actually lives in.
		OrdealAnimatorClient.firstPerson = fp;
		OrdealAnimatorClient.selBone = fp ? "fp_right" : "right_arm";
		OrdealOrbitCam.center();

		return "Imported " + (fp ? "first-person" : "third-person") + " clip \"" + name
				+ "\" - " + keys + " keys over " + Math.round(t) + " ticks";
	}

	private static String fpBone(String src) {
		switch (src) {
			case "right": return "fp_right";
			case "left": return "fp_left";
			case "cam": return "fp_cam";
			default: return null;
		}
	}

	private static float num(JsonObject o, String k, float def) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsFloat() : def;
	}

	private static class Ch {
		final float[] v = new float[3];
		Ease ease = Ease.LINEAR;
	}

	private static OrdealAnimData fromBedrock(JsonObject anim) {
		OrdealAnimData d = new OrdealAnimData();
		d.fps = 20f;
		if (anim.has("loop")) {
			JsonElement l = anim.get("loop");
			d.loop = l.isJsonPrimitive() && l.getAsJsonPrimitive().isBoolean()
					? l.getAsBoolean() : "true".equals(l.getAsString());
		}
		JsonObject bones = anim.getAsJsonObject("bones");
		if (bones == null) return d;
		for (Map.Entry<String, JsonElement> be : bones.entrySet()) {
			String bone = IMPORT_BONE.get(be.getKey().toLowerCase());
			if (bone == null || !be.getValue().isJsonObject()) continue;
			JsonObject bo = be.getValue().getAsJsonObject();
			TreeMap<Float, Ch> rot = channel(bo.get("rotation"), d.fps, rotSign(bone));
			TreeMap<Float, Ch> pos = channel(bo.get("position"), d.fps, posSign(bone));
			TreeSet<Float> times = new TreeSet<>();
			times.addAll(rot.keySet());
			times.addAll(pos.keySet());
			for (float t : times) {
				Key k = new Key(t);
				Ch r = sampleCh(rot, t), p = sampleCh(pos, t);
				if (r != null) { System.arraycopy(r.v, 0, k.rot, 0, 3); k.ease = r.ease; }
				if (p != null) { System.arraycopy(p.v, 0, k.pos, 0, 3); if (r == null) k.ease = p.ease; }
				d.putKey(bone, k);
			}
		}
		// Blockbench can declare a length past the last key (the pose holds).
		// Mirror that with a duplicate hold key so the clip keeps its duration.
		if (anim.has("animation_length")) {
			float lenTicks = anim.get("animation_length").getAsFloat() * d.fps;
			if (lenTicks > d.length() + 0.5f)
				for (List<Key> keys : d.bones.values())
					if (!keys.isEmpty()) {
						Key hold = keys.get(keys.size() - 1).copy();
						hold.t = lenTicks;
						keys.add(hold);
					}
		}
		return d;
	}

	/** One Bedrock channel ("rotation"/"position") -> time-in-ticks -> value. */
	private static TreeMap<Float, Ch> channel(JsonElement el, float fps, float[] sign) {
		TreeMap<Float, Ch> out = new TreeMap<>();
		if (el == null) return out;
		if (el.isJsonArray()) { // static channel: one value for the whole clip
			out.put(0f, chOf(el, sign));
			return out;
		}
		if (!el.isJsonObject()) return out;
		for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
			try {
				out.put(Float.parseFloat(e.getKey()) * fps, chOf(e.getValue(), sign));
			} catch (NumberFormatException ignored) {}
		}
		return out;
	}

	private static Ch chOf(JsonElement val, float[] sign) {
		Ch c = new Ch();
		JsonArray arr = null;
		if (val.isJsonArray()) {
			arr = val.getAsJsonArray();
		} else if (val.isJsonObject()) {
			JsonObject o = val.getAsJsonObject();
			if (o.has("post") && o.get("post").isJsonArray()) arr = o.getAsJsonArray("post");
			else if (o.has("pre") && o.get("pre").isJsonArray()) arr = o.getAsJsonArray("pre");
			if (o.has("lerp_mode") && "catmullrom".equals(o.get("lerp_mode").getAsString()))
				c.ease = Ease.SMOOTH;
		}
		if (arr != null)
			for (int i = 0; i < 3 && i < arr.size(); i++) {
				try {
					c.v[i] = arr.get(i).getAsFloat() * sign[i];
				} catch (Exception ignored) {} // molang expressions land as 0
			}
		return c;
	}

	/** Value of a channel at time t: exact key, held ends, or linear between neighbors. */
	private static Ch sampleCh(TreeMap<Float, Ch> ch, float t) {
		if (ch.isEmpty()) return null;
		Ch exact = ch.get(t);
		if (exact != null) return exact;
		Map.Entry<Float, Ch> lo = ch.floorEntry(t), hi = ch.ceilingEntry(t);
		if (lo == null) return hi.getValue();
		if (hi == null) return lo.getValue();
		float x = (t - lo.getKey()) / (hi.getKey() - lo.getKey());
		Ch c = new Ch();
		for (int i = 0; i < 3; i++)
			c.v[i] = lo.getValue().v[i] + (hi.getValue().v[i] - lo.getValue().v[i]) * x;
		c.ease = lo.getValue().ease;
		return c;
	}

	// ==================================================================
	// EXPORT
	// ==================================================================

	/** Returns a flash message, or null when the dialog was cancelled. */
	public static String exportFile() {
		OrdealAnimData d = OrdealAnimatorClient.data;
		if (d == null) return "Nothing to export";
		String name = OrdealAnimatorClient.clipName.isEmpty() ? "animation" : OrdealAnimatorClient.clipName;
		String path = dialogSave(name + ".animation.json");
		if (path == null) return null;
		try {
			JsonObject root = new JsonObject();
			root.addProperty("format_version", "1.8.0");
			JsonObject anims = new JsonObject();
			anims.add(name, toBedrock(d));
			root.add("animations", anims);
			if (!path.toLowerCase().endsWith(".json")) path += ".json";
			Files.writeString(Path.of(path), new GsonBuilder().setPrettyPrinting().create().toJson(root),
					StandardCharsets.UTF_8);
			return "Exported " + new File(path).getName();
		} catch (Exception ex) {
			return "Export failed: " + ex.getMessage();
		}
	}

	private static JsonObject toBedrock(OrdealAnimData d) {
		JsonObject a = new JsonObject();
		a.addProperty("animation_length", round4(d.length() / d.fps));
		if (d.loop) a.addProperty("loop", true);
		JsonObject bones = new JsonObject();
		for (Map.Entry<String, List<Key>> e : d.bones.entrySet()) {
			String out = EXPORT_BONE.get(e.getKey());
			List<Key> keys = e.getValue();
			if (out == null || keys.isEmpty()) continue;
			boolean anyRot = false, anyPos = false;
			for (Key k : keys) {
				anyRot |= k.rot[0] != 0 || k.rot[1] != 0 || k.rot[2] != 0;
				anyPos |= k.pos[0] != 0 || k.pos[1] != 0 || k.pos[2] != 0;
			}
			if (!anyRot && !anyPos) continue;
			JsonObject bo = new JsonObject();
			if (anyRot) bo.add("rotation", channelOut(keys, d.fps, true, e.getKey()));
			if (anyPos) bo.add("position", channelOut(keys, d.fps, false, e.getKey()));
			bones.add(out, bo);
		}
		a.add("bones", bones);
		return a;
	}

	private static JsonObject channelOut(List<Key> keys, float fps, boolean rot, String bone) {
		JsonObject ch = new JsonObject();
		float[] sign = rot ? rotSign(bone) : posSign(bone);
		for (Key k : keys) {
			JsonArray v = new JsonArray();
			for (int i = 0; i < 3; i++)
				v.add(round4((rot ? k.rot[i] : k.pos[i]) * sign[i]));
			if (k.ease == Ease.SMOOTH) {
				JsonObject o = new JsonObject();
				o.add("post", v);
				o.addProperty("lerp_mode", "catmullrom");
				ch.add(fmtTime(k.t / fps), o);
			} else {
				ch.add(fmtTime(k.t / fps), v);
			}
		}
		return ch;
	}

	private static double round4(double v) {
		return Math.round(v * 10000.0) / 10000.0;
	}

	private static String fmtTime(float seconds) {
		String s = BigDecimal.valueOf(seconds).setScale(4, RoundingMode.HALF_UP)
				.stripTrailingZeros().toPlainString();
		return s.contains(".") ? s : s + ".0";
	}

	// ==================================================================
	// Native file dialogs
	// ==================================================================

	private static String dialogOpen() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer filters = stack.mallocPointer(1);
			filters.put(stack.UTF8("*.json")).flip();
			return TinyFileDialogs.tinyfd_openFileDialog("Import Blockbench animation",
					defaultDir(), filters, "Blockbench animation (*.json)", false);
		} catch (Throwable t) {
			return null;
		}
	}

	private static String dialogSave(String suggested) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer filters = stack.mallocPointer(1);
			filters.put(stack.UTF8("*.json")).flip();
			return TinyFileDialogs.tinyfd_saveFileDialog("Export Blockbench animation",
					defaultDir() + suggested, filters, "Blockbench animation (*.json)");
		} catch (Throwable t) {
			return null;
		}
	}

	private static String defaultDir() {
		return FMLPaths.GAMEDIR.get().toString() + File.separator;
	}
}