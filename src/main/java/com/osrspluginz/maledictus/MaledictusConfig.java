package com.osrspluginz.maledictus;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("maledictus")
public interface MaledictusConfig extends Config
{
    @ConfigItem(
            keyName = "showOverlay",
            name = "Show Overlay",
            description = "Displays the skull overlay with timers"
    )
    default boolean showOverlay()
    {
        return true;
    }

    // NEW HOPPER CONFIG
    @ConfigItem(
            keyName = "worldHopperEnabled",
            position = 3,
            name = "Double click to Hop",
            description = "Enables double clicking worlds in the side panel to quick-hop to them."
    )
    default boolean isWorldHopperEnabled()
    {
        return true;
    }
}