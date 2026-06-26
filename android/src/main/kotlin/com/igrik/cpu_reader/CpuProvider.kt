package com.igrik.cpu_reader

import android.content.Context
import android.os.Build
import android.os.HardwarePropertiesManager
import timber.log.Timber
import java.io.*
import java.util.regex.Pattern

/**
 * This class is responsible for providing CPU specific information
 * such as ABI, number of cores, temperature and frequencies
 */
class CpuDataProvider constructor(private val context: Context? = null) {
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
     * Uses [HardwarePropertiesManager.getDeviceTemperatures] with
     * [HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU] and
     * [HardwarePropertiesManager.TEMPERATURE_CURRENT] on API 24+ (the API
     * requires a [Context]). Falls back to reading the sysfs thermal zone on
     * older devices or when the manager is unavailable.
     */
    fun getCpuTemperature(): Double {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && context != null) {
            try {
                val manager = context.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE)
                        as? HardwarePropertiesManager
                if (manager != null) {
                    val temps = manager.getDeviceTemperatures(
                        HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                        HardwarePropertiesManager.TEMPERATURE_CURRENT
                    )
                    if (temps.isNotEmpty()) {
                        // Take the highest reported CPU temperature as the
                        // overall value (multiple CPU sensors may be returned).
                        return temps.maxOrNull()!!.toDouble()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
        return readCpuTemperatureLegacy()
    }

    private fun readCpuTemperatureLegacy(): Double {
        val tempPath = "sys/class/thermal/thermal_zone0/temp"
        return try {
            RandomAccessFile(tempPath, "r").use { it.readLine().toDouble() / 1000 }
        } catch (e: Exception) {
            Timber.e(e)
            -1.0
        }
    }

    companion object {
        private const val CPU_INFO_DIR = "/sys/devices/system/cpu/"
    }
}