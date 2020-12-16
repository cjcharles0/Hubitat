/**
 *
 *  Fibaro Dimmer 2
 *   
 *	github: Eric Maycock (erocm123)
 *	Date: 2016-07-31 8:03PM
 *	Copyright Eric Maycock
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 * 
 * added button to the current state so Hubitat apps can pick up the 3 buttons and added doubletap and releasable button: borristhecat 24/5/19
 * added basicSet zwave event so physical button presses are handled by the hub. /eriktack 20190812
 * added physical and digital send events. Split logging for info and debug. Added the ability to hide the configure settings. borristhecat 26/8/19
 * overhauled a fair few settings and added nightmode
 */
 
metadata {
	definition (name: "Fibaro Dimmer 2", namespace: "cjcharles0", author: "Chris (and Eric/erocm123 and David/codersaur)") {
		capability "Actuator"
		capability "Switch"
		capability "Switch Level"
		capability "Refresh"
		capability "Configuration"
		capability "Sensor"
       // capability "Polling"
        capability "Energy Meter"
        capability "Power Meter"
        capability "PushableButton"
		capability "HoldableButton"
		capability "ReleasableButton"
        capability "DoubleTapableButton"
        capability "Health Check"
        
        command "enableNightmode"
        command "disableNightmode"
        command "toggleNightmode"
        attribute   "nightmode", "string"
        
        attribute   "needUpdate", "string"
        attribute   "firmware", "String"

        fingerprint mfr: "010F", prod: "0102", model: "2000", deviceJoinName: "Fibaro Dimmer 2"

		fingerprint deviceId: "0x1101", inClusters: "0x72,0x86,0x70,0x85,0x8E,0x26,0x7A,0x27,0x73,0xEF,0x26,0x2B"
        fingerprint deviceId: "0x1101", inClusters: "0x5E,0x20,0x86,0x72,0x26,0x5A,0x59,0x85,0x73,0x98,0x7A,0x56,0x70,0x31,0x32,0x8E,0x60,0x75,0x71,0x27"
        
	}
    
    preferences {
        input name: "settingEnable", type: "bool", title: "Enable setting", defaultValue: false
        input name: "enableDebugging", type: "bool", title: "Enable Debug Logging?", defaultValue: false
        input name: "enableInfo", type: "bool", title: "Enable Info Logging?", defaultValue: true
        input name: "configNightmodeLevel", type: "number", title: "Dimmer level when nightmode is enabled (1-100, 0 to disable)", defaultValue: 0
        input name: "configNightmodeForce", type: "bool", title: "Force nightmode if the dimmer is on when nightmode is enabled", defaultValue: true
        input name: "configNightmodeStartTime", type: "time", title: "Start nightmode at this time", defaultValue: 22.00
        input name: "configNightmodeStopTime", type: "time", title: "Stop nightmode at this time", defaultValue: 06.00
        if (settingEnable) input description: "Once you change values on this page, the corner of the \"configuration\" icon will change orange until all configuration parameters are updated.", title: "Settings", displayDuringSetup: false, type: "paragraph", element: "paragraph"
	if (settingEnable) generate_preferences(configuration_model())
    }
}

private getCommandClassVersions() {
	[
     0x20: 1, // Basic
     0x25: 1, // Switch Binary
     0x70: 1, // Configuration
     0x98: 1, // Security
     0x60: 3, // Multi Channel
     0x8E: 2, // Multi Channel Association
     0x26: 1, // Switch Multilevel
     0x87: 1, // Indicator
     0x72: 2, // Manufacturer Specific
     0x5B: 1, // Central Scene
     0x32: 3, // Meter
     0x85: 2, // Association
     0x86: 1, // Version
     0x75: 2  // Protection
    ]
}

def parse(description) {
    def result = null
    if (description.startsWith("Err 106")) {
        state.sec = 0
        result = createEvent(descriptionText: description, isStateChange: true)
    } else if (description != "updated") {
        def cmd = zwave.parse(description, commandClassVersions)
        if (cmd) {
            result = zwaveEvent(cmd)
            //log.debug("'$cmd' parsed to $result")
        } else {
            log.debug "Couldn't zwave.parse '$description'" 
        }
    }
    result
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    logging(cmd)
    def request = update_needed_settings()
    
    if(request != []){
        return [response(commands(request))]
    } else {
        return null
    }
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd, ep=null) {
    logging("$cmd : Endpoint: $ep")
    /*def event
    if (!ep) {
        event = [createEvent([name: "switch", value: cmd.value? "on":"off"])]					 
    }
    return event*/
}

def zwaveEvent(hubitat.zwave.commands.sceneactivationv1.SceneActivationSet cmd) {
    logging(cmd)
    logging("sceneId: $cmd.sceneId")
    logging("dimmingDuration: $cmd.dimmingDuration")
    logging("Configuration for preference \"Switch Type\" is set to ${settings."20"}")
    
    if (settings."20" == "2") {
        Info("Switch configured as Roller blinds")
        switch (cmd.sceneId) {
            // Roller blinds S1
            case 10: // Turn On (1x click)
                buttonEvent(1, "pushed")
            break
            case 13: // Release
                buttonEvent(1, "released")
            break
            case 14: // 2x click
                buttonEvent(1, "doubleTapped")
            break
            case 17: // Brightening
                buttonEvent(1, "held")
            break
            // Roller blinds S2
            case 11: // Turn Off
                buttonEvent(2, "pushed")
            break
            case 13: // Release
                buttonEvent(2, "released")
            break
            case 14: // 2x click
                buttonEvent(2, "doubleTapped")
            break
            case 15: // 3x click
                buttonEvent(3, "pushed")
            break
            case 18: // Dimming
                buttonEvent(2, "held")
            break
            default:
                logging("Unhandled SceneActivationSet: ${cmd}")
            break
        }
    } else if (settings."20" == "1") {
        Info("Switch configured as Toggle")
        switch (cmd.sceneId) {
            // Toggle S1
            case 10: // Off to On
                buttonEvent(1, "held")
            break
            case 11: // On to Off
                buttonEvent(1, "released")
            break
            case 14: // 2x click
                buttonEvent(1, "doubleTapped")
            break
            // Toggle S2
            case 20: // Off to On
                buttonEvent(2, "held")
            break
            case 21: // On to Off
                buttonEvent(2, "released")
            break
            case 24: // 2x click
                buttonEvent(2, "doubleTapped")
            break
            case 25: // 3x click
                buttonEvent(3, "pushed")
            break
            default:
                logging("Unhandled SceneActivationSet: ${cmd}")
            break
        
        }
    } else {
        if (settings."20" == "0") Info("Switch configured as Momentary") else logging("Switch type not configured") 
        switch (cmd.sceneId) {
            // Momentary S1
            case 16: // 1x click
                buttonEvent(1, "pushed")
            break
            case 14: // 2x click
                buttonEvent(1, "doubleTapped")
            break
            case 12: // held
                buttonEvent(1, "held")
            break
            case 13: // release
                buttonEvent(1, "released")
            break
            // Momentary S2
            case 26: // 1x click
                buttonEvent(2, "pushed")
            break
            case 24: // 2x click
                buttonEvent(2, "doubleTapped")
            break
            case 25: // 3x click
                buttonEvent(3, "pushed")
            break
            case 22: // held
                buttonEvent(2, "held")
            break
            case 23: // release
                buttonEvent(2, "released")
            break
            default:
                logging("Unhandled SceneActivationSet: ${cmd}")
            break
        }
    }  
}

def buttonEvent(button, value) {
    Info("buttonEvent() Button:$button, Value:$value")
	sendEvent(name: value, value: button, isStateChange:true, type: "physical")
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd) {
	logging(cmd)
	dimmerEvents(cmd, (!state.lastRan || now() <= state.lastRan + 2000)?"digital":"physical")
}

def dimmerEvents(hubitat.zwave.Command cmd, source = null) {
	def result = []
	def switchValue = (cmd.value ? "on" : "off")
    //def levelValue = Math.round (cmd.value * 100 / 99)
    def levelValue = cmd.value
    
    // Store last active level, which is needed for nightmode functionality:
    if (levelValue > 0) state.lastActiveLevel = levelValue
    
	def switchEvent = createEvent(name: "switch", value: switchValue, descriptionText: "$device.displayName was turned $value [${source?source:'physical'}]", type: source?source:"physical")
	result << switchEvent
    
	if (cmd.value) {
		//result << createEvent(name: "level", value: cmd.value, unit: "%", descriptionText: "$device.displayName was set to ${cmd.value}% [${source?source:'physical'}]", type: source?source:"physical")
		result << createEvent(name: "level", value: levelValue, unit: "%", descriptionText: "$device.displayName was set to ${levelValue}% [${source?source:'physical'}]", type: source?source:"physical")
	}
    
    // Restore pending level if dimmer has been switched on after nightmode has been disabled:
    if (!state.nightmodeActive & (state.nightmodePendingLevel > 0) & switchEvent.isStateChange & switchValue == "on") {
        logger("dimmerEvent(): Applying Pending Level: ${state.nightmodePendingLevel}","debug")
        result << response(secure(zwave.basicV1.basicSet(value: Math.round(state.nightmodePendingLevel.toInteger() * 99 / 100 ))))
        state.nightmodePendingLevel = 0
    }
    
	if (switchEvent.isStateChange) {
		result << response(["delay 3000", zwave.meterV2.meterGet(scale: 2).format()])
	}
	return result
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
	logging(cmd)
    state."association${cmd.groupingIdentifier}" = cmd.nodeId[0]
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
	if (encapsulatedCommand) {
		state.sec = 1
		def result = zwaveEvent(encapsulatedCommand)
		result = result.collect {
			if (it instanceof hubitat.device.HubAction && !it.toString().startsWith("9881")) {
				response(cmd.CMD + "00" + it.toString())
			} else {
				it
			}
		}
		result
	}
}

def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
   def encapsulatedCommand = zwave.getCommand(cmd.commandClass, cmd.command, cmd.data,1)
   if (encapsulatedCommand) {
       zwaveEvent(encapsulatedCommand)
   } else {
       log.warn "Unable to extract CRC16 command from ${cmd}"
   }
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    logging("Unhandled Z-Wave Event: $cmd")
}

def zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd) {
	logging(cmd)
	if (cmd.meterType == 1) {
		if (cmd.scale == 0) {
			sendEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh", type: "digital")
		} else if (cmd.scale == 1) {
			sendEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kVAh", type: "digital")
		} else if (cmd.scale == 2) {
			sendEvent(name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W", type: "digital")
		} else {
			sendEvent(name: "electric", value: cmd.scaledMeterValue, unit: ["pulses", "V", "A", "R/Z", ""][cmd.scale - 3], type: "digital")
		}
	}
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv1.SensorMultilevelReport cmd){
    logging(cmd)
	def map = [:]
	switch (cmd.sensorType) {
		case 4:
			map.name = "power"
            map.value = cmd.scaledSensorValue.toInteger().toString()
            map.unit = cmd.scale == 1 ? "Btu/h" : "W"
            break
		default:
			map.descriptionText = cmd.toString()
	}
	createEvent(map)
}

def on() {
    state.lastRan = now()
	//commands([zwave.basicV1.basicSet(value: 0xFF), zwave.basicV1.basicGet()])
	commands([zwave.basicV1.basicSet(value: 0xFF), zwave.switchMultilevelV1.switchMultilevelGet()])
}

def off() {
    state.lastRan = now()
	//commands([zwave.basicV1.basicSet(value: 0x00), zwave.basicV1.basicGet()])
	commands([zwave.basicV1.basicSet(value: 0x00), zwave.switchMultilevelV1.switchMultilevelGet()])
}

def refresh() {
   	logging("$device.displayName refresh()")

    def cmds = []
    if (state.lastRefresh != null && now() - state.lastRefresh < 5000) {
        logging("Refresh Double Press")
        def configuration = new XmlSlurper().parseText(configuration_model())
        configuration.Value.each
        {
            if ( "${it.@setting_type}" == "zwave" ) {
                cmds << zwave.configurationV1.configurationGet(parameterNumber: "${it.@index}".toInteger())
            }
        } 
        cmds << zwave.firmwareUpdateMdV2.firmwareMdGet()
    } else {
        cmds << zwave.meterV2.meterGet(scale: 0)
        cmds << zwave.meterV2.meterGet(scale: 2)
	cmds << zwave.basicV1.basicGet()
    }

    state.lastRefresh = now()
    
    commands(cmds)
}

def ping() {
   	logging("$device.displayName ping()")
	
    def cmds = []

    cmds << zwave.meterV2.meterGet(scale: 0)
    cmds << zwave.meterV2.meterGet(scale: 2)
	cmds << zwave.basicV1.basicGet()

    commands(cmds)
}

def setLevel(level, duration) {
    state.lastRan = now()
	logging("setLevel value:$level, duration:$duration")
    def dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
	logging("dimmingDuration: $dimmingDuration")
    
    // Clear nightmodePendingLevel as it's been overridden.
    state.nightmodePendingLevel = 0
    
    def cmds = []
    cmds << zwave.switchMultilevelV2.switchMultilevelSet(value: level < 100 ? level : 99, dimmingDuration: dimmingDuration)
    cmds << zwave.switchMultilevelV1.switchMultilevelGet()
	commands(cmds)
}

def setLevel(level) {
    state.lastRan = now()
    logging("setLevel value:$level")
	if(level > 99) level = 99
    if(level < 1) level = 1
    
    // Clear nightmodePendingLevel as it's been overridden.
    state.nightmodePendingLevel = 0
    
    def cmds = []
    cmds << zwave.basicV1.basicSet(value: level)
    cmds << zwave.switchMultilevelV1.switchMultilevelGet()
    
	commands(cmds)
}

/**
 *  enableNightmode(level)
 *
 *  Force switch-on illuminance level.
 *
 *  Does not return a list of commands, it sends them immediately using commands(). This is required if
 *  triggered by schedule().
 **/
def enableNightmode(level=-1) {
    logging("enableNightmode(${level})")
    def cmds = []

    // Clean level value:
    if (level == -1) level = settings.configNightmodeLevel.toInteger()
    if (level > 100) level = 100
    if (level < 1) level = 1
    level = Math.round(level * 99 / 100 )

    // If nightmode is not already active, save last active level and current value of param19, so they can be restored when nightmode is stopped:
    if (!state.nightmodeActive) {

        state.nightmodePriorLevel = state.lastActiveLevel
        logging("enableNightmode(): Saved previous active level: ${state.nightmodePriorLevel}")

        if (!state.paramCache19) state.paramCache19 = 0
        state.nightmodePriorParam19 = state.paramCache19.toInteger()
        logging("enableNightmode(): Saved previous param19: ${state.paramCache19}")
    }

    // If the dimmer is already on, and configNightmodeForce is enabled, then adjust the level immediately:
    if (("on" == device.latestValue("switch")) && (true == configNightmodeForce)) {
        logging("Setting brightness to nightmode level: ${level}")
        //commands([zwave.basicV1.basicSet(value: level)])
        cmds << zwave.basicV1.basicSet(value: level)
    }

    state.nightmodeActive = true
    sendEvent(name: "nightmode", value: "Enabled", descriptionText: "Nightmode Enabled", isStateChange: true)

    cmds << zwave.configurationV1.configurationSet(configurationValue: integer2Cmd(level.toInteger(), 1), parameterNumber: 19, size: 1)
    commands(cmds)
}

/**
 *  disableNightmode()
 *
 *  Stop nightmode and restore previous values.
 *
 *  Does not return a list of commands, it sends them immediately using commands(). This is required if
 *  triggered by schedule().
 **/
def disableNightmode() {
    logging("disableNightmode()")
    
    def cmds = []

    // If nightmode is active, restore param19:
    if (state.nightmodeActive) {

        logging("disableNightmode(): Restoring previous value of param19 to: ${state.nightmodePriorParam19}")
        //state.paramTarget19 = state.nightmodePriorParam19
        //sync()
        //cmds << zwave.configurationV1.configurationSet(configurationValue: state.nightmodePriorParam19, parameterNumber: 19, size: 1)
        //commands([zwave.configurationV1.configurationSet(configurationValue: state.nightmodePriorParam19, parameterNumber: 19, size: 1)])
        //commands([zwave.configurationV1.configurationSet(configurationValue: integer2Cmd(state.nightmodePriorParam19, 1), parameterNumber: 19, size: 1)])
        cmds << zwave.configurationV1.configurationSet(configurationValue: integer2Cmd(state.nightmodePriorParam19, 1), parameterNumber: 19, size: 1)
        
        if (state.nightmodePriorLevel > 0) {
            if (("on" == device.latestValue("switch")) & (true == configNightmodeForce)) {
                // Dimmer is already on and configNightmodeForce is enabled, so adjust the level immediately:
                logging("disableNightmode(): Restoring level to: ${state.nightmodePriorLevel}")
                //commands([zwave.basicV1.basicSet(value: Math.round(state.nightmodePriorLevel.toInteger() * 99 / 100 ))])
                cmds << zwave.basicV1.basicSet(value: Math.round(state.nightmodePriorLevel.toInteger() * 99 / 100 ))
            }
            else if (0 == state.nightmodePriorParam19) {
                // Dimmer is off (or configNightmodeForce is not enabled), so need to set a flag to restore the level after it's switched on again, but only if param19 is zero.
                logging("disableNightmode(): Setting flag to restore level at next switch-on: ${state.nightmodePriorLevel}")
                state.nightmodePendingLevel = state.nightmodePriorLevel
            }
        }
    }

    state.nightmodeActive = false
    sendEvent(name: "nightmode", value: "Disabled", descriptionText: "Nightmode Disabled", isStateChange: true)
    
    commands(cmds)
    //return cmds
}

/**
 *  toggleNightmode()
 **/
def toggleNightmode() {
    logging("toggleNightmode()")

    if (state.nightmodeActive) {
        disableNightmode()
    }
    else {
        enableNightmode(configNightmodeLevel)
    }
}

/**
 *  manageSchedules()
 *
 *  Schedules/unschedules Nightmode.
 **/
private manageSchedules() {
    logging("manageSchedules()")

    if (configNightmodeStartTime) {
        schedule(configNightmodeStartTime, enableNightmode)
        logging("manageSchedules(): Nightmode scheduled to start at ${configNightmodeStartTime}")
    } else {
        try {
            unschedule("enableNightmode")
        }
        catch(e) {
            // Unschedule failed
        }
    }

    if (configNightmodeStopTime) {
        schedule(configNightmodeStopTime, disableNightmode)
        logging("manageSchedules(): Nightmode scheduled to stop at ${configNightmodeStopTime}")
    } else {
        try {
            unschedule("disableNightmode")
        }
        catch(e) {
            // Unschedule failed
        }
    }

}

def updated()
{
    sendEvent(name: "numberOfButtons", value: 3)
	state.enableDebugging = settings.enableDebugging
	state.enableInfo = settings.enableInfo
    logging("updated() is being called")
    sendEvent(name: "checkInterval", value: 2 * 30 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
    state.needfwUpdate = ""
    
    manageSchedules()
    state.paramCache19 = settings."19".toInteger()
    logging("param19 = ${settings."19".toInteger()}")
    
    def cmds = update_needed_settings()
    
    sendEvent(name:"needUpdate", value: device.currentValue("needUpdate"), displayed:false, isStateChange: true)
    
    commands(cmds)
}

def installed(){
    log.warn "installed..."
    sendEvent(name: "numberOfButtons", value: 3)
}

private command(hubitat.zwave.Command cmd) {
    if (getDataValue("zwaveSecurePairingComplete") == "true") {
        zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
        cmd.format()
    }
}

private commands(commands, delay=1500) {
	delayBetween(commands.collect{ command(it) }, delay)
}

def generate_preferences(configuration_model)
{
    def configuration = new XmlSlurper().parseText(configuration_model)
   
    configuration.Value.each
    {
        switch(it.@type)
        {   
            case ["byte","short","four"]:
                input "${it.@index}", "number",
                    title:"${it.@label}\n" + "${it.Help}",
                    range: "${it.@min}..${it.@max}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
            case "list":
                def items = []
                it.Item.each { items << ["${it.@value}":"${it.@label}"] }
                input "${it.@index}", "enum",
                    title:"${it.@label}\n" + "${it.Help}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}",
                    options: items
            break
            case "decimal":
               input "${it.@index}", "decimal",
                    title:"${it.@label}\n" + "${it.Help}",
                    range: "${it.@min}..${it.@max}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
            case "boolean":
               input "${it.@index}", "bool",
                    title:"${it.@label}\n" + "${it.Help}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
        }  
    }
} 


def update_current_properties(cmd)
{
    def currentProperties = state.currentProperties ?: [:]
    
    currentProperties."${cmd.parameterNumber}" = cmd.configurationValue

    if (settings."${cmd.parameterNumber}" != null)
    {
        if (settings."${cmd.parameterNumber}".toInteger() == convertParam("${cmd.parameterNumber}".toInteger(), cmd2Integer(cmd.configurationValue)))
        {
            sendEvent(name:"needUpdate", value:"NO", displayed:false, isStateChange: true)
        }
        else
        {
            sendEvent(name:"needUpdate", value:"YES", displayed:false, isStateChange: true)
        }
    }

    state.currentProperties = currentProperties
}

def update_needed_settings()
{
    def cmds = []
    def currentProperties = state.currentProperties ?: [:]
     
    def configuration = new XmlSlurper().parseText(configuration_model())
    def isUpdateNeeded = "NO"
    
    if(!state.needfwUpdate || state.needfwUpdate == ""){
       logging("Requesting device firmware version")
       cmds << zwave.versionV1.versionGet()
    }   
    if(!state.association1 || state.association1 == "" || state.association1 == "1"){
       logging("Setting association group 1")
       cmds << zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId)
       cmds << zwave.associationV2.associationGet(groupingIdentifier:1)
    }
    if(!state.association2 || state.association2 == "" || state.association1 == "2"){
       logging("Setting association group 2")
       cmds << zwave.associationV2.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId)
       cmds << zwave.associationV2.associationGet(groupingIdentifier:2)
    }
   
    configuration.Value.each
    {     
        if ("${it.@setting_type}" == "zwave"){
            if (currentProperties."${it.@index}" == null)
            {
                if (device.currentValue("firmware") == null || it.@fw == "" || "${it.@fw}".indexOf(device.currentValue("firmware")) >= 0){
                    isUpdateNeeded = "YES"
                    logging("Current value of parameter ${it.@index} is unknown")
                    cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
                }
            }
            else if (settings."${it.@index}" != null && convertParam(it.@index.toInteger(), cmd2Integer(currentProperties."${it.@index}")) != settings."${it.@index}".toInteger())
            { 
                if (device.currentValue("firmware") == null || it.@fw == "" || "${it.@fw}".indexOf(device.currentValue("firmware")) >= 0){
                    isUpdateNeeded = "YES"

                    logging("Parameter ${it.@index} will be updated to " + settings."${it.@index}")
                    def convertedConfigurationValue = convertParam(it.@index.toInteger(), settings."${it.@index}".toInteger())
                    cmds << zwave.configurationV1.configurationSet(configurationValue: integer2Cmd(convertedConfigurationValue, it.@byteSize.toInteger()), parameterNumber: it.@index.toInteger(), size: it.@byteSize.toInteger())
                    cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
                }
            } 
        }
    }
    
    sendEvent(name:"needUpdate", value: isUpdateNeeded, displayed:false, isStateChange: true)
    return cmds
} 

/**
* Convert 1 and 2 bytes values to integer
*/
def cmd2Integer(array) { 

switch(array.size()) {
	case 1:
		array[0]
    break
	case 2:
    	((array[0] & 0xFF) << 8) | (array[1] & 0xFF)
    break
    case 3:
    	((array[0] & 0xFF) << 16) | ((array[1] & 0xFF) << 8) | (array[2] & 0xFF)
    break
	case 4:
    	((array[0] & 0xFF) << 24) | ((array[1] & 0xFF) << 16) | ((array[2] & 0xFF) << 8) | (array[3] & 0xFF)
	break
    }
}

def integer2Cmd(value, size) {
	switch(size) {
	case 1:
		[value]
    break
	case 2:
    	def short value1   = value & 0xFF
        def short value2 = (value >> 8) & 0xFF
        [value2, value1]
    break
    case 3:
    	def short value1   = value & 0xFF
        def short value2 = (value >> 8) & 0xFF
        def short value3 = (value >> 16) & 0xFF
        [value3, value2, value1]
    break
	case 4:
    	def short value1 = value & 0xFF
        def short value2 = (value >> 8) & 0xFF
        def short value3 = (value >> 16) & 0xFF
        def short value4 = (value >> 24) & 0xFF
		[value4, value3, value2, value1]
	break
	}
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
     update_current_properties(cmd)
     logging("${device.displayName} parameter '${cmd.parameterNumber}' with a byte size of '${cmd.size}' is set to '${cmd2Integer(cmd.configurationValue)}'")
    
     if (cmd.parameterNumber == 19) {
         logging("saving param 19")
         state.paramCache19 = cmd2Integer(cmd.configurationValue)
     }
    
     return null
}

def zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd){
    logging("Firmware Report ${cmd.toString()}")
    def firmwareVersion
    switch(cmd.checksum){
       case "3281":
          firmwareVersion = "3.08"
       break;
       default:
          firmwareVersion = cmd.checksum
    }
    state.needfwUpdate = "false"
    updateDataValue("firmware", firmwareVersion.toString())
    createEvent(name: "currentFirmware", value: firmwareVersion)
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    logging("${device.label?device.label:device.name}: ${cmd}")
    if(cmd.applicationVersion != null && cmd.applicationSubVersion != null) {
	    def firmware = "${cmd.applicationVersion}.${cmd.applicationSubVersion.toString().padLeft(2,'0')}"
        logging("${device.label?device.label:device.name}: Firmware report received: ${firmware}")
        state.needfwUpdate = "false"
        createEvent(name: "firmware", value: "${firmware}")
    } else if(cmd.firmware0Version != null && cmd.firmware0SubVersion != null) {
	    def firmware = "${cmd.firmware0Version}.${cmd.firmware0SubVersion.toString().padLeft(2,'0')}"
        logging("${device.label?device.label:device.name}: Firmware report received: ${firmware}")
        state.needfwUpdate = "false"
        createEvent(name: "firmware", value: "${firmware}")
    }
}

def configure() {
  	state.enableDebugging = settings.enableDebugging
	state.enableInfo = settings.enableInfo
    logging("Configuring Device For Hubitat Use")
    def cmds = []

    cmds = update_needed_settings()
    
    if (cmds != []) commands(cmds)
}

def convertParam(number, value) {
	switch (number){
    	case 201:
        	if (value < 0)
            	256 + value
        	else if (value > 100)
            	value - 256
            else
            	value
        break
        case 202:
        	if (value < 0)
            	256 + value
        	else if (value > 100)
            	value - 256
            else
            	value
        break
        case 203:
            if (value < 0)
            	65536 + value
        	else if (value > 1000)
            	value - 65536
            else
            	value
        break
        case 204:
        	if (value < 0)
            	256 + value
        	else if (value > 100)
            	value - 256
            else
            	value
        break
        default:
        	value
        break
    }
}

private def logging(message) {
    if (state.enableDebugging == null || state.enableDebugging == true) log.debug "$message"
}

private def Info(message) {
    if (state.enableInfo == null || state.enableInfo == true) log.info "$message"
}

def configuration_model()
{
'''
<configuration>
  <Value type="byte" byteSize="1" index="1" label="1 - Minimum brightness level" min="1" max="98" value="1" setting_type="zwave" fw="">
    <Help>
(parameter is set automatically during the calibration process)
The parameter can be changed manually after the calibration.
Range: 1~98
Default: 1
    </Help>
  </Value>
  <Value type="byte" byteSize="1" index="2" label="2 - Maximum brightness level" min="2" max="99" value="99" setting_type="zwave" fw="">
    <Help>
(parameter is set automatically during the calibration process)
The parameter can be changed manually after the calibration.
Range: 2~99
Default: 99
    </Help>
  </Value>
  <Value type="byte" byteSize="1" index="3" label="3 - Incandescence level of CFLs" min="1" max="99" value="99" setting_type="zwave" fw="">
    <Help>
The Dimmer 2 will set to this value after first switch on. It is required for warming up
and switching dimmable compact fluorescent lamps and certain types of light sources.\n" +
Values: 1-99 = Brightness level (%)
Default: 99
    </Help>
  </Value>
  <Value type="byte" byteSize="2" index="4" label="4 - Incandescence Time of CFLs" min="0" max="255" value="0" setting_type="zwave" fw="">
    <Help>
The time required for switching compact fluorescent lamps and certain types of light sources.\n" +
Values: 0 = Function Disabled, 1-255 = 0.1-25.5s in 0.1s steps
    </Help>
  </Value>
  <Value type="byte" byteSize="1" index="5" label="5 - Dimming step size (auto)" min="1" max="99" value="1" setting_type="zwave" fw="">
    <Help>
This parameter defines the percentage value of dimming step during the automatic control.
Range: 1~99
Default: 1
    </Help>
  </Value>
    <Value type="short" byteSize="2" index="6" label="6 - Dimming step time (auto)" min="0" max="255" value="1" setting_type="zwave" fw="">
    <Help>
This parameter defines the time of single dimming step set in parameter 5 during the automatic control.
Range: 0~255
Default: 1
    </Help>
  </Value>
  <Value type="byte" byteSize="1" index="7" label="7 - Dimming step size (manual)" min="1" max="99" value="1" setting_type="zwave" fw="">
    <Help>
This parameter defines the percentage value of dimming step during the manual control.
Range: 1~99
Default: 1
    </Help>
  </Value>
    <Value type="short" byteSize="2" index="8" label="8 - Dimming step time (manual)" min="0" max="255" value="5" setting_type="zwave" fw="">
    <Help>
This parameter defines the time of single dimming step set in parameter 7 during the manual control.
Range: 0~255
Default: 5
    </Help>
  </Value>
    <Value type="list" byteSize="1" index="9" label="9 - State of the device after a power failure" min="0" max="1" value="1" setting_type="zwave" fw="">
    <Help>
The Dimmer 2 will return to the last state before power failure.
0 - the Dimmer 2 does not save the state before a power failure, it returns to the "off" position
1 - the Dimmer 2 restores its state before power failure
Range: 0~1
Default: 1
    </Help>
        <Item label="Off" value="0" />
        <Item label="Previous State" value="1" />
  </Value>
  <Value type="short" byteSize="2" index="10" label="10 - Timer functionality (auto - off)" min="0" max="32767" value="0" setting_type="zwave" fw="">
    <Help>
This parameter allows to automatically switch off the device after specified time (seconds) from switching on the light source.
Range: 1~32767
Default: 0
    </Help>
  </Value>
    <Value type="list" byteSize="1" index="13" label="13 - Force Auto-calibration" min="0" max="2" value="0" setting_type="zwave" fw="">
    <Help>
Force recalibration of Fibaro (this setting will be reset upon pressing save, after the Fibaro has processed it
    </Help>
        <Item label="0: Readout" value="0" />
        <Item label="1: Force auto-calibration WITHOUT Fibaro Bypass" value="1" />
        <Item label="2: Force auto-calibration WITH Fibaro Bypass" value="2" />
  </Value>
  <Value type="byte" byteSize="1" index="19" label="19 - Forced switch on brightness level" min="0" max="99" value="0" setting_type="zwave" fw="">
    <Help>
If the parameter is active, switching on the Dimmer 2 (S1 single click) will always set this brightness level.
Range: 0~99
Default: 0
    </Help>
  </Value>
    <Value type="list" byteSize="1" index="20" label="20 - Switch type" min="0" max="2" value="0" setting_type="zwave" fw="">
    <Help>
Choose between momentary, toggle and roller blind switch.
Range: 0~2
Default: 0
    </Help>
    <Item label="Momentary" value="0" />
    <Item label="Toggle" value="1" />
    <Item label="Roller Blind" value="2" />
  </Value>
      <Value type="list" byteSize="1" index="22" label="22 - Assign toggle switch status to the device status " min="0" max="1" value="0" setting_type="zwave" fw="">
    <Help>
By default each change of toggle switch position results in action of Dimmer 2 (switch on/off) regardless the physical connection of contacts.
0 - device changes status on switch status change
1 - device status is synchronized with switch status 
Range: 0~1
Default: 0
    </Help>
    <Item label="Default" value="0" />
    <Item label="Synchronized" value="1" />
  </Value>
  <Value type="list" byteSize="1" index="23" label="23 - Double click option" min="0" max="1" value="1" setting_type="zwave" fw="">
    <Help>
set the brightness level to MAX
Range: 0~1
Default: 1
    </Help>
    <Item label="Disabled" value="0" />
    <Item label="Enabled" value="1" />
  </Value>
    <Value type="list" byteSize="1" index="26" label="26 - The function of 3-way switch" min="0" max="1" value="0" setting_type="zwave" fw="">
    <Help>
Switch no. 2 controls the Dimmer 2 additionally (in 3-way switch mode). Function disabled for parameter 20 set to 2 (roller blind switch). 
Range: 0~1
Default: 0
    </Help>
    <Item label="Disabled" value="0" />
    <Item label="Enabled" value="1" />
  </Value>
  <Value type="list" byteSize="1" index="28" label="28 - Scene activation functionality" min="0" max="1" value="1" setting_type="zwave" fw="">
    <Help>
SCENE ID depends on the switch type configurations. 
Range: 0~1
Default: 0
    </Help>
    <Item label="Disabled" value="0" />
    <Item label="Enabled" value="1" />
  </Value>
  <Value type="list" byteSize="1" index="29" label="29 - Switch functionality of S1 and S2" min="0" max="1" value="0" setting_type="disabled" fw="">
    <Help>
This parameter allows for switching the role of keys connected to S1 and S2 without changes in connection. 
Range: 0~1
Default: 0
    </Help>
    <Item label="Standard" value="0" />
    <Item label="Switched" value="1" />
  </Value>
  <Value type="list" byteSize="1" index="30" label="30 - Load Control Mode" min="0" max="2" value="2" setting_type="zwave" fw="">
    <Help>
Override the dimmer mode (i.e. leading or trailing edge).
Default: 2
    </Help>
    <Item label="0: Force leading edge mode" value="0" />
    <Item label="1: Force trailing edge mode" value="1" />
    <Item label="2: Automatic (based on auto-calibration)" value="2" />
  </Value>
  <Value type="list" byteSize="1" index="32" label="32 - On/Off Mode" min="0" max="2" value="2" setting_type="zwave" fw="">
    <Help>
This mode is necessary when connecting non-dimmable light sources
Default: 2
    </Help>
    <Item label="0: On/Off mode DISABLED (dimming is possible)" value="0" />
    <Item label="1: On/Off mode ENABLED (dimming not possible)" value="1" />
    <Item label="2: Automatic (based on auto-calibration)" value="2" />
  </Value>
  <Value type="list" byteSize="1" index="34" label="34 - Softstart Time" min="0" max="2" value="1" setting_type="zwave" fw="">
    <Help>
Time required to warm up the filament of halogen bulbs
    </Help>
    <Item label="0: No soft-start" value="0" />
    <Item label="1: Short soft-start (0.1s)" value="1" />
    <Item label="2: Long soft-start (0.5s)" value="2" />
  </Value>
  <Value type="list" byteSize="1" index="35" label="35 - Auto-calibration after power on" min="0" max="4" value="1" setting_type="zwave" fw="">
    <Help>
This parameter determines the trigger of auto-calibration procedure, e.g. power on, load error, etc.
0 - No auto-calibration of the load after power on
1 - Auto-calibration performed after first power on, 2 after EACH power on
LOAD ERROR alarm includes no load, load failure, burnt out bulb), if parameter 37 is set to 1 also after alarms: SURGE (Dimmer 2 output overvoltage) and OVERCURRENT (Dimmer 2 output overcurrent)
Range: 0~4
Default: 1
    </Help>
    <Item label="0: No auto-calibration" value="0" />
    <Item label="1: Auto-calibration after first power on only" value="1" />
    <Item label="2: Auto-calibration after each power on" value="2" />
    <Item label="3: Auto-calibration after first power on and after each LOAD ERROR" value="3" />
    <Item label="4: Auto-calibration after each power on and after each LOAD ERROR" value="4" />
  </Value>
  <Value type="short" byteSize="2" index="39" label="39 - Max Power load" min="0" max="350" value="250" setting_type="zwave" fw="">
    <Help>
This parameter defines the maximum load for a dimmer.
Range: 0~350
Default: 250
    </Help>
  </Value>
  <Value type="list" byteSize="1" index="40" label="40 - Reaction to General Alarm" min="0" max="3" value="3" setting_type="zwave" fw="">
    <Help>
This parameter determines how the device will react to General Alarm frame.
Range: 0~3
Default: 3 (Flash)
    </Help>
    <Item label="Alarm frame is ignored" value="0" />
    <Item label="Turn ON after receiving the alarm frame" value="1" />
    <Item label="Turn OFF after receiving the alarm frame" value="2" />
	<Item label="Flash after receiving the alarm frame" value="3" />
  </Value>
  <Value type="list" byteSize="1" index="41" label="41 - Reaction to Flood Alarm" min="0" max="3" value="2" setting_type="zwave" fw="">
    <Help>
This parameter determines how the device will react to Flood Alarm frame.
Range: 0~3
Default: 2 (OFF)
    </Help>
    <Item label="Alarm frame is ignored" value="0" />
    <Item label="Turn ON after receiving the alarm frame" value="1" />
    <Item label="Turn OFF after receiving the alarm frame" value="2" />
	<Item label="Flash after receiving the alarm frame" value="3" />
  </Value>
  <Value type="list" byteSize="1" index="42" label="42 - Reaction to CO/CO2/Smoke Alarm" min="0" max="3" value="3" setting_type="zwave" fw="">
    <Help>
This parameter determines how the device will react to CO, CO2 or Smoke frame. 
Range: 0~3
Default: 3 (Flash)
    </Help>
    <Item label="Alarm frame is ignored" value="0" />
    <Item label="Turn ON after receiving the alarm frame" value="1" />
    <Item label="Turn OFF after receiving the alarm frame" value="2" />
	<Item label="Flash after receiving the alarm frame" value="3" />
  </Value>
  <Value type="list" byteSize="1" index="43" label="43 - Reaction to Heat Alarm" min="0" max="3" value="1" setting_type="zwave" fw="">
    <Help>
This parameter determines how the device will react to Heat Alarm frame.
Range: 0~3
Default: 1 (ON)
    </Help>
    <Item label="Alarm frame is ignored" value="0" />
    <Item label="Turn ON after receiving the alarm frame" value="1" />
    <Item label="Turn OFF after receiving the alarm frame" value="2" />
	<Item label="Flash after receiving the alarm frame" value="3" />
  </Value>
  <Value type="byte" byteSize="2" index="44" label="44 - Flashing alarm duration" min="1" max="32000" value="600" setting_type="zwave" fw="">
    <Help>
This parameter allows to set duration of flashing alarm mode. 
Range: 1~32000 (1s-32000s)
Default: 600 (10 min)
    </Help>
  </Value>
  <Value type="byte" byteSize="1" index="50" label="50 - Active power reports" min="0" max="100" value="10" setting_type="zwave" fw="">
    <Help>
The parameter defines the power level change that will result in a new power report being sent. The value is a percentage of the previous report.
Range: 0~100
Default: 10
    </Help>
  </Value>
  <Value type="short" byteSize="2" index="52" label="52 - Periodic active power and energy reports" min="0" max="32767" value="3600" setting_type="zwave" fw="">
    <Help>
Parameter 52 defines a time period between consecutive reports. Timer is reset and counted from zero after each report. 
Range: 0~32767
Default: 3600
    </Help>
  </Value>
  <Value type="short" byteSize="2" index="53" label="53 - Energy reports" min="0" max="255" value="10" setting_type="zwave" fw="">
    <Help>
Energy level change which will result in sending a new energy report.
Range: 0~255
Default: 10
    </Help>
  </Value>
  <Value type="list" byteSize="1" index="54" label="54 - Self-measurement" min="0" max="1" value="0" setting_type="zwave" fw="">
    <Help>
The Dimmer 2 may include active power and energy consumed by itself in reports sent to the main controller.
Range: 0~1
Default: 0
    </Help>
    <Item label="Disabled" value="0" />
    <Item label="Enabled" value="1" />
  </Value>
  <Value type="list" byteSize="1" index="58" label="58 - Method of Calculating Active Power" min="0" max="2" value="0" setting_type="zwave" fw="">
    <Help>
Useful in 2-wire configurations with non-resistive loads.
Default: 0
    </Help>
    <Item label="0: Standard algorithm" value="0" />
    <Item label="1: Based on calibration data" value="1" />
    <Item label="2: Based on control angle" value="2" />
  </Value>
  <Value type="short" byteSize="2" index="59" label="59 - Approximated Power at Max Brightness" min="0" max="500" value="0" setting_type="zwave" fw="">
    <Help>
Determines the approximate value of the power that will be reported by the device at it's maximum brightness level
Default: 0
    </Help>
  </Value>
</configuration>
'''
 //   <Value type="boolean" index="enableDebugging" label="Enable Debug Logging?" value="true" setting_type="preference" fw="3.08">
 //   <Help>

//    </Help>
//  </Value>

}
