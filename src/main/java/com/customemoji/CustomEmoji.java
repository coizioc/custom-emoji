package com.customemoji;

import java.awt.*;
import java.awt.image.BufferedImage;

public class CustomEmoji
{
    static final int IMG_WIDTH = 13;
    static final int IMG_HEIGHT = 13;

    private final String name;
    private BufferedImage image;

    CustomEmoji(String name, BufferedImage image)
    {
        this.name = name;
        this.image = image;
        if(image.getWidth() != IMG_WIDTH || image.getHeight() != IMG_HEIGHT)
        {
            rescaleImage();
        }
    }

    private void rescaleImage()
    {
        Image scaledEmoji = this.image.getScaledInstance(IMG_WIDTH, IMG_HEIGHT, Image.SCALE_DEFAULT);
        this.image = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D bGr = this.image.createGraphics();
        bGr.drawImage(scaledEmoji, 0, 0, null);
        bGr.dispose();
    }

    public String getName()
    {
        return name;
    }

    public BufferedImage getImage()
    {
        return image;
    }
}
