package xyz.peppie.splashhelper.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.callback.ClientThread;
import xyz.peppie.splashhelper.model.SplashSession;
import xyz.peppie.splashhelper.model.SplashSpell;
import xyz.peppie.splashhelper.model.OverallStatField;
import xyz.peppie.splashhelper.model.SessionStatField;
import xyz.peppie.splashhelper.SplashHelperConfig;
import xyz.peppie.splashhelper.SplashHelperPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import xyz.peppie.splashhelper.service.SessionManager;
import xyz.peppie.splashhelper.service.SplashWebSocketClient;

@Slf4j
public class SplashStatisticsPanel extends PluginPanel implements SplashSessionHistoryBox.SessionDeleteListener
{
    private final SplashHelperPlugin plugin;
    private final SplashHelperConfig config;
    private final ItemManager itemManager;
    private final SessionManager sessionManager;
    private final SplashWebSocketClient webSocketClient;
    private final ClientThread clientThread;

    // Main layout container
    private final JPanel layoutPanel = new JPanel();
    private final PluginErrorPanel errorPanel = new PluginErrorPanel();

    // Overall statistics
    private final JPanel overallPanel = new JPanel();
    private final JLabel overallSessionsLabel = new JLabel("0");
    private final JLabel overallTimeLabel = new JLabel("0:00");
    private final JLabel overallCastsLabel = new JLabel("0");
    private final JLabel overallXpLabel = new JLabel("0");
    private final JLabel overallCostLabel = new JLabel("0 gp");
    private final JLabel overallRemainingLabel = new JLabel("0");
    private final JLabel overallStatusLabel = new JLabel("-");
    private final JLabel overallPlayerCountLabel = new JLabel("0");
    private final JLabel overallHoursRemainingLabel = new JLabel("0.0h");
    private final JLabel overallPotentialXpLabel = new JLabel("0");
    private final JLabel overallGpPerHourLabel = new JLabel("0 gp/h");
    private final JLabel overallHighestPlayerCountLabel = new JLabel("0");
    private final JLabel overallDeathsLabel = new JLabel("0");

    // Current session
    private final JPanel currentPanel = new JPanel();
    private JPanel currentContainer;
    private JLabel currentTitleLabel;
    private final JLabel resumeCountdownLabel = new JLabel();
    private final JLabel playerLabel = new JLabel("-");
    private final JLabel spellLabel = new JLabel("-");
    private final JLabel worldLabel = new JLabel("-");
    private final JLabel stickyLabel = new JLabel("-");
    private final JLabel timeLabel = new JLabel("0:00");
    private final JLabel castsLabel = new JLabel("0");
    private final JLabel xpGainedLabel = new JLabel("0");
    private final JLabel xpHourLabel = new JLabel("0");
    private final JLabel runeCostLabel = new JLabel("0 gp");
    private final JLabel playerCountLabel = new JLabel("0");
    private final JLabel highestPlayerCountLabel = new JLabel("0");
    private final JLabel deathsLabel = new JLabel("0");

    // Supply tracker
    private SplashSupplyTrackerBox supplyBox;

    // Session history
    private final JPanel historyContainer = new JPanel();
    private final List<SplashSessionHistoryBox> historyBoxes = new ArrayList<>();
    private int lastHistorySize = 0;

    // UI refresh timers (stopped via shutdown() when the plugin is disabled)
    private javax.swing.Timer syncRefreshTimer;
    private final javax.swing.Timer resumeCountdownTimer;

    public SplashStatisticsPanel(SplashHelperPlugin plugin, SplashHelperConfig config, ItemManager itemManager, SessionManager sessionManager, SplashWebSocketClient webSocketClient, ClientThread clientThread)
    {
        super(true);
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        this.sessionManager = sessionManager;
        this.webSocketClient = webSocketClient;
        this.clientThread = clientThread;

        setBorder(new EmptyBorder(6, 6, 6, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        // Create layout panel for wrapping (like LootTrackerPanel)
        layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
        add(layoutPanel, BorderLayout.NORTH);

        // Build initial panels
        buildPanels();

        // Add error panel (shown when no data)
        errorPanel.setContent("Splash Statistics", "Start splashing to see statistics.");
        add(errorPanel, BorderLayout.CENTER);

        // Keep the resume-window countdown live even when no game ticks arrive
        // (e.g. the player logged out — exactly when the resume window matters)
        resumeCountdownTimer = new javax.swing.Timer(1000, e -> updateCurrentSessionState(plugin.getCurrentSession() != null && plugin.getCurrentSession().isActive()));
        resumeCountdownTimer.start();
    }

    /**
     * Stop the panel's Swing timers. Called when the plugin shuts down.
     */
    public void shutdown()
    {
        resumeCountdownTimer.stop();
        if (syncRefreshTimer != null)
        {
            syncRefreshTimer.stop();
        }
    }

    /**
     * Rebuild all panels to reflect current config settings.
     * Called when config changes to update visibility of fields.
     */
    public void rebuildPanels()
    {
        SwingUtilities.invokeLater(() ->
        {
            // Clear existing panels
            layoutPanel.removeAll();
            overallPanel.removeAll();
            currentPanel.removeAll();
            historyContainer.removeAll();
            historyBoxes.clear();
            lastHistorySize = 0;
            currentContainer = null; // reassigned by buildPanels when the section is enabled

            // Rebuild panels with current config
            buildPanels();

            // Restore history if session history panel is enabled
            if (config.showSessionHistory())
            {
                List<SplashSession> history = plugin.getSessionHistory();
                if (history != null && !history.isEmpty())
                {
                    updateHistoryDisplay(history);
                }
            }

            // Refresh display
            revalidate();
            repaint();
        });
    }

    private void buildPanels()
    {
        // Action bar with export buttons at top
        layoutPanel.add(buildActionBar());
        layoutPanel.add(javax.swing.Box.createVerticalStrut(10));

        // Server sync section (setup link button)
        if (config.enableServerSync())
        {
            layoutPanel.add(buildServerSyncPanel());
            layoutPanel.add(javax.swing.Box.createVerticalStrut(10));
        }

        // Only add panels if they are enabled in config
        if (config.showOverallStats())
        {
            layoutPanel.add(buildOverallPanel());
            layoutPanel.add(javax.swing.Box.createVerticalStrut(10));
        }
        
        if (config.showCurrentSession())
        {
            layoutPanel.add(buildCurrentSessionPanel(itemManager));
            layoutPanel.add(javax.swing.Box.createVerticalStrut(10));
        }
        
        if (config.showSessionHistory())
        {
            layoutPanel.add(buildHistoryPanel());
        }
    }

    private JPanel buildActionBar()
    {
        JPanel actionBar = new JPanel(new BorderLayout());
        actionBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        actionBar.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR.darker()));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
        titlePanel.setBorder(new EmptyBorder(5, 10, 5, 10));

        JLabel titleLabel = new JLabel("Splash Statistics");
        titleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        titlePanel.add(titleLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));

        // Clipboard icon button
        JLabel copyButton = createIconButton(createClipboardIcon(), "Copy session data to clipboard as JSON");
        copyButton.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                copySessionsToClipboard();
            }
        });
        buttonPanel.add(copyButton);

        buttonPanel.add(javax.swing.Box.createHorizontalStrut(8));

        // Download icon button
        JLabel exportButton = createIconButton(createDownloadIcon(), "Export session data to a JSON file");
        exportButton.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                exportSessionsToFile();
            }
        });
        buttonPanel.add(exportButton);

        titlePanel.add(buttonPanel, BorderLayout.EAST);
        actionBar.add(titlePanel, BorderLayout.CENTER);
        return actionBar;
    }

    private JPanel buildServerSyncPanel()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR.darker()));

        JPanel inner = new JPanel(new BorderLayout());
        inner.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        inner.setBorder(new EmptyBorder(8, 10, 8, 10));

        JLabel sectionLabel = new JLabel("Server Sync");
        sectionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        sectionLabel.setFont(FontManager.getRunescapeSmallFont());
        inner.add(sectionLabel, BorderLayout.WEST);

        JPanel rightPanel = new JPanel(new BorderLayout(5, 0));
        rightPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel statusLabel = new JLabel();
        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        rightPanel.add(statusLabel, BorderLayout.WEST);

        javax.swing.JButton actionButton = new javax.swing.JButton();
        actionButton.setFocusPainted(false);
        actionButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        rightPanel.add(actionButton, BorderLayout.EAST);

        // Initial state
        updateSyncPanelState(statusLabel, actionButton);

        actionButton.addActionListener(e -> onSetupLinkButtonClicked(statusLabel, actionButton));

        // Periodically update status label (replace any timer from a previous rebuild)
        if (syncRefreshTimer != null)
        {
            syncRefreshTimer.stop();
        }
        syncRefreshTimer = new javax.swing.Timer(2000, e ->
            SwingUtilities.invokeLater(() -> updateSyncPanelState(statusLabel, actionButton)));
        syncRefreshTimer.start();

        inner.add(rightPanel, BorderLayout.EAST);
        panel.add(inner, BorderLayout.CENTER);
        return panel;
    }

    private void updateSyncPanelState(JLabel statusLabel, javax.swing.JButton button)
    {
        if (webSocketClient.isAuthenticated())
        {
            statusLabel.setText("Connected");
            statusLabel.setForeground(new java.awt.Color(0x43B581)); // green

            // Do not show setup prompts while actively authenticated; only show
            // a button when we already have a valid setup link to copy.
            if (webSocketClient.isSetupLinkValid())
            {
                button.setText("Show Link");
                button.setToolTipText("Copy setup link to clipboard");
                button.setVisible(true);
            }
            else
            {
                button.setVisible(false);
            }
        }
        else if (webSocketClient.isConnected())
        {
            statusLabel.setText("Connecting...");
            statusLabel.setForeground(new java.awt.Color(0xFAA61A)); // yellow
            button.setVisible(false);
        }
        else
        {
            statusLabel.setText("Disconnected");
            statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            button.setText("Generate Link");
            button.setToolTipText("Connect and generate a setup link");
            button.setVisible(true);
        }
    }

    private void onSetupLinkButtonClicked(JLabel statusLabel, javax.swing.JButton button)
    {
        if (webSocketClient.isSetupLinkValid())
        {
            // Copy to clipboard and show popup
            String link = webSocketClient.getSetupLink();
            StringSelection selection = new StringSelection(link);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            JOptionPane.showMessageDialog(this,
                "Setup link copied to clipboard!\n\n" + link,
                "Setup Link", JOptionPane.INFORMATION_MESSAGE);
        }
        else
        {
            // Request a new link via re-auth (also connects if disconnected)
            webSocketClient.requestSetupLink();
            button.setText("Requesting...");
            button.setEnabled(false);

            // Poll up to 5 times (1 second apart) for the auth response
            final int maxAttempts = 5;
            final int[] attempt = {0};
            javax.swing.Timer pollTimer = new javax.swing.Timer(1000, null);
            pollTimer.addActionListener(ev -> {
                attempt[0]++;
                Boolean authResult = webSocketClient.getLastAuthSetupRequired();

                if (webSocketClient.isSetupLinkValid())
                {
                    // Link received — copy to clipboard
                    pollTimer.stop();
                    SwingUtilities.invokeLater(() -> {
                        button.setEnabled(true);
                        updateSyncPanelState(statusLabel, button);
                        String link = webSocketClient.getSetupLink();
                        StringSelection sel = new StringSelection(link);
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
                        JOptionPane.showMessageDialog(this,
                            "Setup link copied to clipboard!\n\n" + link,
                            "Setup Link", JOptionPane.INFORMATION_MESSAGE);
                    });
                }
                else if (authResult != null && !authResult)
                {
                    // Auth succeeded but account is already set up
                    pollTimer.stop();
                    SwingUtilities.invokeLater(() -> {
                        button.setEnabled(true);
                        updateSyncPanelState(statusLabel, button);
                        JOptionPane.showMessageDialog(this,
                            "Your account is already set up!\n"
                            + "You can log in on the web dashboard directly.",
                            "Setup Link", JOptionPane.INFORMATION_MESSAGE);
                    });
                }
                else if (attempt[0] >= maxAttempts)
                {
                    // Timed out waiting for a response
                    pollTimer.stop();
                    SwingUtilities.invokeLater(() -> {
                        button.setEnabled(true);
                        updateSyncPanelState(statusLabel, button);
                        JOptionPane.showMessageDialog(this,
                            "Could not generate a setup link.\n"
                            + "Make sure Server Sync is enabled, you are logged in,\n"
                            + "and the server is running.",
                            "Setup Link", JOptionPane.WARNING_MESSAGE);
                    });
                }
            });
            pollTimer.start();
        }
    }

    private JLabel createIconButton(ImageIcon icon, String tooltip)
    {
        JLabel label = new JLabel(icon);
        label.setToolTipText(tooltip);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return label;
    }

    private ImageIcon createClipboardIcon()
    {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
        // Clipboard outline
        g.drawRoundRect(3, 2, 10, 12, 2, 2);
        // Clip at top
        g.fillRoundRect(5, 0, 6, 4, 2, 2);
        // Lines on clipboard
        g.drawLine(5, 7, 11, 7);
        g.drawLine(5, 10, 11, 10);
        g.dispose();
        return new ImageIcon(img);
    }

    private ImageIcon createDownloadIcon()
    {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
        // Down arrow shaft
        g.fillRect(6, 1, 4, 8);
        // Arrow head
        g.fillPolygon(new int[]{3, 8, 13}, new int[]{8, 13, 8}, 3);
        // Base line (tray)
        g.fillRect(2, 14, 12, 2);
        g.dispose();
        return new ImageIcon(img);
    }

    private JPanel buildOverallPanel()
    {
        JPanel overallContainer = new JPanel();
        overallContainer.setLayout(new BoxLayout(overallContainer, BoxLayout.Y_AXIS));
        overallContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        overallContainer.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR.darker()));

        JPanel overallTitlePanel = new JPanel(new BorderLayout());
        overallTitlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
        overallTitlePanel.setBorder(new EmptyBorder(7, 10, 7, 10));
        JLabel overallTitle = new JLabel("Overall Statistics");
        overallTitle.setForeground(Color.CYAN);
        overallTitle.setFont(FontManager.getRunescapeBoldFont());
        overallTitlePanel.add(overallTitle, BorderLayout.WEST);
        overallContainer.add(overallTitlePanel);

        overallPanel.setLayout(new GridLayout(0, 1, 0, 4));
        overallPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        overallPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        overallContainer.add(overallPanel);

        // Always show these fields (not configurable)
        addStatRow(overallPanel, "Sessions:", overallSessionsLabel);
        addStatRow(overallPanel, "Total Time:", overallTimeLabel);
        
        // Conditionally show configurable fields
        if (config.overallStatFields().contains(OverallStatField.TOTAL_CASTS))
            addStatRow(overallPanel, "Total Casts:", overallCastsLabel);
        if (config.overallStatFields().contains(OverallStatField.TOTAL_XP))
            addStatRow(overallPanel, "Total XP:", overallXpLabel);
        if (config.overallStatFields().contains(OverallStatField.TOTAL_COST))
            addStatRow(overallPanel, "Total Cost:", overallCostLabel);
        if (config.overallStatFields().contains(OverallStatField.REMAINING_CASTS))
            addStatRow(overallPanel, "Remaining:", overallRemainingLabel);
        if (config.overallStatFields().contains(OverallStatField.HOURS_REMAINING))
            addStatRow(overallPanel, "Hours Remaining:", overallHoursRemainingLabel);
        if (config.overallStatFields().contains(OverallStatField.POTENTIAL_XP))
            addStatRow(overallPanel, "Potential XP:", overallPotentialXpLabel);
        if (config.overallStatFields().contains(OverallStatField.GP_PER_HOUR))
            addStatRow(overallPanel, "GP/Hour:", overallGpPerHourLabel);
        
        // Always show status (not configurable)
        addStatRow(overallPanel, "Status:", overallStatusLabel);
        
        // Conditionally show player count fields
        if (config.overallStatFields().contains(OverallStatField.CURRENT_PLAYERS))
            addStatRow(overallPanel, "Current Players:", overallPlayerCountLabel);
        if (config.overallStatFields().contains(OverallStatField.HIGHEST_PLAYERS))
            addStatRow(overallPanel, "Highest Players:", overallHighestPlayerCountLabel);
        if (config.overallStatFields().contains(OverallStatField.TOTAL_DEATHS))
            addStatRow(overallPanel, "Total Deaths:", overallDeathsLabel);

        return overallContainer;
    }

    private JPanel buildCurrentSessionPanel(ItemManager itemManager)
    {
        currentContainer = new JPanel();
        currentContainer.setLayout(new BoxLayout(currentContainer, BoxLayout.Y_AXIS));
        currentContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        currentContainer.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR.darker()));

        JPanel currentTitlePanel = new JPanel(new BorderLayout());
        currentTitlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
        currentTitlePanel.setBorder(new EmptyBorder(7, 10, 7, 10));
        currentTitleLabel = new JLabel("Current Session");
        currentTitleLabel.setForeground(Color.ORANGE);
        currentTitleLabel.setFont(FontManager.getRunescapeBoldFont());
        currentTitlePanel.add(currentTitleLabel, BorderLayout.WEST);

        // Resume-window countdown, shown while a finished session can still resume
        resumeCountdownLabel.setForeground(Color.GRAY);
        resumeCountdownLabel.setFont(FontManager.getRunescapeSmallFont());
        resumeCountdownLabel.setVisible(false);
        currentTitlePanel.add(resumeCountdownLabel, BorderLayout.EAST);
        currentContainer.add(currentTitlePanel);

        currentPanel.setLayout(new GridLayout(0, 1, 0, 4));
        currentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        currentPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        currentContainer.add(currentPanel);

        // Always show these fields (not configurable)
        addStatRow(currentPanel, "Player:", playerLabel);
        addStatRow(currentPanel, "World:", worldLabel);
        addStatRow(currentPanel, "Knight:", stickyLabel);
        addStatRow(currentPanel, "Time:", timeLabel);
        
        // Conditionally show configurable fields
        if (config.currentSessionFields().contains(SessionStatField.SPELL))
            addStatRow(currentPanel, "Spell:", spellLabel);
        if (config.currentSessionFields().contains(SessionStatField.CASTS))
            addStatRow(currentPanel, "Casts:", castsLabel);
        if (config.currentSessionFields().contains(SessionStatField.XP_GAINED))
            addStatRow(currentPanel, "XP Gained:", xpGainedLabel);
        if (config.currentSessionFields().contains(SessionStatField.XP_PER_HOUR))
            addStatRow(currentPanel, "XP/Hour:", xpHourLabel);
        if (config.currentSessionFields().contains(SessionStatField.RUNE_COST))
            addStatRow(currentPanel, "Rune Cost:", runeCostLabel);
        if (config.currentSessionFields().contains(SessionStatField.NEARBY_PLAYERS))
            addStatRow(currentPanel, "Nearby Players:", playerCountLabel);
        if (config.currentSessionFields().contains(SessionStatField.HIGHEST_PLAYERS))
            addStatRow(currentPanel, "Highest Players:", highestPlayerCountLabel);
        if (config.currentSessionFields().contains(SessionStatField.PLAYER_DEATHS))
            addStatRow(currentPanel, "Deaths:", deathsLabel);

        supplyBox = new SplashSupplyTrackerBox(itemManager, "Runes Used");
        currentContainer.add(supplyBox);

        return currentContainer;
    }

    private JPanel buildHistoryPanel()
    {
        historyContainer.setLayout(new BoxLayout(historyContainer, BoxLayout.Y_AXIS));
        historyContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        return historyContainer;
    }

    private void updateHistoryDisplay(List<SplashSession> history)
    {
        if (history.size() == lastHistorySize)
        {
            return;
        }

        // Clear existing history boxes
        historyContainer.removeAll();
        historyBoxes.clear();

        // Add session boxes in reverse order (newest first)
        for (int i = history.size() - 1; i >= 0; i--)
        {
            SplashSession session = history.get(i);
            // Only the latest session should be expanded, others collapsed
            boolean isLatest = (i == history.size() - 1);
            
            SplashSessionHistoryBox historyBox = new SplashSessionHistoryBox(session, !isLatest, itemManager, config, sessionManager, this);
            historyBoxes.add(historyBox);
            
            // Add to container
            historyContainer.add(historyBox);
            
            // Add spacer between boxes
            if (i > 0)
            {
                historyContainer.add(javax.swing.Box.createVerticalStrut(10));
            }
        }

        lastHistorySize = history.size();
        historyContainer.revalidate();
        historyContainer.repaint();
    }

    private void addStatRow(JPanel panel, String labelText, JLabel valueLabel)
    {
        JPanel rowPanel = new JPanel(new BorderLayout());
        rowPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel label = new JLabel(labelText);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont());

        valueLabel.setForeground(Color.WHITE);
        valueLabel.setFont(FontManager.getRunescapeSmallFont());

        rowPanel.add(label, BorderLayout.WEST);
        rowPanel.add(valueLabel, BorderLayout.EAST);
        panel.add(rowPanel);
    }

    public void updatePanel()
    {
        if (!config.enableStatistics())
        {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            SplashSession currentSession = plugin.getCurrentSession();
            SplashSession displaySession = plugin.getDisplayableSession();
            java.util.List<SplashSession> history = plugin.getSessionHistory();

            boolean hasActiveSession = currentSession != null && currentSession.isActive();
            boolean hasDisplayableSession = displaySession != null;
            boolean hasHistory = !history.isEmpty();
            boolean hasData = hasActiveSession || hasDisplayableSession || hasHistory;

            if (!hasData)
            {
                errorPanel.setVisible(true);
                return;
            }

            // Show data panel
            layoutPanel.setVisible(true);
            errorPanel.setVisible(false);

            // Calculate totals
            int totalSessions = history.size() + (hasActiveSession ? 1 : 0);
            long totalSeconds = history.stream().mapToLong(SplashSession::getSessionDurationSeconds).sum();
            int totalCasts = history.stream().mapToInt(SplashSession::getSpellsCast).sum();
            int totalXp = history.stream().mapToInt(SplashSession::getMagicXpGained).sum();
            long totalCost = history.stream().mapToLong(SplashSession::getRuneCostGp).sum();
            int totalDeaths = history.stream().mapToInt(SplashSession::getPlayerDeaths).sum();

            if (hasActiveSession && currentSession != null)
            {
                totalSeconds += currentSession.getSessionDurationSeconds();
                totalCasts += currentSession.getSpellsCast();
                totalXp += currentSession.getMagicXpGained();
                totalCost += plugin.getCachedRuneCost();  // Current session cost from cache
                totalDeaths += currentSession.getPlayerDeaths();
            }

            // Update overall stats
            overallSessionsLabel.setText(String.valueOf(totalSessions));
            overallTimeLabel.setText(formatDuration(totalSeconds));
            overallCastsLabel.setText(formatNumber(totalCasts));
            overallXpLabel.setText(formatNumber(totalXp));
            overallCostLabel.setText(formatNumber((int) totalCost) + " gp");
            overallCostLabel.setForeground(Color.ORANGE);

            // Update remaining casts (using cached value from client thread)
            int remaining = plugin.getCachedRemainingCasts();
            overallRemainingLabel.setText(String.valueOf(remaining));
            if (remaining > 100)
            {
                overallRemainingLabel.setForeground(Color.GREEN);
            }
            else if (remaining > 20)
            {
                overallRemainingLabel.setForeground(Color.YELLOW);
            }
            else
            {
                overallRemainingLabel.setForeground(Color.RED);
            }

            // Calculate hours remaining (assuming 1 cast per 3 seconds = 1200 casts/hour)
            double hoursRemaining = (double) remaining / xyz.peppie.splashhelper.util.Constants.CASTS_PER_HOUR;
            overallHoursRemainingLabel.setText(String.format("%.1fh", hoursRemaining));
            overallHoursRemainingLabel.setForeground(Color.CYAN);

            // Calculate potential XP from the spell the remaining-casts cache
            // was computed with, falling back to the selected spell.
            int potentialXp = 0;
            SplashSpell potentialXpSpell = plugin.getCachedProjectionSpell();
            if (potentialXpSpell == null)
            {
                potentialXpSpell = config.selectedSpell();
            }
            if (potentialXpSpell != null)
            {
                potentialXp = (int) Math.round(remaining * potentialXpSpell.getBaseXp());
            }
            overallPotentialXpLabel.setText(formatNumber(potentialXp));
            overallPotentialXpLabel.setForeground(Color.CYAN);

            // Calculate GP per hour
            long gpPerHour = 0;
            if (hasActiveSession && currentSession != null)
            {
                long sessionDuration = currentSession.getSessionDurationSeconds();
                if (sessionDuration > 0)
                {
                    gpPerHour = (currentSession.getRuneCostGp() * 3600) / sessionDuration;
                }
            }
            overallGpPerHourLabel.setText(formatNumber((int) gpPerHour) + " gp/h");
            overallGpPerHourLabel.setForeground(Color.ORANGE);

            // Update highest player count across all sessions
            int highestPlayers = 0;
            for (SplashSession session : history)
            {
                if (session.getHighestPlayerCount() > highestPlayers)
                {
                    highestPlayers = session.getHighestPlayerCount();
                }
            }
            if (hasActiveSession && currentSession != null && currentSession.getHighestPlayerCount() > highestPlayers)
            {
                highestPlayers = currentSession.getHighestPlayerCount();
            }
            overallHighestPlayerCountLabel.setText(String.valueOf(highestPlayers));
            overallHighestPlayerCountLabel.setForeground(Color.CYAN);

            // Update total nearby player deaths across all sessions
            overallDeathsLabel.setText(formatNumber(totalDeaths));
            overallDeathsLabel.setForeground(Color.RED);

            // Update current player count (must be called on client thread)
            clientThread.invoke(() -> {
                int currentPlayerCount = plugin.countNearbyPlayers(config.playerCountRadius());
                SwingUtilities.invokeLater(() -> {
                    overallPlayerCountLabel.setText(String.valueOf(currentPlayerCount));
                    overallPlayerCountLabel.setForeground(Color.CYAN);
                });
            });

            // Update status
            if (hasActiveSession)
            {
                if (plugin.isHasEscaped())
                {
                    overallStatusLabel.setText("ESCAPED!");
                    overallStatusLabel.setForeground(Color.RED);
                }
                else
                {
                    overallStatusLabel.setText("Splashing");
                    overallStatusLabel.setForeground(Color.GREEN);
                }
            }
            else
            {
                overallStatusLabel.setText("Idle");
                overallStatusLabel.setForeground(Color.GRAY);
            }

            // Update current session display
            updateCurrentSessionState(hasActiveSession);

            if (hasActiveSession && currentSession != null)
            {
                playerLabel.setText(currentSession.getPlayerName() != null ? currentSession.getPlayerName() : "-");

                if (currentSession.getSpell() != null)
                {
                    spellLabel.setText(currentSession.getSpell().getName());
                    spellLabel.setForeground(Color.YELLOW);
                }
                else
                {
                    spellLabel.setText("Unknown");
                    spellLabel.setForeground(Color.GRAY);
                }

                worldLabel.setText(String.valueOf(currentSession.getWorld()));

                if (currentSession.isStickyKnight())
                {
                    stickyLabel.setText("Sticky");
                    stickyLabel.setForeground(Color.GREEN);
                }
                else
                {
                    stickyLabel.setText("Normal");
                    stickyLabel.setForeground(Color.WHITE);
                }

                timeLabel.setText(formatDuration(currentSession.getSessionDurationSeconds()));
                timeLabel.setForeground(Color.GREEN);

                castsLabel.setText(String.valueOf(currentSession.getSpellsCast()));

                xpGainedLabel.setText(formatNumber(currentSession.getMagicXpGained()));
                xpHourLabel.setText(formatNumber((int) currentSession.getXpPerHour()) + "/hr");
                xpHourLabel.setForeground(Color.CYAN);

                // Update supply box with actual rune usage (combo runes, excludes infinite)
                java.util.List<int[]> actualRuneUsage = plugin.getCachedActualRuneUsage();
                supplyBox.buildItems(actualRuneUsage, currentSession.getSpellsCast());

                // Use cached rune cost (calculated on client thread in plugin)
                long sessionCost = plugin.getCachedRuneCost();
                runeCostLabel.setText(formatNumber((int) sessionCost) + " gp");
                runeCostLabel.setForeground(Color.ORANGE);

                // Update nearby player count
                double avgPlayerCount = currentSession.getAveragePlayerCount();
                playerCountLabel.setText(String.format("%.1f avg", avgPlayerCount));
                playerCountLabel.setForeground(Color.CYAN);

                // Update highest player count
                highestPlayerCountLabel.setText(String.valueOf(currentSession.getHighestPlayerCount()));
                highestPlayerCountLabel.setForeground(Color.CYAN);

                // Update nearby player death count
                deathsLabel.setText(String.valueOf(currentSession.getPlayerDeaths()));
                deathsLabel.setForeground(Color.RED);
            }
            // When inactive the labels keep the last session's data — it is
            // either hidden entirely or collapsed behind the resume countdown,
            // and re-expands with correct values if the session resumes.

            // Update session history
            updateHistoryDisplay(history);

            revalidate();
            repaint();
        });
    }

    /**
     * Show the Current Session panel in one of three states:
     * active session → fully expanded; finished but still inside the resume
     * window → collapsed to a grayed title with a live countdown (data kept
     * underneath so a resume re-expands seamlessly); finalized → hidden.
     */
    private void updateCurrentSessionState(boolean hasActiveSession)
    {
        if (currentContainer == null)
        {
            return;
        }

        if (hasActiveSession)
        {
            currentContainer.setVisible(true);
            currentTitleLabel.setForeground(Color.ORANGE);
            resumeCountdownLabel.setVisible(false);
            currentPanel.setVisible(true);
            supplyBox.setVisible(true);
        }
        else if (sessionManager.hasResumableSession())
        {
            long remainingMs = sessionManager.getResumableSessionRemainingMs();
            long remainingSeconds = (remainingMs + 999) / 1000;
            currentContainer.setVisible(true);
            currentTitleLabel.setForeground(Color.GRAY);
            resumeCountdownLabel.setText(String.format("resumes %d:%02d", remainingSeconds / 60, remainingSeconds % 60));
            resumeCountdownLabel.setVisible(true);
            currentPanel.setVisible(false);
            supplyBox.setVisible(false);
        }
        else
        {
            currentContainer.setVisible(false);
        }

        currentContainer.revalidate();
        currentContainer.repaint();
    }

    private String formatDuration(long seconds)
    {
        long minutes = seconds / 60;
        long hours = minutes / 60;
        if (hours > 0)
        {
            return String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60);
        }
        return String.format("%d:%02d", minutes, seconds % 60);
    }

    private String formatNumber(int number)
    {
        if (number >= 1000000)
        {
            return String.format("%.1fM", number / 1000000.0);
        }
        else if (number >= 1000)
        {
            return String.format("%.1fK", number / 1000.0);
        }
        return String.valueOf(number);
    }

    private void copySessionsToClipboard()
    {
        try
        {
            String json = sessionManager.exportSessionsAsJson();
            StringSelection selection = new StringSelection(json);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            JOptionPane.showMessageDialog(this, "Session data copied to clipboard!", "Export", JOptionPane.INFORMATION_MESSAGE);
        }
        catch (Exception ex)
        {
            log.error("Failed to copy sessions to clipboard", ex);
            JOptionPane.showMessageDialog(this, "Failed to copy: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportSessionsToFile()
    {
        try
        {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export Splash Statistics");
            fileChooser.setFileFilter(new FileNameExtensionFilter("JSON Files (*.json)", "json"));
            fileChooser.setSelectedFile(new File("splash-statistics.json"));

            int result = fileChooser.showSaveDialog(this);
            if (result == JFileChooser.APPROVE_OPTION)
            {
                File file = fileChooser.getSelectedFile();
                if (!file.getName().endsWith(".json"))
                {
                    file = new File(file.getAbsolutePath() + ".json");
                }
                sessionManager.exportSessionsToFile(file);
                JOptionPane.showMessageDialog(this, "Sessions exported to:\n" + file.getAbsolutePath(), "Export", JOptionPane.INFORMATION_MESSAGE);
            }
        }
        catch (Exception ex)
        {
            log.error("Failed to export sessions to file", ex);
            JOptionPane.showMessageDialog(this, "Failed to export: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void onSessionDeleted(SplashSessionHistoryBox box)
    {
        // Remove the box from our tracking list
        historyBoxes.remove(box);
        
        // Find the index of the box in the container
        int boxIndex = -1;
        for (int i = 0; i < historyContainer.getComponentCount(); i++)
        {
            if (historyContainer.getComponent(i) == box)
            {
                boxIndex = i;
                break;
            }
        }
        
        if (boxIndex >= 0)
        {
            // Remove the box
            historyContainer.remove(box);
            
            // Remove the associated spacer (if it exists)
            // Spacers are added after boxes, so check if there's a spacer at boxIndex + 1
            if (boxIndex < historyContainer.getComponentCount())
            {
                java.awt.Component nextComponent = historyContainer.getComponent(boxIndex);
                if (nextComponent instanceof javax.swing.Box.Filler)
                {
                    historyContainer.remove(nextComponent);
                }
            }
            
            // Refresh the container
            historyContainer.revalidate();
            historyContainer.repaint();
        }
    }
}
