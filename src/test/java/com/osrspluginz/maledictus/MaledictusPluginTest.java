package com.osrspluginz.maledictus;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class MaledictusPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(MaledictusPlugin.class);
        RuneLite.main(args);
    }
}
