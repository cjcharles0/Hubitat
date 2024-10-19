/**
 *  Copyright 2017 Chris Charles
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
 *  Neo Thermostat (Child Device of Neo Hub Bridge)
 *
 *  Author: Chris Charles (cjcharles0)
 *  Date: 2017-04-26
 */

import groovy.json.JsonSlurper

metadata {
	definition (name: "Heatmiser Neo Thermostat", namespace: "cjcharles0", author: "Chris Charles")
    {
    	//capability "Refresh"
        
        capability "Sensor"
		capability "Temperature Measurement"
		capability "Thermostat"
        
        
		command "push"
        command "refresh" //This is used for the overall refresh process
        
        command "boostOneHour" // Custom
		command "boostHours",[[name:"desiredHours",type:"NUMBER", description:"Boost Hours", constraints:["NUMBER"]]] // Custom
        command "boostTempHours",[[name:"desiredTemp",type:"NUMBER", description:"Boost Temperature", constraints:["NUMBER"]],[name:"desiredHours",type:"NUMBER", description:"Boost Hours", constraints:["NUMBER"]]]
        
        command "setHeatingSetpoint" //Required by ST for thermostat type
        //command "setCoolingSetpoint" //Required by ST for thermostat type
        command "setThermostatSetpoint" //Required by ST for thermostat type
        command "setTemperature" //Required by ST for thermostat type
        command "heat" //Required by ST for thermostat type
        command "emergencyHeat" //Required by ST for thermostat type
        command "cool" //Required by ST for thermostat type
        command "setThermostatMode" //Required by ST for thermostat type
        
        //command "fanOn" //Required by ST for thermostat type
        //command "fanAuto" //Required by ST for thermostat type
        //command "fanCirculate" //Required by ST for thermostat type
        //command "setThermostatFanMode" //Required by ST for thermostat type
        
        command "auto" //Required by ST for thermostat type
        command "off" //Required by ST for thermostat type
        command "on" //Required by ST for switch type
        
        command "setpointUp" // Custom
		command "setpointDown" // Custom
		command "durationUp" // Custom
		command "durationDown" // Custom
		command "setTempHoldOn" // Custom
		command "setTempHoldOff" // Custom
		command "setTimerOn" // Custom
		command "setTimerOff" // Custom
        command "away" // Custom
		command "awayOff" // Custom
		command "holidayOff" // Custom

        attribute "holdtime","string" // Custom for how long to hold for
		attribute "nextSetpointText", "string" // Custom for text display
		attribute "statusText", "string" // Custom for neohub response
        
		attribute "awayholiday", "string"
		attribute "floortemp", "number"
        
        
  		attribute "temperature","number" // Temperature Measurement
		attribute "thermostatSetpoint","number" // Thermostat setpoint      
        attribute "coolingSetpoint","number" // Thermostat setpoint
		attribute "heatingSetpoint","number" // Thermostat setpoint
        
	}
    
    preferences {
        section {
        	//Preferences here
        }
	}

//Thermostat Temp and State
	tiles(scale: 2) {

		// Main multi information tile
		multiAttributeTile(name:"temperatureDisplay", type:"thermostat", width:6, height:3, canChangeIcon: true) { //, canChangeBackground: true
			tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
				attributeState("default", label:'${currentValue}', unit:"dC", icon: 'st.Weather.weather2',
					backgroundColors:[
							// Celsius
							[value: 0, color: "#153591"],
							[value: 9, color: "#1e9cbb"],
							[value: 15, color: "#90d2a7"],
							[value: 22, color: "#44b621"],
							[value: 28, color: "#f1d801"],
							[value: 32, color: "#d04e00"],
							[value: 36, color: "#bc2323"]
					] )
			}
			// Operating State - used to get background colour when type is 'thermostat'.
            tileAttribute("device.thermostatOperatingState", key: "OPERATING_STATE") {
                attributeState("heating", backgroundColor:"#e86d13", defaultState: true)
                attributeState("idle", backgroundColor:"#00A0DC")
                attributeState("cooling", backgroundColor:"#00A0DC")
            }
            tileAttribute("device.thermostatMode", key: "THERMOSTAT_MODE") {
                attributeState("heat", label:' ', defaultState: true)
                attributeState("off", label:' ')
            }
            tileAttribute("device.thermostatFanMode", key: "THERMOSTAT_FAN_MODE") {
                attributeState("off", label:' ', defaultState: true)
            }
            tileAttribute("device.heatingSetpoint", key: "HEATING_SETPOINT") {
                attributeState("heatingSetpoint", label:'${currentValue}', unit:"dC", defaultState: true)
            }
            tileAttribute("device.coolingSetpoint", key: "COOLING_SETPOINT") {
                attributeState("coolingSetpoint", label:'${currentValue}', unit:"dC", defaultState: true)
            }
		}
       
		standardTile("raisethermostatSetpoint", "device.raisethermostatSetpoint", width: 1, height: 1, decoration: "flat") {
			state "default", action:"SetpointUp", label:'+1C'//, icon:"st.thermostat.thermostat-up"
		}
		standardTile("lowerthermostatSetpoint", "device.lowerthermostatSetpoint", width: 1, height: 1, decoration: "flat") {
			state "default", action:"SetpointDown", label:'-1C'//, icon:"st.thermostat.thermostat-down"
		}
        
		standardTile("increaseTime", "device.increaseTime", width: 1, height: 1, decoration: "flat") {
			state "default", action:"durationUp", label:'+30m'
		}
        standardTile("decreaseTime", "device.decreaseTime", width: 1, height: 1, decoration: "flat") {
			state "default", action:"durationDown", label:'-30m'
		}
		valueTile("holdtime", "device.holdtime", inactiveLabel: false, decoration: "flat", width: 1, height: 1) {
			state "default", label:'${currentValue}' // icon TBC
		}
        
        standardTile("setTempHold", "setTempHold", inactiveLabel: false, decoration: "flat", width: 2, height: 1) {
			state "setTemp", action:"setTempHoldOn", label:'Set Temp' // icon TBC
			state "setHold", action:"setTempHoldOn", label:'Set Hold' // icon TBC
            state "tempWasSet", action:"setTempHoldOn", label:'Temp Was Set' // icon TBC
            state "cancelHold", action:"setTempHoldOff", label:'Cancel Hold' // icon TBC
		}
        
        
		valueTile("nextSetpointText", "device.nextSetpointText", width: 3, height: 1) {
			state "default", label:'${currentValue}'
		}
        valueTile("statusText", "statusText", decoration: "flat", width: 3, height: 1) {     //, inactiveLabel: false
			state "default", label:'${currentValue}'
		}
        
        
        valueTile("floortemp", "floortemp", decoration: "flat", width: 1, height: 1) {
    		state "floortemp", label:'Floor Temp\r\n${currentValue}'
		}
        standardTile("awayholiday", "awayholiday", inactiveLabel: false, decoration: "flat", width: 2, height: 1) {
			state "off", action:"away", label:'Set Away / Standby' // icon TBC
			state "away", action:"awayOff", label:'Away Activated\r\nPress to cancel' // icon TBC
			state "holiday", action:"holidayOff", label:'Holiday - Press\r\nto cancel all' // icon TBC
		}
		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 1, height: 1) {
			state "default", action:"refresh", label: "Refresh", icon:"st.secondary.refresh"
		}
		
		main "temperatureDisplay"
		details(
				[
				"temperatureDisplay",
				"lowerthermostatSetpoint", "raisethermostatSetpoint",
                "decreaseTime", "holdtime", "increaseTime", 
				"nextSetpointText", "setTempHold", "refresh", 
                "awayholiday", "floortemp", "statusText"])
	}
}


def push(button)
{
    try {
        log.debug "Running function from button press: ${button}"
        "${button}"()
    }
    catch (e) {
        log.debug "Failed to run function: ${e}"
    }
}

def refreshdelay(int delay)
{
    if (delay == null) {delay = Random().nextInt(20) + 1}
    log.debug "Got a delayed refresh message"
    runIn(delay, refresh)
}
def refresh()
{
    //Default refresh method which calls immediate update and ensures it is scheduled
	log.debug "Refreshing thermostat data from parent"
	refreshinfo()
    runEvery5Minutes("refreshinfo")
}
private refreshinfo()
{
	//Actually get a refresh from the parent/Neohub
    parent.childRequestingRefresh(device.deviceNetworkId)
    if (parent.childGetDebugState()) {
    	state.debug = true
    } else {
    	state.debug = false
    }
}

def installed()
{
	//Here we have a new device so lets ensure that all the tiles are correctly filled with something
    log.debug "installed()"
	updated()
}
def updated()
{
	//On every update, lets reset hold time and holding temperature
    log.debug "updated()"
    
    def cmds = []
    cmds << sendEvent(name: "statusText", value: "Please press refresh now")
    cmds << sendEvent(name: "nextSetpointText", value: "Please press refresh now")
    
    cmds << sendEvent(name: "holdtime", value: "0:00")
    
    cmds << sendEvent(name: "supportedThermostatFanModes", value: JsonOutput.toJson(["off"]), isStateChange: true)
    cmds << sendEvent(name: "supportedThermostatModes", value:  JsonOutput.toJson(["off", "heat"]), isStateChange: true)

    
    cmds << sendEvent(name: "temperature", value: "5")
    cmds << sendEvent(name: "thermostatSetpoint", value: "5")
    cmds << sendEvent(name: "coolingSetpoint", value: "5")
    cmds << sendEvent(name: "heatingSetpoint", value: "5")
    
    cmds << sendEvent(name: "thermostatFanMode", value: "off")
    cmds << sendEvent(name: "thermostatMode", value: "heat")
    
    return cmds
}


def processNeoResponse(response)
{
	//Response received from Neo Hub so process it (mainly used for refresh, but could also process the success/fail messages)
	def statusTextmsg = ""
	def cmds = []

	if (state.debug) log.debug response
    if (response.containsKey("devices") && response.devices[0].containsKey("STANDBY"))
    {
    	//If we have a device key and STANDBY key then it is probably a refresh command
        response = response.devices[0]
        //First store the update/refresh date/time
        def dateTime = new Date()
        def updateddatetime = dateTime.format("yyyy-MM-dd HH:mm", location.timeZone)
        cmds << sendEvent(name: "statusText", value: "Last refreshed info at ${updateddatetime}", displayed: false, isStateChange: true)
        
        //Now process information shared between all devices including standby/away and Floor Temp
        if (response.containsKey("STANDBY") && response.containsKey("HOLIDAY")) {
        	//Update standby/away status
            if (response.STANDBY == false && response.HOLIDAY == false) {
                cmds << sendEvent(name: "awayholiday", value: "off", displayed: true)
            }
            else if (response.STANDBY == true) {
                cmds << sendEvent(name: "awayholiday", value: "away", displayed: true)
            }
            else if (response.HOLIDAY == true) {
                cmds << sendEvent(name: "awayholiday", value: "holiday", displayed: true)
            }
        }
        if (response.containsKey("CURRENT_FLOOR_TEMPERATURE")) {
        	//Update the floor temperature in case anybody cares!
            def flrtempstring
            if (response.CURRENT_FLOOR_TEMPERATURE >= 127) {
            	flrtempstring = "N/A"
            }
            else {
            	flrtempstring = response.CURRENT_FLOOR_TEMPERATURE
            }
        	cmds << sendEvent(name: "floortemp", value: flrtempstring, displayed: false)
        }

        if (response.containsKey("STAT_MODE")) {
        	//This is used to identify what type of device it is
			if (response.STAT_MODE.containsKey("THERMOSTAT")) {
                if (response.STAT_MODE.THERMOSTAT == true) {
                	//This device is a thermostat so lets update it!
					if (response.containsKey("HEATING")) {
                        //Update the tiles to show that it is currently calling for heat from the boiler
                        if (response.HEATING) {
                            cmds << sendEvent(name: "thermostatOperatingState", value: "heating", displayed: true)
                        } else {
                            cmds << sendEvent(name: "thermostatOperatingState", value: "idle", displayed: true)
                        }
                    }
                    if (response.containsKey("HOLD_TIME") && response.containsKey("HOLD_TEMPERATURE") && response.containsKey("NEXT_ON_TIME")) {
                    	//Update the set temp text or holding time
                        if (response.HOLD_TIME == "0:00") {
                            //Here we have zero hold time so run until next on time
                            statusTextmsg = "Set to " + response.CURRENT_SET_TEMPERATURE + "C until "
                            if (response.NEXT_ON_TIME.reverse().take(3).reverse() == "255") {
                                //If we see 255:255 in hh:mm field then it is set permanently (hence just check the last three digits)
                                statusTextmsg = statusTextmsg + "changed"
                            }
                            else {
                                //Otherwise add on the time for next change
                                statusTextmsg = statusTextmsg + response.NEXT_ON_TIME.reverse().take(5).reverse()
                            }
                            if (response.containsKey("HOLIDAY") && response.containsKey("STANDBY")) {
                            	//If we have a holiday flag set to true, and a standby flag set to false then display remaining holiday (rounded down)
                            	if ((response.HOLIDAY == true) && (response.STANDBY == false)) {
                            		statusTextmsg = statusTextmsg + "\r\nHoliday for " + response.HOLIDAY_DAYS + " more days"
                                }
                            }
                            //Now send the update
                            cmds << sendEvent(name: "nextSetpointText", value: statusTextmsg)
                            //Lastly if we are here then there should be no holds in place - hence ensure the button doesn't say cancel hold
                            chooseSetTempOrSetHold()
                        }
                        else {
                            //Here we do have a hold time so display temp and time
                            statusTextmsg = "Holding " + response.HOLD_TEMPERATURE + "°C for " + response.HOLD_TIME
                            cmds << sendEvent(name: "nextSetpointText", value: statusTextmsg, displayed: false)
        					cmds << sendEvent(name: "setTempHold", value: "cancelHold", displayed: false)
                        }
                    }
                    if (response.containsKey("CURRENT_TEMPERATURE") && response.containsKey("CURRENT_SET_TEMPERATURE")) {
                        //Got a temperature so update the tile
                        log.debug "Refresh - Temperature is: " + response.CURRENT_TEMPERATURE + " - Setpoint is: " + response.CURRENT_SET_TEMPERATURE + " - Calling for heat? " + response.HEATING
                        cmds << sendEvent(name: "temperature", value: response.CURRENT_TEMPERATURE, displayed: true)

                        //Got a set temperature so update it if above 6 (legacy from old firmware setting 5 for away mode, but keeping it for now)
                        def settempint = response.CURRENT_SET_TEMPERATURE.toBigDecimal() //.toBigDecimal().toInteger()
                        if (settempint >= 6) {
                            cmds << sendEvent(name: "thermostatSetpoint", value: settempint, displayed: false)
                            cmds << sendEvent(name: "heatingSetpoint", value: settempint, displayed: false)
                            cmds << sendEvent(name: "coolingSetpoint", value: settempint, displayed: false)
                        }
                    }
                }
			}
        }
	}
    else if (response.containsKey("result"))
    {
        //We have a success result from a command so process it here by pasting in the response and updating tile
        log.debug "success on last command: " + response
        //Would love to refresh information at this point, but it will fail as the Neostats take a while to update
        //refreshinfo()
        cmds << sendEvent(name: "statusText", value: response)
        cmds << sendEvent(name: "nextSetpointText", value: "Waiting for next refresh", displayed: false)
    }
    return cmds
}


def durationUp() {
	def cmds = []
    def durationMins
    log.debug device.currentValue("holdtime")
    durationMins = timeStringToMins(device.currentValue("holdtime"))
    if (durationMins == 300) {
    	durationMins = 0
    }
    else {
    	durationMins = durationMins + 30
    }
    log.debug durationMins
    chooseSetTempOrSetHold(durationMins)
    cmds << sendEvent(name: "holdtime", value: "${minsToTimeString(durationMins)}", displayed: false, isStateChange: true)
    return cmds
}

def durationDown() {
	def cmds = []
    def durationMins
    durationMins = timeStringToMins(device.currentValue("holdtime"))
    if (durationMins == 0) {
    	durationMins = 300
    }
    else {
    	durationMins = durationMins - 30
    }
    chooseSetTempOrSetHold(durationMins)
    cmds << sendEvent(name: "holdtime", value: "${minsToTimeString(durationMins)}", displayed: false, isStateChange: true)
    return cmds
}

def SetpointUpzz() {
	//Called by tile to increase set temp box
	def cmds = []
	def newtemp = device.currentValue("manualSetpoint").replaceAll("°C", "") + 0.5
    cmds << sendEvent(name: "manualSetpoint", value: "${newtemp}°C")
	chooseSetTempOrSetHold()
    return cmds
}
def SetpointDownzz() {
    //Called by tile to decrease set temp box
	def cmds = []
    def newtemp = device.currentValue("manualSetpoint").replaceAll("°C", "") - 0.5
    cmds << sendEvent(name: "manualSetpoint", value: "${newtemp}°C")
	chooseSetTempOrSetHold()
    return cmds
}

def away() {
	//Set away mode on
	def cmds = []
	if (state.debug) log.debug "${device.label}: away()"
	parent.childFrostOn(device.deviceNetworkId)
    runIn(5, refresh)
    cmds << sendEvent(name: "awayholiday", value: "away", displayed: true)
    return cmds
}
def awayOff() {
	//Set away mode off
	def cmds = []
	if (state.debug) log.debug "${device.label}: awayOff()"
	parent.childFrostOff(device.deviceNetworkId)
    runIn(5, refresh)
    cmds << sendEvent(name: "awayholiday", value: "off", displayed: true)
    return cmds
}
def holidayOff() {
	//Cancel holiday mode
	def cmds = []
	if (state.debug) log.debug "${device.label}: holidayOff()"
	parent.childCancelHoliday(device.deviceNetworkId)
    runIn(5, refresh)
    cmds << sendEvent(name: "awayholiday", value: "off", displayed: true)
    return cmds
}

def chooseSetTempOrSetHold(mins = null) {
	def cmds = []
	if (mins == null) { mins = timeStringToMins(device.currentValue("holdtime")) }
	if (mins == 0) {
    	//We have 0:00 as hh:mm, so somebody would only be able to set temp
        cmds << sendEvent(name: "setTempHold", value: "setTemp", displayed: false)
    }
    else {
    	//Otherwise they can set a hold
    	cmds << sendEvent(name: "setTempHold", value: "setHold", displayed: false)
    }
    return cmds
}

def boostOneHour() {
	//Boost mode for 1 hour
	boostTempHours(0, 1)
    
    //def desiredTemp = device.currentValue("temperature").toInteger() + 2
    
    //Send the command to the Neohub
	//parent.childHold(desiredTemp.toString(), "1", "0", device.deviceNetworkId)
}

def boostHours(int durationHours) {
	//Boost mode for custom number of hours
	boostTempHours(0, durationHours)
    
	//def desiredTemp = device.currentValue("thermostatSetpoint").toInteger() + 2
    
    //Also target 1 hour if not specified
    //if (desiredHours==null) desiredHours = 1
    
    //Finally send the command to the Neohub
	//parent.childHold(desiredTemp.toString(), desiredHours.toString(), "0", device.deviceNetworkId)
}

def boostTempHours(int desiredTemp, int desiredHours) {
	//Boost to chosen target for chosen time
    //If these are set to 0 then it will use defaults of 1 hour and (temperature)+2 (since that will ensure the thermostat turns on with a 1 degree switching range)
    
    if (desiredTemp==0) desiredTemp = device.currentValue("temperature") + 2
    //Also target 1 hour if not specified
    if (desiredHours==0) desiredHours = 1
    
    //Send command to neohub
	parent.childHold(desiredTemp.toString(), desiredHours.toString(), "0", device.deviceNetworkId)
    runIn(5, refresh)
}

def setTempHoldOn() {
	def cmds = []
    def newtemp = device.currentValue("manualSetpoint").replaceAll("°C", "")
    def currentholdtime = device.currentValue("holdtime")
    
    if (state.debug) log.debug "${device.label}: Set temp hold to ${newtemp} for ${currentholdtime} - setTempHoldOn()"

    if (currentholdtime == "0:00") {
    	//Hold time is zero, so use set temp or schedule override
		cmds << sendEvent(name: "setTempHold", value: "tempWasSet", displayed: false, isStateChange: true)
        parent.childSetTemp(newtemp.toString(), device.deviceNetworkId)
        runIn(5, refresh)
    }
    else {
    	//Hold time is above zero, so hold for the chosen time (either timer or thermostat)
        if (state.debug) log.debug hours + "hours, " + minutes + "mins of hold time"
        cmds << sendEvent(name: "setTempHold", value: "cancelHold", displayed: false, isStateChange: true)
        def hoursandmins = timeStringToHoursMins(currentholdtime)
        parent.childHold(newtemp.toString(), hoursandmins[0], hoursandmins[1], device.deviceNetworkId)
        runIn(5, refresh)
	}
    //Also update the tile immediately (shouldn't really do this as should wait until the next update,
    //but if we dont do it now, then Alexa and Google Home get really confused)
    cmds << sendEvent(name: "thermostatSetpoint", value: newtemp, displayed: false)
	cmds << sendEvent(name: "heatingSetpoint", value: newtemp, displayed: false)
	cmds << sendEvent(name: "coolingSetpoint", value: newtemp, displayed: false)
    return cmds
}

def setTempHoldOff() {
	//Cancel the temp hold
	def cmds = []
	if (state.debug) log.debug "${device.label}: cancel hold/temp - setTempHoldOff()"
    if (device.currentValue("raisethermostatSetpoint") == "On") {
    	//The device is a timer so use timer hold with 0 mins to turn off hold
        parent.childTimerHoldOn("0", device.deviceNetworkId)
        runIn(5, refresh)
    }
    else {
    	//The device is a normal thermostat so use cancel hold
		parent.childCancelHold(device.deviceNetworkId)
        runIn(5, refresh)
    }
	chooseSetTempOrSetHold()
    return cmds
}


//These commands are used by Alexa
def setHeatingSetpoint(number) {
	def cmds = []
    //First convert the requested temperature to an integer (it will round down)
    def integerNumber = number.toInteger()
    //If we were trying to increase the temperature then we need to add one (or we get stuck rounding down forever)
    if (number - device.currentValue("thermostatSetpoint") == 0.5) {integerNumber++}
    //Now update the parameter and then send to the thermostat
	cmds << sendEvent(name: "thermostatSetpoint", value: number, displayed: false)
	cmds << sendEvent(name: "heatingSetpoint", value: number, displayed: false)
	cmds << sendEvent(name: "coolingSetpoint", value: number, displayed: false)
	parent.childSetTemp(number.toString(), device.deviceNetworkId)
    runIn(5, refresh)
    return cmds
}
def setThermostatSetpoint(number) {
	def cmds = []
    //First convert the requested temperature to an integer (it will round down)
    def integerNumber = number.toInteger()
    //If we were trying to increase the temperature then we need to add one (or we get stuck rounding down forever)
    if (number - device.currentValue("thermostatSetpoint") == 0.5) {integerNumber++}
    //Now update the parameter and then send to the thermostat
	cmds << sendEvent(name: "thermostatSetpoint", value: number, displayed: false)
	cmds << sendEvent(name: "heatingSetpoint", value: number, displayed: false)
	cmds << sendEvent(name: "coolingSetpoint", value: number, displayed: false)
	parent.childSetTemp(number.toString(), device.deviceNetworkId)
    runIn(5, refresh)
    return cmds
}
def setTemperature(number) {
	def cmds = []
    //First convert the requested temperature to an integer (it will round down)
    def integerNumber = number.toInteger()
    //If we were trying to increase the temperature then we need to add one (or we get stuck rounding down forever)
    if (number - device.currentValue("thermostatSetpoint") == 0.5) {integerNumber++}
    //Now update the parameter and then send to the thermostat
	cmds << sendEvent(name: "thermostatSetpoint", value: number, displayed: false)
	cmds << sendEvent(name: "heatingSetpoint", value: number, displayed: false)
	cmds << sendEvent(name: "coolingSetpoint", value: number, displayed: false)
	parent.childSetTemp(number.toString(), device.deviceNetworkId)
    runIn(5, refresh)
    return cmds
}

private timeStringToMins(String timeString){
	if (timeString?.contains(':')) {
    	def hoursandmins = timeString.split(":")
        def mins = hoursandmins[0].toInteger() * 60 + hoursandmins[1].toInteger()
        if (state.debug) log.debug "${timeString} converted to ${mins}" 
        return mins
    }
}

private minsToTimeString(intMins) {
    //log.debug intMins
	def timeString =  "${(intMins.toInteger()/60).toInteger()}:${(intMins.toInteger()%60).toString().padLeft(2, "0")}"
    if (state.debug) log.debug "${intMins} converted to ${timeString}"
    return timeString
}

private timeStringToHoursMins(String timeString){
    //log.debug timeString
	if (timeString?.contains(':')) {
    	def hoursMins = timeString.split(":")
        if (state.debug) log.debug "${timeString} converted to ${hoursMins[0]}:${hoursMins[1]}"
        return hoursMins
    }
}

private minsToHoursMins(intMins) {
	def hoursMins = []
    hoursMins << (intMins.toInteger()/60).toInteger()
    hoursMins << (intMins.toInteger()%60).toInteger()
    if (state.debug) log.debug "${intMins} converted to ${hoursMins[0]}:${hoursMins[1]}" 
    return hoursMins
}

//Dont use any of these yet as I havent worked out why they would be needed! 
//Just log that they were triggered for troubleshooting
private heat() {
	log.debug "heat()"
}
private emergencyHeat() {
	log.debug "emergencyHeat()"
}
def setThermostatMode(chosenmode) {
	log.debug "setThermostatMode() - ${chosenmode}"
}
private fanOn() {
	log.debug "fanOn()"
}
private fanAuto() {
	log.debug "fanAuto()"
}
private fanCirculate() {
	log.debug "fanCirculate()"
}
def setThermostatFanMode(chosenmode) {
	log.debug "setThermostatFanMode() - ${chosenmode}"
}
private cool() {
	log.debug "cool()"
}
private setCoolingSetpoint(number) {
	log.debug "setCoolingSetpoint() - ${number}"
}
private auto() {
	log.debug "auto()"
}
