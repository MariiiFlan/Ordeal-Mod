package net.mcreator.ordeal.procedures;

import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.core.registries.Registries;

import net.mcreator.ordeal.network.OrdealModVariables;
import net.mcreator.ordeal.init.OrdealModItems;
import net.mcreator.ordeal.init.OrdealModEntities;
import net.mcreator.ordeal.entity.AkonitoProjectileEntity;
import net.mcreator.ordeal.OrdealMod;

public class Akonito3Procedure {
	public static void execute(LevelAccessor world, Entity entity) {
		if (entity == null)
			return;
		String talent_id = "";
		String FirstPersonAni = "";
		String ThirdPersonAni = "";
		boolean movementStunWhileHold = false;
		boolean ExplodeOnImpact = false;
		boolean hold = false;
		boolean Projectile = false;
		boolean fireonpress = false;
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
		double IgniteSeconds = 0;
		double mode = 0;
		double Homing = 0;
		double reqStat_perception = 0;
		double chiPerTick = 0;
		double movementStunTicks = 0;
		double pays = 0;
		if (!world.isClientSide()) {
			if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).talent1_id).equals(talent_id)) {
				talent_Str = entity.getData(OrdealModVariables.PLAYER_VARIABLES).talent1_strength;
			} else if ((entity.getData(OrdealModVariables.PLAYER_VARIABLES).talent2_id).equals(talent_id)) {
				talent_Str = entity.getData(OrdealModVariables.PLAYER_VARIABLES).talent2_strength;
			}
			BaseDMG = 7;
			ExtraDMG = 0.5;
			{
				OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
				_vars.damage = (BaseDMG + talent_Str * ExtraDMG) * entity.getData(OrdealModVariables.PLAYER_VARIABLES).chargePower * entity.getData(OrdealModVariables.PLAYER_VARIABLES).talentState;
				_vars.markSyncDirty();
			}
			Projectile = true;
			ExplodeOnImpact = true;
			Homing = 0;
			OrdealMod.queueServerWork(3, () -> {
				{
					Entity _shootFrom = entity;
					Level projectileLevel = _shootFrom.level();
					if (!projectileLevel.isClientSide()) {
						Projectile _entityToSpawn = initArrowProjectile(new AkonitoProjectileEntity(OrdealModEntities.AKONITO_PROJECTILE.get(), 0, 0, 0, projectileLevel, createArrowWeaponItemStack(projectileLevel, 1, (byte) 2)), entity,
								(float) entity.getData(OrdealModVariables.PLAYER_VARIABLES).damage, true, false, false, AbstractArrow.Pickup.DISALLOWED);
						_entityToSpawn.setPos(_shootFrom.getX(), _shootFrom.getEyeY() - 0.1, _shootFrom.getZ());
						_entityToSpawn.shoot(_shootFrom.getLookAngle().x, _shootFrom.getLookAngle().y, _shootFrom.getLookAngle().z, 3, 0);
						projectileLevel.addFreshEntity(_entityToSpawn);
					}
				}
				if (OrdealModItems.AKONTIO.get() == (entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY).getItem()) {
					if (entity instanceof LivingEntity _entity) {
						ItemStack _setstack7 = new ItemStack(Blocks.AIR).copy();
						_setstack7.setCount(1);
						_entity.setItemInHand(InteractionHand.MAIN_HAND, _setstack7);
						if (_entity instanceof Player _player)
							_player.getInventory().setChanged();
					}
				} else if (OrdealModItems.AKONTIO.get() == (entity instanceof LivingEntity _livEnt ? _livEnt.getOffhandItem() : ItemStack.EMPTY).getItem()) {
					if (entity instanceof LivingEntity _entity) {
						ItemStack _setstack10 = new ItemStack(Blocks.AIR).copy();
						_setstack10.setCount(1);
						_entity.setItemInHand(InteractionHand.OFF_HAND, _setstack10);
						if (_entity instanceof Player _player)
							_player.getInventory().setChanged();
					}
				}
			});
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