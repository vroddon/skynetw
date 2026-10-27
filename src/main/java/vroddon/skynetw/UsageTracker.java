package vroddon.skynetw;

import java.awt.*;
import java.awt.TrayIcon.MessageType;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import javax.swing.*;

public class UsageTracker {
    private static final String DATA_DIRECTORY = "D:\\svn\\skynetw\\data";
    private static final int SAVE_INTERVAL_MINUTES = 5;
    // The usage is measured by sampling every SAMPLE_SECONDS. A sample adds the time elapsed since the
    // previous one only if the user touched the keyboard or the mouse in the last IDLE_LIMIT_MINUTES
    // and the screensaver is not running. A gap longer than MAX_SAMPLE_GAP_SECONDS (computer asleep,
    // program stalled, clock changed) is discarded, so one sample can never add more than that.
    private static final int SAMPLE_SECONDS = 5;
    private static final int IDLE_LIMIT_MINUTES = 5;
    private static final int MAX_SAMPLE_GAP_SECONDS = 30;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String API_URL = "https://cosasbuenas.es/cgi-bin/report.cgi?computertoday=";
    
    private TrayIcon trayIcon;
    private SystemTray tray;
    private Timer timer;
    private Timer sampleTimer;
    private LocalDateTime startTime;
    private LocalDateTime lastSample;
    private Duration todayUsage = Duration.ZERO;
    private LocalDate currentDate;
    private boolean screensaverActive = false;
    private boolean userIdle = false;
    
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
        startTime = LocalDateTime.now();
        lastSample = startTime;
        
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
        // Start sampling the user activity
        setupSampling();

        // Schedule regular updates (saving to disk and reporting to the server)
        timer = new Timer();
        timer.schedule(new TimerTask() {
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
    
    private void setupSampling() {
        // schedule (and not scheduleAtFixedRate): after a suspension the timer must not fire a burst of
        // delayed samples. Samples run on the Swing thread, like the rest of the state changes.
        sampleTimer = new Timer();
        sampleTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> sample());
            }
        }, 0, TimeUnit.SECONDS.toMillis(SAMPLE_SECONDS));
    }
    
    // Adds to today's usage the time since the previous sample, if the user is really using the computer.
    private void sample() {
        LocalDateTime now = LocalDateTime.now();
        long gapMillis = Duration.between(lastSample, now).toMillis();
        lastSample = now;
        
        checkNewDay(now.toLocalDate());
        
        screensaverActive = ScreenSaverDetector.isScreensaverActive();
        userIdle = ScreenSaverDetector.getIdleMillis() >= TimeUnit.MINUTES.toMillis(IDLE_LIMIT_MINUTES);
        
        boolean reasonableGap = gapMillis > 0 && gapMillis <= TimeUnit.SECONDS.toMillis(MAX_SAMPLE_GAP_SECONDS);
        if (reasonableGap && !screensaverActive && !userIdle) {
            todayUsage = todayUsage.plusMillis(gapMillis);
        }
    }
    
    // When the day changes, the file of the previous day is closed and the counter starts again at zero.
    private void checkNewDay(LocalDate today) {
        if (!today.equals(currentDate)) {
            writeUsageFile(); // Save previous day's data (not reported to the server: it is not today's)
            currentDate = today;
            todayUsage = Duration.ZERO;
            loadTodayUsage(); // Load today's data if exists
        }
    }
    
    private void updateUsageTime() {
        checkNewDay(LocalDate.now());
        saveUsageTime();
        updateTrayTooltip();
    }
    
    private void saveUsageTime() {
        writeUsageFile();
        // Call the API to report minutes
        reportToApi(todayUsage.toMinutes());
    }
    
    private void writeUsageFile() {
        String filename = DATA_DIRECTORY + File.separator + currentDate.format(DATE_FORMAT) + ".txt";
        long totalMinutes = todayUsage.toMinutes();
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
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
private void reportToApi(long minutes) {
        // Run the API call in a separate thread to avoid blocking the UI
        new Thread(() -> {
            try {
                URL url = new URL(API_URL + minutes);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    System.out.println("Successfully reported " + minutes + " minutes to API");
                } else {
                    System.err.println("API call failed with response code: " + responseCode);
                }
            } catch (Exception e) {
                System.err.println("Error reporting to API: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
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
        String activityStatus = userIdle ? "idle (not counted)" : "active (counted)";
        String version ="";
        try{
        URL classUrl = UsageTracker.class.getResource("UsageTracker.class");
        File classFile = new File(classUrl.toURI());
        long lastModified = classFile.lastModified();
        Date compilationTime = new Date(lastModified);
        version = ":" + compilationTime;        
        }catch(Exception e){}
        
        JOptionPane.showMessageDialog(null,
            String.format("Version: " + version +"\n" +
                         "Today's computer usage: %d hours, %d minutes\n" +
                         "Tracking since: %s\n" +
                         "Current screensaver status: %s\n" +
                         "Data saved every %d minutes to:\n%s",
                         hours, minutes, 
                         startTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                         screensaverStatus,
                         activityStatus,
                         SAVE_INTERVAL_MINUTES,
                         DATA_DIRECTORY + File.separator + currentDate.format(DATE_FORMAT) + ".txt"),
            "SkynetW Usage Statistics",
            JOptionPane.INFORMATION_MESSAGE);
    }
}