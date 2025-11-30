package com.osrspluginz.maledictus;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class MaledictusPanel extends PluginPanel
{
    private final MaledictusPlugin plugin;

    private final com.osrspluginz.maledictus.FixedWidthPanel listContainer = new com.osrspluginz.maledictus.FixedWidthPanel();
    private final GridBagConstraints constraints = new GridBagConstraints();

    private final JCheckBox showClosestCheck;
    private final JCheckBox showOverlayCheck;

    @Inject
    public MaledictusPanel(MaledictusPlugin plugin)
    {
        this.plugin = plugin;

        // 1. Panel Setup
        setBorder(new EmptyBorder(10, 5, 10, 5));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        // --- Header Panel (North) ---
        JPanel headerPanel = new JPanel(new GridBagLayout());
        headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        GridBagConstraints hConstraints = new GridBagConstraints();
        hConstraints.fill = GridBagConstraints.HORIZONTAL;
        hConstraints.gridx = 0;
        hConstraints.gridy = 0;
        hConstraints.weightx = 1;

        // Show Overlay Checkbox
        showOverlayCheck = new JCheckBox("Show Overlay", plugin.getConfig().showOverlay());
        showOverlayCheck.setForeground(Color.WHITE);
        showOverlayCheck.setBackground(ColorScheme.DARK_GRAY_COLOR);
        showOverlayCheck.setOpaque(false);
        showOverlayCheck.addActionListener(e -> plugin.setOverlayConfig(showOverlayCheck.isSelected()));
        headerPanel.add(showOverlayCheck, hConstraints);

        // Sort by Closest Spawns Checkbox
        hConstraints.gridy = 1;
        showClosestCheck = new JCheckBox("Sort by Closest Spawn");
        showClosestCheck.setForeground(Color.WHITE);
        showClosestCheck.setBackground(ColorScheme.DARK_GRAY_COLOR);
        showClosestCheck.setOpaque(false);
        headerPanel.add(showClosestCheck, hConstraints);

        headerPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
        add(headerPanel, BorderLayout.NORTH);

        // --- List Container Setup (Center) ---
        listContainer.setLayout(new GridBagLayout());
        listContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.gridx = 0;
        constraints.insets = new Insets(2, 0, 2, 0);

        JScrollPane scrollPane = new JScrollPane(listContainer);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(new EmptyBorder(0, 0, 0, 15));
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // FIX 2: Increased unit increment to 30 for smoother scrolling on the tall rows.
        scrollPane.getVerticalScrollBar().setUnitIncrement(30);

        add(scrollPane, BorderLayout.CENTER);

        updatePanel();
    }

    public void updatePanel()
    {
        listContainer.removeAll();

        List<MaledictusPlugin.WorldTimer> timers = plugin.getAllWorldTimers();

        List<MaledictusPlugin.WorldTimer> sortedTimers = timers.stream()
                .collect(Collectors.toList());

        if (showClosestCheck.isSelected())
        {
            sortedTimers.sort(Comparator.comparingLong(MaledictusPlugin.WorldTimer::secondsLeft));
        }
        else
        {
            sortedTimers.sort(Comparator.comparingInt(MaledictusPlugin.WorldTimer::getWorld));
        }

        constraints.gridy = 0;
        MaledictusTimerRow lastRow = null;

        for (MaledictusPlugin.WorldTimer timer : sortedTimers)
        {
            MaledictusTimerRow row = new MaledictusTimerRow(plugin, timer);
            row.updateRow();

            if (lastRow != null)
            {
                constraints.weighty = 0;
                listContainer.add(lastRow, constraints);
                constraints.gridy++;
            }

            lastRow = row;
        }

        if (lastRow != null)
        {
            constraints.weighty = 1;
            listContainer.add(lastRow, constraints);
        }
        else
        {
            JLabel filler = new JLabel("No Maledictus worlds initialized.");
            filler.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            constraints.gridy = 0;
            constraints.weighty = 1;
            listContainer.add(filler, constraints);
        }

        showOverlayCheck.setSelected(plugin.getConfig().showOverlay());

        listContainer.revalidate();
        listContainer.repaint();
    }
}