package net.mcreator.ordeal.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.network.chat.Component;

import net.mcreator.ordeal.network.OrdealModVariables;
import net.mcreator.ordeal.init.OrdealModMobEffects;

public class Tomas0Procedure {
	public static void execute(LevelAccessor world, Entity entity) {
		if (entity == null)
			return;
		boolean fireonpress = false;
		boolean hold = false;
		boolean ExplodeOnImpact = false;
		boolean Projectile = false;
		boolean movementStunWhileHold = false;
		String talent_id = "";
		String ExploVFX = "";
		String FirstPersonAni = "";
		String ThirdPersonAni = "";
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
		double Homing = 0;
		double IgniteSeconds = 0;
		double ExplodeRadius = 0;
		double ProjectileSpeed = 0;
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
				_vars.abilityName = "Tomas";
				_vars.markSyncDirty();
			}
			cooldownTicks = 200 * (1 - Math.min(0.35, entity.getData(OrdealModVariables.PLAYER_VARIABLES).statAgility * 0.0035));
			Chi_Cost = 40 * (1 - Math.min(0.4, entity.getData(OrdealModVariables.PLAYER_VARIABLES).statChiControl * 0.004));
			BaseDMG = 15;
			ExtraDMG = 0.4;
			{
				OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
				_vars.damage = (BaseDMG + talent_Str * ExtraDMG) * entity.getData(OrdealModVariables.PLAYER_VARIABLES).chargePower;
				_vars.markSyncDirty();
			}
			Talent_STR_Req = 28;
			reqStat_Str = 0;
			reqStat_Dura = 0;
			reqStat_Agil = 0;
			reqStat_perception = 0;
			pays = 1;
			hold = true;
			fireonpress = false;
			movementStunWhileHold = false;
			movementStunTicks = 0;
			mode = 1;
			chiPerTick = 0.5;
			ThirdPersonAni = "";
			FirstPersonAni = "";
			Projectile = true;
			ExplodeOnImpact = true;
			ExploVFX = "";
			ExplodeRadius = 0;
			IgniteSeconds = 0;
			Homing = 0;
			if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_1)) {
				cooldown = (entity instanceof LivingEntity _livEnt && _livEnt.hasEffect(OrdealModMobEffects.CD_1) ? _livEnt.getEffect(OrdealModMobEffects.CD_1).getDuration() : 0) / 20;
			} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_2)) {
				cooldown = (entity instanceof LivingEntity _livEnt && _livEnt.hasEffect(OrdealModMobEffects.CD_2) ? _livEnt.getEffect(OrdealModMobEffects.CD_2).getDuration() : 0) / 20;
			} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_3)) {
				cooldown = (entity instanceof LivingEntity _livEnt && _livEnt.hasEffect(OrdealModMobEffects.CD_3) ? _livEnt.getEffect(OrdealModMobEffects.CD_3).getDuration() : 0) / 20;
			} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_4)) {
				cooldown = (entity instanceof LivingEntity _livEnt && _livEnt.hasEffect(OrdealModMobEffects.CD_4) ? _livEnt.getEffect(OrdealModMobEffects.CD_4).getDuration() : 0) / 20;
			} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_5)) {
				cooldown = (entity instanceof LivingEntity _livEnt && _livEnt.hasEffect(OrdealModMobEffects.CD_5) ? _livEnt.getEffect(OrdealModMobEffects.CD_5).getDuration() : 0) / 20;
			} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_6)) {
				cooldown = (entity instanceof LivingEntity _livEnt && _livEnt.hasEffect(OrdealModMobEffects.CD_6) ? _livEnt.getEffect(OrdealModMobEffects.CD_6).getDuration() : 0) / 20;
			} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_7)) {
				cooldown = (entity instanceof LivingEntity _livEnt && _livEnt.hasEffect(OrdealModMobEffects.CD_7) ? _livEnt.getEffect(OrdealModMobEffects.CD_7).getDuration() : 0) / 20;
			} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_8)) {
				cooldown = (entity instanceof LivingEntity _livEnt && _livEnt.hasEffect(OrdealModMobEffects.CD_8) ? _livEnt.getEffect(OrdealModMobEffects.CD_8).getDuration() : 0) / 20;
			} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_9)) {
				cooldown = (entity instanceof LivingEntity _livEnt && _livEnt.hasEffect(OrdealModMobEffects.CD_9) ? _livEnt.getEffect(OrdealModMobEffects.CD_9).getDuration() : 0) / 20;
			} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_10)) {
				cooldown = (entity instanceof LivingEntity _livEnt && _livEnt.hasEffect(OrdealModMobEffects.CD_10) ? _livEnt.getEffect(OrdealModMobEffects.CD_10).getDuration() : 0) / 20;
			}
			if (entity.getData(OrdealModVariables.PLAYER_VARIABLES).combatMode == true) {
				if (talent_Str >= Talent_STR_Req) {
					if (!(entity instanceof LivingEntity _livEnt16 && _livEnt16.hasEffect(OrdealModMobEffects.STUNNED))) {
						if (cooldown <= 0) {
							if (entity.getData(OrdealModVariables.PLAYER_VARIABLES).chi >= Chi_Cost) {
								net.mcreator.ordeal.Tomas.fire(entity);
								if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_1)) {
									if (entity instanceof LivingEntity _entity && !_entity.level().isClientSide())
										_entity.addEffect(new MobEffectInstance(OrdealModMobEffects.CD_1, (int) cooldownTicks, 1, false, false));
								} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_2)) {
									if (entity instanceof LivingEntity _entity && !_entity.level().isClientSide())
										_entity.addEffect(new MobEffectInstance(OrdealModMobEffects.CD_2, (int) cooldownTicks, 1, false, false));
								} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_3)) {
									if (entity instanceof LivingEntity _entity && !_entity.level().isClientSide())
										_entity.addEffect(new MobEffectInstance(OrdealModMobEffects.CD_3, (int) cooldownTicks, 1, false, false));
								} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_4)) {
									if (entity instanceof LivingEntity _entity && !_entity.level().isClientSide())
										_entity.addEffect(new MobEffectInstance(OrdealModMobEffects.CD_4, (int) cooldownTicks, 1, false, false));
								} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_5)) {
									if (entity instanceof LivingEntity _entity && !_entity.level().isClientSide())
										_entity.addEffect(new MobEffectInstance(OrdealModMobEffects.CD_5, (int) cooldownTicks, 1, false, false));
								} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_6)) {
									if (entity instanceof LivingEntity _entity && !_entity.level().isClientSide())
										_entity.addEffect(new MobEffectInstance(OrdealModMobEffects.CD_6, (int) cooldownTicks, 1, false, false));
								} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_7)) {
									if (entity instanceof LivingEntity _entity && !_entity.level().isClientSide())
										_entity.addEffect(new MobEffectInstance(OrdealModMobEffects.CD_7, (int) cooldownTicks, 1, false, false));
								} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_8)) {
									if (entity instanceof LivingEntity _entity && !_entity.level().isClientSide())
										_entity.addEffect(new MobEffectInstance(OrdealModMobEffects.CD_8, (int) cooldownTicks, 1, false, false));
								} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_9)) {
									if (entity instanceof LivingEntity _entity && !_entity.level().isClientSide())
										_entity.addEffect(new MobEffectInstance(OrdealModMobEffects.CD_9, (int) cooldownTicks, 1, false, false));
								} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName).equals(entity.getData(OrdealModVariables.PLAYER_VARIABLES).loadout_10)) {
									if (entity instanceof LivingEntity _entity && !_entity.level().isClientSide())
										_entity.addEffect(new MobEffectInstance(OrdealModMobEffects.CD_10, (int) cooldownTicks, 1, false, false));
								}
								{
									OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
									_vars.chi = entity.getData(OrdealModVariables.PLAYER_VARIABLES).chi - Chi_Cost;
									_vars.markSyncDirty();
								}
							} else {
								if (entity instanceof Player _player && !_player.level().isClientSide())
									_player.displayClientMessage(Component.literal(("\u00A74" + "\u00A7l" + ("You need at least " + new java.text.DecimalFormat("##").format(Chi_Cost) + " chi to use this."))), true);
							}
						} else {
							if (entity instanceof Player _player && !_player.level().isClientSide())
								_player.displayClientMessage(Component.literal(("\u00A74" + "\u00A7l" + ("You have " + new java.text.DecimalFormat("##").format(cooldown) + " seconds left of cooldown."))), true);
						}
					} else {
						if (entity instanceof Player _player && !_player.level().isClientSide())
							_player.displayClientMessage(Component.literal(("\u00A74" + "\u00A7l" + "You are stunned")), true);
					}
				} else {
					if (entity instanceof Player _player && !_player.level().isClientSide())
						_player.displayClientMessage(Component.literal(("\u00A74" + "\u00A7l" + ("Your talent strength needs to be " + new java.text.DecimalFormat("##").format(Talent_STR_Req) + "to use this ability."))), false);
				}
			} else {
				if (entity instanceof Player _player && !_player.level().isClientSide())
					_player.displayClientMessage(Component.literal(("\u00A7l" + "Combat mode needs to be on.")), true);
			}
		}
	}
}