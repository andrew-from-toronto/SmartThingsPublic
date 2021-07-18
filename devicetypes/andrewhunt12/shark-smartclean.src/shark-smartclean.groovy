/**
 *  Tesla
 *
 *  Copyright 2018 Trent Foley
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
	definition (name: "Shark SmartClean", namespace: "andrewhunt12", author: "Andrew Hunt") {
		capability "Actuator"
		capability "Sensor"
        capability "Battery"
        capability "Refresh"

		attribute "state", "string"
		attribute "blendedState", "enum", [ "charging", "emptying", "docking", "cleaning", "paused", "error" ]
        attribute "operatingMode", "number"

        command "setOperatingMode"
	}


	simulator {
		// TODO: define status and reply messages here
	}
    
	/*
          standardTile("state", "device.state", width: 2, height: 2) {
            state "asleep", label: "Asleep", backgroundColor: "#eeeeee", action: "wake", icon: "st.Bedroom.bedroom2"
            state "online", label: "Online", backgroundColor: "#00a0dc", icon: "st.tesla.tesla-front"
        }*/

	preferences {
    	/*
        	attribution
        	 - Icons made by iconixar from https://www.flaticon.com/
        */
    }

	tiles(scale: 2) {  
    	multiAttributeTile(name:"statusTile", type:"generic", width:6, height:4) {
            tileAttribute("blendedState", key: "PRIMARY_CONTROL") {
                attributeState "charging", label:'Charging', backgroundColor:"#00A0DC"//, nextState:"cleaning"
                attributeState "emptying", label:'Emptying', backgroundColor:"#00A0DC"//, nextState:"cleaning"
                attributeState "docking", label:'Docking', backgroundColor:"#ffffff"//, nextState:"turningOn"
                attributeState "cleaning", label:'Cleaning', backgroundColor:"#79b821"//, nextState:"cleaning"
                attributeState "paused", label:'Paused', backgroundColor:"#e86d13"//, nextState:"turningOff"
                attributeState "error", label:'Error', backgroundColor:"#e86d13"//, nextState:"turningOff"
            }
            
            tileAttribute("battery", key: "SECONDARY_CONTROL") {
            	attributeState "level", label:'${currentValue}% battery', defaultState: true
            }
        }
        
        standardTile("startStop", "blendedState", decoration: "flat", width: 2, height: 2) {
			state "charging", label: 'Start', action:"refresh.refresh", icon:"st.switches.switch.on"
            state "cleaning", label: 'Stop', action:"refresh.refresh", icon:"st.switches.switch.off"
            state "paused", label: 'Stop', action:"refresh.refresh", icon:"st.switches.switch.off"
		}
        
        /*
        standardTile("pause", "blendedState", decoration: "flat", width: 2, height: 2) {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh", defaultState: true
		}
        */
        
        standardTile("refresh", "blendedState", decoration: "flat", width: 2, height: 2) {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh", defaultState: true
		}
        
        main("statusTile")
        details("statusTile", "startStop", "refresh")
	}
}

def initialize() {
	log.debug "Executing 'initialize'"
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
}

def process(vacuum) {
	if(vacuum) {
    	log.debug "processVacuum"
        
        def operatingMode = vacuum.properties["GET_Operating_Mode"].value
        def powerMode = vacuum.properties["GET_Power_Mode"].value
        def charging = (vacuum.properties["GET_Charging_Status"].value == 1)
        def battery = vacuum.properties["GET_Battery_Capacity"].value
        
        def blendedState = "charging"
        if (!charging) {
        	switch (operatingMode) {
            	case "0":
                	blendedState = "paused"
                    break
                case "2":
                	blendedState = "cleaning"
                    break
                case "3":
                	blendedState = "docking"
                    break
            }
        }
        
        log.debug "Blended State: ${blendedState}"
        
    	//sendEvent(name: "state", value: vacuum)
        sendEvent(name: "blendedState", value: blendedState)
        sendEvent(name: "operatingMode", value: operatingMode)
        sendEvent(name: "powerMode", value: powerMode)
        sendEvent(name: "charging", value: charging)
        sendEvent(name: "battery", value: battery)
	} else {
    	log.error "No vacuum found for ${device.deviceNetworkId}"
    }
}

def refresh() {
	log.debug "Executing 'refresh'"

	parent.refresh(device.deviceNetworkId)
}

def setOperatingMode(mode) {
	
}