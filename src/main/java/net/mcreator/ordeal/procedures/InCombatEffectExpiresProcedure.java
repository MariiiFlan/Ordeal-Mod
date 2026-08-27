package net.mcreator.ordeal.procedures;

import net.minecraft.world.entity.Entity;

import net.mcreator.ordeal.network.OrdealModVariables;

public class InCombatEffectExpiresProcedure {
	public static void execute(Entity entity) {
		if (entity == null)
			return;
		{
			OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
			_vars.inCombatWith = "None";
			_vars.markSyncDirty();
		}
	}
}