package com.osrspluginz.maledictus;

import net.runelite.client.ui.ColorScheme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

// NOTE: Changed back to extend JPanel (or whatever the base class is for FixedWidthPanel)
// to ensure the layout structure that fixed the skull icon is maintained.
public class MaledictusTimerRow extends JPanel
{
    private final com.osrspluginz.maledictus.MaledictusPlugin plugin;
    private final com.osrspluginz.maledictus.MaledictusPlugin.WorldTimer timer;

    // Changed names to match your current code (`skullIconLabel` was used in your current version)
    private final JLabel worldLabel = new JLabel();
    private final JLabel timeLabel = new JLabel();
    private final JLabel skullIconLabel = new JLabel();

    public MaledictusTimerRow(com.osrspluginz.maledictus.MaledictusPlugin plugin, com.osrspluginz.maledictus.MaledictusPlugin.WorldTimer timer)
    {
        this.plugin = plugin;
        this.timer = timer;

        // --- Row Setup (UI Initialization) ---
        setLayout(new BorderLayout()); // Use plain BorderLayout for the main row
        setBorder(new EmptyBorder(10, 5, 10, 5)); // Increased vertical padding
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        // --- Left Panel (Icon and World ID) ---
        // Creating a nested panel to correctly position the icon and world label
        JPanel leftPanel = new JPanel(new BorderLayout(5, 0));
        leftPanel.setOpaque(false);
        leftPanel.setBorder(new EmptyBorder(0, 0, 0, 5));

        // Skull Icon
        // FIX: Increased preferred size to correctly display the skull icon image.
        skullIconLabel.setPreferredSize(new Dimension(24, 30));
        leftPanel.add(skullIconLabel, BorderLayout.WEST);

        // World Label
        worldLabel.setText("W" + timer.getWorld());
        worldLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR.brighter());
        leftPanel.add(worldLabel, BorderLayout.CENTER);

        add(leftPanel, BorderLayout.WEST);

        // --- Time Label (Center) ---
        timeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        timeLabel.setFont(timeLabel.getFont().deriveFont(Font.BOLD)); // Added font bolding from the working version
        add(timeLabel, BorderLayout.CENTER);

        // Removed skullIconLabel from BorderLayout.EAST since it's now in leftPanel.
        // We will now rely on the coloring logic to apply the correct World ID color.

        // --- CRITICAL: World Hopping Fix (Mouse Listener) ---
        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseReleased(MouseEvent e)
            {
                plugin.hopTo(timer.getWorld());
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }
        });
    }

    /**
     * Determines the correct text color for the timer based on the remaining time.
     */
    private Color getTimerColor(long remaining)
    {
        // Default text color is white for 45-15 mins
        Color textColor = Color.WHITE;

        if (remaining == Long.MAX_VALUE)
        {
            // No Data
            textColor = Color.LIGHT_GRAY;
        }
        else if (remaining <= 0)
        {
            // Eligible/Active
            textColor = Color.CYAN;
        }
        else if (remaining <= com.osrspluginz.maledictus.MaledictusPlugin.TIME_RED_THRESHOLD_SECS)
        {
            // 15-0 minutes remaining (Red)
            textColor = Color.RED;
        }

        return textColor;
    }

    // --- CRITICAL: Color & Skull Display Logic ---
    public void updateRow()
    {
        // 1. Set the Skull Icon (Image is selected based on logic in plugin file)
        BufferedImage skullImage = timer.getSkullIcon(plugin);
        skullIconLabel.setIcon(new ImageIcon(skullImage));

        // 2. Set the Time Text
        timeLabel.setText(timer.getDisplayText());

        // 3. Set the Colors
        long remaining = timer.secondsLeft();
        Color rowColor = getTimerColor(remaining);

        // Applying the determined color to both the time and the world label (FIX)
        timeLabel.setForeground(rowColor);
        worldLabel.setForeground(rowColor);

        revalidate();
        repaint();
    }
}