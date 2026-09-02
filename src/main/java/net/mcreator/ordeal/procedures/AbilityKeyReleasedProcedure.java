package net.mcreator.ordeal.procedures;

import net.minecraft.world.entity.Entity;

import net.mcreator.ordeal.network.OrdealModVariables;

public class AbilityKeyReleasedProcedure {
	public static void execute(Entity entity) {
		if (entity == null)
			return;
		{
			OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
			_vars.key_pressed = false;
			_vars.markSyncDirty();
		}
	}
}