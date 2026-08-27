package net.mcreator.ordeal.core;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.mcreator.ordeal.network.OrdealModVariables;


@EventBusSubscriber(modid = "ordeal")
public class OrdealChiCharge {

	public static final int HOLD_TICKS = 60;
	/** Re-fire cadence for the charge effect; author the fx around this length. */
	public static final int FX_PERIOD = 40;
	public static final String FX = "photon:chi_charge_white";

	private static final String KEY = "ordeal_sneakTicks";

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		Player p = event.getEntity();
		if (p.level().isClientSide()) return;

		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);

		if (!p.isShiftKeyDown()) {
			if (v.chiCharging > 0) { v.chiCharging = 0; v.markSyncDirty(); }
			if (p.getPersistentData().getInt(KEY) != 0) p.getPersistentData().putInt(KEY, 0);
			return;
		}

		int held = p.getPersistentData().getInt(KEY) + 1;
		p.getPersistentData().putInt(KEY, held);

		if (held < HOLD_TICKS) return;

		if (v.chiCharging <= 0) {
			v.chiCharging = 1;
			v.markSyncDirty();
		}
		if ((held - HOLD_TICKS) % FX_PERIOD == 0)
			OrdealFx.spawnAccent(p, FX);
	}
}