package com.customemoji;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static net.runelite.client.RuneLite.RUNELITE_DIR;

public class CustomEmojiLoaderTest
{
    private static final File CUSTOM_EMOJI_DIR = new File(RUNELITE_DIR, "customemoji");
    private static final List<String> IMG_FILE_TYPES = Arrays.asList("png", "jpg", "jpeg");
    private static final FileFilter IMG_FILE_FILTER = pathname -> {
        String filename = pathname.getName();
        String fileExtension = filename.substring(filename.lastIndexOf('.') + 1);
        return IMG_FILE_TYPES.contains(fileExtension);
    };

    public static void main(String[] args)
    {
        File[] emojiFiles = CUSTOM_EMOJI_DIR.listFiles(IMG_FILE_FILTER);
        assert emojiFiles != null;
        for(File emojiFile : emojiFiles)
        {
            String filename = emojiFile.getName();
            String emojiName = filename.substring(0, filename.lastIndexOf("."));

            BufferedImage bf = null;
            try
            {
                bf = ImageIO.read(emojiFile);
            }
            catch (IOException ex)
            {
                System.out.println("Failed to load the image for emoji " + emojiName);
            }

            if (bf == null)
            {
                System.out.println("Unable to use image for emoji " + emojiName
                        + ". Please use a different image file for this emoji.");
                continue;
            }

            System.out.println("Added emoji " + emojiName);
        }
    }
}
