package net.mcreator.ordeal;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Ordeal Animator — clip storage.
 *
 * Load order (config-first, assets-fallback):
 *   1. config/ordealanim/<name>.json          — writable; editor saves here, dev overrides
 *   2. assets/ordeal/anim/<name>.json (jar)   — shipped defaults, read-only
 *
 * Saving writes BOTH the config copy (which the running game picks up straight
 * away) and, when the mod's source tree is sitting next to the run folder, the
 * real asset at src/main/resources/assets/ordeal/anim/. That second copy is the
 * one that gets baked into the jar, so clips ship with the mod instead of
 * living in a config folder nobody else has.
 *
 * list() merges the config folder with the jar's _index.json
 * (generated at build time by anim_index.gradle), since jar contents
 * can't be enumerated at runtime.
 */
public final class OrdealAnimStore {

	private static final String CONFIG_DIR = "ordealanim";
	private static final String ASSET_PATH = "/assets/ordeal/anim/";

	/**
	 * Subfolders of assets/ordeal/anim/ that shipped clips may live in. Add a
	 * folder name here and clips inside it load by their plain name.
	 * Dev-workspace loading walks the tree and ignores this list; it only
	 * matters once the clips are baked into a jar, which cannot be listed.
	 */
	public static String[] ASSET_SUBDIRS = { "flight", "combat", "state", "misc" };
	private static final Gson GSON = new Gson();

	private OrdealAnimStore() {}

	/**
	 * The mod's own asset folder in the source tree — only present in the dev
	 * workspace (run/ sits beside src/). Null in a shipped game, where the
	 * clips are already inside the jar.
	 */
	public static Path assetSourceDir() {
		try {
			Path root = FMLPaths.GAMEDIR.get().toAbsolutePath().getParent();
			if (root == null) return null;
			Path res = root.resolve("src").resolve("main").resolve("resources");
			if (!Files.isDirectory(res)) return null;
			Path dir = res.resolve("assets").resolve("ordeal").resolve("anim");
			Files.createDirectories(dir);
			return dir;
		} catch (Exception e) {
			return null;
		}
	}

	public static Path configDir() {
		Path dir = FMLPaths.CONFIGDIR.get().resolve(CONFIG_DIR);
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			System.err.println("[OrdealAnim] Could not create config dir: " + e.getMessage());
		}
		return dir;
	}

	// ------------------------------------------------------------------
	// Load
	// ------------------------------------------------------------------

	/** Returns the clip, or null when it exists nowhere. */
	public static OrdealAnimData load(String name) {
		String safe = sanitize(name);
		if (safe == null)
			return null;

		// 1) config
		Path file = configDir().resolve(safe + ".json");
		if (Files.isRegularFile(file)) {
			try {
				return OrdealAnimData.fromJson(Files.readString(file, StandardCharsets.UTF_8));
			} catch (Exception e) {
				System.err.println("[OrdealAnim] Failed reading config clip '" + safe + "': " + e.getMessage());
			}
		}

		// 2) the source asset, which is newer than whatever the jar was built with
		Path assets = assetSourceDir();
		Path found = findAsset(assets, safe);
		if (found != null) {
			try {
				return OrdealAnimData.fromJson(Files.readString(found, StandardCharsets.UTF_8));
			} catch (Exception e) {
				System.err.println("[OrdealAnim] Failed reading asset clip '" + safe + "': " + e.getMessage());
			}
		}

		// 3) baked assets
		try (InputStream in = OrdealAnimStore.class.getResourceAsStream(ASSET_PATH + safe + ".json")) {
			if (in != null)
				return OrdealAnimData.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		} catch (Exception e) {
			System.err.println("[OrdealAnim] Failed reading baked clip '" + safe + "': " + e.getMessage());
		}
		// 4) baked assets, in a subfolder. A jar cannot be listed at runtime, so
		// the folders to probe are named here rather than discovered.
		for (String sub : ASSET_SUBDIRS) {
			try (InputStream in = OrdealAnimStore.class.getResourceAsStream(ASSET_PATH + sub + "/" + safe + ".json")) {
				if (in != null)
					return OrdealAnimData.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			} catch (Exception e) {
				System.err.println("[OrdealAnim] Failed reading baked clip '" + sub + "/" + safe + "': " + e.getMessage());
			}
		}
		return null;
	}

	/**
	 * True when this clip is a FIRST-PERSON clip - it has keys on the fp_ tracks
	 * and nothing on the body. The browser groups on this, so an fp clip and the
	 * third-person clip of the same move do not sit in one undifferentiated list.
	 *
	 * Cached, because the browser asks once per name per frame; the cache is
	 * dropped whenever a clip is written or removed.
	 */
	public static boolean firstPerson(String name) {
		String safe = sanitize(name);
		if (safe == null) return false;
		Boolean known = FP_CACHE.get(safe);
		if (known != null) return known;
		OrdealAnimData d = load(safe);
		boolean fp = false;
		if (d != null) {
			boolean anyFp = false, anyBody = false;
			for (java.util.Map.Entry<String, List<OrdealAnimData.Key>> e : d.bones.entrySet()) {
				if (e.getValue().isEmpty()) continue;
				if (OrdealAnimData.isFp(e.getKey())) anyFp = true;
				else anyBody = true;
			}
			fp = anyFp && !anyBody;
		}
		FP_CACHE.put(safe, fp);
		return fp;
	}

	private static final java.util.Map<String, Boolean> FP_CACHE = new java.util.HashMap<>();

	/** Drop the first-person classification cache. */
	public static void forgetKinds() {
		FP_CACHE.clear();
	}

	public static boolean exists(String name) {
		String safe = sanitize(name);
		if (safe == null || HIDDEN.contains(safe))
			return false;
		if (Files.isRegularFile(configDir().resolve(safe + ".json")))
			return true;
		if (inMod(safe))
			return true;
		try (InputStream in = OrdealAnimStore.class.getResourceAsStream(ASSET_PATH + safe + ".json")) {
			if (in != null) return true;
		} catch (IOException e) {
			// fall through to the subfolder probe
		}
		for (String sub : ASSET_SUBDIRS) {
			try (InputStream in = OrdealAnimStore.class.getResourceAsStream(ASSET_PATH + sub + "/" + safe + ".json")) {
				if (in != null) return true;
			} catch (IOException ignored) {}
		}
		return false;
	}

	// ------------------------------------------------------------------
	// Save / delete / rename / duplicate (config side only)
	// ------------------------------------------------------------------

	/**
	 * Clips deleted this session.
	 *
	 * A clip that was baked into the jar on the last build cannot be deleted
	 * off disk - the file inside the jar is still there until you build again.
	 * Without this it would come straight back in the list and read as "delete
	 * does not work", so a deleted name is hidden for the rest of the session.
	 */
	private static final java.util.Set<String> HIDDEN = new java.util.HashSet<>();


	public static boolean save(String name, OrdealAnimData data) {
		String safe = sanitize(name);
		if (safe == null)
			return false;
		// saving brings a name back from the dead - without this, deleting a
		// clip and then saving one with the same name left it hidden and the
		// browser showed nothing
		HIDDEN.remove(safe);
		FP_CACHE.remove(safe);
		String json = data.toJson();
		boolean ok = false;
		try {
			Files.writeString(configDir().resolve(safe + ".json"), json, StandardCharsets.UTF_8);
			ok = true;
		} catch (IOException e) {
			System.err.println("[OrdealAnim] Failed saving clip '" + safe + "': " + e.getMessage());
		}
		Path assets = assetSourceDir();
		if (assets != null) {
			try {
				// SAVE BACK WHERE IT ALREADY LIVES. If this clip is already on
				// disk in a subfolder - anim/flight/, say - write to that exact
				// file instead of dropping a second copy at the top level. Before
				// this, every save in the editor put the clip back in anim/ and
				// you had to move it into flight/ again by hand, and the stale
				// top-level copy would then shadow the one you moved.
				Path existing = findAsset(assets, safe);
				Path target = existing != null ? existing : assets.resolve(safe + ".json");
				Files.writeString(target, json, StandardCharsets.UTF_8);
				writeIndex(assets);
				ok = true;
			} catch (IOException e) {
				System.err.println("[OrdealAnim] Failed writing asset copy: " + e.getMessage());
			}
		}
		return ok;
	}

	/** True when the clip landed in the mod's assets and will ship with a build. */
	public static boolean inMod(String name) {
		String safe = sanitize(name);
		return safe != null && findAsset(assetSourceDir(), safe) != null;
	}

	/**
	 * Find <safe>.json under the asset folder, SUBFOLDERS INCLUDED.
	 *
	 * Clip names are flat - sanitize() strips slashes, so "flight/omni_man_idle"
	 * can never be a name. But organising the files into folders is the obvious
	 * thing to do, and before this the store only ever looked at the top level,
	 * so anything filed into anim/flight/ simply did not exist as far as
	 * playback was concerned. The name stays flat; only the lookup got deeper.
	 *
	 * Top level wins, so a clip sitting directly in anim/ still shadows a
	 * same-named one in a subfolder. Depth is capped and the walk is cheap;
	 * load() is called once per clip and OrdealAnimPlayback caches the result.
	 */
	private static Path findAsset(Path assets, String safe) {
		if (assets == null || safe == null) return null;
		Path flat = assets.resolve(safe + ".json");
		if (Files.isRegularFile(flat)) return flat;
		String want = safe + ".json";
		try (Stream<Path> walk = Files.walk(assets, 4)) {
			return walk.filter(Files::isRegularFile)
					.filter(f -> f.getFileName().toString().equalsIgnoreCase(want))
					.findFirst().orElse(null);
		} catch (Exception e) {
			return null;
		}
	}

	/** Jar contents can't be listed at runtime, so the names are baked beside them. */
	private static void writeIndex(Path assets) throws IOException {
		JsonArray arr = new JsonArray();
		try (Stream<Path> s = Files.walk(assets, 4)) {
			s.map(p -> p.getFileName().toString())
					.filter(n -> n.endsWith(".json") && !n.startsWith("_"))
					.sorted()
					.forEach(n -> arr.add(n.substring(0, n.length() - 5)));
		}
		Files.writeString(assets.resolve("_index.json"), GSON.toJson(arr), StandardCharsets.UTF_8);
	}

	/** Deletes the writable copy. A baked copy of the same name (if any) becomes visible again. */
	public static boolean delete(String name) {
		String safe = sanitize(name);
		if (safe == null)
			return false;
		HIDDEN.add(safe);
		FP_CACHE.remove(safe);
		boolean gone = false;
		try {
			gone = Files.deleteIfExists(configDir().resolve(safe + ".json"));
		} catch (IOException e) {
			System.err.println("[OrdealAnim] Failed deleting clip '" + safe + "': " + e.getMessage());
		}
		Path assets = assetSourceDir();
		if (assets != null) {
			try {
				gone |= Files.deleteIfExists(assets.resolve(safe + ".json"));
				writeIndex(assets);
			} catch (IOException e) {
				System.err.println("[OrdealAnim] Failed deleting asset copy: " + e.getMessage());
			}
		}
		// even with no file to remove it is now hidden, so the list agrees with
		// what you just asked for
		return true;
	}

	/** True if this name was deleted this session but may still be in the jar. */
	public static boolean hidden(String name) {
		String safe = sanitize(name);
		return safe != null && HIDDEN.contains(safe);
	}

	public static boolean rename(String oldName, String newName) {
		if (sanitize(newName) == null) return false;
		if (sanitize(oldName).equals(sanitize(newName))) return true;
		OrdealAnimData d = load(oldName);
		if (d == null || !save(newName, d))
			return false;
		delete(oldName);
		return true;
	}

	public static boolean duplicate(String source, String copyName) {
		OrdealAnimData d = load(source);
		return d != null && save(copyName, d.copy());
	}

	/** "punch" -> "punch_2", or the next free number after that. */
	public static String freeName(String base) {
		String safe = sanitize(base);
		if (safe == null) return "clip";
		safe = safe.replaceFirst("_\\d+$", "");
		for (int i = 2; i < 100; i++) {
			String candidate = safe + "_" + i;
			if (!exists(candidate)) return candidate;
		}
		return safe + "_copy";
	}

	// ------------------------------------------------------------------
	// List
	// ------------------------------------------------------------------

	/** All known clip names: config folder ∪ jar _index.json. Sorted. */
	public static List<String> list() {
		Set<String> names = new LinkedHashSet<>();
		// deleted-this-session names are stripped at the end

		try (Stream<Path> s = Files.list(configDir())) {
			s.filter(p -> p.getFileName().toString().endsWith(".json"))
					.forEach(p -> {
						String n = p.getFileName().toString();
						names.add(n.substring(0, n.length() - 5));
					});
		} catch (IOException e) {
			System.err.println("[OrdealAnim] Could not list config dir: " + e.getMessage());
		}

		Path assets = assetSourceDir();
		if (assets != null) {
			try (Stream<Path> s = Files.walk(assets, 4)) {
				s.map(p -> p.getFileName().toString())
						.filter(n -> n.endsWith(".json") && !n.startsWith("_"))
						.forEach(n -> names.add(n.substring(0, n.length() - 5)));
			} catch (IOException e) {
				System.err.println("[OrdealAnim] Could not list asset dir: " + e.getMessage());
			}
		}

		try (InputStream in = OrdealAnimStore.class.getResourceAsStream(ASSET_PATH + "_index.json")) {
			if (in != null) {
				JsonArray arr = GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), JsonArray.class);
				for (JsonElement el : arr)
					names.add(el.getAsString());
			}
		} catch (Exception e) {
			System.err.println("[OrdealAnim] Could not read baked index: " + e.getMessage());
		}

		names.removeAll(HIDDEN);
		List<String> out = new ArrayList<>(names);
		Collections.sort(out);
		return out;
	}

	// ------------------------------------------------------------------

	/** Filename hardening: letters, digits, _ and - only. Returns null when nothing survives. */
	private static String sanitize(String name) {
		if (name == null)
			return null;
		String s = name.trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
		return s.isEmpty() ? null : s;
	}
}