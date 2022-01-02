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
 *  Neo Plug (Child Device of Neo Hub Bridge)
 *
 *  Author: Chris Charles (cjcharles0)
 *  Date: 2017-04-26
 */

import groovy.json.JsonSlurper

metadata {
	definition (name: "Heatmiser Neo Plug", namespace: "cjcharles0", author: "Chris Charles")
    {
    	//capability "Refresh"
        //capability "Actuator"
        capability "Relay Switch"
        capability "Switch"
        
		command "push"
        command "refresh" //This is used for the overall refresh process
        
        command "off"
        command "on"
        
        command "away" // Custom
		command "awayOff" // Custom
		command "holidayOff" // Custom

        attribute "holdtime","string" // Custom for how long to hold for
		attribute "statusText", "string" // Custom for neohub response
		attribute "awayholiday", "string"
		attribute "floortemp", "number"
        
        
  		attribute "temperature","number" // Temperature Measurement
	}
    
    preferences {
        section {
        	//Preferences here
        }
	}

//Thermostat Temp and State
	tiles(scale: 2) {

		valueTile("holdtime", "device.holdtime", inactiveLabel: false, decoration: "flat", width: 1, height: 1) {
			state "default", label:'${currentValue}' // icon TBC
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
				"holdtime", 
				"nextSetpointText", "refresh", 
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
			if (response.STAT_MODE.containsKey("TIMECLOCK")) {
                if (response.STAT_MODE.TIMECLOCK == true) {
                	//This device is a timer so lets update it (starting by setting it to On/Off mode)
                    cmds << sendEvent(name: "raisethermostatSetpoint", value: "On", displayed: false)
                    cmds << sendEvent(name: "lowerthermostatSetpoint", value: "Off", displayed: false)
                    if (response.DEVICE_TYPE == 7) {
                    	//Device is a Timeclock
                        cmds << sendEvent(name: "manualSetpoint", value: "Timer Boost 1h", displayed: false)
                    }
                    else if (response.DEVICE_TYPE == 6) {
                    	//Device is a Neoplug
                        cmds << sendEvent(name: "manualSetpoint", value: "Neoplug Control", displayed: false)
                    }
                }
			}
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


private on() {
    parent.childTimerOn(device.deviceNetworkId)
    runIn(5, refresh)
}
private off() {
	parent.childTimerOff(device.deviceNetworkId)
    runIn(5, refresh)
}
def auto() {
	log.debug "auto()"
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
