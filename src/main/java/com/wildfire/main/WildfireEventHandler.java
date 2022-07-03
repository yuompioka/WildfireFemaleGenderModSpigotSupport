package com.wildfire.main;

import com.wildfire.gui.screen.WildfirePlayerListScreen;
import com.wildfire.main.networking.PacketSendGenderInfo;
import com.wildfire.main.networking.PacketSync;

import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class WildfireEventHandler {

	public static final KeyBinding toggleEditGUI = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.wildfire_gender.gender_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.wildfire_gender.generic"));

	private static int timer = 0;

	public static void registerClientEvents() {

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (!handler.getPlayer().world.isClient()) {
				//Send all other players to the player who joined. Note: We don't send the player to
				// other players as that will happen once the player finishes sending themselves to the server
				PacketSync.sendTo(handler.getPlayer());
			}
		});
		ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if(!world.isClient) return;
			if(entity instanceof  AbstractClientPlayerEntity plr) {
				UUID uuid = plr.getUuid();
				GenderPlayer aPlr = WildfireGender.getPlayerById(plr.getUuid());
				if (aPlr == null) {
					aPlr = new GenderPlayer(uuid);
					WildfireGender.CLOTHING_PLAYERS.put(uuid, aPlr);
					WildfireGender.loadGenderInfoAsync(uuid, uuid.equals(MinecraftClient.getInstance().player.getUuid()));
					return;
				}
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if(client.world == null) WildfireGender.CLOTHING_PLAYERS.clear();

			timer++;
			if (timer >= 5) {
				try {
					GenderPlayer aPlr = WildfireGender.getPlayerById(MinecraftClient.getInstance().player.getUuid());
					if(aPlr == null) return;
					PacketSendGenderInfo.send(aPlr);
				} catch (Exception e) {}
				timer = 0;
			}

			//Receive hurt

			ClientPlayNetworking.registerGlobalReceiver(new Identifier(WildfireGender.MODID, "hurt"),
					(client2, handler, buf, responseSender) -> {
						UUID uuid = buf.readUuid();
						GenderPlayer.Gender gender = buf.readEnumConstant(GenderPlayer.Gender.class);
						boolean hurtSounds = buf.readBoolean();

						//Vector3d pos = new Vector3d(buf.readDouble(), buf.readDouble(), buf.readDouble());

						SoundEvent hurtSound = null;
						if(gender == GenderPlayer.Gender.FEMALE) {
							hurtSound = Math.random() > 0.5f ? WildfireSounds.FEMALE_HURT1 : WildfireSounds.FEMALE_HURT2;
						}
						if(hurtSound == null) return;

						if(hurtSounds) {
							PlayerEntity ent = MinecraftClient.getInstance().world.getPlayerByUuid(uuid);
							if (ent != null) {
								long randomLong = new Random().nextLong(0L,1L);
								client.getSoundManager().play(new EntityTrackingSoundInstance(hurtSound, SoundCategory.PLAYERS, 1f, 1f, ent.getEventSource(), randomLong));
							}
						}
					});

			while (toggleEditGUI.wasPressed()) {
				client.setScreen(new WildfirePlayerListScreen(client));
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(new Identifier(WildfireGender.MODID, "sync"),
		(client, handler, buf, responseSender) -> {
			PacketSync.handle(client, handler, buf, responseSender);
		});
	}

	//TODO: Eventually we may want to replace this with a map or something and replace things like drowning sounds with other drowning sounds
	private final Set<SoundEvent> playerHurtSounds = Set.of(SoundEvents.ENTITY_PLAYER_HURT,
		SoundEvents.ENTITY_PLAYER_HURT_DROWN,
		SoundEvents.ENTITY_PLAYER_HURT_FREEZE,
		SoundEvents.ENTITY_PLAYER_HURT_ON_FIRE,
		SoundEvents.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH
	);
}
