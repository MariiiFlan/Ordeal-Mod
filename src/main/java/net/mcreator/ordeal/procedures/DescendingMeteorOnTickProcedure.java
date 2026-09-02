package net.mcreator.ordeal.procedures;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandSource;

import net.mcreator.ordeal.network.PlayPlayerAnimationMessage;
import net.mcreator.ordeal.network.OrdealModVariables;
import net.mcreator.ordeal.init.OrdealModMobEffects;

import java.util.Comparator;

public class DescendingMeteorOnTickProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null)
			return;
		String ExploVFX = "";
		String talent_id = "";
		boolean ExplodeOnImpact = false;
		boolean hold = false;
		boolean Projectile = false;
		boolean fireonpress = false;
		double holdChiPerSecond = 0;
		double cooldownTicks = 0;
		double reqStat_Str = 0;
		double IgniteSeconds = 0;
		double mode = 0;
		double Homing = 0;
		double cooldown = 0;
		double reqStat_perception = 0;
		double chiPerTick = 0;
		double holdTickEvery = 0;
		double BaseDMG = 0;
		double ExtraDMG = 0;
		double reqStat_Dura = 0;
		double talent_Str = 0;
		double holdMaxSeconds = 0;
		double Chi_Cost = 0;
		double reqStat_Agil = 0;
		double Talent_STR_Req = 0;
		double ExplodeRadius = 0;
		double pays = 0;
		if (!world.isClientSide()) {
			if (world instanceof Level _level) {
				if (!_level.isClientSide()) {
					_level.playSound(null, BlockPos.containing(x, y, z), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("item.firecharge.use")), SoundSource.PLAYERS, 1, (float) 0.5);
				} else {
					_level.playLocalSound(x, y, z, BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("item.firecharge.use")), SoundSource.PLAYERS, 1, (float) 0.5, false);
				}
			}
			cooldownTicks = 170;
			if (entity.getData(OrdealModVariables.PLAYER_VARIABLES).key_pressed == false || entity.onGround()) {
				if (entity instanceof LivingEntity _entity)
					_entity.removeEffect(OrdealModMobEffects.DESCENDING_METEOR);
				if (entity instanceof Player) {
					if (entity.level().isClientSide()) {
						CompoundTag data = entity.getPersistentData();
						data.remove("PlayerCurrentAnimation");
						data.remove("PlayerAnimationProgress");
						data.putBoolean("ResetPlayerAnimation", true);
						data.putBoolean("FirstPersonAnimation", false);
					} else {
						PacketDistributor.sendToPlayersInDimension((ServerLevel) entity.level(), new PlayPlayerAnimationMessage(entity.getId(), "", false, false));
					}
				}
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
			}
			{
				final Vec3 _center = new Vec3((entity.getX() + 1 * entity.getLookAngle().x), (entity.getY() + 1.6 + 1 * entity.getLookAngle().x), (entity.getZ() + 1 * entity.getLookAngle().z));
				for (Entity entityiterator : world.getEntitiesOfClass(Entity.class, new AABB(_center, _center).inflate(7 / 2d), e -> true).stream().sorted(Comparator.comparingDouble(_entcnd -> _entcnd.distanceToSqr(_center))).toList()) {
					if (!(entityiterator == entity)) {
						{
							OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
							_vars.knockback = (entity.getData(OrdealModVariables.PLAYER_VARIABLES).statStrength - entityiterator.getData(OrdealModVariables.PLAYER_VARIABLES).statDurability)
									* entity.getData(OrdealModVariables.PLAYER_VARIABLES).chargePower;
							_vars.knockback = entity.getData(OrdealModVariables.PLAYER_VARIABLES).knockback * 0.1;
							_vars.markSyncDirty();
						}
						if (entity.getData(OrdealModVariables.PLAYER_VARIABLES).knockback <= 0) {
							{
								OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
								_vars.knockback = 0.2;
								_vars.markSyncDirty();
							}
						}
						if (entity.getData(OrdealModVariables.PLAYER_VARIABLES).knockback >= 2 * entity.getData(OrdealModVariables.PLAYER_VARIABLES).chargePower) {
							{
								OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
								_vars.knockback = 2 * entity.getData(OrdealModVariables.PLAYER_VARIABLES).chargePower;
								_vars.markSyncDirty();
							}
						}
						if (entity.getData(OrdealModVariables.PLAYER_VARIABLES).knockback >= 2) {
							if (entityiterator instanceof LivingEntity _entity && !_entity.level().isClientSide())
								_entity.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 1, false, false));
						}
						entityiterator.setDeltaMovement(new Vec3((entity.getDeltaMovement().x()), (entity.getData(OrdealModVariables.PLAYER_VARIABLES).knockback * (-1)), (entity.getDeltaMovement().z())));
						entityiterator.hurt(new DamageSource(world.holderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("ordeal:talent"))), entity), (float) entity.getData(OrdealModVariables.PLAYER_VARIABLES).damage);
					}
					{
						Entity _ent = entity;
						if (!_ent.level().isClientSide() && _ent.getServer() != null) {
							_ent.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(CommandSource.NULL, _ent.position(), _ent.getRotationVector(), _ent.level() instanceof ServerLevel ? (ServerLevel) _ent.level() : null, 4,
									_ent.getName().getString(), _ent.getDisplayName(), _ent.level().getServer(), _ent), "photon fx photon:ilios_descendingmeteor entity @s 0 -0.2 0 0 0 0 1 1 1 0 false true xrot");
						}
					}
					if (entityiterator instanceof LivingEntity _entity && !_entity.level().isClientSide())
						_entity.addEffect(new MobEffectInstance(OrdealModMobEffects.SCREEN_SHAKE, 3, 1, false, false));
					if (entity instanceof LivingEntity _entity && !_entity.level().isClientSide())
						_entity.addEffect(new MobEffectInstance(OrdealModMobEffects.DOWN_DASH, 2, (int) (2 * entity.getData(OrdealModVariables.PLAYER_VARIABLES).chargePower), false, false));
				}
			}
		}
	}
}