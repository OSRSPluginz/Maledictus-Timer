package com.osrspluginz.maledictus;

import java.awt.Dimension;
import javax.swing.JPanel;
import net.runelite.client.ui.PluginPanel;

// Utility class to enforce the standard RuneLite sidebar width.
class FixedWidthPanel extends JPanel
{
    @Override
    public Dimension getPreferredSize()
    {
        // Force width to match the PluginPanel standard width
        return new Dimension(PluginPanel.PANEL_WIDTH, super.getPreferredSize().height);
    }
}