package xyz.peppie.splashhelper;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;

public class SplashStatisticsPanel extends PluginPanel
{
    private static final String ERROR_PANEL = "ERROR";
    private static final String DATA_PANEL = "DATA";

    private final SplashHelperPlugin plugin;
    private final SplashHelperConfig config;
    private final ItemManager itemManager;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel container = new JPanel(cardLayout);
    private final PluginErrorPanel errorPanel = new PluginErrorPanel();

    // Overall statistics
    private final JPanel overallPanel = new JPanel();
    private final JLabel overallSessionsLabel = new JLabel("0");
    private final JLabel overallTimeLabel = new JLabel("0:00");
    private final JLabel overallCastsLabel = new JLabel("0");
    private final JLabel overallXpLabel = new JLabel("0");
    private final JLabel overallRemainingLabel = new JLabel("0");
    private final JLabel overallStatusLabel = new JLabel("-");

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

    // Supply tracker
    private SplashSupplyTrackerBox supplyBox;

    // Session history
    private final JPanel historyContainer = new JPanel();
    private final List<SplashSessionHistoryBox> historyBoxes = new ArrayList<>();
    private int lastHistorySize = 0;

    public SplashStatisticsPanel(SplashHelperPlugin plugin, SplashHelperConfig config, ItemManager itemManager)
    {
        super(false);
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Error panel
        errorPanel.setContent("Splash Statistics", "Start splashing to see statistics.");

        // Data panel with stats
        JPanel dataPanel = new JPanel();
        dataPanel.setLayout(new BoxLayout(dataPanel, BoxLayout.Y_AXIS));
        dataPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        dataPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        dataPanel.add(buildOverallPanel());
        dataPanel.add(javax.swing.Box.createVerticalStrut(10));
        dataPanel.add(buildCurrentSessionPanel(itemManager));
        dataPanel.add(javax.swing.Box.createVerticalStrut(10));
        dataPanel.add(buildHistoryPanel());

        // Add panels to card layout
        container.add(errorPanel, ERROR_PANEL);
        container.add(dataPanel, DATA_PANEL);

        add(container, BorderLayout.NORTH);

        // Show error panel initially
        cardLayout.show(container, ERROR_PANEL);
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

        addStatRow(overallPanel, "Sessions:", overallSessionsLabel);
        addStatRow(overallPanel, "Total Time:", overallTimeLabel);
        addStatRow(overallPanel, "Total Casts:", overallCastsLabel);
        addStatRow(overallPanel, "Total XP:", overallXpLabel);
        addStatRow(overallPanel, "Remaining:", overallRemainingLabel);
        addStatRow(overallPanel, "Status:", overallStatusLabel);

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

        addStatRow(currentPanel, "Player:", playerLabel);
        addStatRow(currentPanel, "Spell:", spellLabel);
        addStatRow(currentPanel, "World:", worldLabel);
        addStatRow(currentPanel, "Knight:", stickyLabel);
        addStatRow(currentPanel, "Time:", timeLabel);
        addStatRow(currentPanel, "Casts:", castsLabel);
        addStatRow(currentPanel, "XP Gained:", xpGainedLabel);
        addStatRow(currentPanel, "XP/Hour:", xpHourLabel);

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

        // New sessions added to history
        for (int i = lastHistorySize; i < history.size(); i++)
        {
            SplashSession session = history.get(i);
            // Only the latest session should be expanded, others collapsed
            boolean isLatest = (i == history.size() - 1);
            
            // Collapse all existing boxes when a new one is added
            for (SplashSessionHistoryBox box : historyBoxes)
            {
                box.collapse();
            }
            
            // Get rune usage from spell
            List<int[]> runeUsage = getRuneUsageFromSpell(session.getSpell());
            
            SplashSessionHistoryBox historyBox = new SplashSessionHistoryBox(session, !isLatest, itemManager, runeUsage);
            historyBoxes.add(0, historyBox); // Add to front of list
            
            // Add new session at the top (index 0)
            historyContainer.add(historyBox, 0);
            
            // Add spacer after the new box (which is now at index 0)
            if (historyContainer.getComponentCount() > 1)
            {
                historyContainer.add(javax.swing.Box.createVerticalStrut(10), 1);
            }
        }

        lastHistorySize = history.size();
        historyContainer.revalidate();
        historyContainer.repaint();
    }

    private List<int[]> getRuneUsageFromSpell(SplashSpell spell)
    {
        List<int[]> runeUsage = new ArrayList<>();
        if (spell != null)
        {
            for (RuneCost cost : spell.getRuneCosts())
            {
                runeUsage.add(new int[]{cost.getItemId(), cost.getAmount()});
            }
        }
        return runeUsage;
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
                cardLayout.show(container, ERROR_PANEL);
                return;
            }

            // Show data panel
            cardLayout.show(container, DATA_PANEL);

            // Calculate totals
            int totalSessions = history.size() + (hasActiveSession ? 1 : 0);
            long totalSeconds = history.stream().mapToLong(SplashSession::getSessionDurationSeconds).sum();
            int totalCasts = history.stream().mapToInt(SplashSession::getSpellsCast).sum();
            int totalXp = history.stream().mapToInt(SplashSession::getMagicXpGained).sum();

            if (hasActiveSession)
            {
                totalSeconds += currentSession.getSessionDurationSeconds();
                totalCasts += currentSession.getSpellsCast();
                totalXp += currentSession.getMagicXpGained();
            }

            // Update overall stats
            overallSessionsLabel.setText(String.valueOf(totalSessions));
            overallTimeLabel.setText(formatDuration(totalSeconds));
            overallCastsLabel.setText(formatNumber(totalCasts));
            overallXpLabel.setText(formatNumber(totalXp));

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
            if (hasActiveSession)
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
                    stickyLabel.setText("STICKY");
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
}
