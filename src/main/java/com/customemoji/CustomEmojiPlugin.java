package com.customemoji;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import joptsimple.internal.Strings;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.*;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;

import static net.runelite.client.RuneLite.RUNELITE_DIR;

@Slf4j
@PluginDescriptor(
	name = "Custom Emoji",
	description = "Replaces Discord-style emoji tags with user-defined emoji images"
)
public class CustomEmojiPlugin extends Plugin
{
	private static final String LIST_EMOJI_COMMAND_STRING = "!emoji";

	private static final Pattern EMOJI_REGEXP = Pattern.compile(":.+?:");

	private static final File CUSTOM_EMOJI_DIR = new File(RUNELITE_DIR, "customemoji");
	private static final List<String> IMG_FILE_TYPES = Arrays.asList("png", "jpg", "jpeg");
	private static final FileFilter IMG_FILE_FILTER = pathname -> {
		String filename = pathname.getName();
		String fileExtension = filename.substring(filename.lastIndexOf('.') + 1);
		return IMG_FILE_TYPES.contains(fileExtension);
	};

	private static final Map<String, CustomEmoji> emojiMap = new HashMap<>();
	private static final List<String> emojiNames = new ArrayList<>();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatCommandManager chatCommandManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	private int modIconsStart = -1;

	@Override
	protected void startUp() throws Exception
	{
		clientThread.invoke(this::loadEmojiIcons);
		chatCommandManager.registerCommand(LIST_EMOJI_COMMAND_STRING, this::listEmoji);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			loadEmojiIcons();
		}
	}

	private CustomEmoji[] loadCustomEmojis() {
		File[] emojiFiles = CUSTOM_EMOJI_DIR.listFiles(IMG_FILE_FILTER);

		// If there are no image files in the directory, return an empty array.
		if (emojiFiles == null)
		{
			log.debug("0 emoji files loaded.");
			return new CustomEmoji[0];
		}

		log.debug(emojiFiles.length + " emoji files loaded.");

		for (final File currentEmojiFile : emojiFiles)
		{
			String filename = currentEmojiFile.getName();
			String emojiName = filename.substring(0, filename.lastIndexOf("."));

			if (!emojiMap.containsKey(emojiName))
			{
				BufferedImage bf = null;
				try
				{
					bf = ImageIO.read(currentEmojiFile);
				}
				catch (IOException ex)
				{
					log.warn("Failed to load the image for emoji " + emojiName, ex);
				}

				// If unable to load image, send console message telling user to replace that image.
				if (bf == null)
				{
					String message = "Unable to use image for emoji " + emojiName
							+ ". Please use a different image file for this emoji.";
					sendChatMessage(message, ChatColorType.HIGHLIGHT);
					continue;
				}

				CustomEmoji newEmoji = new CustomEmoji(emojiName, bf);
				emojiMap.put(emojiName, newEmoji);
				log.debug("Added emoji " + emojiName);
			}
			else
			{
				log.info("Failed to load duplicate emoji " + emojiName);
			}
		}

		Object[] valueArray = emojiMap.values().toArray();
		CustomEmoji[] customEmojiList = Arrays.copyOf(valueArray, valueArray.length, CustomEmoji[].class);

		for(final CustomEmoji customEmoji : customEmojiList)
		{
			emojiNames.add(customEmoji.getName());
		}

		return customEmojiList;
	}

	private void loadEmojiIcons()
	{
		final IndexedSprite[] modIcons = client.getModIcons();
		if (modIconsStart != -1 || modIcons == null)
		{
			return;
		}


		final CustomEmoji[] customEmojis = loadCustomEmojis();
		final IndexedSprite[] newModIcons = Arrays.copyOf(modIcons, modIcons.length + customEmojis.length);
		modIconsStart = modIcons.length;

		for (int i = 0; i < customEmojis.length; i++)
		{
			final CustomEmoji customEmoji = customEmojis[i];

			try
			{
				final BufferedImage image = customEmoji.getImage();
				final IndexedSprite sprite = ImageUtil.getImageIndexedSprite(image, client);
				newModIcons[modIconsStart + i] = sprite;
				log.debug("Added emoji " + customEmoji.getName());
			}
			catch (Exception ex)
			{
				log.warn("Failed to load the sprite for emoji " + customEmoji.getName(), ex);
			}
		}

		log.debug("Adding custom emoji icons");
		client.setModIcons(newModIcons);

		sendChatMessage("Loaded " + emojiNames.size() + " custom emoji.", ChatColorType.NORMAL);
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (client.getGameState() != GameState.LOGGED_IN || modIconsStart == -1)
		{
			return;
		}

		switch (chatMessage.getType())
		{
			case PUBLICCHAT:
			case MODCHAT:
			case FRIENDSCHAT:
			case PRIVATECHAT:
			case PRIVATECHATOUT:
			case MODPRIVATECHAT:
				break;
			default:
				return;
		}

		final MessageNode messageNode = chatMessage.getMessageNode();
		final String message = messageNode.getValue();
		final String updatedMessage = updateMessage(message);

		if (updatedMessage == null)
		{
			return;
		}

		messageNode.setRuneLiteFormatMessage(updatedMessage);
		chatMessageManager.update(messageNode);
		client.refreshChat();
	}

	@Subscribe
	public void onOverheadTextChanged(final OverheadTextChanged event)
	{
		if (!(event.getActor() instanceof Player))
		{
			return;
		}

		final String message = event.getOverheadText();
		final String updatedMessage = updateMessage(message);

		if (updatedMessage == null)
		{
			return;
		}

		event.getActor().setOverheadText(updatedMessage);
	}

	@Nullable
	String updateMessage(final String message)
	{
		String newMessage = message;
		boolean editedMessage = false;
		Matcher matcher = EMOJI_REGEXP.matcher(newMessage);

		while (matcher.find())
		{
			String emojiWithColons = matcher.group();
			final String emojiName = emojiWithColons.substring(1, emojiWithColons.length() - 1);
			final CustomEmoji emoji = emojiMap.get(emojiName);

			if (emoji == null)
			{
				continue;
			}

			final int emojiId = modIconsStart + emojiNames.indexOf(emojiName);
			newMessage = newMessage.replace(emojiWithColons, "<img=" + emojiId + ">");
			editedMessage = true;
		}

		// If we haven't edited the message any, don't update it.
		if (!editedMessage)
		{
			return null;
		}

		return newMessage;
	}

	private void listEmoji(ChatMessage chatMessage, String message) {
		StringBuilder sb = new StringBuilder();
		File[] emojiFiles = CUSTOM_EMOJI_DIR.listFiles(IMG_FILE_FILTER);
		if( emojiFiles == null )
		{
			sb.append("No emoji found.");
		}
		else
		{
			sb.append("Emoji: ");
			for (String emojiName : emojiNames)
			{
				sb.append(emojiName).append(" ");
			}
		}

		String response = sb.toString();

		sendChatMessage(response, ChatColorType.NORMAL);
	}

	private void sendChatMessage(String chatMessage, ChatColorType chatColorType)
	{
		final String message = new ChatMessageBuilder()
				.append(chatColorType)
				.append(chatMessage)
				.build();

		chatMessageManager.queue(
				QueuedMessage.builder()
						.type(ChatMessageType.CONSOLE)
						.runeLiteFormattedMessage(message)
						.build());
	}

}
