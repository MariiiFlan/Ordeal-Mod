package net.mcreator.ordeal.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.Minecraft;

import net.mcreator.ordeal.network.OrdealModVariables;

public class AbilityCallProcedure {
	public static void execute(LevelAccessor world, Entity entity) {
		if (entity == null)
			return;
		if (!world.isClientSide()) {
			if (!(getEntityGameType(entity) == GameType.SPECTATOR)) {
				{
					OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
					_vars.key_pressed = true;
					_vars.markSyncDirty();
				}
				if (("Phoenix Spear").equals(net.mcreator.ordeal.AbilityHold.pressed(entity))) {
					PhoenixSpear0Procedure.execute(world, entity);
				}
				if (("Phoenix Flames").equals(net.mcreator.ordeal.AbilityHold.pressed(entity))) {
					PhoenixFlames0Procedure.execute(world, entity);
				}
				if (("Descending Meteor").equals(net.mcreator.ordeal.AbilityHold.pressed(entity))) {
					DescendingMeteor0Procedure.execute(world, entity);
				}
				if (("Trochos Armaton").equals(net.mcreator.ordeal.AbilityHold.pressed(entity))) {
					TrochosArmaton0Procedure.execute(world, entity);
				}
				if (("Akontio").equals(net.mcreator.ordeal.AbilityHold.pressed(entity))) {
					Akonito0Procedure.execute(world, entity);
				}
				if (("Claymore").equals(net.mcreator.ordeal.AbilityHold.pressed(entity))) {
					Claymore0Procedure.execute(world, entity);
				}
				if (("Breath of the Phoenix").equals(net.mcreator.ordeal.AbilityHold.pressed(entity))) {
					BreathOfThePhoenix0Procedure.execute(world, entity);
				}
				if (("Tomas").equals(net.mcreator.ordeal.AbilityHold.pressed(entity))) {
					Tomas0Procedure.execute(world, entity);
				}
			}
		}
	}

	private static GameType getEntityGameType(Entity entity) {
		if (entity instanceof ServerPlayer serverPlayer) {
			return serverPlayer.gameMode.getGameModeForPlayer();
		} else if (entity instanceof Player player && player.level().isClientSide()) {
			PlayerInfo playerInfo = Minecraft.getInstance().getConnection().getPlayerInfo(player.getGameProfile().getId());
			if (playerInfo != null)
				return playerInfo.getGameMode();
		}
		return null;
	}
}