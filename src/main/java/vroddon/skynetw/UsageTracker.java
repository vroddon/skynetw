package vroddon.skynetw;

import java.awt.*;
import java.awt.TrayIcon.MessageType;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import javax.swing.*;

public class UsageTracker {
    private static final String DATA_DIRECTORY = "D:\\svn\\skynetw\\data";
    private static final int SAVE_INTERVAL_MINUTES = 5;
    private static final int SCREENSAVER_CHECK_SECONDS = 5;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private TrayIcon trayIcon;
    private SystemTray tray;
    private Timer timer;
    private Timer screensaverTimer;
    private LocalDateTime lastActivity;
    private Duration todayUsage = Duration.ZERO;
    private LocalDate currentDate;
    private boolean screensaverActive = false;
    private LocalDateTime screensaverStartTime = null;
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new UsageTracker().startTracking();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        });
    }
    
    public UsageTracker() {
        if (!SystemTray.isSupported()) {
            System.err.println("SystemTray is not supported");
            System.exit(1);
        }
        
        currentDate = LocalDate.now();
        lastActivity = LocalDateTime.now();
        
        // Create data directory if it doesn't exist
        File dataDir = new File(DATA_DIRECTORY);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        
        // Load today's usage if file exists
        loadTodayUsage();
        
        // Set up system tray icon
        setupSystemTray();
    }
    
    private void startTracking() {
        // Start screensaver detection
        setupScreensaverDetection();
        
        // Schedule regular updates
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> updateUsageTime());
            }
        }, 0, TimeUnit.MINUTES.toMillis(SAVE_INTERVAL_MINUTES));
        
        // Add shutdown hook to save usage on exit
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveUsageTime));
    }
    
    private void setupSystemTray() {
        try {
            tray = SystemTray.getSystemTray();
            Image image = Toolkit.getDefaultToolkit().createImage(getClass().getResource("/icon.png"));
            if (image == null) {
                // Fallback to a basic image
                image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            }
            
            PopupMenu popup = new PopupMenu();
            
            MenuItem showItem = new MenuItem("Show Stats");
            showItem.addActionListener(e -> showStatistics());
            popup.add(showItem);
            
            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> {
                saveUsageTime();
                System.exit(0);
            });
            popup.add(exitItem);
            
            trayIcon = new TrayIcon(image, "SkynetW Usage Tracker", popup);
            trayIcon.setImageAutoSize(true);
            tray.add(trayIcon);
            
            trayIcon.displayMessage("SkynetW Usage Tracker", 
                                   "Computer usage tracking started", 
                                   MessageType.INFO);
        } catch (Exception e) {
            System.err.println("Error setting up system tray: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void setupScreensaverDetection() {
        // Check screensaver status periodically using our ScreensaverDetector
        screensaverTimer = new Timer();
        screensaverTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkScreensaverStatus();
            }
        }, 0, TimeUnit.SECONDS.toMillis(SCREENSAVER_CHECK_SECONDS));
        
        // Add global mouse and keyboard listeners to detect user activity
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof MouseEvent || event instanceof KeyEvent) {
                // User activity detected
                handleUserActivity();
            }
        }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
    }
    
    private void checkScreensaverStatus() {
        boolean currentStatus = ScreenSaverDetector.isScreensaverActive();
        
        // Screensaver has just started
        if (currentStatus && !screensaverActive) {
            screensaverActive = true;
            screensaverStartTime = LocalDateTime.now();
            
            // Update current usage before pausing
            Duration sessionTime = Duration.between(lastActivity, screensaverStartTime);
            todayUsage = todayUsage.plus(sessionTime);
            
            System.out.println("Screensaver activated, pausing time tracking");
        } 
        // Screensaver has just ended
        else if (!currentStatus && screensaverActive) {
            handleUserActivity();
        }
    }
    
    private void handleUserActivity() {
        if (screensaverActive) {
            screensaverActive = false;
            System.out.println("Screensaver deactivated, resuming time tracking");
            
            // Reset the last activity time to now
            lastActivity = LocalDateTime.now();
            screensaverStartTime = null;
        }
    }
    
    private void updateUsageTime() {
        LocalDate now = LocalDate.now();
        
        // Check if day changed
        if (!now.equals(currentDate)) {
            saveUsageTime(); // Save previous day's data
            currentDate = now;
            todayUsage = Duration.ZERO;
            loadTodayUsage(); // Load today's data if exists
        }
        
        if (!screensaverActive) {
            LocalDateTime currentTime = LocalDateTime.now();
            Duration sessionTime = Duration.between(lastActivity, currentTime);
            
            // Only add time if it's reasonable (less than the check interval)
            if (sessionTime.toMinutes() <= SAVE_INTERVAL_MINUTES * 2) {
                todayUsage = todayUsage.plus(sessionTime);
            }
            
            lastActivity = currentTime;
        }
        
        saveUsageTime();
        updateTrayTooltip();
    }
    
    private void saveUsageTime() {
        String filename = DATA_DIRECTORY + File.separator + currentDate.format(DATE_FORMAT) + ".txt";
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            long totalMinutes = todayUsage.toMinutes();
            long hours = totalMinutes / 60;
            long minutes = totalMinutes % 60;
            
            writer.println("Date: " + currentDate.format(DATE_FORMAT));
            writer.println("Total usage time: " + String.format("%d hours, %d minutes", hours, minutes));
            writer.println("Last updated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            writer.println("Raw minutes: " + totalMinutes);
        } catch (IOException e) {
            System.err.println("Error saving usage data: " + e.getMessage());
            trayIcon.displayMessage("Error", "Failed to save usage data", MessageType.ERROR);
        }
    }
    
    private void loadTodayUsage() {
        String filename = DATA_DIRECTORY + File.separator + currentDate.format(DATE_FORMAT) + ".txt";
        Path file = Paths.get(filename);
        
        if (Files.exists(file)) {
            try {
                for (String line : Files.readAllLines(file)) {
                    if (line.startsWith("Raw minutes: ")) {
                        String minutesStr = line.substring("Raw minutes: ".length()).trim();
                        long minutes = Long.parseLong(minutesStr);
                        todayUsage = Duration.ofMinutes(minutes);
                        break;
                    }
                }
            } catch (IOException | NumberFormatException e) {
                System.err.println("Error loading today's usage: " + e.getMessage());
            }
        }
    }
    
    private void updateTrayTooltip() {
        long totalMinutes = todayUsage.toMinutes();
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        
        trayIcon.setToolTip(String.format("SkynetW Usage Tracker\nToday: %d hours, %d minutes", 
                            hours, minutes));
    }
    
    private void showStatistics() {
        long totalMinutes = todayUsage.toMinutes();
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        
        String screensaverStatus = screensaverActive ? "Active" : "Inactive";
        
        JOptionPane.showMessageDialog(null,
            String.format("Today's computer usage: %d hours, %d minutes\n" +
                         "Tracking since: %s\n" +
                         "Current screensaver status: %s\n" +
                         "Data saved every %d minutes to:\n%s",
                         hours, minutes, 
                         lastActivity.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                         screensaverStatus,
                         SAVE_INTERVAL_MINUTES,
                         DATA_DIRECTORY + File.separator + currentDate.format(DATE_FORMAT) + ".txt"),
            "SkynetW Usage Statistics",
            JOptionPane.INFORMATION_MESSAGE);
    }
}