package net.mcreator.ordeal;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
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

/** One combat beat sent to viewers: a number to float, or a screen event to fire. */
@EventBusSubscriber(modid = "ordeal", bus = EventBusSubscriber.Bus.MOD)
public record OrdealVfxPayload(int kind, double x, double y, double z, float amount)
		implements CustomPacketPayload {

	public static final int THROUGH  = 0;
	public static final int ABSORBED = 1;
	public static final int BREAK    = 2;
	public static final int CHIP     = 3;
	/** Victim-only: red vignette + shake. */
	public static final int FLASH    = 4;
	/** Attacker-only: combo count mirror; amount = links, x = target entity id. */
	public static final int COMBO    = 5;
	/** Viewers: a mob's guard changed; x = entity id, y = regen lockout, amount = guard. */
	public static final int GUARD    = 6;

	public static final Type<OrdealVfxPayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath("ordeal", "vfx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OrdealVfxPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, OrdealVfxPayload::kind,
					ByteBufCodecs.DOUBLE, OrdealVfxPayload::x,
					ByteBufCodecs.DOUBLE, OrdealVfxPayload::y,
					ByteBufCodecs.DOUBLE, OrdealVfxPayload::z,
					ByteBufCodecs.FLOAT, OrdealVfxPayload::amount,
					OrdealVfxPayload::new);

	@Override
	public Type<OrdealVfxPayload> type() {
		return TYPE;
	}

	@SubscribeEvent
	public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
		event.registrar("ordeal").playToClient(TYPE, STREAM_CODEC, OrdealVfxPayload::handle);
	}

	private static void handle(OrdealVfxPayload p, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (FMLEnvironment.dist.isClient()) Client.accept(p);
		});
	}

	@OnlyIn(Dist.CLIENT)
	private static final class Client {
		private static void accept(OrdealVfxPayload p) {
			if (p.kind() == FLASH) { OrdealVfx.flash(); return; }
			if (p.kind() == COMBO) {
				var pl = net.minecraft.client.Minecraft.getInstance().player;
				if (pl != null) {
					pl.getPersistentData().putInt("ordeal_combo", (int) p.amount());
					pl.getPersistentData().putInt("ordeal_combo_t", p.amount() > 0 ? OrdealCombo.WINDOW_TICKS : 0);
					if (p.x() > 0) pl.getPersistentData().putInt("ordeal_combo_target", (int) p.x());
				}
				return;
			}
			if (p.kind() == GUARD) {
				var lvl = net.minecraft.client.Minecraft.getInstance().level;
				if (lvl != null && lvl.getEntity((int) p.x()) instanceof net.minecraft.world.entity.LivingEntity le)
					net.mcreator.ordeal.core.OrdealCombat.clientMobGuard(le, p.amount(), (int) p.y());
				return;
			}
			OrdealVfx.number(p.kind(), p.x(), p.y(), p.z(), p.amount());
		}
	}
}