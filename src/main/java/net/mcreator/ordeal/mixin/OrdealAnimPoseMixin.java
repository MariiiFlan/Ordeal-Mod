package net.mcreator.ordeal.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.mcreator.ordeal.OrdealAnimRender;


@Mixin(value = PlayerModel.class, priority = 1500)
public abstract class OrdealAnimPoseMixin<T extends LivingEntity> {

	@Inject(method = "setupAnim", at = @At("TAIL"))
	private void ordeal$applyClipPose(T entity, float limbSwing, float limbSwingAmount,
			float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (entity instanceof Player player)
			OrdealAnimRender.apply((PlayerModel<?>) (Object) this, player);
	}
}