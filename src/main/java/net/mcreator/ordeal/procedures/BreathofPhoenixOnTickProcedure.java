package net.mcreator.ordeal.procedures;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.Registries;

import net.mcreator.ordeal.network.PlayPlayerAnimationMessage;
import net.mcreator.ordeal.network.OrdealModVariables;
import net.mcreator.ordeal.init.OrdealModMobEffects;
import net.mcreator.ordeal.init.OrdealModEntities;
import net.mcreator.ordeal.entity.BreathofPhoenixProjectileEntity;

public class BreathofPhoenixOnTickProcedure {
	public static void execute(LevelAccessor world, Entity entity) {
		if (entity == null)
			return;
		String FirstPersonAni = "";
		String talent_id = "";
		String ThirdPersonAni = "";
		boolean movementStunWhileHold = false;
		boolean ExplodeOnImpact = false;
		boolean hold = false;
		boolean Projectile = false;
		boolean fireonpress = false;
		double cooldownTicks = 0;
		double reqStat_Str = 0;
		double IgniteSeconds = 0;
		double mode = 0;
		double Homing = 0;
		double cooldown = 0;
		double reqStat_perception = 0;
		double chiPerTick = 0;
		double BaseDMG = 0;
		double ExtraDMG = 0;
		double reqStat_Dura = 0;
		double talent_Str = 0;
		double movementStunTicks = 0;
		double Chi_Cost = 0;
		double reqStat_Agil = 0;
		double Talent_STR_Req = 0;
		double pays = 0;
		if (!world.isClientSide()) {
			cooldownTicks = 300;
			ExtraDMG = 0.5;
			BaseDMG = 5;
			{
				OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
				_vars.damage = (BaseDMG + talent_Str * ExtraDMG) * entity.getData(OrdealModVariables.PLAYER_VARIABLES).chargePower * entity.getData(OrdealModVariables.PLAYER_VARIABLES).talentState;
				_vars.markSyncDirty();
			}
			if (entity.getData(OrdealModVariables.PLAYER_VARIABLES).key_pressed == false) {
				if (entity instanceof LivingEntity _entity)
					_entity.removeEffect(OrdealModMobEffects.BREATHOF_PHOENIX);
				if (entity instanceof LivingEntity _entity)
					_entity.removeEffect(OrdealModMobEffects.CAMERA_LOCK_POTION);
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
			entity.getPersistentData().putDouble("ticks", (entity.getPersistentData().getDouble("ticks") + 1));
			if (entity.getPersistentData().getDouble("ticks") >= 3) {
				{
					Entity _shootFrom = entity;
					Level projectileLevel = _shootFrom.level();
					if (!projectileLevel.isClientSide()) {
						Projectile _entityToSpawn = initArrowProjectile(new BreathofPhoenixProjectileEntity(OrdealModEntities.BREATHOF_PHOENIX_PROJECTILE.get(), 0, 0, 0, projectileLevel, createArrowWeaponItemStack(projectileLevel, 1, (byte) 0)),
								entity, (float) entity.getData(OrdealModVariables.PLAYER_VARIABLES).damage, true, false, false, AbstractArrow.Pickup.DISALLOWED);
						_entityToSpawn.setPos(_shootFrom.getX(), _shootFrom.getEyeY() - 0.1, _shootFrom.getZ());
						_entityToSpawn.shoot(_shootFrom.getLookAngle().x, _shootFrom.getLookAngle().y, _shootFrom.getLookAngle().z, 3, 0);
						projectileLevel.addFreshEntity(_entityToSpawn);
					}
				}
				entity.getPersistentData().putDouble("ticks", 0);
			}
		}
	}

	private static AbstractArrow initArrowProjectile(AbstractArrow entityToSpawn, Entity shooter, float damage, boolean silent, boolean fire, boolean particles, AbstractArrow.Pickup pickup) {
		entityToSpawn.setOwner(shooter);
		entityToSpawn.setBaseDamage(damage);
		if (silent)
			entityToSpawn.setSilent(true);
		if (fire)
			entityToSpawn.igniteForSeconds(100);
		if (particles)
			entityToSpawn.setCritArrow(true);
		entityToSpawn.pickup = pickup;
		return entityToSpawn;
	}

	private static ItemStack createArrowWeaponItemStack(Level level, int knockback, byte piercing) {
		ItemStack weapon = new ItemStack(Items.ARROW);
		if (knockback > 0)
			weapon.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.KNOCKBACK), knockback);
		if (piercing > 0)
			weapon.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PIERCING), piercing);
		return weapon;
	}
}