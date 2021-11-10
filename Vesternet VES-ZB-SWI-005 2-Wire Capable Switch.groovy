/**
 *	Vesternet VES-ZB-SWI-005 2-Wire Capable Switch
 * 
 */
import groovy.json.JsonOutput
metadata {
	definition (name: "Vesternet VES-ZB-SWI-005 2-Wire Capable Switch", namespace: "Vesternet", author: "Vesternet", mcdSync:true, ocfDeviceType: "oic.d.switch", mnmn: "SmartThings", vid: "generic-switch") {
        capability "Switch"
        capability "Actuator"
		capability "Refresh"		
		capability "Configuration"

		fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0B05,1000", outClusters: "0019", manufacturer: "Sunricher", model: "Micro Smart OnOff", deviceJoinName: "Vesternet VES-ZB-SWI-005 2-Wire Capable Switch"        
        fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0B05,1000", outClusters: "0019", manufacturer: "Sunricher", model: "HK-SL-RELAY-A", deviceJoinName: "Vesternet VES-ZB-SWI-005 2-Wire Capable Switch"    
	}
	preferences {
        input name: "logEnable", type: "bool", title: "Debug"
	}
}

def installed() {
    device.updateSetting("logEnable", [value: "true", type: "bool"])
    logDebug("installed called")	
    runIn(1800,logsOff)
}

def updated() {
    logDebug("updated called")
	log.warn("debug logging is: ${settings.logEnable != false}")
    state.clear()
	unschedule()
	if (logEnable) runIn(1800,logsOff)
}

void parse(String description) {
	logDebug("parse called")
	logDebug("got description: ${description}")	
    def event
    if (!(description.startsWith("catchall"))) {
        event = zigbee.getEvent(description)        
    }
    if (event) {
        logDebug("got event: ${event}")	
        if (event.name == "switch") {
            def type = "physical"
            def descriptionText = "${device.displayName} was turned ${event.value}"
            if (device.currentValue("switch") && event.value == device.currentValue("switch")) {
                descriptionText = "${device.displayName} is ${event.value}"
            }                
            if (device.currentValue("action") == "digitalon" || device.currentValue("action") == "digitaloff") {
                logDebug("action is ${device.currentValue("action")}")
                type = "digital"
                sendEvent(getEvent([name: "action", value: "unknown", isStateChange: true, displayed: false]))
            }
            sendEvent(getEvent([name: "switch", value: event.value, type: type, descriptionText: descriptionText])) 
        }
        else {
            logDebug("skipped")	
        }
    }
    else {
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
}

def getEvents(descriptionMap) {
    logDebug("getEvents called")
    logDebug("got descriptionMap: ${descriptionMap}")
	def events = []    
    def command = "zigbee command ignored"    
    switch (descriptionMap.command) {
        case "07": 
            command = "configure reporting response"
            break
        case "0B":
            command = "default response"
            break
        case "01":
            command = "read attributes response"
            break
        case "0A":
            command = "report attributes"
            break
    }
    if (descriptionMap.cluster == "0006" || descriptionMap.clusterId == "0006" || descriptionMap.clusterInt == 6) {
        logDebug("switch (0006) ${command}")        
	}
    else if (descriptionMap.cluster == "8021" || descriptionMap.clusterId == "8021" || descriptionMap.clusterInt == 32801) {  
        logDebug("zdo bind response (0x8021) ${descriptionMap.data}")
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

def configure() {
	logDebug("configure called")
    def cmds = onOffConfig() 
	logDebug("sending ${cmds}")
	return cmds
}

def refresh() {
	logDebug("refresh called")
	def cmds = onOffRefresh()
    logDebug("sending ${cmds}")
	return cmds
}

def on() {
	logDebug("on called")
	def cmds = zigbee.on()
	logDebug("sending ${cmds}")
    sendEvent(getEvent([name: "action", value: "digitalon", isStateChange: true, displayed: false]))      
	return cmds
}

def off() {
	logDebug("off called")
	def cmds = zigbee.off()
	logDebug("sending ${cmds}")    
    sendEvent(getEvent([name: "action", value: "digitaloff", isStateChange: true, displayed: false]))  
	return cmds
}

def onOffRefresh() {
    logDebug("onOffRefresh called")
	def cmds = zigbee.onOffRefresh()
    return cmds
}

def onOffConfig() {
    logDebug("onOffConfig called")
	def cmds = zigbee.onOffConfig()
    return cmds
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

def logsOff() {
    log.warn("debug logging disabled")
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}