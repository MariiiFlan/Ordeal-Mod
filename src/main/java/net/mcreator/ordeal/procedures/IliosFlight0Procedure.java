package net.mcreator.ordeal.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.Entity;

import net.mcreator.ordeal.network.OrdealModVariables;

public class IliosFlight0Procedure {
	public static void execute(LevelAccessor world, Entity entity) {
		if (entity == null)
			return;
		String talent_id = "";
		String ThirdPersonAni = "";
		String FirstPersonAni = "";
		boolean fireonpress = false;
		boolean hold = false;
		boolean ExplodeOnImpact = false;
		boolean Projectile = false;
		boolean movementStunWhileHold = false;
		double levelneeded = 0;
		double cooldown = 0;
		double cooldownTicks = 0;
		double Talent_STR_Req = 0;
		double Chi_Cost = 0;
		double BaseDMG = 0;
		double ExtraDMG = 0;
		double reqStat_Agil = 0;
		double reqStat_Dura = 0;
		double reqStat_Str = 0;
		double talent_Str = 0;
		double reqStat_perception = 0;
		double mode = 0;
		double chiPerTick = 0;
		double pays = 0;
		double IgniteSeconds = 0;
		double Homing = 0;
		double reqStat_PHYSICAL_str = 0;
		double movementStunTicks = 0;
		if (!world.isClientSide()) {
			talent_id = "ilios";
			if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).talent1_id).equals(talent_id)) {
				talent_Str = entity.getData(OrdealModVariables.PLAYER_VARIABLES).talent1_strength;
			} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).talent2_id).equals(talent_id)) {
				talent_Str = entity.getData(OrdealModVariables.PLAYER_VARIABLES).talent2_strength;
			}
			{
				OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
				_vars.abilityName = "Flight";
				_vars.markSyncDirty();
			}
			cooldownTicks = 0 * (1 - Math.min(0.35, entity.getData(OrdealModVariables.PLAYER_VARIABLES).statAgility * 0.0035));
			Chi_Cost = 0 * (1 - Math.min(0.4, entity.getData(OrdealModVariables.PLAYER_VARIABLES).statChiControl * 0.004));
			BaseDMG = 0;
			ExtraDMG = 0;
			{
				OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
				_vars.damage = (BaseDMG + talent_Str * ExtraDMG) * entity.getData(OrdealModVariables.PLAYER_VARIABLES).chargePower * entity.getData(OrdealModVariables.PLAYER_VARIABLES).talentState;
				_vars.markSyncDirty();
			}
			IgniteSeconds = 0;
			Talent_STR_Req = 5;
			reqStat_Str = 0;
			reqStat_Dura = 0;
			reqStat_Agil = 0;
			reqStat_perception = 0;
			pays = 1;
			hold = false;
			fireonpress = false;
			movementStunWhileHold = false;
			movementStunTicks = 0;
			mode = 1;
			chiPerTick = 0;
			ThirdPersonAni = "";
			FirstPersonAni = "";
			Projectile = false;
			ExplodeOnImpact = true;
			Homing = 0;
		}
	}
}