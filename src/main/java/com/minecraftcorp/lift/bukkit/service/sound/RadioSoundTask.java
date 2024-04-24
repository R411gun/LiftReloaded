package com.minecraftcorp.lift.bukkit.service.sound;

import com.minecraftcorp.lift.bukkit.model.BukkitElevator;
import com.minecraftcorp.lift.common.exception.ConfigurationException;
import com.xxmicloxx.NoteBlockAPI.model.FadeType;
import com.xxmicloxx.NoteBlockAPI.model.Playlist;
import com.xxmicloxx.NoteBlockAPI.model.Song;
import com.xxmicloxx.NoteBlockAPI.songplayer.Fade;
import com.xxmicloxx.NoteBlockAPI.songplayer.RadioSongPlayer;
import com.xxmicloxx.NoteBlockAPI.utils.NBSDecoder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Entity;

public class RadioSoundTask extends SoundTask {

	private static final Fade RADIO_FADE = new Fade(FadeType.LINEAR, 20);
	private static Song[] songs;
	private final RadioSongPlayer radio;

	RadioSoundTask(BukkitElevator elevator) {
		super(elevator, 10);
		if (songs == null) {
			reload();
		}
		radio = new RadioSongPlayer(new Playlist(songs));
		radio.setVolume((byte) volume);
		radio.setRandom(true);
		startRadio(elevator.getPassengers());
	}

	@Override
	public void run() {
		super.run();
		List<UUID> uuidsInElevator = elevator.getInvolvedEntities()
				.map(Entity::getUniqueId)
				.toList();
		radio.getPlayerUUIDs()
				.stream()
				.filter(uuid -> !uuidsInElevator.contains(uuid))
				.forEach(radio::removePlayer);
	}

	@Override
	public void cancel() throws IllegalStateException {
		super.cancel();
		plugin.logDebug("Cancelled SoundTask");
		stopRadio(elevator.getPassengers());
	}

	private void startRadio(Set<Entity> entities) {
		filterPlayers(entities).forEach(radio::addPlayer);
		radio.setPlaying(true, RADIO_FADE);
		int randomIndex = ThreadLocalRandom.current()
				.nextInt(radio.getPlaylist().getSongList().size());

		radio.playSong(randomIndex);
		plugin.logDebug("Playing " + radio.getSong().getPath().getName());
	}

	private void stopRadio(Set<Entity> entities) {
		filterPlayers(entities).forEach(radio::removePlayer);
		radio.setPlaying(false, RADIO_FADE);
	}

	public static void reload() {
		songs = getMusicFiles()
				.stream()
				.map(NBSDecoder::parse)
				.toArray(Song[]::new);
	}

	private static List<File> getMusicFiles() {
		Path songDir = plugin.getDataFolder().toPath()
				.resolve("music");
		if (!Files.isDirectory(songDir)) {
			try {
				Files.createDirectories(songDir);
			} catch (IOException e) {
				throw new ConfigurationException("Unable to create music directory");
			}
			plugin.logInfo("Created " + songDir + ". You can drop your .nbs files there.");
		}
		try {
			return Arrays.stream(Objects.requireNonNull(songDir.toFile()
					.listFiles((file, name) -> name.endsWith(".nbs"))))
					.toList();
		} catch (Exception e) {
			throw new ConfigurationException("Unable to find music files in " + songDir, e);
		}
	}
}
