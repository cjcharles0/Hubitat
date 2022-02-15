/**
 *  
 *	Fibaro FGS-224 Double Smart Module
 *  
 *	Author: Chris Charles
 *	Date: 2020-12-01
 */
 
metadata {
definition (name: "Fibaro Double Smart FGS-224", namespace: "cjcharles0", author: "Chris (help from Eric and Robin)") {
capability "Switch"
capability "Configuration"
capability "Refresh"
capability "Zw Multichannel"

attribute "switch1", "string"
attribute "switch2", "string"

command "on1"
command "off1"
command "on2"
command "off2"
    
command "CreateChildren"
command "RemoveChildren"
command "componentOn"
command "componentOff"
command "componentRefresh"

command "updateSingleParam" // This custom command can be used with Rule Machine or webCoRE, to send parameter values (paramNr & paramvalue) to the device

// device type 516 = 0x0204, manufacturer 271 = 0x010F, 4096 = 0x1000
fingerprint mfr:"010F", prod:"0204", deviceId: "1000", inClusters:"0x5E,0x55,0x98,0x9F,0x56,0x6C,0x22"
fingerprint mfr:"010F", prod:"0204", deviceId: "1000", inClusters:"0x25,0x85,0x8E,0x59,0x86,0x72,0x5A,0x73,0x5B,0x60,0x70,0x75,0x71,0x7A"

}
    
   preferences {
	   
	input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	   
        input name: "param1", type: "enum", defaultValue: "1", required: true,
            title: "1 - State of outputs after a power failure",
       		options: [
                    ["0" : "0 - Remain switched off"],
                    ["1" : "1 - Restore original state"],
                    ["2" : "2 - Restore original, except toggle where it should match switch status"]
                ]
       
        input name: "param20", type: "enum", defaultValue: "0", required: true,
            title: "20 - Switch 1 Input Type",
       		options: [
                    ["0" : "0 - Momentary switch"],
                    ["1" : "1 - Toggle, output synchronised to switch"],
                    ["2" : "2 - Toggle, every switch change toggles output"]
                ]
       
        input name: "param21", type: "enum", defaultValue: "0", required: true,
            title: "21 - Switch 2 Input Type",
       		options: [
                    ["0" : "0 - Momentary switch"],
                    ["1" : "1 - Toggle, output synchronised to switch"],
                    ["2" : "2 - Toggle, every switch change toggles output"]
                ]
       
        input name: "param24", type: "enum", defaultValue: "0", required: true,
            title: "24 - Inputs orientation (Swap S1/S2)",
       		options: [
                    ["0" : "0 - Default"],
                    ["1" : "1 - Reversed"]
                ]
       
        input name: "param25", type: "enum", defaultValue: "0", required: true,
            title: "25 - Output Orientation (Swap Q1/Q2)",
       		options: [
                    ["0" : "0 - Default"],
                    ["1" : "1 - Reversed"]
                ]
       
        input name: "param40", type: "number", range: "0..15", defaultValue: "15", required: true,
            title: "40 - S1 Input Scenes Sent.\n" +
                   "Default value: 15 (1+2=3 means scenes for single/double click are sent."
       
        input name: "param41", type: "number", range: "0..15", defaultValue: "15", required: true,
            title: "41 - S2 Input Scenes Sent.\n" +
                   "Default value: 15 (1+2=3 means scenes for single/double click are sent."
       
        input name: "param150", type: "enum", defaultValue: "0", required: true,
            title: "150 - First channel operating mode",
       		options: [
                    ["0" : "0 - Standard operation"],
                    ["1" : "1 - Delayed off"],
                    ["2" : "2 - Auto off"],
                    ["3" : "3 - Flashing"]
                ]
       
        input name: "param151", type: "enum", defaultValue: "0", required: true,
            title: "151 - Second channel operating mode",
       		options: [
                    ["0" : "0 - Standard operation"],
                    ["1" : "1 - Delayed off"],
                    ["2" : "2 - Auto off"],
                    ["3" : "3 - Flashing"]
                ]
       
        input name: "param152", type: "enum", defaultValue: "0", required: true,
            title: "152 - First channel reaction to input change in delayed/auto OFF modes",
       		options: [
                    ["0" : "0 - Cancel mode and set default state"],
                    ["1" : "1 - No reaction, mode runs until it ends"],
                    ["2" : "2 - Reset timer, start from the beginning"]
                ]
       
        input name: "param153", type: "enum", defaultValue: "0", required: true,
            title: "153 - Second channel reaction to input change in delayed/auto OFF modes",
       		options: [
                    ["0" : "0 - Cancel mode and set default state"],
                    ["1" : "1 - No reaction, mode runs until it ends"],
                    ["2" : "2 - Reset timer, start from the beginning"]
                ]
       
        input name: "param154", type: "number", range: "0..65535", defaultValue: "5", required: true,
            title: "154 - First channel time for delayed/auto OFF/flashing\n" +
                   "0.1s steps for auto off or cycle period."
       
        input name: "param155", type: "number", range: "0..65535", defaultValue: "5", required: true,
            title: "155 - Second channel time for delayed/auto OFF/flashing\n" +
                   "0.1s steps for auto off or cycle period."
       
        input name: "param162", type: "enum", defaultValue: "0", required: true,
            title: "162 - Q1 output type",
       		options: [
                    ["0" : "0 - Normally Open"],
                    ["1" : "1 - Normally closed"]
                ]
       
        input name: "param163", type: "enum", defaultValue: "0", required: true,
            title: "163 - Q2 output type",
       		options: [
                    ["0" : "0 - Normally Open"],
                    ["1" : "1 - Normally closed"]
                ]
       
        input name: "param164", type: "enum", defaultValue: "0", required: true,
            title: "164 - Lock simultaneous switching of Q1 and Q2 outputs",
       		options: [
                    ["0" : "0 - Lock disabled"],
                    ["1" : "1 - Lock enabled"]
                ]
    }
}

def CreateChildren()
{
     try {
        for (i in 1..2) {
	       addChildDevice("hubitat", "Generic Component Switch", "${device.deviceNetworkId}-ep${i}",
		      [completedSetup: true, label: "${device.displayName} (S${i})",
		      isComponent: false, componentName: "ep$i", componentLabel: "Switch $i"])
        }
    } catch (e) {
         log.debug "Didnt create children for some reason: ${e}"
    }
}

def RemoveChildren()
{
	// This will remove all child devices
	log.debug "Removing Child Devices"
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

def componentOn(child)
{
    if (logEnable) log.debug "componentOn(${child.deviceNetworkId})"
    if (child.deviceNetworkId.substring(child.deviceNetworkId.length()-3) == "ep2") {
        on2()
    }
    else {
        on1()
    }
}

def componentOff(child)
{
    if (logEnable) log.debug "componentOff(${child.deviceNetworkId})"
    if (child.deviceNetworkId.substring(child.deviceNetworkId.length()-3) == "ep2") {
        off2()
    }
    else {
        off1()
    }
}

def componentRefresh(child)
{
    if (logEnable) log.debug "componentRefresh(${child.deviceNetworkId})"
    refresh()
}

def updateChild(String ep, String status)
{
    if (logEnable) log.debug "Updating child with endpoint ${ep} to ${status}"
    if (ep == "both") {
        //Updating both endpoints so do 1 at a time and then exit
        updateChild("1", status)
        updateChild("2", status)
        return
    }
    //First update the parent
    sendEvent(name: "switch"+ep, value: status)
    if (status == "on")
    {
        sendEvent(name: "switch", value: "on")
    }
    else
    {
        //Status is off, so check if the other endpoint is off and update the combined status
        def otherep = ep=="1" ? "switch2" : "switch1"
        if (device.currentState(otherep).getValue() == "off")
        {
            sendEvent(name: "switch", value: "off")
        }
    }

    //Now find and update the child
	def childName = device.deviceNetworkId+"-ep"+ep
	def curdevice = null
	try
	{
		// Got a zone status so first try to find the correct child device
		curdevice = getChildDevices()?.find { it.deviceNetworkId == childName }
	}
	catch (e)
	{
		log.debug "Failed to find child " + childName + " - exception ${e}"
	}

	if (curdevice == null)
	{
		log.debug "Failed to find child called " + childName + " - exception ${e}"
	}
	else
	{
		curdevice?.sendEvent(name: "switch", value: status)
    }
}

def parse(String description)
{
    def result = []
    def cmd = zwave.parse(description)
    if (cmd)
    {
        result += zwaveEvent(cmd)
        //log.debug "Parsed ${cmd} to ${result.inspect()}"
    }
    else
    {
        log.debug "Non-parsed event: ${description}"
    }
    return result
}


def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd)
{
    if (logEnable) log.debug "hubitat.zwave.commands.basicv1.BasicSet ${cmd}"
    def result = []
    result << zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:2).format()
    result << zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:2, commandClass:37, command:2).format()
    response(delayBetween(result, 500)) // returns the result of reponse()
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd)
{
    if (logEnable) log.debug "hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport ${cmd}"
    def result = []
    result << zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:2).format()
    result << zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:2, commandClass:37, command:2).format()
    response(delayBetween(result, 500)) // returns the result of reponse()
}

def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCapabilityReport cmd) 
{
    if (logEnable) log.debug "hubitat.zwave.commands.multichannelv3.MultiChannelCapabilityReport ${cmd}"
    if (cmd.endPoint == 2 ) {
        def currstate = device.currentValue("switch2")
        if (currstate == "on") {
            updateChild("2", "off")
        }
        else if (currstate == "off") {
            updateChild("2", "on")
        }
    }
    else if (cmd.endPoint == 1 ) {
        def currstate = device.currentValue("switch1")
        if (currstate == "on") {
            updateChild("1", "off")
        }
        else if (currstate == "off") {
            updateChild("1", "on")
        }
    }
}

def zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCapabilityReport cmd) 
{
    if (logEnable) log.debug "hubitat.zwave.commands.multichannelv4.MultiChannelCapabilityReport ${cmd}"
    if (cmd.endPoint == 2 ) {
        def currstate = device.currentState("switch2").getValue()
        if (currstate == "on") {
            updateChild("2", "off")
        }
        else if (currstate == "off") {
            updateChild("2", "on")
        }
    }
    else if (cmd.endPoint == 1 ) {
        def currstate = device.currentState("switch1").getValue()
        if (currstate == "on") {
            updateChild("1", "off")
        }
        else if (currstate == "off") {
            updateChild("1", "on")
        }
    }
}

def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
    if (logEnable) log.debug "hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap ${cmd}"
    def map = [ name: "switch$cmd.sourceEndPoint" ]
    def currstate = "off"
       if (cmd.destinationEndPoint == 2 ) {

           if (cmd.parameter.first() > 180) {
            updateChild("2", "on")
           }
         
           else if (cmd.parameter.first() == 0) {
            updateChild("2", "off")
           }
    }
    else if (cmd.destinationEndPoint == 1 ) {

        if (cmd.parameter.first() > 180){
            updateChild("1", "on")
        }
        else if (cmd.parameter.first() == 0) {
            updateChild("1", "off")
        }
    }
}

def zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCmdEncap cmd) {
    if (logEnable) log.debug "zwave.multichannelv4.MultiChannelCmdEncap ${cmd} - dest:${cmd.destinationEndPoint} src:${cmd.sourceEndPoint} firstparam:${cmd.parameter.first()}"
    if (cmd.sourceEndPoint == 2 ) {
        if (cmd.parameter.first() > 180) {
            updateChild("2", "on")
        }
        else if (cmd.parameter.first() == 0) {
            updateChild("2", "off")
        }
    }
    
    else if (cmd.sourceEndPoint == 1 ) {
        if (cmd.parameter.first() > 180) {
            updateChild("1", "on")
        }
        else if (cmd.parameter.first() == 0) {
            updateChild("1", "off")
        }
    }
    
    else if (cmd.destinationEndPoint == 0 ) {
        if (cmd.parameter.first() > 180) {
            updateChild(cmd.sourceEndPoint.toString(), "on")
           }
        else if (cmd.parameter.first() == 0) {
            updateChild(cmd.sourceEndPoint.toString(), "off")
        }
    }
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    // This will capture any commands not handled by other instances of zwaveEvent
    // and is recommended for development so you can see every command the device sends
    log.debug "catchall ${cmd}"
    return createEvent(descriptionText: "${device.displayName}: ${cmd}")
}

def zwaveEvent(hubitat.zwave.commands.switchallv1.SwitchAllReport cmd) {
    log.debug "hubitat.zwave.commands.switchallv1.SwitchAllReport ${cmd}"
}

def refresh() {
	def cmds = []
	cmds << zwave.manufacturerSpecificV2.manufacturerSpecificGet().format()
	cmds << zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:2).format()
	cmds << zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:2, commandClass:37, command:2).format()
	delayBetween(cmds, 500)
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	//log.debug "msr: $msr"
	updateDataValue("MSR", msr)
}

def poll() {
	def cmds = []
	cmds << zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:2).format()
	cmds << zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:2, commandClass:37, command:2).format()
	delayBetween(cmds, 500)
}

def configure() {
	log.debug "Executing 'configure'"
	def cmds = []
    
    if(!state.association2 || state.association2 == "" || state.association2 == "1") {
       log.debug "Setting association group 2 to " + zwaveHubNodeId 
       //cmds << secureCmd(zwave.associationV2.associationRemove(groupingIdentifier:2, nodeId:zwaveHubNodeId))
       cmds << secureCmd(zwave.associationV2.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId))
       cmds << secureCmd(zwave.associationV2.associationGet(groupingIdentifier:2))
    }
    if(!state.association3 || state.association3 == "" || state.association3 == "2") {
       log.debug "Setting association group 3 to " + zwaveHubNodeId 
       //cmds << secureCmd(zwave.associationV2.associationRemove(groupingIdentifier:3, nodeId:zwaveHubNodeId))
       cmds << secureCmd(zwave.associationV2.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId))
       cmds << secureCmd(zwave.associationV2.associationGet(groupingIdentifier:3))
    }

    cmds << secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: param1.toInteger(), parameterNumber:1, size: 1))
    cmds << secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: param20.toInteger(), parameterNumber:20, size: 1))
    cmds << secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: param21.toInteger(), parameterNumber:21, size: 1))
    cmds << secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: param24.toInteger(), parameterNumber:24, size: 1))
    cmds << secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: param25.toInteger(), parameterNumber:25, size: 1))
    cmds << secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: param40.toInteger(), parameterNumber:40, size: 1))
    cmds << secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: param41.toInteger(), parameterNumber:41, size: 1))
    cmds << secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: param150.toInteger(), parameterNumber:150, size: 1))
    cmds << secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: param151.toInteger(), parameterNumber:151, size: 1))
    cmds << secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: param152.toInteger(), parameterNumber:152, size: 1))
    cmds << secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: param153.toInteger(), parameterNumber:153, size: 1))
    cmds << secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: param154.toInteger(), parameterNumber:154, size: 2))
    cmds << secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: param155.toInteger(), parameterNumber:155, size: 2))
    cmds << secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: param162.toInteger(), parameterNumber:162, size: 1))
    cmds << secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: param163.toInteger(), parameterNumber:163, size: 1))
    cmds << secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: param164.toInteger(), parameterNumber:164, size: 1))
    
    return delayBetween(cmds, 500)
}

def updateSingleparam(paramNum, paramValue, paramSize) {
	//log.debug "Updating single Parameter (paramNum: $paramNum, paramValue: $paramValue)"
    secureCmd(zwave.configurationV1.configurationSet(parameterNumber: paramNum, scaledConfigurationValue: paramValue, size: paramSize))
}

/**
* Triggered when Save button is pushed on Preference UI
*/
def updated()
{
	log.debug "Preferences have been changed. Attempting configure()"
    configure()
    //def cmds = configure()
    //response(cmds)
}

def on() {
    log.debug "on"
    delayBetween([
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:1, parameter:[255]).format(),
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:2, destinationEndPoint:2, commandClass:37, command:1, parameter:[255]).format(),
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:2).format(),
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:2, destinationEndPoint:2, commandClass:37, command:2).format()
    ], 250)
}
def off() {
    log.debug "off"
    delayBetween([
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:1, parameter:[0]).format(),
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:2, destinationEndPoint:2, commandClass:37, command:1, parameter:[0]).format(),
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:2).format(),
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:2, destinationEndPoint:2, commandClass:37, command:2).format()
    ], 250)
}

def on1() {
    log.debug "on1"
    //updateChild("1", "on")
    delayBetween([
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:1, parameter:[255]).format(),
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:2).format()
    ], 250)
}

def off1() {
    log.debug "off1"
    //updateChild("1", "off")
    //zwave.basicV1.basicSet(value: 0x00, destinationEndPoint:1)
    delayBetween([
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:1, parameter:[0]).format(),
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:2).format()
    ], 250)
}

def on2() {
    log.debug "on2"
    //updateChild("2", "on")
    delayBetween([
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:2, destinationEndPoint:2, commandClass:37, command:1, parameter:[255]).format(),
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:2, destinationEndPoint:2, commandClass:37, command:2).format()
    ], 250)
}

def off2() {
    log.debug "off2"
    //updateChild("2", "off")
    delayBetween([
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:2, destinationEndPoint:2, commandClass:37, command:1, parameter:[0]).format(),
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:2, destinationEndPoint:2, commandClass:37, command:2).format()
    ], 250)
}


String secureCmd(cmd) {
	if ((getDataValue("zwaveSecurePairingComplete") == "false") || (getDataValue("zwaveSecurePairingComplete") == null)){
		if (logEnable) log.debug "insecure ${cmd}"
		return cmd.format()
	}
	else if (getDataValue("zwaveSecurePairingComplete") == "true" && getDataValue("S2") == null) {
		if (logEnable) log.debug "security-v1 ${cmd}"
		return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	}
	else {
		if (logEnable) log.debug "secure ${cmd}"
		return secure(cmd)
	}	
}

String secure(String cmd){
    return zwaveSecureEncap(cmd)
}

String secure(hubitat.zwave.Command cmd){
    return zwaveSecureEncap(cmd)
}

def zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd){
    hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
    if (encapCmd) {
        zwaveEvent(encapCmd)
    }
    sendHubCommand(new hubitat.device.HubAction(secure(zwave.supervisionV1.supervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0)), hubitat.device.Protocol.ZWAVE))
}
