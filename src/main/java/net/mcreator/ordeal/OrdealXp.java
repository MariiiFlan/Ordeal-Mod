package net.mcreator.ordeal.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.mcreator.ordeal.network.OrdealModVariables;

/**
 * XP comes from fighting, not mining. You earn it by hitting, by killing, and by
 * eating a hit on your Guard — nothing else in the mod moves level.
 */
@EventBusSubscriber(modid = "ordeal")
public class OrdealXp {

	/** XP per point of damage you land. */
	public static final double PER_DAMAGE   = 1.0;
	/** XP per point of damage your Guard swallows. */
	public static final double PER_ABSORBED = 0.5;
	/** Kill bonus as a share of the victim's max health. */
	public static final double PER_KILL     = 0.5;
	/** Everything earned against another player is worth this much more. */
	public static final double PVP_MULT     = 2.5;
	/** Flat XP for connecting with an ability. */
	public static final double PER_ABILITY  = 3.0;

	public static void award(Player p, double amount) {
		if (p == null || p.level().isClientSide() || amount <= 0) return;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		if (v.level >= 100) return;
		v.xp += amount;
		v.markSyncDirty();
	}

	/** Called from the damage pipeline once the real numbers are known. */
	public static void onHit(LivingEntity attacker, Player victim, double throughGuard, double absorbed) {
		if (!(attacker instanceof Player p) || p == victim) return;
		double mult = PVP_MULT;
		award(p, (throughGuard * PER_DAMAGE + absorbed * PER_DAMAGE * 0.5) * mult);
		award(victim, absorbed * PER_ABSORBED * mult);
	}

	/** Mobs have no Guard, so their damage is counted straight off the event. */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onMobHit(LivingIncomingDamageEvent event) {
		if (event.getEntity() instanceof Player) return;
		if (!(event.getSource().getEntity() instanceof Player p)) return;
		award(p, Math.min(event.getAmount(), event.getEntity().getMaxHealth()) * PER_DAMAGE);
	}

	public static void onAbility(Player p) {
		award(p, PER_ABILITY);
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		LivingEntity dead = event.getEntity();
		if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
		if (killer == dead) return;
		double bonus;
		if (dead instanceof Player) {
			bonus = dead.getMaxHealth() * PER_KILL * PVP_MULT;
		} else {
			// Mobs carry their own worth: set ordeal_xp on the entity when you set
			// its stats, same as Invincible. Unset mobs fall back to max health.
			double stat = dead.getPersistentData().getDouble("ordeal_xp");
			bonus = stat > 0 ? stat : dead.getMaxHealth() * PER_KILL;
		}
		award(killer, bonus);
	}
}