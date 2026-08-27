package net.mcreator.ordeal.core;

import java.util.Locale;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-tinted Photon FX. argb 0 means "use the caster's talent accent",
 * resolved on the client where the talent JSON lives.
 */
@EventBusSubscriber(modid = "ordeal", bus = EventBusSubscriber.Bus.MOD)
public record OrdealFxPayload(ResourceLocation fxLocation, int entityId, int argb,
                              float offX, float offY, float offZ,
                              float rotX, float rotY, float rotZ,
                              float scaleX, float scaleY, float scaleZ,
                              int delay, boolean forceDeath, boolean allowMulti, String autoRotate,
                              int tintTarget)
        implements CustomPacketPayload {

	public static final int TINT_START = 0;
	public static final int TINT_TRAIL = 1;
	public static final int TINT_BOTH = 2;
	/** Sentinel argb: resolve to the target player's talent accent client-side. */
	public static final int TALENT_ACCENT = 0;

	public OrdealFxPayload(ResourceLocation fxLocation, int entityId, int argb) {
		this(fxLocation, entityId, argb, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f,
				0, false, true, "none", TINT_START);
	}

	public static final Type<OrdealFxPayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath("ordeal", "colored_fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OrdealFxPayload> STREAM_CODEC =
			StreamCodec.ofMember(OrdealFxPayload::write, OrdealFxPayload::read);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeResourceLocation(fxLocation);
		buf.writeVarInt(entityId);
		buf.writeInt(argb);
		buf.writeFloat(offX);   buf.writeFloat(offY);   buf.writeFloat(offZ);
		buf.writeFloat(rotX);   buf.writeFloat(rotY);   buf.writeFloat(rotZ);
		buf.writeFloat(scaleX); buf.writeFloat(scaleY); buf.writeFloat(scaleZ);
		buf.writeVarInt(delay);
		buf.writeBoolean(forceDeath);
		buf.writeBoolean(allowMulti);
		buf.writeUtf(autoRotate);
		buf.writeVarInt(tintTarget);
	}

	private static OrdealFxPayload read(RegistryFriendlyByteBuf buf) {
		return new OrdealFxPayload(
				buf.readResourceLocation(), buf.readVarInt(), buf.readInt(),
				buf.readFloat(), buf.readFloat(), buf.readFloat(),
				buf.readFloat(), buf.readFloat(), buf.readFloat(),
				buf.readFloat(), buf.readFloat(), buf.readFloat(),
				buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(),
				buf.readVarInt());
	}

	@SubscribeEvent
	public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
		event.registrar("ordeal").playToClient(TYPE, STREAM_CODEC, OrdealFxPayload::handle);
	}

	private static void handle(OrdealFxPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (FMLEnvironment.dist.isClient())
				ClientSpawner.spawn(payload);
		});
	}

	@OnlyIn(Dist.CLIENT)
	private static final class ClientSpawner {
		private static void spawn(OrdealFxPayload payload) {
			net.minecraft.world.level.Level level = net.minecraft.client.Minecraft.getInstance().level;
			if (level == null) return;

			net.minecraft.world.entity.Entity entity = level.getEntity(payload.entityId());
			if (entity == null) return;

			int argb = payload.argb();
			if (argb == TALENT_ACCENT) argb = accentOf(entity);

			com.lowdragmc.photon.client.fx.FX fx =
					com.lowdragmc.photon.client.fx.FXHelper.getFX(payload.fxLocation(), false);
			if (fx == null) return;

			int target = payload.tintTarget();
			for (var obj : fx.getFxData().objects()) {
				if (obj instanceof com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter pe) {
					if (target == TINT_START || target == TINT_BOTH)
						pe.config.setStartColor(
								com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction.color(argb));
					if (target == TINT_TRAIL || target == TINT_BOTH)
						pe.config.trails.setColorOverLifetime(
								com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction.color(argb));
				}
			}

			com.lowdragmc.photon.client.fx.EntityEffectExecutor.AutoRotate autoRotate;
			try {
				autoRotate = com.lowdragmc.photon.client.fx.EntityEffectExecutor.AutoRotate
						.valueOf(payload.autoRotate().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				autoRotate = com.lowdragmc.photon.client.fx.EntityEffectExecutor.AutoRotate.NONE;
			}

			var effect = new com.lowdragmc.photon.client.fx.EntityEffectExecutor(fx, level, entity, autoRotate);
			effect.setOffset(payload.offX(), payload.offY(), payload.offZ());
			effect.setRotation(payload.rotX(), payload.rotY(), payload.rotZ());
			effect.setScale(payload.scaleX(), payload.scaleY(), payload.scaleZ());
			effect.setDelay(payload.delay());
			effect.setForcedDeath(payload.forceDeath());
			effect.setAllowMulti(payload.allowMulti());
			effect.start();
		}

		private static int accentOf(net.minecraft.world.entity.Entity entity) {
			if (entity instanceof net.minecraft.world.entity.player.Player p) {
				var v = p.getData(net.mcreator.ordeal.network.OrdealModVariables.PLAYER_VARIABLES);
				var t = net.mcreator.ordeal.core.client.OrdealTalents.get(v.talent1_id);
				if (t != null) return t.accent;
			}
			return 0xFFFFFFFF;
		}
	}
}