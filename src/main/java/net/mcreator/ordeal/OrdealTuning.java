package net.mcreator.ordeal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ordeal's balance file. Every combat number the mod uses is read from
 * config/ordeal-tuning.json — edit it, restart the game, done. No rebuild.
 *
 * The file writes itself with defaults on first launch and quietly adds any
 * key that a newer build introduces, so it never goes stale. Delete it to
 * reset everything.
 *
 * Precedence: a value you hand-edited in the json always wins. A value still
 * sitting at its old default gets replaced when the default in the code
 * changes — so editing a number in the Java works too. The "_defaults" block
 * tracks which is which; don't edit it.
 */
public final class OrdealTuning {

	private OrdealTuning() {}

	private static JsonObject loaded;
	private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();
	private static boolean dirty = false;

	private static Path file() {
		return FMLPaths.CONFIGDIR.get().resolve("ordeal-tuning.json");
	}

	private static synchronized JsonObject data() {
		if (loaded == null) {
			loaded = new JsonObject();
			try {
				if (Files.exists(file()))
					loaded = new Gson().fromJson(Files.readString(file()), JsonObject.class);
				if (loaded == null) loaded = new JsonObject();
			} catch (Exception ignored) {}
		}
		return loaded;
	}

	private static synchronized void save() {
		if (!dirty) return;
		dirty = false;
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(data()));
		} catch (IOException ignored) {}
	}

	private static synchronized JsonObject defs() {
		JsonObject o = data();
		if (!o.has("_defaults")) o.add("_defaults", new JsonObject());
		return o.getAsJsonObject("_defaults");
	}

	/**
	 * Write a value back to the tuning file and keep it.
	 *
	 * Used by things the editor captures rather than the code decides - the
	 * first-person rest pose, for one - so a base you set by hand survives a
	 * restart instead of reverting to whatever the code shipped with.
	 */
	public static synchronized void set(String key, double value) {
		JsonObject o = data();
		o.addProperty(key, value);

		// _defaults must keep holding the CODE default, never the value we just
		// wrote. This file resets any entry that still matches its recorded
		// default when the code default moves - that is what keeps shipped
		// tuning up to date. Writing the new value into _defaults as well made
		// a hand-set value look untouched, so the next load threw it away.
		// That is how the captured first-person base pose disappeared.
		Object codeDef = DEFAULTS.get(key);
		if (codeDef instanceof Number n)
			defs().addProperty(key, n.doubleValue());
		else if (!defs().has(key))
			// never read before, so the code default is unknown - NaN never
			// equals the stored value, so this reads as customised and stays
			defs().addProperty(key, Double.NaN);

		dirty = true;
		save();
	}

	public static double d(String key, double def) {
		DEFAULTS.put(key, def);
		JsonObject o = data();
		JsonObject dd = defs();
		if (!o.has(key)) {
			o.addProperty(key, def); dd.addProperty(key, def);
			dirty = true; save();
			return def;
		}
		double stored;
		try { stored = o.get(key).getAsDouble(); } catch (Exception e) { return def; }
		double storedDef = stored;
		if (dd.has(key)) try { storedDef = dd.get(key).getAsDouble(); } catch (Exception ignored) {}
		if (stored == storedDef && stored != def) {
			// still at the old default but the code default moved -> code wins
			o.addProperty(key, def); dd.addProperty(key, def);
			dirty = true; save();
			return def;
		}
		if (!dd.has(key) || storedDef != def) { dd.addProperty(key, def); dirty = true; save(); }
		return stored;
	}

	public static int i(String key, int def) {
		return (int) Math.round(d(key, def));
	}
}