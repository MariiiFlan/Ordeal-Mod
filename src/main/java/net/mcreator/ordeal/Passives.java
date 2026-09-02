package net.mcreator.ordeal;

import net.mcreator.ordeal.core.client.OrdealTalents;
import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@EventBusSubscriber(modid = "ordeal")
public final class Passives {

	private static final String KEY = "ordeal_passives_off";
	public static String CLIENT_OFF = "";

	private Passives() {}

	public static boolean isPassive(OrdealTalents.Ability a) {
		return a != null && a.kind != null && a.kind.toUpperCase(Locale.ROOT).contains("PASSIVE");
	}

	public static boolean on(Player p, String abilityId) {
		if (p == null || abilityId == null || abilityId.isEmpty()) return false;
		return !has(p.getPersistentData().getString(KEY), abilityId);
	}

	public static boolean onClient(String abilityId) {
		return !has(CLIENT_OFF, abilityId);
	}

	private static boolean has(String csv, String id) {
		if (csv == null || csv.isEmpty()) return false;
		for (String s : csv.split(",")) if (s.equals(id)) return true;
		return false;
	}

	public static void toggle(Player p, String abilityId) {
		if (p == null || p.level().isClientSide() || abilityId == null || abilityId.isEmpty()) return;
		OrdealTalents.Ability ab = OrdealTalents.ability(abilityId);
		if (!isPassive(ab)) return;
		String csv = p.getPersistentData().getString(KEY);
		List<String> list = new ArrayList<>();
		if (!csv.isEmpty()) for (String s : csv.split(",")) if (!s.isEmpty()) list.add(s);
		if (!list.remove(abilityId)) list.add(abilityId);
		p.getPersistentData().putString(KEY, String.join(",", list));
		sync(p);
	}

	public static boolean pay(Player p, String abilityName, double amount) {
		if (p == null || amount <= 0) return true;
		if (p.level().isClientSide()) return false;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		if (v.chi >= amount) {
			v.chi -= amount;
			v.markSyncDirty();
			return true;
		}
		double shortfall = amount - v.chi;
		if (net.mcreator.ordeal.core.OrdealTalentChi.canDraw(p, abilityName, shortfall)) {
			net.mcreator.ordeal.core.OrdealTalentChi.draw(p, abilityName, shortfall);
			v.chi = 0;
			v.markSyncDirty();
			return true;
		}
		return false;
	}

	public static void sync(Player p) {
		if (p instanceof ServerPlayer sp)
			PacketDistributor.sendToPlayer(sp, new Sync(sp.getPersistentData().getString(KEY)));
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		sync(event.getEntity());
	}

	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		sync(event.getEntity());
	}

	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		String s = event.getOriginal().getPersistentData().getString(KEY);
		if (!s.isEmpty()) event.getEntity().getPersistentData().putString(KEY, s);
	}

	public record Sync(String off) implements CustomPacketPayload {

		public static final Type<Sync> TYPE =
				new Type<>(ResourceLocation.fromNamespaceAndPath(OrdealMod.MODID, "passives_sync"));

		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC =
				StreamCodec.of((RegistryFriendlyByteBuf buf, Sync msg) -> buf.writeUtf(msg.off, 4096),
						(RegistryFriendlyByteBuf buf) -> new Sync(buf.readUtf(4096)));

		@Override
		public Type<Sync> type() {
			return TYPE;
		}

		public static void handleData(final Sync message, final IPayloadContext context) {
			if (context.flow() != PacketFlow.CLIENTBOUND) return;
			context.enqueueWork(() -> CLIENT_OFF = message.off == null ? "" : message.off);
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		OrdealMod.addNetworkMessage(Sync.TYPE, Sync.STREAM_CODEC, Sync::handleData);
	}
}