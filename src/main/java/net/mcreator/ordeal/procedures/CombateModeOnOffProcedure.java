package net.mcreator.ordeal.procedures;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;

import net.mcreator.ordeal.network.OrdealModVariables;

public class CombateModeOnOffProcedure {
	public static void execute(Entity entity) {
		if (entity == null)
			return;
		if (entity.getData(OrdealModVariables.PLAYER_VARIABLES).combatMode == true) {
			{
				OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
				_vars.combatMode = false;
				_vars.markSyncDirty();
			}
			if (entity instanceof Player _player && !_player.level().isClientSide())
				_player.displayClientMessage(Component.literal("\u00A7fCombat Mode: \u00A7aDeactivated"), true);
		} else if (entity.getData(OrdealModVariables.PLAYER_VARIABLES).combatMode == false) {
			{
				OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
				_vars.combatMode = true;
				_vars.markSyncDirty();
			}
			if (entity instanceof Player _player && !_player.level().isClientSide())
				_player.displayClientMessage(Component.literal("\u00A7fCombat Mode: \u00A74Activated"), true);
		}
	}
}