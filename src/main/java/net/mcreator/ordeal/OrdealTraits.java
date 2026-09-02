package net.mcreator.ordeal.core.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mcreator.ordeal.network.OrdealModVariables;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Traits — named modifiers that come from a SOURCE rather than from a menu.
 *
 * A trait is never "given" and stored; it is owned for exactly as long as its
 * source holds. Carry the talent, wear the cloak, belong to the clan — the trait
 * is there. Drop the source and it is gone, with no bookkeeping and nothing to
 * clean up. That is what lets passives, clan buffs and item effects all be one
 * system instead of three.
 *
 * One file per trait in assets/ordeal/traits/:
 *
 *   {
 *     "id": "perfect_vessel",
 *     "name": "Perfect Vessel",
 *     "desc": "...",
 *     "obtain": "Born with the Thanatos talent.",
 *     "accent": "9B6BFF",
 *     "grant": { "type": "talent", "id": "thanatos", "source": "birth" }
 *   }
 *
 * grant.type is one of:
 *   talent  - this talent is equipped. Optional "source" also matches how it was
 *             acquired, so "born with it" is a different trait from "given it".
 *   race    - "id" matches the race variable
 *   clan    - "id" matches the clan variable
 *   family  - "id" matches the family variable
 *   always  - everybody has it
 *   never   - nobody yet. Use for a trait that is designed but not wired.
 */
public final class OrdealTraits {

	private OrdealTraits() {}

	public static class Grant {
		public String type = "never";
		public String id = "";
		/** Optional extra match for talent grants: how the talent was acquired. */
		public String source = "";
	}

	public static class Trait {
		public String id = "", name = "", desc = "", obtain = "";
		public int accent = OrdealDraw.CYAN;
		public Grant grant = new Grant();
	}

	private static final Map<String, Trait> REGISTRY = new LinkedHashMap<>();
	private static boolean loaded = false;

	public static void ensure() { if (!loaded) reload(); }

	/** Every trait in the mod, authored order, name-sorted. */
	public static List<Trait> all() {
		ensure();
		List<Trait> out = new ArrayList<>(REGISTRY.values());
		out.sort(Comparator.comparing(t -> t.name));
		return out;
	}

	public static Trait get(String id) {
		ensure();
		return id == null ? null : REGISTRY.get(id);
	}

	/** Does this player currently meet the trait's source condition? */
	public static boolean has(Player p, Trait t) {
		if (p == null || t == null) return false;
		return has(p.getData(OrdealModVariables.PLAYER_VARIABLES), t);
	}

	public static boolean has(OrdealModVariables.PlayerVariables v, Trait t) {
		if (v == null || t == null || t.grant == null) return false;
		String type = t.grant.type == null ? "never" : t.grant.type.toLowerCase();
		switch (type) {
			case "always": return true;
			case "never":  return false;
			case "race":   return eq(v.race, t.grant.id);
			case "clan":   return eq(v.clan, t.grant.id);
			case "family": return eq(v.family, t.grant.id);
			case "talent":
				return talentMatch(v.talent1_id, v.talent1_source, t.grant)
						|| talentMatch(v.talent2_id, v.talent2_source, t.grant);
			default: return false;
		}
	}

	/** Procedure-callable: does this entity have the named trait right now? */
	public static boolean hasTrait(Entity e, String traitId) {
		if (!(e instanceof Player p)) return false;
		return has(p, get(traitId));
	}

	/** Traits this player currently owns. */
	public static List<Trait> owned(OrdealModVariables.PlayerVariables v) {
		List<Trait> out = new ArrayList<>();
		for (Trait t : all()) if (has(v, t)) out.add(t);
		return out;
	}

	private static boolean talentMatch(String id, String source, Grant g) {
		if (!eq(id, g.id)) return false;
		if (g.source == null || g.source.isEmpty()) return true;
		return eq(source, g.source);
	}

	private static boolean eq(String a, String b) {
		return a != null && b != null && !a.isEmpty() && a.equalsIgnoreCase(b);
	}

	// ---- loading ------------------------------------------------------------

	public static void reload() {
		loaded = true;
		REGISTRY.clear();
		for (Map.Entry<ResourceLocation, Resource> e : find("traits")) {
			JsonObject o = read(e.getValue());
			if (o == null) continue;
			Trait t = parse(o);
			if (!t.id.isEmpty()) REGISTRY.put(t.id, t);
		}
	}

	private static Trait parse(JsonObject o) {
		Trait t = new Trait();
		t.id = str(o, "id", "");
		t.name = str(o, "name", t.id.replace('_', ' ').toUpperCase());
		t.desc = str(o, "desc", str(o, "description", ""));
		t.obtain = str(o, "obtain", "");
		t.accent = colour(str(o, "accent", "7ED8F5"));
		if (o.has("grant") && o.get("grant").isJsonObject()) {
			JsonObject g = o.getAsJsonObject("grant");
			t.grant.type = str(g, "type", "never");
			t.grant.id = str(g, "id", "");
			t.grant.source = str(g, "source", "");
		}
		return t;
	}

	private static Iterable<Map.Entry<ResourceLocation, Resource>> find(String dir) {
		try {
			return Minecraft.getInstance().getResourceManager()
					.listResources(dir, path -> path.getNamespace().equals("ordeal")
							&& path.getPath().endsWith(".json"))
					.entrySet();
		} catch (Throwable e) {
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

	private static String str(JsonObject o, String k, String def) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
	}

	private static int colour(String hex) {
		try { return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16); }
		catch (Exception e) { return OrdealDraw.CYAN; }
	}

	/** Re-read on every join so an edited json lands without a restart. */
	@net.neoforged.fml.common.EventBusSubscriber(modid = "ordeal",
			value = net.neoforged.api.distmarker.Dist.CLIENT)
	public static final class Hooks {
		private Hooks() {}

		@net.neoforged.bus.api.SubscribeEvent
		public static void onJoin(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn e) {
			reload();
		}
	}
}