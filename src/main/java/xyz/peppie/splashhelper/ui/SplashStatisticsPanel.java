package xyz.peppie.splashhelper.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import xyz.peppie.splashhelper.model.SplashSession;
import xyz.peppie.splashhelper.model.OverallStatField;
import xyz.peppie.splashhelper.model.SessionStatField;
import xyz.peppie.splashhelper.SplashHelperConfig;
import xyz.peppie.splashhelper.SplashHelperPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import xyz.peppie.splashhelper.service.SessionManager;

@Slf4j
public class SplashStatisticsPanel extends PluginPanel implements SplashSessionHistoryBox.SessionDeleteListener
{
    private final SplashHelperPlugin plugin;
    private final SplashHelperConfig config;
    private final ItemManager itemManager;
    private final SessionManager sessionManager;

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

    // Current session
    private final JPanel currentPanel = new JPanel();
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

    // Supply tracker
    private SplashSupplyTrackerBox supplyBox;

    // Session history
    private final JPanel historyContainer = new JPanel();
    private final List<SplashSessionHistoryBox> historyBoxes = new ArrayList<>();
    private int lastHistorySize = 0;

    public SplashStatisticsPanel(SplashHelperPlugin plugin, SplashHelperConfig config, ItemManager itemManager, SessionManager sessionManager)
    {
        super(true);
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        this.sessionManager = sessionManager;

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

        // Export buttons
        JPanel exportButtonPanel = new JPanel();
        exportButtonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
        exportButtonPanel.setLayout(new BoxLayout(exportButtonPanel, BoxLayout.X_AXIS));

        JButton copyButton = new JButton("Copy");
        copyButton.setFont(FontManager.getRunescapeSmallFont());
        copyButton.setPreferredSize(new Dimension(50, 20));
        copyButton.setToolTipText("Copy session data to clipboard as JSON");
        copyButton.addActionListener(e -> copySessionsToClipboard());
        exportButtonPanel.add(copyButton);

        exportButtonPanel.add(javax.swing.Box.createHorizontalStrut(4));

        JButton exportButton = new JButton("Export");
        exportButton.setFont(FontManager.getRunescapeSmallFont());
        exportButton.setPreferredSize(new Dimension(58, 20));
        exportButton.setToolTipText("Export session data to a JSON file");
        exportButton.addActionListener(e -> exportSessionsToFile());
        exportButtonPanel.add(exportButton);

        overallTitlePanel.add(exportButtonPanel, BorderLayout.EAST);
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

        return overallContainer;
    }

    private JPanel buildCurrentSessionPanel(ItemManager itemManager)
    {
        JPanel currentContainer = new JPanel();
        currentContainer.setLayout(new BoxLayout(currentContainer, BoxLayout.Y_AXIS));
        currentContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        currentContainer.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR.darker()));

        JPanel currentTitlePanel = new JPanel(new BorderLayout());
        currentTitlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
        currentTitlePanel.setBorder(new EmptyBorder(7, 10, 7, 10));
        JLabel currentTitle = new JLabel("Current Session");
        currentTitle.setForeground(Color.ORANGE);
        currentTitle.setFont(FontManager.getRunescapeBoldFont());
        currentTitlePanel.add(currentTitle, BorderLayout.WEST);
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

            if (hasActiveSession && currentSession != null)
            {
                totalSeconds += currentSession.getSessionDurationSeconds();
                totalCasts += currentSession.getSpellsCast();
                totalXp += currentSession.getMagicXpGained();
                totalCost += plugin.getCachedRuneCost();  // Current session cost from cache
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
            double hoursRemaining = remaining / 1200.0;
            overallHoursRemainingLabel.setText(String.format("%.1fh", hoursRemaining));
            overallHoursRemainingLabel.setForeground(Color.CYAN);

            // Calculate potential XP (using current session spell XP if available)
            int potentialXp = 0;
            if (hasActiveSession && currentSession != null && currentSession.getSpell() != null)
            {
                potentialXp = (int) (remaining * currentSession.getSpell().getBaseXp());
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

            // Update current player count
            int currentPlayerCount = plugin.countNearbyPlayers(config.playerCountRadius());
            overallPlayerCountLabel.setText(String.valueOf(currentPlayerCount));
            overallPlayerCountLabel.setForeground(Color.CYAN);

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
            if (hasActiveSession && currentSession != null)
            {
                currentPanel.setVisible(true);
                supplyBox.setVisible(true);

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
            }
            else
            {
                // No active session - reset current session to zeroed values
                currentPanel.setVisible(true);
                supplyBox.setVisible(true);

                playerLabel.setText("-");
                spellLabel.setText("-");
                spellLabel.setForeground(Color.GRAY);
                worldLabel.setText("-");
                stickyLabel.setText("-");
                stickyLabel.setForeground(Color.GRAY);
                timeLabel.setText("0:00");
                timeLabel.setForeground(Color.GRAY);
                castsLabel.setText("0");
                xpGainedLabel.setText("0");
                xpHourLabel.setText("0/hr");
                xpHourLabel.setForeground(Color.GRAY);
                runeCostLabel.setText("0 gp");
                runeCostLabel.setForeground(Color.GRAY);
                playerCountLabel.setText("0");
                playerCountLabel.setForeground(Color.GRAY);
                highestPlayerCountLabel.setText("0");
                highestPlayerCountLabel.setForeground(Color.GRAY);

                // Show empty runes row
                supplyBox.buildItems(new java.util.ArrayList<>(), 0);
            }

            // Update session history
            updateHistoryDisplay(history);

            revalidate();
            repaint();
        });
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
