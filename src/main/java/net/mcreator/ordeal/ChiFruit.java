package net.mcreator.ordeal;

import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "ordeal")
public final class ChiFruit {

	public static double CHI_MAX_PER    = OrdealTuning.d("chifruit.chi_max_per_fruit", 125);
	public static double STAT_CAP_PER   = OrdealTuning.d("chifruit.stat_cap_per_fruit", 35);
	public static double TALENT_CAP_PER = OrdealTuning.d("chifruit.talent_cap_per_fruit", 0);
	public static double CHI_LIMIT_PER  = OrdealTuning.d("chifruit.chi_limit_per_fruit", 25);
	public static double CHI_LIMIT_MAX  = OrdealTuning.d("chifruit.chi_limit_bonus_max", 100);
	public static double GATE_STEP      = OrdealTuning.d("chifruit.str_gate_step", 0);
	public static double GATE_MAX       = OrdealTuning.d("chifruit.str_gate_max", 150);

	private static final String KEY = "ordeal_chifruit_eaten";

	private ChiFruit() {}

	public static int eaten(Entity e) {
		return e == null ? 0 : e.getPersistentData().getInt(KEY);
	}

	public static double required(Entity e) {
		return Math.min(GATE_MAX, eaten(e) * GATE_STEP);
	}

	public static double limitBonus(Entity e) {
		return Math.min(CHI_LIMIT_MAX, eaten(e) * CHI_LIMIT_PER);
	}

	public static double chiBonus(Entity e) {
		return eaten(e) * CHI_MAX_PER;
	}

	public static boolean eat(Entity e) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return false;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);

		double req = required(p);
		double str = Math.max(v.talent1_strength, v.talent2_strength);
		if (str < req) {
			p.displayClientMessage(Component.literal("§4§lYour body cannot hold this yet - talent strength "
					+ (int) req + " needed."), true);
			return false;
		}

		p.getPersistentData().putInt(KEY, eaten(p) + 1);
		v.spLifetime_Cap += STAT_CAP_PER;
		v.talentSp_Lifetime_Cap += TALENT_CAP_PER;
		v.markSyncDirty();
		p.displayClientMessage(Component.literal("§6§lPure chi settles into you. Your limits move."), true);
		return true;
	}

	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		int n = event.getOriginal().getPersistentData().getInt(KEY);
		if (n > 0) event.getEntity().getPersistentData().putInt(KEY, n);
	}
}