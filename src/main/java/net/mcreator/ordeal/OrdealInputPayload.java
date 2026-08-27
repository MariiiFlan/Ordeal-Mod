package net.mcreator.ordeal.core;

import net.mcreator.ordeal.OrdealMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.mcreator.ordeal.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber
public record OrdealInputPayload(
		boolean attack, boolean ability1, boolean ability2, boolean ability3,
		boolean ability4, boolean ability5, boolean combatMode,
		boolean forward, boolean back, boolean left, boolean right,
		boolean jump, boolean sneak, boolean sprint
) implements CustomPacketPayload {

	public static final Type<OrdealInputPayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(OrdealMod.MODID, "input_state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OrdealInputPayload> STREAM_CODEC =
			StreamCodec.of((RegistryFriendlyByteBuf b, OrdealInputPayload p) -> {
				b.writeBoolean(p.attack());     b.writeBoolean(p.ability1());
				b.writeBoolean(p.ability2());   b.writeBoolean(p.ability3());
				b.writeBoolean(p.ability4());   b.writeBoolean(p.ability5());
				b.writeBoolean(p.combatMode()); b.writeBoolean(p.forward());
				b.writeBoolean(p.back());       b.writeBoolean(p.left());
				b.writeBoolean(p.right());      b.writeBoolean(p.jump());
				b.writeBoolean(p.sneak());      b.writeBoolean(p.sprint());
			}, (RegistryFriendlyByteBuf b) -> new OrdealInputPayload(
					b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
					b.readBoolean(), b.readBoolean(), b.readBoolean(),
					b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
					b.readBoolean(), b.readBoolean(), b.readBoolean()));

	@Override
	public Type<OrdealInputPayload> type() {
		return TYPE;
	}

	public static void handleData(final OrdealInputPayload m, final IPayloadContext context) {
		if (context.flow() != PacketFlow.SERVERBOUND) return;
		context.enqueueWork(() -> write(context.player(), m)).exceptionally(e -> {
			context.connection().disconnect(Component.literal(e.getMessage()));
			return null;
		});
	}

	private static void write(Player p, OrdealInputPayload m) {
		if (p == null) return;
		var d = p.getPersistentData();
		d.putBoolean("ordeal_attack", m.attack());
		d.putBoolean("ordeal_ability1", m.ability1());
		d.putBoolean("ordeal_ability2", m.ability2());
		d.putBoolean("ordeal_ability3", m.ability3());
		d.putBoolean("ordeal_ability4", m.ability4());
		d.putBoolean("ordeal_ability5", m.ability5());
		d.putBoolean("ordeal_combatMode", m.combatMode());
		d.putBoolean("ordeal_forward", m.forward());
		d.putBoolean("ordeal_back", m.back());
		d.putBoolean("ordeal_left", m.left());
		d.putBoolean("ordeal_right", m.right());
		d.putBoolean("ordeal_jump", m.jump());
		d.putBoolean("ordeal_sneak", m.sneak());
		d.putBoolean("ordeal_sprint", m.sprint());
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		OrdealMod.addNetworkMessage(TYPE, STREAM_CODEC, OrdealInputPayload::handleData);
	}
}