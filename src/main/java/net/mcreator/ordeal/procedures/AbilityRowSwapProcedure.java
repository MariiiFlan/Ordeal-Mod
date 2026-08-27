package net.mcreator.ordeal.procedures;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;

import net.mcreator.ordeal.network.OrdealModVariables;

public class AbilityRowSwapProcedure {
	public static void execute(Entity entity) {
		if (entity == null)
			return;
		if (entity.getData(OrdealModVariables.PLAYER_VARIABLES).combatMode == true) {
			if (entity.getData(OrdealModVariables.PLAYER_VARIABLES).ability_Row == 1) {
				{
					OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
					_vars.ability_Row = 2;
					_vars.markSyncDirty();
				}
				if (entity instanceof Player _player && !_player.level().isClientSide())
					_player.displayClientMessage(Component.literal("\u00A7fCombat Row: 2"), true);
			} else if (entity.getData(OrdealModVariables.PLAYER_VARIABLES).ability_Row == 2) {
				{
					OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
					_vars.ability_Row = 1;
					_vars.markSyncDirty();
				}
				if (entity instanceof Player _player && !_player.level().isClientSide())
					_player.displayClientMessage(Component.literal("\u00A7fCombat Row: 1"), true);
			}
		} else {
			if (entity instanceof Player _player && !_player.level().isClientSide())
				_player.displayClientMessage(Component.literal("\u00A7fBattle mode needs to be activated first."), true);
		}
	}
}