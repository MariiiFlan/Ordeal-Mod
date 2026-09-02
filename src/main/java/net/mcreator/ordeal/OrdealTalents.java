package net.mcreator.ordeal.core.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One file per talent in talents/, abilities inline. */
public final class OrdealTalents {

	private OrdealTalents() {}

	public static class Ability {
		public String id = "", icon = "??", name = "", kind = "", desc = "";
		public ResourceLocation iconTex;
		public int req, levelNeeded;
		public final Map<String, Double> reqStats = new LinkedHashMap<>();
		public double chi, cdTicks, base, per;
		/**
		 * Which pool pays for this ability.
		 *   0  player chi only (default)
		 *   1  player chi first, the talent's reserve covers the shortfall
		 *   2  the talent's reserve first, your own chi covers the shortfall
		 */
		public int pays = 0;
		public int stunTicks = 0;
		/** Null for an instant ability - press and it fires, exactly as before. */
		public Hold hold;
		/** Null unless the ability throws something. */
		public Projectile projectile;
	}

	/**
	 * How an ability behaves while its key is held down. Declared per ability
	 * in the talent JSON; an ability without a "hold" block stays instant.
	 *
	 *   charge  - hold to build power, release to fire once, harder the longer
	 *   channel - keeps firing every tickEvery ticks while held
	 *   toggle  - stays on until pressed again or the chi runs out
	 *   ramp    - both: keeps firing like a channel, and each pulse lands
	 *             harder the longer you have been holding, on the charge curve
	 */
	public static class Hold {
		public String mode = "charge";
		/**
		 * Pool that pays the per-tick drain, so a state can be cheap to enter and
		 * expensive to sustain. -1 means "same as the ability".
		 */
		public int pays = -1;
		/** charge: how many discrete steps the hold has. */
		public int levels = 5;
		/** charge: how long each step takes to reach. */
		public double secondsPerLevel = 0.5;
		/** charge: floor level on release. 0 = a tap fires the ability uncharged. */
		public int minLevel = 0;
		/**
		 * true  - the press fires the ability at once, and charging is a bonus
		 *         second, stronger hit on release (only if you reached level 1).
		 * false - nothing happens until you let go, and the shot comes out at
		 *         whatever level you reached. A tap is level 0.
		 */
		public boolean fireOnPress = false;
		/** Ticks at the start of a hold that cost no chi, so a tap is free. */
		public int graceTicks = 3;
		/** Chi taken every tick while holding, before Chi Control. */
		public double chiPerTick = 0.5;
		/** Chi Control shaves this fraction off the drain at 100. */
		public double chiControlMax = 0.70;
		/** Multiplier for an uncharged tap, and at the top charge level. */
		public double powerMin = 1.0, powerMax = 2.2;
		/**
		 * The animation that runs for as long as the hold is up.
		 *
		 * Two fields because the two views travel by different roads. anim3p is
		 * a Player Animation API name and is broadcast to everyone who can see
		 * you; anim1p is a COMMAND, run as you, because the first-person clip
		 * lives in the Ordeal animator - typically
		 * "ordealanimations play @s <clip>". Either can be left blank.
		 *
		 * AbilityHold starts them when the hold begins and clears them when it
		 * ends, so a procedure never has to. Author the clip with Loop on and it
		 * runs the whole hold.
		 */
		public String anim3p = "", anim1p = "";
		/**
		 * Root the player while the hold is up.
		 *
		 * stunWhileHold pins you for exactly as long as you hold - it is
		 * refreshed every tick and lapses a few ticks after you let go.
		 * stunTicks is a fixed root applied the moment the hold starts, so a
		 * move can commit you for a set window whether you hold it or not.
		 *
		 * Both can be on: the fixed window never gets cut short by the refresh,
		 * because a shorter duration at the same amplifier does not replace a
		 * longer one.
		 */
		public boolean stunWhileHold = false;
		public int stunTicks = 0;
		/** channel/toggle/ramp: how often it re-fires while held. */
		public int tickEvery = 4;
		/**
		 * channel/toggle/ramp: cap on how long it can run.
		 *
		 * On a ramp this is the whole hold, while levels/secondsPerLevel decide
		 * how fast the power climbs inside it - set maxSeconds longer than
		 * levels * secondsPerLevel and the last stretch runs at full power.
		 */
		public double maxSeconds = 3.0;

		public boolean isCharge() { return "charge".equalsIgnoreCase(mode); }
		public boolean isChannel() { return "channel".equalsIgnoreCase(mode); }
		public boolean isToggle() { return "toggle".equalsIgnoreCase(mode); }
		/** A channel whose pulses climb the charge curve as you hold. */
		public boolean isRamp() { return "ramp".equalsIgnoreCase(mode); }

		/** Modes that re-fire on a timer instead of once on release. */
		public boolean pulses() { return isChannel() || isRamp(); }

		/** Modes that build power over the hold. */
		public boolean climbs() { return isCharge() || isRamp(); }

		public int ticksPerLevel() { return Math.max(1, (int) Math.round(secondsPerLevel * 20)); }

		/** Ticks to reach the top level, or the channel cap. */
		public int maxTicks() {
			return isCharge() ? Math.max(1, levels) * ticksPerLevel()
					: (int) Math.round(maxSeconds * 20);
		}

		/** Level reached. Never below minLevel, so a tap always fires. */
		public int levelAt(int ticks) {
			int top = Math.max(1, levels);
			return Math.max(Math.min(minLevel, top), Math.min(top, ticks / ticksPerLevel()));
		}

		/**
		 * Multiplier for a charge level. Level 0 is the bare tap and sits at
		 * powerMin; the levels are the steps ABOVE it, up to powerMax, so every
		 * level you pay for is worth more than the tap was.
		 */
		public double powerAtLevel(int level) {
			if (!climbs() || level <= 0) return powerMin;
			int top = Math.max(1, levels);
			double f = Math.max(0, Math.min(1, level / (double) top));
			return powerMin + (powerMax - powerMin) * f;
		}

		/** Per-tick drain after Chi Control. */
		public double drainPerTick(double chiControl) {
			double cut = Math.min(chiControlMax, chiControl * (chiControlMax / 100.0));
			return Math.max(0, chiPerTick * (1.0 - cut));
		}
	}

	/**
	 * A talent's own chi. Absence of the "chi" block in the json means the talent
	 * has NO reserve at all - that is the opt-in flag, there is no enabled field.
	 *
	 *   "chi": {
	 *     "base": 150,            // what the reserve is worth at strength 0
	 *     "perStrength": 2.0,     // added per point of talent strength
	 *     "regenOutOfCombat": 0.2,// chi per second, out of combat
	 *     "regenInCombat": 0,     // chi per second, in combat
	 *     "grantFullOnAcquire": true,
	 *     "refillOnDeath": false
	 *   }
	 */
	public static class TalentChi {
		public double base = 0;
		public double perStrength = 2.0;
		public double regenOutOfCombat = 0.2;
		public double regenInCombat = 0.0;
		public boolean grantFullOnAcquire = true;
		public boolean refillOnDeath = false;

		public double max(double strength) {
			return base + Math.max(0, strength) * perStrength;
		}
	}

	/**
	 * How the thing this ability throws behaves. Absent from the json means the
	 * ability is not a projectile at all.
	 *
	 * speed is movement ONLY - it never scales the damage. OrdealProjectile
	 * pins the damage to what the procedure computed, so retuning the speed
	 * cannot silently retune the hit.
	 */
	public static class Projectile {
		public double speed = 2.0;
		public double gravity = 0.0;
		public int lifeTicks = 80;
		public int pierce = 0;
		public double radius = 0.0;
		public double homing = 0.0;
		public double homingRange = 24.0;
		public double igniteSeconds = 0.0;
		public boolean explodeOnImpact = false;
		/** Photon fx and sound names, authored next to the numbers. */
		public String explodeFx = "", trailFx = "", impactFx = "", hitSound = "";
	}

	public static class Talent {
		public String id = "", name = "", shortName = "";
		/** Flavour line shown under the type chips on the TALENTS tab. */
		public String desc = "";
		public int accent = OrdealDraw.CYAN;
		public String[] types = new String[0];
		/** One colour per entry in types, filled in by parseTalent. */
		public int[] typeColours = new int[0];
		/** Null when this talent has no reserve of its own. */
		public TalentChi chi;
		public final List<Ability> abilities = new ArrayList<>();
	}

	/**
	 * Colour for a talent type tag. A talent can override it per tag by writing
	 * the type as an object instead of a bare string:
	 *
	 *     "types": [ "ELEMENTAL", { "name": "STATE", "color": "9B8CFF" } ]
	 */
	public static int typeColour(String type) {
		if (type == null) return OrdealDraw.CYAN_DIM;
		switch (type.trim().toUpperCase()) {
			case "ELEMENTAL":  return 0xFFFF8A3C;
			case "STATE":      return 0xFF9B8CFF;
			case "PHYSICAL":   return 0xFFFF6B6B;
			case "BODY":       return 0xFFFFB877;
			case "MENTAL":     return 0xFF5FE3A0;
			case "SENSORY":    return 0xFF7ED8F5;
			case "SPATIAL":    return 0xFF8CD3FF;
			case "TIME":       return 0xFFB6C2D2;
			case "SUPPORT":    return 0xFF6FD8B0;
			case "WEAPON":     return 0xFFC9B27A;
			case "SUMMON":     return 0xFFB07AD8;
			case "UNIVERSAL":  return 0xFF7ED8F5;
			case "CONTROL":    return 0xFF6E9BE8;
			case "RANGE CONTROL": return 0xFF8CD3FF;
			case "CREATION":   return 0xFFB08BD1;
			case "HIDE":       return 0xFF46C88C;
			case "HASTE":      return 0xFFC6D94A;
			case "SHIFT":      return 0xFFE8608F;
			case "POWER":      return 0xFFFF6B6B;
			case "MYTHIC":     return 0xFFFFD24A;
			case "FORBIDDEN":  return 0xFFFF4D6D;
			case "UNSET":      return OrdealDraw.LOCKED;
			default:           return OrdealDraw.CYAN_DIM;
		}
	}

	/**
	 * The loaded talents.
	 *
	 * NOT final, and never mutated in place. reload() builds a whole new map and
	 * swaps it in when it is finished, so a reader is always iterating a map
	 * that is complete and will never change again.
	 *
	 * That matters because this registry is read from BOTH threads: the client
	 * thread draws the terminal and the HUD from it, and the server thread hits
	 * it every tick through AbilityHold and the ability procedures. The old
	 * version cleared and refilled one shared map, so a reload on one thread
	 * while the other was walking REGISTRY.values() threw
	 * ConcurrentModificationException mid-tick and took the server down.
	 */
	private static volatile Map<String, Talent> REGISTRY = new LinkedHashMap<>();
	private static volatile Talent basic = fallbackBasic();

	/**
	 * The json is read the first time anything asks for it, not only when the
	 * terminal opens. Before this the HUD came up blank on a fresh join and only
	 * filled in once you opened the GUI, because the GUI was what loaded it.
	 */
	private static volatile boolean loaded = false;

	/**
	 * Load on first use, once, no matter how many threads ask at the same time.
	 * The fast path is a plain volatile read, so the lock is only ever touched
	 * before the first successful load.
	 */
	public static void ensure() {
		if (loaded) return;
		synchronized (OrdealTalents.class) {
			if (!loaded) reload();
		}
	}

	public static Talent basic() { ensure(); return basic; }

	public static Talent get(String id) {
		ensure();
		if (id == null || id.isEmpty() || id.equals("none")) return null;
		if (id.equals("basic")) return basic;
		return REGISTRY.get(id);
	}

	public static Ability ability(String abilityId) {
		ensure();
		if (abilityId == null || abilityId.isEmpty()) return null;
		for (Ability a : basic.abilities) if (a.id.equals(abilityId)) return a;
		for (Talent t : REGISTRY.values())
			for (Ability a : t.abilities) if (a.id.equals(abilityId)) return a;
		return null;
	}

	/** Loadout slots store the display name, since that is what the dispatcher compares. */
	public static Ability abilityByName(String name) {
		ensure();
		if (name == null || name.isEmpty()) return null;
		for (Ability a : basic.abilities) if (a.name.equals(name)) return a;
		for (Talent t : REGISTRY.values())
			for (Ability a : t.abilities) if (a.name.equals(name)) return a;
		return null;
	}

	public static Talent ownerOfName(String name) {
		ensure();
		for (Ability a : basic.abilities) if (a.name.equals(name)) return basic;
		for (Talent t : REGISTRY.values())
			for (Ability a : t.abilities) if (a.name.equals(name)) return t;
		return null;
	}

	public static Talent ownerOf(String abilityId) {
		ensure();
		for (Ability a : basic.abilities) if (a.id.equals(abilityId)) return basic;
		for (Talent t : REGISTRY.values())
			for (Ability a : t.abilities) if (a.id.equals(abilityId)) return t;
		return null;
	}

	public static synchronized void reload() {
		// Build into a private map. Nothing published until it is finished and
		// sorted, so no reader on another thread can ever see a half-built
		// registry - or worse, be iterating the one we are clearing.
		Map<String, Talent> built = new LinkedHashMap<>();
		Talent newBasic = fallbackBasic();

		for (Map.Entry<ResourceLocation, Resource> e : find("talents")) {
			JsonObject o = read(e.getValue());
			if (o == null) continue;
			Talent t = parseTalent(o);
			if (o.has("abilities"))
				for (var el : o.getAsJsonArray("abilities"))
					t.abilities.add(parseAbility(el.getAsJsonObject()));
			if (t.id.equals("basic")) newBasic = t;
			else built.put(t.id, t);
		}

		// Talent strength requirement decides the order, always. Level is the
		// tie-break, then the weight of any stat requirements, then the name -
		// so the list reads as a ladder from what you can use now to what you
		// are working toward.
		Comparator<Ability> order = Comparator
				.comparingInt((Ability a) -> a.req)
				.thenComparingInt(a -> a.levelNeeded)
				.thenComparingDouble(a -> a.reqStats.values().stream()
						.mapToDouble(Double::doubleValue).sum())
				.thenComparing(a -> a.name);
		newBasic.abilities.sort(order);
		for (Talent t : built.values()) t.abilities.sort(order);

		// publish, then flip the flag - a thread that reads loaded == true is
		// guaranteed to see the finished map behind it
		basic = newBasic;
		REGISTRY = built;
		loaded = true;
	}

	private static Iterable<Map.Entry<ResourceLocation, Resource>> find(String dir) {
		try {
			return Minecraft.getInstance().getResourceManager()
					.listResources(dir, p -> p.getNamespace().equals("ordeal")
							&& p.getPath().endsWith(".json"))
					.entrySet();
		} catch (Throwable e) {
			// no client resource manager (dedicated server) - nothing to read
			return new ArrayList<>();
		}
	}

	private static JsonObject read(Resource r) {
		try (BufferedReader br = r.openAsReader()) {
			return JsonParser.parseReader(br).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	private static Talent parseTalent(JsonObject o) {
		Talent t = new Talent();
		t.id = str(o, "id", "unknown");
		t.name = str(o, "name", t.id.toUpperCase());
		t.shortName = str(o, "short", t.name.split(" ")[0]);
		t.desc = str(o, "desc", str(o, "description", ""));
		if (o.has("chi") && o.get("chi").isJsonObject()) t.chi = parseTalentChi(o.getAsJsonObject("chi"));
		t.accent = colour(str(o, "accent", "7ED8F5"));
		if (o.has("types")) {
			JsonArray arr = o.getAsJsonArray("types");
			t.types = new String[arr.size()];
			t.typeColours = new int[arr.size()];
			for (int i = 0; i < arr.size(); i++) {
				var el = arr.get(i);
				if (el.isJsonObject()) {
					JsonObject to = el.getAsJsonObject();
					t.types[i] = str(to, "name", str(to, "type", "?"));
					t.typeColours[i] = to.has("color") || to.has("colour")
							? colour(str(to, "color", str(to, "colour", "7ED8F5")))
							: typeColour(t.types[i]);
				} else {
					t.types[i] = el.getAsString();
					t.typeColours[i] = typeColour(t.types[i]);
				}
			}
		}
		return t;
	}

	private static TalentChi parseTalentChi(JsonObject o) {
		TalentChi c = new TalentChi();
		c.base = num(o, "base", 0);
		c.perStrength = num(o, "perStrength", num(o, "per_strength", 2.0));
		c.regenOutOfCombat = num(o, "regenOutOfCombat", 0.2);
		c.regenInCombat = num(o, "regenInCombat", 0.0);
		c.grantFullOnAcquire = !o.has("grantFullOnAcquire") || o.get("grantFullOnAcquire").getAsBoolean();
		c.refillOnDeath = o.has("refillOnDeath") && o.get("refillOnDeath").getAsBoolean();
		return c;
	}

	private static Ability parseAbility(JsonObject o) {
		Ability a = new Ability();
		a.id = str(o, "id", "");
		a.icon = str(o, "icon", "??");
		a.name = str(o, "name", a.id);
		a.kind = str(o, "kind", "");
		a.desc = str(o, "desc", "");
		a.req = (int) num(o, "talentStrReq", num(o, "req", 0));
		a.levelNeeded = (int) num(o, "levelNeeded", 0);
		if (o.has("reqStats") && o.get("reqStats").isJsonObject())
			for (var e : o.getAsJsonObject("reqStats").entrySet())
				a.reqStats.put(e.getKey().toLowerCase(), e.getValue().getAsDouble());
		a.iconTex = resolveIcon(a.id);
		a.chi = num(o, "chiCost", num(o, "chi", 0));
		a.cdTicks = num(o, "cooldownTicks", num(o, "cd", 0) * 20);
		a.base = num(o, "baseDmg", num(o, "base", 0));
		a.per = num(o, "extraDmg", num(o, "per", 0));
		a.pays = (int) num(o, "pays", 0);
		a.stunTicks = (int) num(o, "stunTicks", 0);
		if (o.has("hold") && o.get("hold").isJsonObject())
			a.hold = parseHold(o.getAsJsonObject("hold"));
		if (o.has("projectile") && o.get("projectile").isJsonObject())
			a.projectile = parseProjectile(o.getAsJsonObject("projectile"));
		return a;
	}

	private static Projectile parseProjectile(JsonObject o) {
		Projectile pr = new Projectile();
		pr.speed = num(o, "speed", 2.0);
		pr.gravity = num(o, "gravity", 0.0);
		pr.lifeTicks = (int) num(o, "lifeTicks", 80);
		pr.pierce = (int) num(o, "pierce", 0);
		pr.radius = num(o, "radius", 0.0);
		pr.homing = num(o, "homing", 0.0);
		pr.homingRange = num(o, "homingRange", 24.0);
		pr.igniteSeconds = num(o, "igniteSeconds", 0.0);
		pr.explodeOnImpact = o.has("explodeOnImpact") && o.get("explodeOnImpact").getAsBoolean();
		pr.explodeFx = str(o, "explodeFx", "");
		pr.trailFx = str(o, "trailFx", "");
		pr.impactFx = str(o, "impactFx", "");
		pr.hitSound = str(o, "hitSound", "");
		return pr;
	}

	private static Hold parseHold(JsonObject o) {
		Hold h = new Hold();
		h.mode = str(o, "mode", "charge");
		h.pays = (int) num(o, "pays", -1);
		h.levels = (int) Math.max(1, num(o, "levels", 5));
		h.secondsPerLevel = num(o, "secondsPerLevel", 0.5);
		h.minLevel = (int) num(o, "minLevel", 0);
		h.fireOnPress = o.has("fireOnPress") && o.get("fireOnPress").getAsBoolean();
		if (o.has("graceTicks")) h.graceTicks = o.get("graceTicks").getAsInt();
		h.chiControlMax = num(o, "chiControlMax", 0.70);
		h.stunWhileHold = o.has("stunWhileHold") && o.get("stunWhileHold").getAsBoolean();
		h.stunTicks = (int) num(o, "stunTicks", 0);
		h.anim3p = str(o, "anim3p", "");
		h.anim1p = str(o, "anim1p", "");
		h.tickEvery = (int) Math.max(1, num(o, "tickEvery", 4));
		h.maxSeconds = num(o, "maxSeconds", 3.0);
		// ONE unit: chi per tick. chiPerSecond used to be accepted as a second
		// spelling of the same number and it was nothing but a trap - two keys
		// in one block, and whichever the engine happened to prefer silently
		// won. If you want per-second, divide by 20 yourself. The sync strips
		// any chiPerSecond it finds, so old blocks clean themselves up.
		h.chiPerTick = num(o, "chiPerTick", 0.5);
		if (o.has("power") && o.get("power").isJsonObject()) {
			JsonObject p = o.getAsJsonObject("power");
			h.powerMin = num(p, "min", 1.0);
			h.powerMax = num(p, "max", h.powerMin);
		}
		return h;
	}

	/** assets/ordeal/textures/abilities/<id>_icon.png - falls back to the letter code. */
	private static ResourceLocation resolveIcon(String id) {
		if (id == null || id.isEmpty()) return null;
		// the server has no resource manager, and AbilityHold reads talents there
		if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT)
			return null;
		// MCreator drops imported GUI textures into screens/, so look there too
		for (String dir : new String[] { "textures/abilities/", "textures/screens/" }) {
			ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("ordeal", dir + id + "_icon.png");
			if (Minecraft.getInstance().getResourceManager().getResource(rl).isPresent())
				return rl;
		}
		return null;
	}

	/** Re-read on every join, so an edited json lands without a restart. */
	@net.neoforged.fml.common.EventBusSubscriber(modid = "ordeal",
			value = net.neoforged.api.distmarker.Dist.CLIENT)
	public static final class Hooks {
		private Hooks() {}

		@net.neoforged.bus.api.SubscribeEvent
		public static void onJoin(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn e) {
			reload();
		}
	}

	private static Talent fallbackBasic() {
		Talent t = new Talent();
		t.id = "basic";
		t.name = "BASIC ABILITIES";
		t.shortName = "BASIC";
		t.accent = OrdealDraw.CYAN;
		t.types = new String[] { "UNIVERSAL" };
		t.typeColours = new int[] { typeColour("UNIVERSAL") };
		t.desc = "Techniques every awakened body can learn. No talent required - "
				+ "only the discipline to spend chi well.";
		return t;
	}

	private static String str(JsonObject o, String k, String def) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
	}

	private static double num(JsonObject o, String k, double def) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : def;
	}

	private static int colour(String hex) {
		try { return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16); }
		catch (Exception e) { return OrdealDraw.CYAN; }
	}
}