package me.pangrusak.serverstatus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class ServerStatusPlugin extends JavaPlugin implements CommandExecutor {
    private final long startedAt = System.currentTimeMillis();

    @Override
    public void onEnable() {
        if (getCommand("checkstatus") != null) {
            getCommand("checkstatus").setExecutor(this);
        }
        getLogger().info("ServerStatus enabled. Players can use /checkstatus.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("serverstatus.use")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        double memoryPercent = max == 0 ? 0.0 : (used * 100.0 / max);
        double[] tps = Bukkit.getServer().getTPS();
        String temp = readTemperature();
        String clock = readClock();
        String throttle = readThrottle();

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "---------------- "
                + ChatColor.YELLOW + "JupiterSMP Status " + ChatColor.GOLD + ChatColor.STRIKETHROUGH + "----------------");
        sender.sendMessage(ChatColor.YELLOW + "TPS: " + tpsColor(tps[0]) + format(tps[0])
                + ChatColor.GRAY + " | " + tpsColor(tps[1]) + format(tps[1])
                + ChatColor.GRAY + " | " + tpsColor(tps[2]) + format(tps[2])
                + ChatColor.DARK_GRAY + " (1m / 5m / 15m)");
        sender.sendMessage(ChatColor.YELLOW + "Memory: " + ChatColor.WHITE + formatGb(used) + " / " + formatGb(max)
                + ChatColor.GRAY + " (" + String.format(Locale.US, "%.0f", memoryPercent) + "%)");
        sender.sendMessage(ChatColor.YELLOW + "CPU temperature: " + temperatureColor(temp) + temp);
        sender.sendMessage(ChatColor.YELLOW + "CPU clock: " + ChatColor.WHITE + clock);
        sender.sendMessage(ChatColor.YELLOW + "Power / throttle: " + throttleColor(throttle) + throttle);
        sender.sendMessage(ChatColor.YELLOW + "Players: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size()
                + ChatColor.GRAY + " / " + Bukkit.getMaxPlayers());
        sender.sendMessage(ChatColor.YELLOW + "Uptime: " + ChatColor.WHITE + formatDuration(System.currentTimeMillis() - startedAt));
        sender.sendMessage(ChatColor.YELLOW + "Paper: " + ChatColor.WHITE + Bukkit.getVersion());
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "------------------------------------------------------");
        return true;
    }

    private String readTemperature() {
        String raw = readFirstLine(Path.of("/sys/class/thermal/thermal_zone0/temp"));
        if (raw != null) {
            try {
                double celsius = Double.parseDouble(raw.trim()) / 1000.0;
                return String.format(Locale.US, "%.1f°C %s", celsius, tempLabel(celsius));
            } catch (NumberFormatException ignored) { }
        }
        String vcgencmd = run("vcgencmd", "measure_temp");
        if (vcgencmd != null) {
            String digits = vcgencmd.replaceAll("[^0-9.]", "");
            try {
                double celsius = Double.parseDouble(digits);
                return String.format(Locale.US, "%.1f°C %s", celsius, tempLabel(celsius));
            } catch (NumberFormatException ignored) { }
        }
        return "Unavailable";
    }

    private String tempLabel(double celsius) {
        if (celsius >= 80.0) return "[HOT]";
        if (celsius >= 70.0) return "[WARM]";
        return "[GOOD]";
    }

    private String readClock() {
        String raw = readFirstLine(Path.of("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"));
        if (raw != null) {
            try {
                double mhz = Double.parseDouble(raw.trim()) / 1000.0;
                return String.format(Locale.US, "%.0f MHz", mhz);
            } catch (NumberFormatException ignored) { }
        }
        String vcgencmd = run("vcgencmd", "measure_clock", "arm");
        if (vcgencmd != null && vcgencmd.contains("=")) {
            try {
                long hz = Long.parseLong(vcgencmd.substring(vcgencmd.indexOf('=') + 1).trim());
                return String.format(Locale.US, "%.0f MHz", hz / 1_000_000.0);
            } catch (NumberFormatException ignored) { }
        }
        return "Unavailable";
    }

    private String readThrottle() {
        String output = run("vcgencmd", "get_throttled");
        if (output == null || !output.contains("0x")) return "Unavailable (vcgencmd not found)";
        try {
            long flags = Long.parseLong(output.substring(output.indexOf("0x") + 2).trim(), 16);
            if (flags == 0) return "No undervoltage or thermal throttling detected";
            List<String> notices = new ArrayList<>();
            if ((flags & 0x1) != 0) notices.add("UNDERVOLTAGE NOW");
            if ((flags & 0x2) != 0) notices.add("ARM FREQUENCY CAPPED NOW");
            if ((flags & 0x4) != 0) notices.add("THROTTLED NOW");
            if ((flags & 0x8) != 0) notices.add("SOFT TEMP LIMIT NOW");
            if ((flags & 0x10000) != 0) notices.add("undervoltage occurred");
            if ((flags & 0x20000) != 0) notices.add("frequency cap occurred");
            if ((flags & 0x40000) != 0) notices.add("thermal throttling occurred");
            if ((flags & 0x80000) != 0) notices.add("soft temp limit occurred");
            return String.join(", ", notices) + " (" + output.trim() + ")";
        } catch (NumberFormatException e) {
            return "Could not parse " + output.trim();
        }
    }

    private String run(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(800, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.readLine();
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private String readFirstLine(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return reader.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    private ChatColor tpsColor(double tps) {
        if (tps >= 19.0) return ChatColor.GREEN;
        if (tps >= 17.0) return ChatColor.YELLOW;
        return ChatColor.RED;
    }

    private ChatColor temperatureColor(String temperature) {
        if (temperature.contains("[HOT]")) return ChatColor.RED;
        if (temperature.contains("[WARM]")) return ChatColor.YELLOW;
        if (temperature.contains("[GOOD]")) return ChatColor.GREEN;
        return ChatColor.GRAY;
    }

    private ChatColor throttleColor(String throttle) {
        return throttle.startsWith("No undervoltage") ? ChatColor.GREEN
                : throttle.equals("Unavailable (vcgencmd not found)") ? ChatColor.GRAY : ChatColor.RED;
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", Math.min(20.0, value));
    }

    private String formatGb(long bytes) {
        return String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }

    private String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(millis);
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
