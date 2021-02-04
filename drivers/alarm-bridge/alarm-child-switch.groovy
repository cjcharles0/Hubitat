/**
 *  Child Switch
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
 */
metadata {
	definition (name: "Alarm Bridge Child Switch", namespace: "cjcharles0", author: "Chris Charles") {
		capability "Switch"
        
		command "sendData"
        
		//capability "Relay Switch"
		//capability "PushableButton"
	}
}


def on() {
	sendEvent(name: "switch", value: "on")
	sendData("on")
	//runIn(2, off)
}

def off() {
	sendData("off")
    runIn(2, off_update)
}

def off_update() {
	sendEvent(name: "switch", value: "off")
}

def sendData(String value) {
	parent.sendData("${device.deviceNetworkId} ${value}")  
}

def parse(message) {
	log.debug message
}
