package net.mcreator.ordeal.core;

import net.mcreator.ordeal.OrdealHeavy;
import net.mcreator.ordeal.OrdealMobStats;
import net.mcreator.ordeal.OrdealTuning;
import net.mcreator.ordeal.network.OrdealModVariables;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Chi comes back from fighting, and from EXACTLY two things:
 *
 *   a landed heavy punch  - half of what the heavy itself cost
 *   a guard break         - a little on top
 *
 * Nothing else pays. An ordinary M1 gives you nothing.
 *
 * A heavy is not detectable from the damage alone - OrdealHeavy lands through
 * the same playerAttack source a normal swing uses, and judging it by size
 * meant every punch that took a fifth of somebody's health counted, which is
 * most of them. So OrdealHeavy marks the hit on its way out and this reads the
 * mark. No guessing.
 */
@EventBusSubscriber(modid = "ordeal")
public final class OrdealChiRefund {

	private OrdealChiRefund() {}

	/** Fraction of the heavy's own chi cost handed back when it lands. */
	public static double HEAVY_REFUND_FRACTION = OrdealTuning.d("combat.chi_refund_heavy_fraction", 0.5);
	/** Flat chi for taking somebody's guard to zero. */
	public static double BREAK_REFUND = OrdealTuning.d("combat.chi_refund_break", 3.0);

	private static final String GUARD_SNAP = "ordeal_refundGuardSnap";
	private static final String HEAVY_MARK = "ordeal_heavyMark";

	/**
	 * Called by OrdealHeavy immediately before its hit goes out. The mark is a
	 * timestamp rather than a flag so it cannot get stuck on and quietly turn
	 * every later swing into a heavy.
	 */
	public static void markHeavy(Player p) {
		if (p == null) return;
		p.getPersistentData().putLong(HEAVY_MARK, p.level().getGameTime());
	}

	private static boolean wasHeavy(Player p) {
		long at = p.getPersistentData().getLong(HEAVY_MARK);
		return at > 0 && p.level().getGameTime() - at <= 1;
	}

	/** Before anybody has touched the damage: remember the victim's guard. */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void snapshot(LivingIncomingDamageEvent event) {
		LivingEntity victim = event.getEntity();
		if (victim.level().isClientSide()) return;
		if (!(event.getSource().getEntity() instanceof Player)) return;
		victim.getPersistentData().putDouble(GUARD_SNAP, guardOf(victim));
	}

	/** LOWEST, so guard, chip and absorption have all been settled. */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void refund(LivingIncomingDamageEvent event) {
		if (event.isCanceled()) return;
		LivingEntity victim = event.getEntity();
		if (victim.level().isClientSide()) return;

		Entity src = event.getSource().getEntity();
		if (!(src instanceof Player attacker) || attacker == victim) return;

		double back = 0;

		// a heavy that actually connected
		if (wasHeavy(attacker)) {
			attacker.getPersistentData().putLong(HEAVY_MARK, 0);
			double cost = OrdealHeavy.CHI_COST * net.mcreator.ordeal.OrdealCombo.costMult(attacker);
			back += cost * HEAVY_REFUND_FRACTION;
		}

		// and the guard going to zero, whatever put it there
		double before = victim.getPersistentData().getDouble(GUARD_SNAP);
		double after = guardOf(victim);
		if (before > 0 && after <= 0) back += BREAK_REFUND;

		if (back > 0) give(attacker, back);
	}

	/** Straight into the player's own chi, never into a talent's reserve. */
	public static void give(Player p, double amount) {
		if (amount <= 0 || p.level().isClientSide()) return;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		if (v.chiMax <= 0 || v.chi >= v.chiMax) return;
		v.chi = Math.min(v.chiMax, v.chi + amount);
		v.markSyncDirty();
	}

	/** Players carry guard in their variables; mobs carry it in persistent data. */
	private static double guardOf(LivingEntity e) {
		if (e instanceof Player p)
			return p.getData(OrdealModVariables.PLAYER_VARIABLES).guard;
		double dur = e.getPersistentData().getDouble(OrdealMobStats.DUR);
		return dur > 0 ? OrdealCombat.mobGuard(e, dur) : 0;
	}
}