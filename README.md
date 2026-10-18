# ServerStatus (Paper 1.16+)

A custom Paper/Spigot plugin tailored for monitoring Minecraft server hardware and runtime health directly in-game. Built specifically with Raspberry Pi host server hardware in mind (JupiterSMP).

---

## 📊 Features

* **TPS Monitoring:** Displays real-time 1m, 5m, and 15m server TPS with color coding.
* **RAM Usage:** Tracks current heap memory usage against total allocated RAM.
* **Hardware Health (Raspberry Pi/Linux):**
  * Reads CPU core temperature via `/sys/class/thermal/thermal_zone0/temp` or `vcgencmd`.
  * Checks CPU clock frequency (`cpufreq` / `vcgencmd`).
  * Reports active/historical power undervoltage and thermal throttling flags (`vcgencmd get_throttled`).
* **Server Runtime Data:** Displays connected player counts, server uptime, and Paper core version.

---

## 🛠️ Build & Installation

### Requirements
* **Java:** 11 or higher
* **Server:** Paper / Spigot 1.16.5+
* **Build Tool:** Maven

### Building from Source

```bash
git clone [https://github.com/pangrusak/ServerStatus-1.16-Paper.git](https://github.com/pangrusak/ServerStatus-1.16-Paper.git)
cd ServerStatus-1.16-Paper
mvn clean package
