package com.osrspluginz.maledictus;

import com.google.inject.Provides;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;

@Slf4j
@PluginDescriptor(
        name = "Maledictus Timer",
        description = "Tracks Maledictus spawn/death times",
        tags = {"boss", "mal", "timer", "gwd"}
)
public class MaledictusPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(MaledictusPlugin.class);

    // --- CONSTANTS ---
    public static final Duration RESET_TIMER = Duration.ofMinutes(45);
    public static final int MALEDICTUS_ID = 11246;
    private static final int DISPLAY_SWITCHER_MAX_ATTEMPTS = 10;

    // Time threshold for skull colors (in seconds)
    public static final long TIME_RED_THRESHOLD_SECS = Duration.ofMinutes(15).getSeconds(); // 15 mins (900s)

    // --- COMPLETE LIST OF MEMBER WORLDS (UPDATED/CONFIRMED) ---
    private static final int[] MEMBER_WORLDS = {
            302,303,304,305,306,307,309,310,311,312,313,314,315,317,318,320,321,322,
            323,324,325,327,328,329,330,331,332,333,334,336,337,338,339,340,341,342,343,344,346,347,348,349,350,351,
            352,353,354,355,356,357,358,359,360,361,362,363,364,365,366,367,368,369,370,371,373,374,375,376,377,
            378,385,386,387,388,389,390,391,394,395,396,402,403,404,405,406,407,408,409,410,411,412,415,416,420,421,
            422,423,424,425,426,428,429,438,439,440,441,442,443,444,445,446,447,448,449,450,457,458,459,461,462,463,
            464,465,466,467,470,471,472,473,474,475,476,477,478,479,480,481,482,484,485,486,487,488,489,490,491,492,
            493,494,495,496,500,501,503,504,505,506,507,508,509,510,511,512,513,514,515,516,517,518,519,520,521,522,
            523,524,525,526,527,528,529,531,532,533,534,535,536,538,541,542,543,544,545,546,547,550,551,556,557,558,
            559,562,563,564,566,567,569,570,573,574,575,578,582,590,591,592,593,594,595,599,600,601,602,603,604,605,
            606,607,608,609,610,611,612,613,614,615,616,617,618,619,620,621,622,623,624,625,626,627,628
    };

    // Quick check if the player is in one of the worlds we track
    private final boolean isPlayerInTrackedWorld(int worldId) {
        // Use binary search for O(log n) lookup, assuming MEMBER_WORLDS is sorted.
        return Arrays.binarySearch(MEMBER_WORLDS, worldId) >= 0;
    }

    private net.runelite.api.World quickHopTargetWorld;
    int displaySwitcherAttempts = 0;

    // --- INJECTS ---
    @Inject private OverlayManager overlayManager;
    @Inject private com.osrspluginz.maledictus.MaledictusOverlay overlay;
    @Inject private com.osrspluginz.maledictus.MaledictusConfig config;
    @Inject private ClientToolbar clientToolbar;
    @Inject private Client client;
    @Inject private ConfigManager configManager;
    @Inject private com.osrspluginz.maledictus.MaledictusPanel panel;
    @Inject private ClientThread clientThread;
    @Inject private WorldService worldService;
    @Inject private ChatMessageManager chatMessageManager;


    private NavigationButton navButton;
    private final Map<Integer, WorldTimer> worldTimers = new HashMap<>();

    // Cached skull icons
    private BufferedImage skullWhite;
    private BufferedImage skullRed;
    private BufferedImage skullPanel;

    @Provides
    com.osrspluginz.maledictus.MaledictusConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(com.osrspluginz.maledictus.MaledictusConfig.class);
    }

    @Override
    protected void startUp()
    {
        // Image loading paths reverted to include the leading slash (Fixes previous break)
        skullWhite = ImageUtil.loadImageResource(getClass(), "/skullwhite.png");
        skullRed = ImageUtil.loadImageResource(getClass(), "/skullred.png");
        skullPanel = ImageUtil.loadImageResource(getClass(), "/skullpanel.png");

        overlayManager.add(overlay);

        navButton = NavigationButton.builder()
                .tooltip("Maledictus Tracker")
                .icon(skullPanel)
                .panel(panel)
                .priority(10)
                .build();
        clientToolbar.addNavigation(navButton);

        // --- Initialization: Add all potential worlds with 'No Data' marker ---
        // Ensure the list is sorted for binary search utility.
        Arrays.sort(MEMBER_WORLDS);
        for (int worldId : MEMBER_WORLDS)
        {
            worldTimers.put(worldId, new WorldTimer(worldId, Instant.MIN));
        }

        log.info("Maledictus Timer started, tracking {} member worlds.", MEMBER_WORLDS.length);
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);

        if (navButton != null)
            clientToolbar.removeNavigation(navButton);

        worldTimers.clear();
        skullWhite = null;
        skullRed = null;
        skullPanel = null;

        log.info("Maledictus Timer stopped.");
    }

    // --- CONSOLE MESSAGE HELPER ---

    private void sendConsoleMessage(String message) {
        String chatMessage = new ChatMessageBuilder()
                .append(ChatColorType.NORMAL)
                .append("Maledictus Hopper: ")
                .append(ChatColorType.HIGHLIGHT)
                .append(message)
                .build();

        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.CONSOLE)
                .runeLiteFormattedMessage(chatMessage)
                .build());
    }

    // --- Timer Management & Game Events ---

    /**
     * Sets the next **eligibility** time for Maledictus to spawn, which is
     * 45 minutes after the current spawn announcement (spawnTime).
     * @param world The world ID where the spawn occurred.
     * @param spawnTime The Instant the spawn announcement was received.
     */
    public void setMaledictusEligibility(int world, Instant spawnTime)
    {
        // Calculate the next time the spawn chance can accumulate (Spawn Time + 45 minutes)
        Instant nextEligibility = spawnTime.plus(RESET_TIMER);
        worldTimers.put(world, new WorldTimer(world, nextEligibility));
        log.info("Maledictus spawned on W{}. Next eligibility for spawn begins at {}", world, nextEligibility);
    }

    @Schedule(
            period = 1,
            unit = ChronoUnit.SECONDS,
            asynchronous = true
    )
    public void updateTimers()
    {
        if (panel.isVisible())
        {
            panel.updatePanel();
        }
    }

    // --- TRACKING MALEDICTUS SPAWN (Timer Reset) ---

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        // Check for Maledictus spawn announcement
        if (event.getType() == ChatMessageType.GAMEMESSAGE)
        {
            // Use startsWith to correctly capture the message regardless of the location text that follows
            if (event.getMessage().startsWith("A superior revenant has been awoken"))
            {
                if (client.getGameState() == GameState.LOGGED_IN)
                {
                    int currentWorld = client.getWorld();
                    // The moment the spawn is announced, the 45-minute prevention timer begins.
                    setMaledictusEligibility(currentWorld, Instant.now());
                }
            }
        }

        // World hop block logic
        if (event.getType() != ChatMessageType.GAMEMESSAGE)
        {
            return;
        }
        if (event.getMessage().equals("Please finish what you're doing before using the World Switcher."))
        {
            sendConsoleMessage("Hop blocked by game: Please stop what you are doing (e.g. combat, skilling) and try again.");
            // We intentionally do not call resetQuickHopper() here so the hop retries on the next tick
        }
    }

    // --- WorldTimer Static Inner Class (Data Model) ---
    @Value
    public static class WorldTimer
    {
        private final int world;
        private final Instant nextEligibility; // Renamed for clarity

        public WorldTimer(int world, Instant nextEligibility)
        {
            this.world = world;
            this.nextEligibility = nextEligibility;
        }

        public int getWorld()
        {
            return world;
        }

        public Instant getNextSpawn() // Kept method name for interface consistency
        {
            return nextEligibility;
        }

        /**
         * Returns remaining seconds until eligible to spawn. Returns Long.MAX_VALUE if no data is observed.
         */
        public long secondsLeft()
        {
            if (getNextSpawn() == Instant.MIN)
            {
                return Long.MAX_VALUE;
            }
            return Instant.now().until(getNextSpawn(), ChronoUnit.SECONDS);
        }

        /**
         * Returns the formatted time string.
         * For Eligible/Active (remaining <= 0), it shows only the +time elapsed.
         */
        public String getDisplayText()
        {
            if (getNextSpawn() == Instant.MIN)
            {
                return "No Data"; // Display "No Data" for unobserved worlds
            }

            long remaining = secondsLeft();
            long absRemaining = Math.abs(remaining);

            long hours = absRemaining / 3600;
            long minutes = (absRemaining % 3600) / 60;
            long seconds = absRemaining % 60;

            String timeStr = (hours > 0)
                    ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
                    : String.format("%02d:%02d", minutes, seconds);

            // If remaining <= 0, the prevention period is over, and the boss is ELIGIBLE/ACTIVE
            if (remaining <= 0)
            {
                // Display ONLY the time since eligibility began (negative seconds)
                return String.format("+%s", timeStr);
            }

            // Display countdown to eligibility
            return timeStr;
        }

        /**
         * Final simplified skull logic: 45-15m = White, 15-0m = Red, <=0m = Panel Skull (Eligible/Active), No Data = White.
         */
        public BufferedImage getSkullIcon(MaledictusPlugin plugin)
        {
            // Worlds with 'No Data' use the white skull
            if (getNextSpawn() == Instant.MIN)
            {
                return plugin.getSkullWhite();
            }

            long remaining = secondsLeft();

            // 1. Eligible/Active (remaining <= 0) - This is the "cyan skull" state
            if (remaining <= 0)
            {
                return plugin.getSkullPanel();
            }

            final long TIME_RED_THRESHOLD_SECS = MaledictusPlugin.TIME_RED_THRESHOLD_SECS;

            // 2. 0 to 15 mins left in prevention period (Red Skull)
            if (remaining <= TIME_RED_THRESHOLD_SECS)
            {
                return plugin.getSkullRed();
            }

            // 3. 15 mins to 45 mins left (White Skull)
            return plugin.getSkullWhite();
        }
    }

    // --- Public Getters/Setters ---

    /**
     * Gets the timer for a world. If the world is the local player's current world and
     * is not tracked, a temporary 'No Data' timer is created and returned on the fly
     * to support the overlay and panel showing status for the current world.
     */
    public WorldTimer getWorldTimer(int worldId)
    {
        WorldTimer timer = worldTimers.get(worldId);

        // If the current world is not in the tracked list (e.g. F2P or untracked member world)
        // and we are requesting the timer for the local player's world, provide a default 'No Data' timer.
        if (timer == null && client.getWorld() == worldId)
        {
            return new WorldTimer(worldId, Instant.MIN);
        }

        return timer;
    }

    /**
     * Returns a list of all world timers.
     */
    public List<WorldTimer> getAllWorldTimers()
    {
        return new ArrayList<>(worldTimers.values());
    }

    public com.osrspluginz.maledictus.MaledictusConfig getConfig() { return config; }
    public Client getClient() { return client; }
    public BufferedImage getSkullWhite() { return skullWhite; }
    public BufferedImage getSkullRed() { return skullRed; }
    public BufferedImage getSkullPanel() { return skullPanel; }

    public void setOverlayConfig(boolean selected)
    {
        configManager.setConfiguration(
                com.osrspluginz.maledictus.MaledictusConfig.class.getAnnotation(ConfigGroup.class).value(),
                "showOverlay",
                selected
        );
    }

    // --- WORLD HOPPING LOGIC (Merged for thread safety) ---

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        handleHop();
    }

    // Public method called by the MaledictusTimerRow (Swing Thread)
    public void hopTo(int worldId)
    {
        // All checks and the hop execution must run on the Client Thread
        clientThread.invoke(() -> {

            if (client.getWorld() == worldId)
            {
                sendConsoleMessage("You are already on World " + worldId);
                return;
            }

            if (client.getGameState() != GameState.LOGGED_IN && client.getGameState() != GameState.LOGIN_SCREEN)
            {
                sendConsoleMessage("Cannot quick-hop while not logged in or at login screen.");
                return;
            }

            // Check 1: Config is enabled
            if (!config.isWorldHopperEnabled())
            {
                sendConsoleMessage("World hopping is disabled in the plugin configuration.");
                return;
            }

            WorldResult worldResult = worldService.getWorlds();
            // Check 2: World list is successfully fetched
            if (worldResult == null)
            {
                sendConsoleMessage("Failed to fetch world list from RuneLite API. Cannot hop.");
                return;
            }

            World world = worldResult.findWorld(worldId);
            // Check 3: Target world exists in the list
            if (world == null)
            {
                sendConsoleMessage("World ID " + worldId + " not found in the fetched world list. Cannot hop.");
                return;
            }

            final net.runelite.api.World rsWorld = client.createWorld();
            rsWorld.setActivity(world.getActivity());
            rsWorld.setAddress(world.getAddress());
            rsWorld.setId(world.getId());
            rsWorld.setPlayerCount(world.getPlayers());
            rsWorld.setLocation(world.getLocation());
            rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));

            sendConsoleMessage("Quick-hopping to World " + world.getId() + "...");

            if (client.getGameState() == GameState.LOGIN_SCREEN)
            {
                client.changeWorld(rsWorld);
                return;
            }

            // Only set the target world if all checks pass
            quickHopTargetWorld = rsWorld;
            displaySwitcherAttempts = 0;
        });
    }

    private void handleHop()
    {
        if (quickHopTargetWorld == null)
        {
            return;
        }

        if (client.getWidget(WidgetInfo.WORLD_SWITCHER_LIST) == null)
        {
            client.openWorldHopper();

            if (++displaySwitcherAttempts >= DISPLAY_SWITCHER_MAX_ATTEMPTS)
            {
                sendConsoleMessage("Failed to quick-hop after " + displaySwitcherAttempts + " attempts. Aborting hop target. (Game likely blocking the hop)");

                resetQuickHopper();
            }
        }
        else
        {
            // World switcher is open, execute the hop
            client.hopToWorld(quickHopTargetWorld);
            resetQuickHopper();
        }
    }

    private void resetQuickHopper()
    {
        quickHopTargetWorld = null;
        displaySwitcherAttempts = 0;
    }
}