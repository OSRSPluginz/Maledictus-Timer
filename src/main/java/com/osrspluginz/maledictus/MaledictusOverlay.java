package com.osrspluginz.maledictus;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.util.ColorUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
// Removed: import java.time.Duration;

public class MaledictusOverlay extends OverlayPanel
{
    private final MaledictusPlugin plugin;
    private final Client client;

    @Inject
    public MaledictusOverlay(MaledictusPlugin plugin, Client client)
    {
        super(plugin);
        this.plugin = plugin;
        this.client = client;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    private Color getTimerColor(long remaining)
    {
        Color textColor = Color.WHITE;
        if (remaining == Long.MAX_VALUE)
        {
            textColor = Color.LIGHT_GRAY;
        }
        else if (remaining <= 0)
        {
            textColor = Color.CYAN;
        }
        else if (remaining <= MaledictusPlugin.TIME_RED_THRESHOLD_SECS)
        {
            textColor = Color.RED;
        }
        return textColor;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.getConfig().showOverlay()) return null;

        // Use the plugin's new logic to get the timer, which returns a 'No Data' timer
        // if the world is not explicitly tracked (e.g., F2P world or untracked member world).
        MaledictusPlugin.WorldTimer timer = plugin.getWorldTimer(client.getWorld());

        // If the client's world is not available, skip rendering.
        if (timer == null)
        {
            return null;
        }

        long secondsLeft = timer.secondsLeft();
        String timeText = timer.getDisplayText();
        Color timeColor = getTimerColor(secondsLeft);
        BufferedImage skullImage = timer.getSkullIcon(plugin);
        String worldText = "W" + client.getWorld();

        panelComponent.getChildren().clear();

        // 1. Add Skull Icon
        if (skullImage != null)
        {
            panelComponent.getChildren().add(new ImageComponent(skullImage));
        }

        // 2. Add World ID and Timer on a single line
        if (secondsLeft == Long.MAX_VALUE)
        {
            // Handle the 'No Data' state explicitly
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(worldText)
                    .leftColor(Color.LIGHT_GRAY)
                    .right("No Data")
                    .rightColor(Color.LIGHT_GRAY)
                    .build());
        }
        else
        {
            // Display the countdown/elapsed time
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(worldText)
                    .leftColor(timeColor) // Color the World ID
                    .right(ColorUtil.prependColorTag(timeText, timeColor))
                    .rightColor(timeColor) // Color the Timer
                    .build());
        }

        return super.render(graphics);
    }
}