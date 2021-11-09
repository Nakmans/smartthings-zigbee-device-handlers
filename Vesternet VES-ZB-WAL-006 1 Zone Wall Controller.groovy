/**
 *	Vesternet VES-ZB-WAL-006 1 Zone Wall Controller
 * 
 */
import groovy.json.JsonOutput
metadata {
	definition (name: "Vesternet VES-ZB-WAL-006 1 Zone Wall Controller", namespace: "Vesternet", author: "Vesternet", mcdSync:true, ocfDeviceType: "x.com.st.d.remotecontroller", mnmn: "Sunricher", vid: "generic-2-button") {
        capability "Button"
        capability "Sensor"
		capability "Battery"
        capability "Configuration"
        
		fingerprint profileId: "0104", endpointId: "01", inClusters: "0000,0001,0003,0B05", outClusters: "0003,0004,0005,0006,0008,0019,0300,1000", manufacturer: "Sunricher", model: "ZGRC-KEY-007", deviceJoinName: "Vesternet VES-ZB-WAL-006 1 Zone Wall Controller"
        fingerprint profileId: "0104", endpointId: "01", inClusters: "0000,0001,0003,0B05", outClusters: "0003,0004,0005,0006,0008,0019,0300,1000", manufacturer: "Sunricher", model: "ZG2833K2_EU07", deviceJoinName: "Vesternet VES-ZB-WAL-006 1 Zone Wall Controller"        
	}
	preferences {
        input name: "logEnable", type: "bool", title: "Debug"
	}
}

def installed() {
    device.updateSetting("logEnable", [value: "true", type: "bool"])
    logDebug("installed called")
	def numberOfButtons = modelNumberOfButtons[device.getDataValue("model")]
    sendEvent(getEvent(name: "numberOfButtons", value: numberOfButtons, displayed: false))
    sendEvent(getEvent(name: "supportedButtonValues", value: supportedButtonValues.encodeAsJSON(), displayed: false))
    for(def buttonNumber : 1..numberOfButtons) {
        sendEvent(getEvent(name: "button", value: "pushed", data: [buttonNumber: buttonNumber], isStateChange: true, displayed: false))
    }
    setupChildDevices()
    runIn(1800,logsOff)
}

def updated() {
    logDebug("updated called")
	log.warn("debug logging is: ${logEnable != false}")
	state.clear()
	unschedule()
	if (logEnable) runIn(1800,logsOff)
}
	

def configure() {
    logDebug("configure called")	
    def cmds = [ "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0006 {${device.zigbeeId}} {}", "delay 200",
                "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0008 {${device.zigbeeId}} {}", "delay 200",
                "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0001 {${device.zigbeeId}} {}", "delay 200",
                "st cr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0001 0x21 0x20 7200 7200 {0x01}" ]
    logDebug("sending ${cmds}")
	return cmds
}

void parse(String description) {
	logDebug("parse called")
	logDebug("got description: ${description}")	
    def descriptionMap = zigbee.parseDescriptionAsMap(description)
    def events = getEvents(descriptionMap)	
	if (events) {	
        events.each {		    
            sendEvent(getEvent(it))
        }
	}
	else {	
        log.warn("Unhandled command: ${descriptionMap}")			        	
	}   
}

def getEvents(descriptionMap) {
    logDebug("getEvents called")
    logDebug("got descriptionMap: ${descriptionMap}")
	def events = []    
    if (descriptionMap.cluster == "0006" || descriptionMap.clusterId == "0006" || descriptionMap.clusterInt == 6) {
        def switchCommand = "${descriptionMap.command} unknown"
        switch (descriptionMap.command) {
            case "00": 
                switchCommand = "off"
                break
            case "01": 
                switchCommand = "on"
                break            
        }
        logDebug("switch (0006) command: ${switchCommand}")
        if (descriptionMap.command == "00" || descriptionMap.command == "01") {
            switchCommand = zigbee.convertHexToInt(descriptionMap.command)            
			def buttonNumber = switchCommand == 1 ? "1" : "2"
            logDebug("button number is ${buttonNumber}")
            events.add([name: "button", value: "pushed", data: [buttonNumber: buttonNumber], descriptionText: "Button Number ${buttonNumber} was pushed", isStateChange: true])
            sendEventToChild(buttonNumber, "pushed")
		}
        else {
            logDebug("switch (0006) command skipped")
        }
	}
    else if (descriptionMap.cluster == "0008" || descriptionMap.clusterId == "0008" || descriptionMap.clusterInt == 8) {
        def levelCommand = "${descriptionMap.command} unknwon"
        switch (descriptionMap.command) {
            case "05": 
                levelCommand = "move with on/off"
                break
            case "07": 
                levelCommand = "stop"
                break            
        }       
        logDebug("level (0008) command: ${levelCommand}") 
        if (descriptionMap.command == "05") {
            def levelDirectionData = descriptionMap.data[0];
            if (levelDirectionData == "00" || levelDirectionData == "01") {
                def levelDirection = "${levelDirectionData} unknown"
                switch (levelDirectionData) {
                    case "00": 
                        levelDirection = "up"
                        break
                    case "01": 
                        levelDirection = "down"
                        break            
                }        
                logDebug("level (0008) direction: ${levelDirection}")
                levelDirection = zigbee.convertHexToInt(levelDirectionData)            
                def buttonNumber = levelDirection == 0 ? "1" : "2"
                logDebug("button number is ${buttonNumber}")
                logDebug("button event is held")                
                events.add([name: "button", value: "held", data: [buttonNumber: buttonNumber], descriptionText: "Button Number ${buttonNumber} was held", isStateChange: true])
                events.add([name: "lastheld", value: buttonNumber, isStateChange: true, displayed: false])
                sendEventToChild(buttonNumber, "held")           
            }
            else {
                logDebug("level (0008) direction: ${levelDirectionData} unknown")
            }
		}
        else if (descriptionMap.command == "07") {          
            def buttonNumber = device.currentValue("lastheld")
            if (buttonNumber) {                
                logDebug("button number was ${buttonNumber}")
                logDebug("button event is released")            
                events.add([name: "button", value: "double", data: [buttonNumber: buttonNumber], descriptionText: "Button Number ${buttonNumber} was released", isStateChange: true])
                sendEventToChild(buttonNumber, "double")
            }
            else {
                logDebug("could not determine buttonNumber")
            }
		}
        else {
            logDebug("level (0008) command skipped")
        }
	}    
    else if (descriptionMap.cluster == "001" || descriptionMap.clusterId == "001" || descriptionMap.clusterInt == 1) {        
        if (descriptionMap.attrId == "0021" || descriptionMap.attrInt == 33) {
            logDebug("power configuration (0001) battery report")
            def batteryValue = zigbee.convertHexToInt(descriptionMap.value)
            logDebug("battery percentage report is ${batteryValue}")			                          
            events.add([name: "battery", value: batteryValue, unit: "%", descriptionText: "${device.displayName} is ${batteryValue}%"])
		}
        else {
            logDebug("power configuration (0001) command skipped")
        }
	}  
    else if (descriptionMap.cluster == "8021" || descriptionMap.clusterId == "8021" || descriptionMap.clusterInt == 32801) {  
        logDebug("zdo bind reponse (0x8021) ${descriptionMap.data}")
    }
    else {
        logDebug("skipped")
    }
    if (descriptionMap.additionalAttrs) {
        logDebug("got additionalAttrs: ${descriptionMap.additionalAttrs}")
        descriptionMap.additionalAttrs.each { 
            it.clusterInt = descriptionMap.clusterInt
            it.cluster = descriptionMap.cluster
            it.clusterId = descriptionMap.clusterId          
            it.command = descriptionMap.command  
            events.add(getEvents(it))
        }
    }
	return events
}

def setupChildDevices(){
    logDebug("setupChildDevices called")
	if(!childDevices) {
		addChildButtons(modelNumberOfButtons[device.getDataValue("model")])
	}
}

def addChildButtons(numberOfButtons) {
    logDebug("addChildButtons called buttons: ${numberOfButtons}")
	for (def endpoint : 1..numberOfButtons) {
		try {
			String childDni = "${device.deviceNetworkId}:$endpoint"
			def componentLabel = (device.displayName.endsWith(' 1') ? device.displayName[0..-2] : (device.displayName + " Button ")) + "${endpoint}"
			def child = addChildDevice("Vesternet", "Vesternet VES-ZB-WAL-006 1 Zone Wall Controller Child Button", childDni, device.getHub().getId(), [
					completedSetup: true,
					label         : componentLabel,
					isComponent   : true,
					componentName : "button$endpoint",
					componentLabel: "Button $endpoint"
			])
		} 
        catch(Exception e) {
			logDebug "Exception: ${e}"
		}
	}
}

def sendEventToChild(buttonNumber, value) {
    logDebug("sendEventToChild called buttonNumber: ${buttonNumber} value: ${value}")
    String childDni = "${device.deviceNetworkId}:$buttonNumber"
	def child = childDevices.find { it.deviceNetworkId == childDni }
    child?.sendButtonEvent(buttonNumber, value)
}

def getModelNumberOfButtons() {
    logDebug("getModelNumberOfButtons called")
    ["ZGRC-KEY-007" : 2]
}

def getSupportedButtonValues() {
    logDebug("getSupportedButtonValues called")
	def values = ["pushed", "held", "double"]	//no released value supported by smartthings 
	return values
}

def getEvent(event) {
    logDebug("getEvent called data: ${event}")
    return createEvent(event)
}

def logDebug(msg) {
	if (settings.logEnable != false) {
		log.debug("${msg}")
	}
}

def getLogging() {
	return logEnable != false
}

def logsOff() {
    log.warn("debug logging disabled")
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}