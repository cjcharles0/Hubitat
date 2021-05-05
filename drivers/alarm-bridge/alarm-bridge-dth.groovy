/**
 *  Copyright 2017 Chris Charles and LeeF Automation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Alarm Bridge Controller for Hubitat
 *
 *  Author: Chris Charles (cjcharles0) and LeeF Automation
 *  Date: 2020-10-18
 *  Version: 1.2
 */

import groovy.json.JsonSlurper

metadata
{
	definition (name: "Alarm Bridge", namespace: "cjcharles0", author: "Chris Charles")
	{
		capability "Refresh"
		capability "Configuration"
		
		attribute "alarmStatus", "string"
		attribute "alarmEvents", "string"
		attribute "problemtext", "string"
		attribute "armaway", "string"
		attribute "armhome", "string"
		attribute "disarm", "string"
		
		command "AlarmArmAway"
		command "AlarmArmHome"
		command "AlarmDisarm"
		command "AlarmTrigger"
        
        command "push"
		command "ESPRestart"
		
		command "CreateZoneDevices"
		command "RemoveZoneDevices"
        
		command "childOff"
		command "childOn"
		command "childRefresh"
        
		(1..2).each { n ->
			command "on$n"
			command "off$n"
		}
	}

	simulator {
	}
	
	preferences {
		input name: "ip", type: "string", title:"Alarm IP Address", description: "e.g. 192.168.1.10", required: true, displayDuringSetup: true
		input name: "createcontrolchilddevices", type: "bool", title: "Create child devices that control the alarm", description: "true or false", required: true, displayDuringSetup: true
		input name: "changehsm", type: "bool", title: "Update HSM mode when alarm arms/disarms", description: "true or false", required: true, displayDuringSetup: true
		input name: "prename", type: "string", title:"Add this before child zone names", description: "e.g. 'Zone' would give 'Zone Kitchen'", required: false, displayDuringSetup: true
		input name: "postname", type: "string", title:"Add this after child zone names", description: "e.g. 'Zone' would give 'Kitchen Zone'", required: false, displayDuringSetup: true
		input name: "alarmtriggermethod", type: "enum", title: "Method to trigger alarm", options: ["Serial", "IO"], description: "Default IO if unsure", required: true, displayDuringSetup: true
		input name: "inactivityseconds", type: "string", title:"Motion sensor inactivity timeout", description: "override the default of 20s (60s max)", required: false, displayDuringSetup: false
		input name: "password", type: "password", title:"Password", required:false, displayDuringSetup:false
	}

	tiles (scale: 2)
	{

		valueTile("alarmStatus", "device.alarmStatus", decoration: "flat", width: 2, height: 1)
		{
			state "default", label:'${currentValue}'
		}
		valueTile("alarmEvents", "device.alarmEvents", decoration: "flat", width: 2, height: 1)
		{
			state "default", label:'${currentValue}'
		}
		valueTile("problemtext", "device.problemtext", decoration: "flat", width: 2, height: 1)
		{
			state "default", label:'${currentValue}'
		}

		standardTile("AlarmArmAway", "armaway", height: 2, width:2, decoration:"flat", inactiveLabel: false)
		{
			state "inactive", label:"Away", action:"AlarmArmAway", backgroundColor:"#D8D8D8"
			state "changing", label:"Arming Away", action:"", backgroundColor:"#FF9900"
			state "active", label:"Armed Away", action:"", backgroundColor:"#00CC00"
		}
		standardTile("AlarmArmHome", "armhome", height: 2, width: 2, decoration:"flat", inactiveLabel: false)
		{
			state "inactive", label:"Home", action:"AlarmArmHome", backgroundColor:"#D8D8D8"
			state "changing", label:"Arming Home", action:"", backgroundColor:"#FF9900"
			state "active", label:"Armed Home", action:"", backgroundColor:"#00CC00"
		}
		standardTile("AlarmDisarm", "disarm", height: 2, width: 2, decoration:"flat", inactiveLabel: false)
		{
			state "inactive", label:"Disarm", action:"AlarmDisarm", backgroundColor:"#D8D8D8"
			state "changing", label:"Disarming", action:"", backgroundColor:"#FF9900"
			state "active", label:"Disarmed", action:"", backgroundColor:"#00CC00"
		}
		
		// This will create a tile for all zones up to 32 (which should cover most boards including Wired ones)
		// We can have all of these tiles, but not display them
		(1..32).each { n ->
			valueTile("zonename$n", "panelzonename$n", height: 1, width: 2) {
				state "default", label:'${currentValue}', backgroundColor:"#FFFFFF"
			}
			standardTile("zone$n", "panelzone$n", height: 1, width: 1) {
				state "inactive", label:"Inactive", action:"", icon:""
				state "active", label:"Active", action:"", icon:"", backgroundColor:"#00CC00"
				state "closed", label:"Closed", action:"", icon:""
				state "open", label:"Open", action:"", icon:"", backgroundColor:"#00CC00"
				state "bypass", label:"Bypass", action:"", icon:"", backgroundColor:"#FFD800"
				state "smoke", label:"Smoke", action:"", icon:"", backgroundColor:"#FF3F00"
				state "clear", label:"Clear", action:"", icon:""
			}
		}
        
		// This will create an output tile as needed for control of some alarm panels (not Visonic)
		(1..8).each { n ->
			valueTile("outputname$n", "paneloutputname$n", height: 1, width: 2) {
				state "default", label:'${currentValue}', backgroundColor:"#FFFFFF"
			}
			standardTile("output$n", "paneloutput$n", height: 1, width: 1) {
				state "on", label:"On", action:"off$n", icon:"", backgroundColor:"#00CC00"
				state "off", label:"Off", action:"on$n", icon:""
			}
		}
        
		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 1, height: 1)
		{
			state "default", label:"", action:"refresh", icon:"st.secondary.refresh"
		}
	  
		standardTile("configure", "device.configure", inactiveLabel: false, width: 1, height: 1, decoration: "flat")
		{
			state "default", label:'', action:"configure", icon:"st.secondary.configure"
		}

		valueTile("ip", "ip", decoration: "flat", width: 2, height: 1)
		{
			state "default", label:'ST IP Addr:\r\n${currentValue}'
		}
		
		standardTile("createtile", "device.createzonedevices", inactiveLabel: false, decoration: "flat", width: 2, height: 1)
		{
			state "default", label:'Create Zone Devices', action:"CreateZoneDevices", icon: "st.unknown.zwave.remote-controller"
		}
		standardTile("removetile", "device.removezonedevices", inactiveLabel: false, decoration: "flat", width: 2, height: 1)
		{
			state "default", label:'Remove Zone Devices', action:"RemoveZoneDevices", icon: "st.samsung.da.washer_ic_cancel"
		}
	}

	main(["AlarmStatus"])
	details(["alarmStatus", "alarmEvents", "AlarmArmAway", "AlarmArmHome", "AlarmDisarm",
			 
		// Add all your zones here (zonenameX first then zoneX)- e.g. add "zonename29", "zone29" if you want to display zone 29 in the alarm panel
		"zonename1", "zone1", "zonename2", "zone2", "zonename3", "zone3", "zonename4", "zone4",
		"zonename5", "zone5", "zonename6", "zone6", "zonename7", "zone7", "zonename8", "zone8", 
			 
		// Add any outputs here by uncommenting the row and then add in the same format as for zones, but without output rather than zone
		// "outputname1", "output1",
             
		"refresh", "configure", "problemtext", "alarm", "createtile", "removetile", "ip"
	])
}

def AlarmArmAway()
{
	// Send an arm away command to the alarm and log that it is changing
	log.debug "armaway()"
	sendEvent(name: "armaway", value: "changing")
	sendEvent(name: "armhome", value: "inactive", displayed: false)
	sendEvent(name: "disarm", value: "inactive", displayed: false)
	sendEvent(name: "alarmStatus", value: "Arming Away", displayed: false)
	getAction("/armaway")
}

def AlarmArmHome()
{
	// Send an arm home command to the alarm and log that it is changing
	log.debug "armhome()"
	sendEvent(name: "armaway", value: "inactive", displayed: false)
	sendEvent(name: "armhome", value: "changing")
	sendEvent(name: "disarm", value: "inactive", displayed: false)
	sendEvent(name: "alarmStatus", value: "Arming Home", displayed: false)
	getAction("/armhome")
}

def AlarmDisarm()
{
	// Send a disarm command to the alarm and log that it is changing
	// May need the required output connected to a keyswitch zone
	log.debug "disarm()"
	sendEvent(name: "armaway", value: "inactive", displayed: false)
	sendEvent(name: "armhome", value: "inactive", displayed: false)
	sendEvent(name: "disarm", value: "changing")
	sendEvent(name: "alarmStatus", value: "Disarming", displayed: false)
	getAction("/disarm")
}

def AlarmTrigger()
{
	// Try to manually trigger the alarm (there are several ways possible - some working!)
	log.debug "Trying to trigger the alarm by the ${alarmtriggermethod} method"
	if (alarmtriggermethod != null) {
		def outputinfo = ""
		switch (alarmtriggermethod) {
			case ["IO"]:
				outputinfo = "?method=IO"
				break
			case ["Serial"]:
				outputinfo = "?method=Serial"
				break
		}
		getAction("/alarm${outputinfo}")
	}
}

def ESPRestart()
{
	// Can power cycle the ESP device if needed
	log.debug "restart() - Reboot the ESP (restart is safer than reset)"
	getAction("/restart")
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

def CreateZoneDevices()
{
	// This will create child devices from the /getzonenames response
	log.debug "Requesting List of Alarm Zones"
	getAction("/getzonenames")
}

def RemoveZoneDevices()
{
	// This will remove all child devices
	log.debug "Removing Child Zone Devices"
	try
	{
		getChildDevices()?.each
		{
			try
			{
                log.debug "Removing ${it.deviceNetworkId} child device"
				deleteChildDevice(it.deviceNetworkId)
			}
			catch (e)
			{
				log.debug "Error deleting ${it.deviceNetworkId}, probably locked into a SmartApp: ${e}"
			}
		}
	}
	catch (err)
	{
		log.debug "Either no child devices exist or there was an error finding child devices: ${err}"
	}
}

// This will configure the device to talk to the smart home platform
def configure()
{
	def hub = location.hubs[0]
	def cmds = []
	log.debug "Configuring Alarm (getting zones+types, configuring IP/port/timeout)"

	def requeststring = "/config?ip_for_st=${hub.getDataValue("localIP")}&port_for_st=${hub.getDataValue("localSrvPortTCP")}"

	if (inactivityseconds?.isInteger())
	{
		// Inactivityseconds is both populated and an integer, so lets send it to the Wemos
		requeststring = requeststring + "&inactivity_seconds=${settings.inactivityseconds}"
	}

	// Send the details to Smart Home platform
	cmds << getAction(requeststring)
	return cmds
}

def refresh()
{
	log.debug "refresh()"
	def hub = location.hubs[0]
	// SendEvents should be before any getAction, otherwise getAction does nothing
	sendEvent(name: "ip", value: hub.getDataValue("localIP")+"\r\nPort: "+hub.getDataValue("localSrvPortTCP"), displayed: false)

	// Now refresh Alarm status
	getAction("/refresh")
}

def installed()
{
	log.debug "installed()"
	//configure()
}

def updated()
{
	log.debug "updated()"
	configure()
}

def ping()
{
	log.debug "ping()"
	getAction("/ping")
}

// These functions are needed due to the alarm device capability and hence will serve to arm/disarm the alarm (though unlikely to be called)
def stop()
{
	AlarmDisarm()
}

def off()
{
	AlarmDisarm()
}

def strobe()
{
	AlarmArmHome()
}

def siren()
{
	AlarmArmAway()
}

def both()
{
	AlarmTrigger()
}

def parse(description)
{
	def map = [:]
	def events = []
	def cmds = []
	
	if(description == "updated") return
	def descMap = parseDescriptionAsMap(description)

	if (descMap == null)
	{
		log.debug "Not valid json response/message"
		log.debug description
		return
	}

	def body = new String(descMap["body"].decodeBase64())

	def slurper = new JsonSlurper()
	def result;
	try
	{
		result = slurper.parseText(body)
	}
	catch (e)
	{
		log.debug "Invalid response from system: " + body
		return
	}
	
	log.debug result
	
	if (result.containsKey("update_type"))
	{
		switch (result.update_type)
		{
			case ["system_status"]: // alarm status update
				handleAlarmStatus(result)
				break

			case ["zone_status"]:	// single zone status update
				handleZoneStatus(result)
				break

			case ["output_status"]:	// single output status update
				handleOutputStatus(result)
				break

			case ["refresh"]:	// capability to process alarm status update and also zone update
				handleRefresh(result)
				break

			case ["create_zones", "zone_names"]:
				log.debug "Handling getzonenames page"
				handleCreateZones(result)
				break

			case ["remove_zones"]:
				log.debug "Removing zones where possible as received command from bridge"
				RemoveZoneDevices()
				break
		}
		// If we receive a problem report then lets check if we need to update the tile with new problems (this can happen in multiple status updates)
		if (result.containsKey("problem_report"))
		{
			if (result.problem_report == problemtext)
			{
				// Do nothing here as problem text has not changed
			}
			else
			{
				// We have an updated problem text so push it onto the tile
				sendEvent(name: "problemtext", value: "${result.problem_report}", displayed: true, isStateChange: true)
			}
		}
	}
}

def sendData(message) {
    if (message.contains(" ")) {
        def parts = message.split(" ")
        // name will be the DNI and value will be the switch status
        def name  = parts.length>0?parts[0].trim():null
        def value = parts.length>0?parts[1].trim():null
        //log.debug name
        if (value == "on") {
            // First set switch back to off if we can find the correct child device
            try
            {
                def childdevice = getChildDevices()?.find { it.deviceNetworkId == name }
                childdevice.off()
            }
            catch (e)
            {
                log.debug "Failed to find child - ${e}"
            }
            // And now we can change the alarm status
            switch (name) {
                case ["alarmdisarm"]:
	    	    	log.debug "Disarming Alarm"
	    	    	AlarmDisarm()
	    	    	break
                
                case ["alarmarmaway"]:
		        	log.debug "Arming Alarm Away"
		        	AlarmArmAway()
		        	break
                
                case ["alarmarmhome"]:
		        	log.debug "Arming Alarm Home"
		        	AlarmArmHome()
		        	break
            }
        }
    }
}

private handleRefresh(result)
{
	// Handle the alarm status
	handleAlarmStatus(result)
	
	// And now handle the status for each zone
	for (def curzone in result.zones)
	{
		handleZoneStatus(curzone.zone_id, curzone.zone_status)
	}
}

private handleAlarmStatus(result)
{
	// If we receive a key containing 'stat_update_from' then we should add it to the event log and update tile
    // Only do this if an organic update (i.e. not from refresh command)
	if (result.containsKey("stat_update_from"))
	{
		def dateTime = new Date()
		def sensorStateChangedDate = dateTime.format("yyyy-MM-dd HH:mm", location.timeZone)
		def status_string = result.stat_str + " by " + result.stat_update_from + " at " + sensorStateChangedDate
		// Send the status string that we have built
		sendEvent(name: "alarmEvents", value: "${status_string}")
	}
	// Now update 
	switch (result.stat_str)
	{
		case ["Disarmed", "Disarm", "Ready"]:
			sendEvent(name: "disarm", value: "active", displayed: false)
			sendEvent(name: "armaway", value: "inactive", displayed: false)
			sendEvent(name: "armhome", value: "inactive", displayed: false)
			sendEvent(name: "alarmStatus", value: "Disarmed")
			if (settings.changehsm) {
				log.debug "Updating HSM"
				sendLocationEvent(name: "hsmSetArm", value: "disarm")
			}
			log.debug "Disarmed Status found"
			break

		case ["Not Ready"]:
			sendEvent(name: "disarm", value: "inactive", displayed: false)
			sendEvent(name: "armaway", value: "inactive", displayed: false)
			sendEvent(name: "armhome", value: "inactive", displayed: false)
			sendEvent(name: "alarmStatus", value: "Not Ready")
			log.debug "Not-ready Status found"
			break

		case ["Armed Away", "Arm Away"]:
			sendEvent(name: "disarm", value: "inactive", displayed: false)
			sendEvent(name: "armaway", value: "active", displayed: false)
			sendEvent(name: "armhome", value: "inactive", displayed: false)
			sendEvent(name: "alarmStatus", value: "Armed Away")
			if (settings.changehsm) {
				log.debug "Updating HSM"
				sendLocationEvent(name: "hsmSetArm", value: "armAway")
			}
			log.debug "Armed Away Status found"
			break

		case ["Armed Home", "Arm Home"]:
			sendEvent(name: "disarm", value: "inactive", displayed: false)
			sendEvent(name: "armaway", value: "inactive", displayed: false)
			sendEvent(name: "armhome", value: "active", displayed: false)
			sendEvent(name: "alarmStatus", value: "Armed Home")
			if (settings.changehsm) {
				log.debug "Updating HSM"
				sendLocationEvent(name: "hsmSetArm", value: "armHome")
			}
			log.debug "Armed Home Status found"
			break

		case ["Exit Delay"]:
			sendEvent(name: "disarm", value: "inactive", displayed: false)
			sendEvent(name: "armaway", value: "changing")
			sendEvent(name: "armhome", value: "inactive", displayed: false)
			sendEvent(name: "alarmStatus", value: "Exit Delay")
			log.debug "Exit Delay Status found"
			break

		case ["Delay Alarm", "Confirm Alarm", "Perimeter Alarm"]:
			sendEvent(name: "disarm", value: "inactive", displayed: false)
			sendEvent(name: "armaway", value: "inactive", displayed: false)
			sendEvent(name: "armhome", value: "inactive", displayed: false)
			sendEvent(name: "alarmStatus", value: "Alarm Going Off!!")
			log.debug "Alarm Status found - Uh Oh!!"
			break

		default:
			log.debug "Unknown Alarm state received = ${result.stat_str}"
			break
	}
}

private handleZoneStatus(result)
{
	def thisZoneDeviceId = "alarmchildzone"+result.zone_id
	def curdevice = null
	try
	{
		// Got a zone status so first try to find the correct child device
		curdevice = getChildDevices()?.find { it.deviceNetworkId == thisZoneDeviceId }
	}
	catch (e)
	{
		log.debug "Failed to find child zone for zone " + zoneId + "exception ${e}"
	}

	if (curdevice == null)
	{
		log.debug "Failed to find child device for zone: " + zoneId + " expecting " + thisZoneDeviceId
	}
	else
	{
		// Check the device type for this child, not using this right now, but could do
		boolean isMotionDevice = (curdevice.capabilities.find { it.name == "MotionSensor" } != null)
		boolean isContactDevice = (curdevice.capabilities.find { it.name == "ContactSensor" } != null)
		boolean isSmokeDevice = (curdevice.capabilities.find { it.name == "SmokeDetector" } != null)
        
        //log.debug "motion ${isMotionDevice} - contact ${isContactDevice} - smoke ${isSmokeDevice}"

		// Now switch action/update based on what has happened to the zone
		switch (result.zone_status)
		{
			case ["Violated (Motion)", "Active"]:
				log.debug "Got Active zone: " + result.zone_id + ", which is called - " + curdevice
				if (isMotionDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "active", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "motion", value: "active") // Correct message
				}
				else if (isContactDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "open", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "contact", value: "open")
				}
				else if (isSmokeDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "smoke", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "smoke", value: "detected")
				}
				break

			case ["No Motion", "Inactive"]:
				log.debug "Got Inactive zone: " + result.zone_id + ", which is called - " + curdevice
				if (isMotionDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "inactive", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "motion", value: "inactive") // Correct message
				}
				else if (isContactDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "closed", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "contact", value: "closed")
				}
				else if (isSmokeDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "clear", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "smoke", value: "clear")
				}
				break

			case "Open":
				//log.debug "Got Open zone: " + result.zone_id + ", which is called - " + curdevice
				if (isMotionDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "active", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "motion", value: "active")
				}
				else if (isContactDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "open", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "contact", value: "open") // Correct message
				}
				else if (isSmokeDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "smoke", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "smoke", value: "detected")
				}
				break

			case "Closed":
				//log.debug "Got Closed zone: " + result.zone_id + ", which is called - " + curdevice
				if (isMotionDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "inactive", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "motion", value: "inactive")
				}
				else if (isContactDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "closed", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "contact", value: "closed") // Correct message
				}
				else if (isSmokeDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "clear", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "smoke", value: "clear")
				}
				break

			case ["Bypassed (Motion)", "Bypassed - Active"]:
				//log.debug "Got Active Bypassed zone: " + result.zone_id + ", which is called - " + curdevice
				if (isMotionDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "active", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "motion", value: "active") // Correct message
				}
				else if (isContactDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "open", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "contact", value: "open")
				}
				else if (isSmokeDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "smoke", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "smoke", value: "detected")
				}
				break

			case ["Bypassed", "Bypassed - Inactive"]:
				//log.debug "Got Inactive Bypassed zone: " + result.zone_id + ", which is called - " + curdevice
				if (isMotionDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "inactive", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "motion", value: "inactive") // Correct message
				}
				else if (isContactDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "closed", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "contact", value: "closed")
				}
				else if (isSmokeDevice)
				{
					//sendEvent(name: "panelzone"+result.zone_id, value: "clear", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "smoke", value: "clear")
				}
				break

			case "Tamper":
				//log.debug "Got Tamper for zone: " + result.zone_id + ", which is called - " + curdevice
				// We'll set it to open/motion for now, since at least that gives an indication something is wrong!
				if (isMotionDevice)
				{
					sendEvent(name: "panelzone"+result.zone_id, value: "active", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "motion", value: "active") // Correct message
				}
				else if (isContactDevice)
				{
					sendEvent(name: "panelzone"+result.zone_id, value: "open", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "contact", value: "open")
				}
				else if (isSmokeDevice)
				{
					sendEvent(name: "panelzone"+result.zone_id, value: "smoke", displayed: false, isStateChange: true)
					curdevice?.sendEvent(name: "smoke", value: "detected")
				}
                break

			default:
				log.debug "Unknown zone status received: ${result.zone_name} / ${result.zone_id} is ${result.zone_status}"
				break
		}
	}
}

private handleCreateZones(result)
{
	// This code will pull zone information out of the /getzonenamespage (and allows for zones not in order)
	def hub = location.hubs[0]
	log.debug "Handling getzonenames page"
	for (def curzone in result.zones)
	{
		// First setup the prepend and postpend names for the new child zone device
		def thisname = ""
		if (prename != null) {thisname = thisname + prename + " "}
		thisname = thisname + curzone.zonename
		if (postname != null) {thisname = thisname + " " + postname}
		
		def thisid = ""
		if (curzone.zone_id) {thisid = curzone.zone_id}
		if (curzone.zoneid) {thisid = curzone.zoneid}

		// Now try to find a child device with the right name - wrapped in try in case it fails to find any children
		def curchildzone = null
		try
		{
			if (curzone.zonetype == "Output")
			{
				// This is if you have an output device (used on some panels) - First update tile name then try to find child device
				sendEvent(name: "paneloutputname"+(thisid), value: thisname)
				log.debug "Trying to add child with name: ${thisname}, ID: alarmchildoutput${thisid} to Hub ${hub.id}"
				curchildzone = getChildDevices()?.find { it.deviceNetworkId == "alarmchildoutput${thisid}"}
			}
			else
			{
				// This is the normal panel zone name - First update tile name then try to find child device
				sendEvent(name: "panelzonename"+(thisid), value: thisname)
				log.debug "Trying to add child with name: ${thisname}, ID: alarmchildzone${thisid} to Hub ${hub.id}"
				curchildzone = getChildDevices()?.find { it.deviceNetworkId == "alarmchildzone${thisid}"}
			}
		}
		catch (e)
		{
			// Would reach here if it cant find any children or that child doesnt exist so we can try and create it
			log.debug "Couldnt find device, probably doesn't exist so safe to add a new one: ${e}"
		}

		// If we don't have a matching child already, and the name isn't Unknown, then we can finally start creating the child
		if (curchildzone == null)
		{
			if (curzone.zonename != "Unknown")
			{
				try
				{
					switch (curzone.zonetype)
					{
						case ["Magnet", "Contact", "Entry/Exit"]:
							// If it is a magnetic sensor then add it as a contact sensor
							addChildDevice("hubitat", "Virtual Contact Sensor", "alarmchildzone${thisid}", [name: thisname])
							log.debug "Creating contact zone"
							break

						case ["Motion", "Interior", "Wired"]:
							// If it is a motion sensor then add it as a motion detector
							addChildDevice("hubitat", "Virtual Motion Sensor", "alarmchildzone${thisid}", [name: thisname])
							log.debug "Creating motion zone"
							break

						case ["Smoke", "Fire"]:
							try
							{
								addChildDevice("hubitat", "Virtual Smoke Detector", "alarmchildzone${thisid}", [name: thisname])
							}
							catch (e)
							{
								log.debug "Couldn't create Smoke Detector child device. Is device handler installed ? Creating motion detector zone instead."
								addChildDevice("hubitat", "Virtual Motion Sensor", "alarmchildzone${thisid}", [name: thisname])
							}
							log.debug "Created Smoke Alarm zone child device"
							break

						case ["Shock", "Vibration", "Gas", "Panic", "KeySwitch"]:
							// Add the remainders as motion detectors for now - will display motion/no-motion instead of active/inactive sadly
							addChildDevice("hubitat", "Virtual Motion Sensor", "alarmchildzone${thisid}", [name: thisname])
							break

						case ["Output"]:
							// This is an output zone for controlling on/off
							addChildDevice("erocm123", "Switch Child Device", "alarmchildoutput${thisid}", [name: "${thisname} Output"])
							log.debug "Creating output zone"
							break

						default:
							log.debug "Unknown sensor found, we'll have to ignore for now"
							break
					}
				}
				catch (e)
				{
					log.error "Couldnt add device, probably already exists: ${e}"
				}
			}
			else
			{
				log.debug "Zone name for zone ${curzone.zoneid} is unknown - not adding child device"
			}
		}
		else
		{
			log.debug "Child device named alarmchildzone${curzone.zoneid} already exists - not adding child device"
		}
	}
    // Now lets create the child switches if desired, to Disarm/ArmHome/ArmAway the Alarm
    if (settings.createcontrolchilddevices) {
        def switches = ["disarm", "armhome", "armaway"] //, "panic", "armhome_bypass", "armaway_bypass"]
        switches.each {
            try {
                def child = addChildDevice("cjcharles0", "Alarm Bridge Child Switch", "alarm${it.value}", [name: "Alarm ${it.value.toString().capitalize()}"])
                child.off()
            }
            catch (e)
			{
				log.error "Couldnt add device, probably already exists: ${e}"
			}
        }
    }
}

private handleOutputStatus(result)
{
	def thisOutputDeviceId = "alarmchildoutput"+result.output_id
	def curdevice = null
	try
	{
		// Got a zone status so first try to find the correct child device
		curdevice = getChildDevices()?.find { it.deviceNetworkId == thisOutputDeviceId }
	}
	catch (e)
	{
		log.debug "Failed to find child output number " + result.output_id + "exception ${e}"
	}

	if (curdevice == null)
	{
		log.debug "Failed to find child output number " + result.output_id + " expecting " + thisOutputDeviceId
	}
	else
	{
		// Now switch based on what has happened
		switch (result.output_status)
		{
			case "On":
				// Output zone has turned on
				sendEvent(name: "paneloutput"+result.output_id, value: "on", displayed: false, isStateChange: true)
				curdevice?.sendEvent(name: "switch", value: "on")
				break

			case "Off":
				// Output zone has turned off
				sendEvent(name: "paneloutput"+result.output_id, value: "off", displayed: false, isStateChange: true)
				curdevice?.sendEvent(name: "switch", value: "off")
				break

			default:
				log.debug "Unknown output status received: ${result.output_name} / ${result.output_id} is ${result.output_status}"
				break
		}
	}
}

private getAction(uri)
{ 
	log.debug "uri ${uri}"
	updateDNI()

	def userpass

	if (password != null && password != "") {
		userpass = encodeCredentials("admin", password)
	}
	
	def headers = getHeader(userpass)
    
	def hubAction = new hubitat.device.HubAction(
		method: "GET",
		path: uri,
		headers: headers
		)
	return hubAction
}

private postAction(uri)
{ 
	log.debug "uri ${uri}"
	updateDNI()

	def userpass

	if (password != null && password != "") {
		userpass = encodeCredentials("admin", password)
	}
	
	def headers = getHeader(userpass)
    
	def hubAction = new hubitat.device.HubAction(
		method: "POST",
		path: uri,
		headers: headers
		)
	return hubAction
}

def parseDescriptionAsMap(description)
{
	description.split(",").inject([:])
	{
		map, param ->
		def nameAndValue = param.split(":")
		if (nameAndValue.size() > 1) {
			map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
		}
	}
}

private getHeader(userpass = null)
{
	def headers = [:]
	headers.put("Host", getHostAddress())
	headers.put("Content-Type", "application/x-www-form-urlencoded")
	if (userpass != null)
	   headers.put("Authorization", userpass)
	return headers
}

private encodeCredentials(username, password)
{
	def userpassascii = "${username}:${password}"
	def userpass = "Basic " + userpassascii.encodeAsBase64().toString()
	return userpass
}

private updateDNI()
{ 
	if (state.dni != null && state.dni != "" && device.deviceNetworkId != state.dni)
	{
	   device.deviceNetworkId = state.dni
	}
}

private getHostAddress()
{
	if(getDeviceDataByName("ip") && getDeviceDataByName("port"))
	{
		return "${getDeviceDataByName("ip")}:${getDeviceDataByName("port")}"
	}
	else
	{
		return "${ip}:80"
	}
}


private outputon(outputnumber, period = 0)
{
	// Turn an output on (for a period of time - default to 0 (permanent) if not specified)
	log.debug "outputon()"
	getAction("/on?output=${outputnumber}&period=${period}")
}
private outputoff(outputnumber, period = 0)
{ 
	// Turn an output off (no period allowed for off so ignored)
	log.debug "outputoff()"
	getAction("/off?output=${outputnumber}")
}

def childOn(String dni) {
    log.debug "childOn($dni)"
    def outputnumber = dni.replaceAll("alarmchildoutput", "")
    outputon(outputnumber, 0)
}
def childOff(String dni) {
    log.debug "childOff($dni)"
    def outputnumber = dni.replaceAll("alarmchildoutput", "")
    outputoff(outputnumber, 0)
}

def on1() { outputon(1, 0) }
def on2() { outputon(2, 0) }
def on3() { outputon(3, 0) }
def on4() { outputon(4, 0) }
def on5() { outputon(5, 0) }
def on6() { outputon(6, 0) }
def on7() { outputon(7, 0) }
def on8() { outputon(8, 0) }

def off1() { outputoff(1, 0) }
def off2() { outputoff(2, 0) }
def off3() { outputoff(3, 0) }
def off4() { outputoff(4, 0) }
def off5() { outputoff(5, 0) }
def off6() { outputoff(6, 0) }
def off7() { outputoff(7, 0) }
def off8() { outputoff(8, 0) }
