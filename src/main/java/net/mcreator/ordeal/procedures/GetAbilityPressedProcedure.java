package net.mcreator.ordeal.procedures;

import net.minecraft.world.entity.Entity;

import net.mcreator.ordeal.network.OrdealModVariables;

public class GetAbilityPressedProcedure {
	public static String execute(Entity entity) {
		if (entity == null)
			return "";
		if (entity.getData(OrdealModVariables.PLAYER_VARIABLES).ability_Row == 1) {
			if (net.mcreator.ordeal.core.OrdealInput.ability1(entity)) {
				return entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_1;
			}
			if (net.mcreator.ordeal.core.OrdealInput.ability2(entity)) {
				return entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_2;
			}
			if (net.mcreator.ordeal.core.OrdealInput.ability3(entity)) {
				return entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_3;
			}
			if (net.mcreator.ordeal.core.OrdealInput.ability4(entity)) {
				return entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_4;
			}
			if (net.mcreator.ordeal.core.OrdealInput.ability5(entity)) {
				return entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_5;
			}
		}
		if (entity.getData(OrdealModVariables.PLAYER_VARIABLES).ability_Row == 2) {
			if (net.mcreator.ordeal.core.OrdealInput.ability1(entity)) {
				return entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_6;
			}
			if (net.mcreator.ordeal.core.OrdealInput.ability2(entity)) {
				return entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_7;
			}
			if (net.mcreator.ordeal.core.OrdealInput.ability3(entity)) {
				return entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_8;
			}
			if (net.mcreator.ordeal.core.OrdealInput.ability4(entity)) {
				return entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_9;
			}
			if (net.mcreator.ordeal.core.OrdealInput.ability5(entity)) {
				return entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_10;
			}
		}
		return "None";
	}
}