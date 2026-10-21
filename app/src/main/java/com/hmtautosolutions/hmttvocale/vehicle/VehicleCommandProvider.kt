package com.hmtautosolutions.hmttvocale.vehicle

import com.hmtautosolutions.hmttvocale.Prefs

enum class VehicleFunction { CLIMATE_POWER, TEMPERATURE, FAN_SPEED, WINDOWS }

/** Honest support level for each vehicle function. */
enum class SupportLevel { VERIFIED, INTERFACE_UNTESTED, NOT_SUPPORTED }

enum class VehicleAction(val function: VehicleFunction) {
    CLIMATE_ON(VehicleFunction.CLIMATE_POWER),
    CLIMATE_OFF(VehicleFunction.CLIMATE_POWER),
    TEMP_SET(VehicleFunction.TEMPERATURE),
    TEMP_UP(VehicleFunction.TEMPERATURE),
    TEMP_DOWN(VehicleFunction.TEMPERATURE),
    FAN_UP(VehicleFunction.FAN_SPEED),
    FAN_DOWN(VehicleFunction.FAN_SPEED),
    WINDOWS_OPEN(VehicleFunction.WINDOWS),
    WINDOWS_CLOSE(VehicleFunction.WINDOWS)
}

enum class VehicleResultStatus { EXECUTED_VERIFIED, SIMULATED, NOT_SUPPORTED, FAILED }

data class VehicleResult(val status: VehicleResultStatus, val detail: String = "")

/**
 * Plug-in point for real vehicle control. A real implementation must only use a documented,
 * authorised interface (manufacturer SDK, system service, etc.) and must read back the vehicle
 * state before returning EXECUTED_VERIFIED.
 */
interface VehicleCommandProvider {
    val name: String
    val isSimulation: Boolean
    fun capabilities(): Map<VehicleFunction, SupportLevel>
    fun execute(action: VehicleAction, value: Int?): VehicleResult
}

/** Default when no verified Coolray interface exists: never sends anything. */
class UnavailableVehicleProvider : VehicleCommandProvider {
    override val name = "None"
    override val isSimulation = false
    override fun capabilities() = VehicleFunction.values().associateWith { SupportLevel.NOT_SUPPORTED }
    override fun execute(action: VehicleAction, value: Int?) = VehicleResult(VehicleResultStatus.NOT_SUPPORTED)
}

/** Test the assistant flow without a car. Results are always labelled SIMULATED. */
class SimulatedVehicleProvider : VehicleCommandProvider {
    override val name = "Simulation"
    override val isSimulation = true
    private var climateOn = false
    private var temperature = 22
    private var fan = 2
    private var windowsOpen = false

    override fun capabilities() = VehicleFunction.values().associateWith { SupportLevel.NOT_SUPPORTED }

    override fun execute(action: VehicleAction, value: Int?): VehicleResult {
        when (action) {
            VehicleAction.CLIMATE_ON -> climateOn = true
            VehicleAction.CLIMATE_OFF -> climateOn = false
            VehicleAction.TEMP_SET -> temperature = (value ?: temperature).coerceIn(16, 32)
            VehicleAction.TEMP_UP -> temperature = (temperature + 1).coerceAtMost(32)
            VehicleAction.TEMP_DOWN -> temperature = (temperature - 1).coerceAtLeast(16)
            VehicleAction.FAN_UP -> fan = (fan + 1).coerceAtMost(7)
            VehicleAction.FAN_DOWN -> fan = (fan - 1).coerceAtLeast(0)
            VehicleAction.WINDOWS_OPEN -> windowsOpen = true
            VehicleAction.WINDOWS_CLOSE -> windowsOpen = false
        }
        return VehicleResult(VehicleResultStatus.SIMULATED,
            "climate=$climateOn temp=$temperature fan=$fan windowsOpen=$windowsOpen")
    }
}

object VehicleProviders {
    private val simulated = SimulatedVehicleProvider()
    fun create(prefs: Prefs): VehicleCommandProvider =
        if (prefs.simulationMode) simulated else UnavailableVehicleProvider()
}
