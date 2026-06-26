package com.igrik.cpu_reader

import android.os.Build
import timber.log.Timber
import java.io.*
import java.util.regex.Pattern

/**
 * This class is responsible for providing CPU specific information
 * such as ABI, number of cores, temperature and frequencies
 */
class CpuDataProvider constructor() {
    /**
    Read Android Binary Interface information from the device
     */
    fun getAbi(): String {
        return if (Build.VERSION.SDK_INT >= 21) {
            Build.SUPPORTED_ABIS[0]
        } else {
            @Suppress("DEPRECATION")
            Build.CPU_ABI
        }
    }

    fun getNumberOfCores(): Int {
        return if (Build.VERSION.SDK_INT >= 17) {
            Runtime.getRuntime().availableProcessors()
        } else {
            getNumCoresLegacy()
        }
    }

    /**
     * Checking frequencies directories and return current value if exists (otherwise we can
     * assume that core is stopped - value -1)
     */
    fun getCurrentFreq(coreNumber: Int): Long {
        val currentFreqPath = "${CPU_INFO_DIR}cpu$coreNumber/cpufreq/scaling_cur_freq"
        return try {
            RandomAccessFile(currentFreqPath, "r").use { it.readLine().toLong() / 1000 }
        } catch (e: Exception) {
            Timber.e(e)
            -1
        }
    }

    /**
     * Read max/min frequencies for specific [coreNumber]. Return [Pair] with min and max frequency
     * or [Pair] with -1.
     */
    fun getMinMaxFreq(coreNumber: Int): Pair<Long, Long> {
        val minPath = "${CPU_INFO_DIR}cpu$coreNumber/cpufreq/cpuinfo_min_freq"
        val maxPath = "${CPU_INFO_DIR}cpu$coreNumber/cpufreq/cpuinfo_max_freq"
        return try {
            val minMhz = RandomAccessFile(minPath, "r").use { it.readLine().toLong() / 1000 }
            val maxMhz = RandomAccessFile(maxPath, "r").use { it.readLine().toLong() / 1000 }
            Pair(minMhz, maxMhz)
        } catch (e: Exception) {
            Timber.e(e)
            Pair(-1, -1)
        }
    }

    /**
     * Gets the number of cores available in this device, across all processors.
     * Requires: Ability to peruse the filesystem at "/sys/devices/system/cpu"
     *
     * @return The number of cores, or 1 if check fails
     */
    private fun getNumCoresLegacy(): Int {
        class CpuFilter : FileFilter {
            override fun accept(pathname: File): Boolean {
                // Check if filename is "cpu", followed by a single digit number
                if (Pattern.matches("cpu[0-9]+", pathname.name)) {
                    return true
                }
                return false
            }
        }
        return try {
            val dir = File(CPU_INFO_DIR)
            val files = dir.listFiles(CpuFilter())
            files.size
        } catch (e: Exception) {
            1
        }
    }

    /**
     * Retrieves the current overall thermal temperature for all the CPUs.
     *
     * Scans the sysfs thermal zones (`/sys/class/thermal/thermal_zone*`) and
     * picks the one whose `type` identifies a CPU sensor (e.g. `x86_pkg_temp`,
     * `cpu_thermal`, `soc_thermal`), then reads its `temp` file. Falls back to
     * `thermal_zone0` if no CPU zone is found. Returns -1.0 on failure.
     */
    fun getCpuTemperature(): Double {
        val zone = findCpuThermalZone() ?: "thermal_zone0"
        val tempPath = "$THERMAL_DIR$zone/temp"
        return try {
            RandomAccessFile(tempPath, "r").use { it.readLine().toDouble() / 1000 }
        } catch (e: Exception) {
            Timber.e(e)
            -1.0
        }
    }

    /**
     * Returns the name of the sysfs thermal zone whose `type` matches a known
     * CPU sensor, or null if none is found.
     */
    private fun findCpuThermalZone(): String? {
        return try {
            val dir = File(THERMAL_DIR)
            val zones = dir.listFiles { file -> file.name.startsWith("thermal_zone") }
                ?: return null
            for (zone in zones) {
                val type = try {
                    RandomAccessFile(File(zone, "type"), "r").use { it.readLine() }
                } catch (e: Exception) {
                    null
                }
                if (type != null && CPU_THERMAL_TYPE_REGEX.containsMatchIn(type)) {
                    return zone.name
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val CPU_INFO_DIR = "/sys/devices/system/cpu/"
        private const val THERMAL_DIR = "/sys/class/thermal/"
        // Common CPU thermal-zone type names across vendors.
        private val CPU_THERMAL_TYPE_REGEX =
            Regex("(?i)x86_pkg_temp|cpu[-_]?thermal|soc[-_]?thermal|cpu|cpu-0|apc")
    }
}