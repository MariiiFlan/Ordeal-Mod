package net.mcreator.ordeal.procedures;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandSource;

import net.mcreator.ordeal.network.PlayPlayerAnimationMessage;
import net.mcreator.ordeal.network.OrdealModVariables;
import net.mcreator.ordeal.init.OrdealModItems;
import net.mcreator.ordeal.OrdealMod;

public class Claymore1Procedure {
	public static void execute(LevelAccessor world, Entity entity) {
		if (entity == null)
			return;
		String talent_id = "";
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
		ItemStack swapItem = ItemStack.EMPTY;
		ItemStack itembefore = ItemStack.EMPTY;
		if (!world.isClientSide()) {
			if (entity instanceof ServerPlayer || entity instanceof Player) {
				{
					Entity _ent = entity;
					if (!_ent.level().isClientSide() && _ent.getServer() != null) {
						_ent.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(CommandSource.NULL, _ent.position(), _ent.getRotationVector(), _ent.level() instanceof ServerLevel ? (ServerLevel) _ent.level() : null, 4,
								_ent.getName().getString(), _ent.getDisplayName(), _ent.level().getServer(), _ent), "");
					}
				}
				{
					Entity _ent = entity;
					if (!_ent.level().isClientSide() && _ent.getServer() != null) {
						_ent.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(CommandSource.NULL, _ent.position(), _ent.getRotationVector(), _ent.level() instanceof ServerLevel ? (ServerLevel) _ent.level() : null, 4,
								_ent.getName().getString(), _ent.getDisplayName(), _ent.level().getServer(), _ent), "photon fx photon:ilios_claymore_start entity @s 0 -0.5 0 0 0 0 1 1 1 0 false false xrot");
					}
				}
				if (entity instanceof Player) {
					if (entity.level().isClientSide()) {
						CompoundTag data = entity.getPersistentData();
						data.putString("PlayerCurrentAnimation", "ordeal:ilios_claymore");
						data.putBoolean("OverrideCurrentAnimation", true);
						data.putBoolean("FirstPersonAnimation", false);
					} else {
						PacketDistributor.sendToPlayersInDimension((ServerLevel) entity.level(), new PlayPlayerAnimationMessage(entity.getId(), "ordeal:ilios_claymore", true, false));
					}
				}
			}
			OrdealMod.queueServerWork(48, () -> {
				if (!((entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY).getItem() == OrdealModItems.ILIOS_CLAYMORE.get())) {
					{
						OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
						_vars.beforeSwap = (entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY).copy();
						_vars.markSyncDirty();
					}
					entity.getData(OrdealModVariables.PLAYER_VARIABLES).beforeSwap.applyComponents((entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY).getComponents());
					{
						OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
						_vars.itemSwap = new ItemStack(OrdealModItems.ILIOS_CLAYMORE.get()).copy();
						_vars.markSyncDirty();
					}
					if (entity instanceof LivingEntity _entity) {
						ItemStack _setstack12 = entity.getData(OrdealModVariables.PLAYER_VARIABLES).itemSwap.copy();
						_setstack12.setCount(1);
						_entity.setItemInHand(InteractionHand.MAIN_HAND, _setstack12);
						if (_entity instanceof Player _player)
							_player.getInventory().setChanged();
					}
					if (entity instanceof Player _player) {
						ItemStack _setstack = entity.getData(OrdealModVariables.PLAYER_VARIABLES).beforeSwap.copy();
						_setstack.setCount(1);
						ItemHandlerHelper.giveItemToPlayer(_player, _setstack);
					}
				}
			});
		}
	}
}