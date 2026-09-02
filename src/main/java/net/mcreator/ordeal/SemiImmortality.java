package net.mcreator.ordeal;

import net.mcreator.ordeal.core.OrdealTalentChi;
import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = "ordeal")
public final class SemiImmortality {

	public static String ABILITY_ID    = "semi_immortality";
	public static double CHI_PER_DAMAGE = OrdealTuning.d("semi.chi_per_damage", 1.0);
	public static boolean ONLY_LETHAL   = OrdealTuning.i("semi.only_lethal", 1) != 0;
	public static boolean USES_RESERVE  = OrdealTuning.i("semi.uses_talent_reserve", 1) != 0;
	public static int    COOLDOWN       = OrdealTuning.i("semi.cooldown_ticks", 0);
	public static int    MERCY_TICKS    = OrdealTuning.i("semi.mercy_invuln_ticks", 10);
	public static String FX             = "";

	private static final String READY_AT = "ordeal_semi_ready_at";

	private SemiImmortality() {}

	public static boolean active(Entity e) {
		if (!(e instanceof Player p)) return false;
		if (!Enhancements.bonded(p, Enhancements.SEMI)) return false;
		return Passives.on(p, ABILITY_ID);
	}

	public static boolean ready(Entity e) {
		if (!active(e) || !(e instanceof Player p)) return false;
		return p.level().getGameTime() >= p.getPersistentData().getLong(READY_AT);
	}

	public static int cooldownLeft(Entity e) {
		if (!(e instanceof Player p)) return 0;
		return (int) Math.max(0, p.getPersistentData().getLong(READY_AT) - p.level().getGameTime());
	}

	public static double pool(Entity e) {
		if (!(e instanceof Player p)) return 0;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		double out = v.chi;
		if (USES_RESERVE) {
			int slot = OrdealTalentChi.slotOf(v, v.talent1_id);
			if (slot == 0) slot = OrdealTalentChi.slotOf(v, v.talent2_id);
			if (slot != 0) out += OrdealTalentChi.get(v, slot);
		}
		return out;
	}

	public static double covers(Entity e) {
		return CHI_PER_DAMAGE <= 0 ? 0 : pool(e) / CHI_PER_DAMAGE;
	}

	public static boolean affordable(Entity e, double damage) {
		return pool(e) >= damage * CHI_PER_DAMAGE;
	}

	public static boolean affordable(Entity e) {
		return e instanceof Player p && affordable(p, p.getHealth());
	}

	private static boolean spend(Player p, double chi) {
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		double owed = chi;
		double fromBar = Math.min(v.chi, owed);
		v.chi -= fromBar;
		owed -= fromBar;

		if (owed > 0 && USES_RESERVE) {
			int slot = OrdealTalentChi.slotOf(v, v.talent1_id);
			if (slot == 0) slot = OrdealTalentChi.slotOf(v, v.talent2_id);
			if (slot != 0) {
				double have = OrdealTalentChi.get(v, slot);
				double take = Math.min(have, owed);
				OrdealTalentChi.set(v, slot, have - take);
				owed -= take;
			}
		}
		v.markSyncDirty();
		return owed <= 0.0001;
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onDamage(LivingIncomingDamageEvent event) {
		if (event.isCanceled()) return;
		if (!(event.getEntity() instanceof Player p) || p.level().isClientSide()) return;
		if (!ready(p)) return;

		float amount = event.getAmount();
		if (amount <= 0) return;
		boolean lethal = amount >= p.getHealth();
		if (ONLY_LETHAL && !lethal) return;

		double cost = amount * CHI_PER_DAMAGE;
		if (!affordable(p, amount)) return;

		spend(p, cost);
		event.setCanceled(true);
		if (MERCY_TICKS > 0) p.invulnerableTime = Math.max(p.invulnerableTime, MERCY_TICKS);
		if (COOLDOWN > 0) p.getPersistentData().putLong(READY_AT, p.level().getGameTime() + COOLDOWN);

		if (lethal) {
			StatusLine.hush(p);
			p.displayClientMessage(Component.literal("§6§lTHE CHI TOOK IT §7· "
					+ (int) cost + " spent"), true);
			Fx.at(p, FX);
		}
	}
}