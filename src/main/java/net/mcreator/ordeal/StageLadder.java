package net.mcreator.ordeal;

import net.mcreator.ordeal.core.OrdealTalentChi;
import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "ordeal")
public final class StageLadder {

	public static String TALENT_ID = "ilios";
	// 1 = your own chi bar pays for the state. 2 would spend Ilios's reserve.
	public static int    PAYS      = OrdealTuning.i("state.pays", 1);
	public static int    ASSERT_EVERY = OrdealTuning.i("state.assert_every_ticks", 20);

	public static String[] STAGE_NAMES = { "ILIOS STATE", "COMBAT MODE", "WAR STATE", "ANOINTED WAR STATE" };

	public static double[] CHI_PER_TICK = {
			OrdealTuning.d("state.chi_per_tick_1", 0.05),
			OrdealTuning.d("state.chi_per_tick_2", 0.15),
			OrdealTuning.d("state.chi_per_tick_3", 0.40),
			OrdealTuning.d("state.chi_per_tick_4", 0.75) };

	public static double[] STR_REQ = {
			OrdealTuning.d("state.str_req_1", 0),
			OrdealTuning.d("state.str_req_2", 20),
			OrdealTuning.d("state.str_req_3", 45),
			OrdealTuning.d("state.str_req_4", 80) };

	public static double[] LEO_DAMAGE = {
			OrdealTuning.d("state.leo_damage_1", 1.15),
			OrdealTuning.d("state.leo_damage_2", 1.35),
			OrdealTuning.d("state.leo_damage_3", 1.70),
			OrdealTuning.d("state.leo_damage_4", 2.20) };

	public static double[] CHE_DAMAGE = {
			OrdealTuning.d("state.che_damage_1", 1.20),
			OrdealTuning.d("state.che_damage_2", 1.45),
			OrdealTuning.d("state.che_damage_3", 1.90),
			OrdealTuning.d("state.che_damage_4", 2.50) };

	public static double[] LEO_SPEED = {
			OrdealTuning.d("state.leo_speed_1", 0.10),
			OrdealTuning.d("state.leo_speed_2", 0.22),
			OrdealTuning.d("state.leo_speed_3", 0.35),
			OrdealTuning.d("state.leo_speed_4", 0.50) };

	public static int[] LEO_REGEN_AMP = {
			OrdealTuning.i("state.leo_regen_amp_1", 0),
			OrdealTuning.i("state.leo_regen_amp_2", 0),
			OrdealTuning.i("state.leo_regen_amp_3", 1),
			OrdealTuning.i("state.leo_regen_amp_4", 2) };

	public static double[] CHE_ARMOR = {
			OrdealTuning.d("state.che_armor_1", 4),
			OrdealTuning.d("state.che_armor_2", 8),
			OrdealTuning.d("state.che_armor_3", 14),
			OrdealTuning.d("state.che_armor_4", 20) };

	public static double[] CHE_KNOCKBACK = {
			OrdealTuning.d("state.che_knockback_1", 0.2),
			OrdealTuning.d("state.che_knockback_2", 0.4),
			OrdealTuning.d("state.che_knockback_3", 0.6),
			OrdealTuning.d("state.che_knockback_4", 0.8) };

	
	public static String FLIGHT_PASSIVE = "flight";

	public static double FLIGHT_LEO_SPEED = OrdealTuning.d("state.flight_leo_speed", 1.5);
	public static double FLIGHT_CHE_SPEED = OrdealTuning.d("state.flight_che_speed", 1.0);
	public static double FLIGHT_LEO_CHI   = OrdealTuning.d("state.flight_leo_chi", 0.6);
	public static double FLIGHT_CHE_CHI   = OrdealTuning.d("state.flight_che_chi", 0.6);

	public static boolean STAGE4_NEEDS_SECOND_TALENT =
			OrdealTuning.i("state.stage4_needs_second_talent", 1) != 0;

	/**
	 * FX per stage. Empty string = nothing plays, which is how you switch a
	 * stage off rather than commenting code out.
	 *
	 *   ENTRY_FX   once, when the fire first lights (stage 0 -> 1)
	 *   STAGE_FX   on arriving at that stage, climbing OR sinking into it
	 *   LOOP_FX    every LOOP_EVERY ticks while sitting at that stage
	 *   EXIT_FX    once, when it goes out entirely
	 *
	 * Index 0 is stage 1. Tell me what you want in each slot and I will set it.
	 */
	public static String ENTRY_FX = "";
	public static String EXIT_FX  = "";

	public static String[] STAGE_FX = {
			"photon:ilios_phoenixflames",   // 1 ILIOS STATE
			"photon:ilios_phoenixflames",   // 2 COMBAT MODE
			"",                             // 3 WAR STATE - blue
			"" };                           // 4 ANOINTED - dark red

	public static String[] LOOP_FX = { "", "", "", "" };

	/** Ticks between LOOP_FX plays. 0 turns the loop off entirely. */
	public static int LOOP_EVERY = OrdealTuning.i("state.loop_fx_ticks", 0);

	private static String fx(String[] table, int stage) {
		int i = Math.max(0, Math.min(table.length - 1, stage - 1));
		return table[i] == null ? "" : table[i];
	}

	private static final String STAGE   = "ordeal_state_stage";
	private static final String VARIANT = "ordeal_state_variant";
	private static final String PICKING = "ordeal_state_picking";
	private static final String DEBT    = "ordeal_state_debt";

	private static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath("ordeal", "state_speed");
	private static final ResourceLocation ARMOR_ID = ResourceLocation.fromNamespaceAndPath("ordeal", "state_armor");
	private static final ResourceLocation KB_ID    = ResourceLocation.fromNamespaceAndPath("ordeal", "state_knockback");

	private StageLadder() {}

	/**
	 * Client mirrors. Persistent data never leaves the server, so without these
	 * every client-side readout reported stage 0 / no variant no matter what.
	 * StageLadderPayload keeps them current for the local player.
	 */
	public static int CLIENT_STAGE = 0;
	public static String CLIENT_VARIANT = "";

	public static int stage(Entity e) {
		if (!(e instanceof Player p)) return 0;
		if (p.level().isClientSide()) return Math.max(0, Math.min(4, CLIENT_STAGE));
		return Math.max(0, Math.min(4, p.getPersistentData().getInt(STAGE)));
	}

	public static boolean picking(Entity e) {
		return e instanceof Player p && p.getPersistentData().getInt(PICKING) != 0;
	}

	public static String variant(Entity e) {
		if (!(e instanceof Player p)) return "";
		if (p.level().isClientSide()) return CLIENT_VARIANT == null ? "" : CLIENT_VARIANT;
		String s = p.getPersistentData().getString(VARIANT);
		return s == null ? "" : s;
	}

	public static boolean isLeo(Entity e) { return !"che".equals(variant(e)); }

	public static double damageMultiplier(Entity e) {
		int s = stage(e);
		if (s <= 0) return 1.0;
		return (isLeo(e) ? LEO_DAMAGE : CHE_DAMAGE)[s - 1];
	}

	public static String stageName(Entity e) {
		int s = stage(e);
		return s <= 0 ? "" : STAGE_NAMES[s - 1];
	}

	public static void setVariant(Entity e, String v) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return;
		p.getPersistentData().putString(VARIANT, "che".equalsIgnoreCase(v) ? "che" : "leo");
		StageLadderPayload.sync(p);
	}

	public static void clearVariant(Entity e) {
		if (e instanceof Player p) {
			p.getPersistentData().putString(VARIANT, "");
			p.getPersistentData().putInt(PICKING, 0);
			StageLadderPayload.sync(p);
		}
	}

	public static void press(Entity e) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return;
		var data = p.getPersistentData();

		// no variant yet -> put the real screen up and stop here. The confirming
		// click comes back through IliosStatePayload and lands in confirmVariant.
		if (variant(p).isEmpty()) {
			data.putInt(PICKING, 1);
			IliosStatePayload.openFor(p);
			return;
		}

		if (p.isShiftKeyDown()) drop(p);
		else climb(p);
	}

	/**
	 * The screen came back with a choice. The press that opened it is not
	 * wasted - confirming drops you straight into stage 1.
	 */
	public static void confirmVariant(Entity e, String choice) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return;
		if (!variant(p).isEmpty()) return;          // already picked, ignore a stale packet
		setVariant(p, choice);
		p.getPersistentData().putInt(PICKING, 0);
		boolean che = "che".equalsIgnoreCase(variant(p));
		p.sendSystemMessage(Component.literal("§6§lILIOS STATE §7· §e"
				+ (che ? "ARMOR OF THE SUN" : "WINGS OF THE PHOENIX")
				+ "   §8full details in the terminal"));
		climb(p);
	}


	public static void climb(Entity e) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return;
		int cur = stage(p);
		int next = cur + 1;
		if (next > 4) {
			p.displayClientMessage(Component.literal("§6" + STAGE_NAMES[3] + " · already at the top"), true);
			return;
		}
		String why = blocked(p, next);
		if (!why.isEmpty()) {
			StatusLine.hush(p);
			p.displayClientMessage(Component.literal("§4§l" + why), true);
			return;
		}
		p.getPersistentData().putInt(STAGE, next);
		apply(p, next);
		StageLadderPayload.sync(p);
		announce(p, next, true);
		if (cur == 0 && !ENTRY_FX.isEmpty()) Fx.at(p, ENTRY_FX);
		String on = fx(STAGE_FX, next);
		if (!on.isEmpty()) Fx.at(p, on);
	}

	public static void drop(Entity e) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return;
		int cur = stage(p);
		if (cur <= 0) return;
		int next = cur - 1;
		p.getPersistentData().putInt(STAGE, next);
		apply(p, next);
		StageLadderPayload.sync(p);
		announce(p, next, false);
		if (next <= 0) {
			if (!EXIT_FX.isEmpty()) Fx.at(p, EXIT_FX);
		} else {
			String on = fx(STAGE_FX, next);
			if (!on.isEmpty()) Fx.at(p, on);
		}
	}

	/** Jump straight to a stage, skipping every gate. Testing only. */
	public static void force(Entity e, int stage) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return;
		int n = Math.max(0, Math.min(4, stage));
		if (n > 0 && variant(p).isEmpty()) setVariant(p, "leo");
		p.getPersistentData().putInt(STAGE, n);
		apply(p, n);
		StageLadderPayload.sync(p);
		if (n > 0) announce(p, n, true);
	}

	public static void exit(Entity e) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return;
		p.getPersistentData().putInt(STAGE, 0);
		apply(p, 0);
		StageLadderPayload.sync(p);
		Flight.clear(p, "ilios_state");
	}

	public static String blocked(Player p, int stage) {
		if (stage < 1 || stage > 4) return "";
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		double str = strength(v);
		if (str < STR_REQ[stage - 1])
			return STAGE_NAMES[stage - 1] + " needs talent strength " + (int) STR_REQ[stage - 1] + ".";
		if (stage == 4 && STAGE4_NEEDS_SECOND_TALENT && !hasSecondTalent(v))
			return "Anointed War State needs a second talent or talent weapon.";
		return "";
	}

	private static boolean hasSecondTalent(OrdealModVariables.PlayerVariables v) {
		String a = v.talent1_id == null ? "" : v.talent1_id;
		String b = v.talent2_id == null ? "" : v.talent2_id;
		boolean one = !a.isEmpty() && !a.equals("none");
		boolean two = !b.isEmpty() && !b.equals("none");
		return one && two;
	}

	private static double strength(OrdealModVariables.PlayerVariables v) {
		if (TALENT_ID.equals(v.talent1_id)) return v.talent1_strength;
		if (TALENT_ID.equals(v.talent2_id)) return v.talent2_strength;
		return 0;
	}

	private static void announce(Player p, int stage, boolean up) {
		StatusLine.hush(p, 30);
		if (stage <= 0) {
			p.displayClientMessage(Component.literal("§8the fire goes out"), true);
			return;
		}
		String colour = stage >= 4 ? "§4" : stage == 3 ? "§9" : stage == 2 ? "§6" : "§e";
		p.displayClientMessage(Component.literal(colour + "§l" + STAGE_NAMES[stage - 1]
				+ "  §r§7" + (up ? "▲" : "▼") + " " + stage + "/4"), true);
	}

	private static void apply(Player p, int stage) {
		clearModifiers(p);
		if (stage <= 0) {
			TalentState.clear(p, "ilios_state");
			Flight.clear(p, "ilios_state");
			return;
		}
		int i = stage - 1;
		TalentState.set(p, "ilios_state", (isLeo(p) ? LEO_DAMAGE : CHE_DAMAGE)[i]);

		// the state is one grant among however many the mod ends up with -
		// Flight owns the flying, this only says "you may, this well"
		Flight.grant(p, "ilios_state", stage, TALENT_ID, FLIGHT_PASSIVE,
				isLeo(p) ? FLIGHT_LEO_SPEED : FLIGHT_CHE_SPEED,
				isLeo(p) ? FLIGHT_LEO_CHI : FLIGHT_CHE_CHI);

		if (isLeo(p)) {
			addModifier(p, Attributes.MOVEMENT_SPEED, SPEED_ID, LEO_SPEED[i],
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			addModifier(p, Attributes.ARMOR, ARMOR_ID, CHE_ARMOR[i],
					AttributeModifier.Operation.ADD_VALUE);
			addModifier(p, Attributes.KNOCKBACK_RESISTANCE, KB_ID, CHE_KNOCKBACK[i],
					AttributeModifier.Operation.ADD_VALUE);
		}
	}

	private static void addModifier(Player p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
			ResourceLocation id, double amount, AttributeModifier.Operation op) {
		if (amount == 0) return;
		AttributeInstance inst = p.getAttribute(attr);
		if (inst == null) return;
		inst.removeModifier(id);
		inst.addTransientModifier(new AttributeModifier(id, amount, op));
	}

	private static void clearModifiers(Player p) {
		AttributeInstance sp = p.getAttribute(Attributes.MOVEMENT_SPEED);
		if (sp != null) sp.removeModifier(SPEED_ID);
		AttributeInstance ar = p.getAttribute(Attributes.ARMOR);
		if (ar != null) ar.removeModifier(ARMOR_ID);
		AttributeInstance kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (kb != null) kb.removeModifier(KB_ID);
	}

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		Player p = event.getEntity();
		if (p.level().isClientSide()) return;
		int stage = stage(p);
		if (stage <= 0) return;

		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		if (strength(v) <= 0 && !TALENT_ID.equals(v.talent1_id) && !TALENT_ID.equals(v.talent2_id)) {
			exit(p);
			return;
		}

		if (!pay(p, v, CHI_PER_TICK[stage - 1])) {
			drop(p);
			if (stage(p) > 0)
				p.displayClientMessage(Component.literal("§4the flame sinks"), true);
			return;
		}

		// the ambient loop runs on its own clock, independent of the assert
		if (LOOP_EVERY > 0 && p.tickCount % LOOP_EVERY == 0) {
			String loop = fx(LOOP_FX, stage);
			if (!loop.isEmpty()) Fx.at(p, loop);
		}

		if (p.tickCount % Math.max(1, ASSERT_EVERY) != 0) return;
		apply(p, stage);
		if (isLeo(p) && LEO_REGEN_AMP[stage - 1] >= 0 && LEO_REGEN_AMP[stage - 1] < 10)
			p.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
					Math.max(1, ASSERT_EVERY) + 20, LEO_REGEN_AMP[stage - 1], false, false));
	}

	private static boolean pay(Player p, OrdealModVariables.PlayerVariables v, double perTick) {
		if (perTick <= 0) return true;
		var data = p.getPersistentData();
		double debt = data.getDouble(DEBT) + perTick;
		if (debt < 1) { data.putDouble(DEBT, debt); return true; }

		double take = Math.floor(debt);
		data.putDouble(DEBT, debt - take);

		int slot = OrdealTalentChi.slotOf(v, TALENT_ID);
		double reserve = slot == 0 ? 0 : OrdealTalentChi.get(v, slot);

		if (PAYS == 2 || PAYS == 1) {
			double first = PAYS == 2 ? reserve : v.chi;
			if (first >= take) {
				if (PAYS == 2) OrdealTalentChi.set(v, slot, reserve - take);
				else v.chi -= take;
				v.markSyncDirty();
				return true;
			}
			double rest = take - first;
			if (PAYS == 2) {
				if (v.chi < rest) return false;
				if (slot != 0) OrdealTalentChi.set(v, slot, 0);
				v.chi -= rest;
			} else {
				if (reserve < rest) return false;
				v.chi = 0;
				OrdealTalentChi.set(v, slot, reserve - rest);
			}
			v.markSyncDirty();
			return true;
		}

		if (v.chi < take) return false;
		v.chi -= take;
		v.markSyncDirty();
		return true;
	}

	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		var from = event.getOriginal().getPersistentData();
		var to = event.getEntity().getPersistentData();
		to.putString(VARIANT, from.getString(VARIANT));
		if (event.isWasDeath()) {
			to.putInt(STAGE, 0);
			TalentState.clear(event.getEntity(), "ilios_state");
			Flight.clear(event.getEntity(), "ilios_state");
		} else {
			to.putInt(STAGE, from.getInt(STAGE));
		}
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		Player p = event.getEntity();
		apply(p, stage(p));
		StageLadderPayload.sync(p);
	}
}