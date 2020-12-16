/**
 *  
 *	Fibaro FGS-224 Double Smart Module
 *  
 *	Author: Chris Charles
 *	Date: 2020-12-01
 */
 
metadata {
definition (name: "Fibaro FGS-224", namespace: "cjcharles", author: "Chris (help from Eric and Robin)") {
capability "Switch"
capability "Relay Switch"
capability "Polling"
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
command "childOn"
command "childOff"
command "childRefresh"
    
command "updateSingleParam" // This custom command can be used with Rule Machine or webCoRE, to send parameter values (paramNr & paramvalue) to the device

fingerprint deviceId: "0x4096", inClusters:"0x5E,0x55,0x98,0x9F,0x56,0x6C,0x22"
fingerprint deviceId: "0x4096", inClusters:"0x25,0x85,0x8E,0x59,0x86,0x72,0x5A,0x73,0x5B,0x60,0x70,0x75,0x71,0x7A"

}
    
   preferences {

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
    }
}

def CreateChildren()
{
     try {
        for (i in 1..2) {
	       addChildDevice("erocm123", "Metering Switch Child Device", "${device.deviceNetworkId}-ep${i}",
		      [completedSetup: true, label: "${device.displayName} (S${i})",
		      isComponent: false, componentName: "ep$i", componentLabel: "Switch $i"])
        }
    } catch (e) {
        log.debug e
	    runIn(2, "sendAlert")
    }
}

private sendAlert()
{
   sendEvent(
      descriptionText: "Child device creation failed. Please make sure that the \"Metering Switch Child Device\" is installed and published.",
	  eventType: "ALERT",
	  name: "childDeviceCreation",
	  value: "failed",
	  displayed: true,
   )
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

def childOn(String dni)
{
    log.debug "childOn(${dni})"
    if (dni.substring(dni.length()-3) == "ep2") {
        on2()
    }
    else {
        on1()
    }
}

def childOff(String dni)
{
    log.debug "childOff(${dni})"
    if (dni.substring(dni.length()-3) == "ep2") {
        off2()
    }
    else {
        off1()
    }
}

def updateChild(String ep, String status)
{
    log.debug "Updating child with endpoint ${ep} to ${status}"
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
		log.debug "Failed to find child called " + childName + " - exception ${e}"
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
    log.debug "hubitat.zwave.commands.basicv1.BasicSet ${cmd}"
    def result = []
    result << zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:2).format()
    result << zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:2, commandClass:37, command:2).format()
    response(delayBetween(result, 500)) // returns the result of reponse()
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd)
{
    log.debug "hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport ${cmd}"
    def result = []
    result << zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:2).format()
    result << zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:2, commandClass:37, command:2).format()
    response(delayBetween(result, 500)) // returns the result of reponse()
}


def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCapabilityReport cmd) 
{
    log.debug "hubitat.zwave.commands.multichannelv3.MultiChannelCapabilityReport ${cmd}"
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
    log.debug "hubitat.zwave.commands.multichannelv4.MultiChannelCapabilityReport ${cmd}"
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
    log.debug "hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap ${cmd}"
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
    log.debug "hubitat.zwave.commands.multichannelv4.MultiChannelCmdEncap ${cmd}"
    log.debug "dest:${cmd.destinationEndPoint} src:${cmd.sourceEndPoint} firstparam:${cmd.parameter.first()}"
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
//	log.debug "Executing 'configure'"
    def cmds = []
    delayBetween([
          zwave.configurationV1.configurationSet(parameterNumber:1, configurationValue:[param1.value]).format(),
          zwave.configurationV1.configurationSet(parameterNumber:20, configurationValue:[param20.value]).format(),
          zwave.configurationV1.configurationSet(parameterNumber:21, configurationValue:[param21.value]).format()
    ])
}

def updateSingleparam(paramNum, paramValue) {
//	log.debug "Updating single Parameter (paramNum: $paramNum, paramValue: $paramValue)"
    	zwave.configurationV1.configurationSet(parameterNumber: paramNum, ConfigurationValue: paramValue)
}

/**
* Triggered when Save button is pushed on Preference UI
*/
def updated()
{
//	log.debug "Preferences have been changed. Attempting configure()"
    def cmds = configure()
    response(cmds)
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
    updateChild("1", "on")
    delayBetween([
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:1, parameter:[255]).format(),
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:2).format()
    ], 250)
}

def off1() {
    log.debug "off1"
    updateChild("1", "off")
//    zwave.basicV1.basicSet(value: 0x00, destinationEndPoint:1)
    delayBetween([
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:1, parameter:[0]).format(),
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:2).format()
    ], 250)
}

def on2() {
    log.debug "on2"
    updateChild("2", "on")
    delayBetween([
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:2, destinationEndPoint:2, commandClass:37, command:1, parameter:[255]).format(),
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:2, destinationEndPoint:2, commandClass:37, command:2).format()
    ], 250)
}

def off2() {
    log.debug "off2"
    updateChild("2", "off")
    delayBetween([
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:2, destinationEndPoint:2, commandClass:37, command:1, parameter:[0]).format(),
        zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint:2, destinationEndPoint:2, commandClass:37, command:2).format()
    ], 250)
}
