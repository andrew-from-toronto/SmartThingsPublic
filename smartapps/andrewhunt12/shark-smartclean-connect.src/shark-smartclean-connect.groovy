/**
 *  Shark SmartClean Connect
 *
 *  Copyright 2019 Andrew Hunt
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
 * 	Notes:
 *  	Shout out to Trent Foley for his work on the Tesla Connect SmartApp. A lot of this is based on his code. Thank you!
 *  	This has only been tested with the RV1001AE, but should work with the RV1000 since they are the same except for the bin. 
 *		I expect it would also work on other Shark WiFi vacuums, however do not have one to test with.
 */
definition(
    name: "Shark SmartClean Connect",
    namespace: "andrewhunt12",
    author: "Andrew Hunt",
    description: "The unofficial way to make your Shark IQ Vacuum smart(er).",
    category: "Convenience",
    iconUrl: "https://i.imgur.com/uik5R5x.png",
    iconX2Url: "https://i.imgur.com/kM4PoiK.png",
    iconX3Url: "https://i.imgur.com/kM4PoiK.png",
    singleInstance: true
) {
    appSetting "appid"
    appSetting "appsecret"
}

def getAuthServerUrl() { "https://user-field.aylanetworks.com" }
def getServerUrl() { "https://ads-field.aylanetworks.com/apiv1/" }
def getUserAgent() { "SharkClean/2.1.7 (iPhone; iOS 13.2.3; Scale/3.00)" }
def getServerHeaders() { [ 'User-Agent': userAgent, 'If-Modified-Since': 'Wed, 14 Dec 2050 18:43:58 GMT' ] } // Stay under the radar
def getAccessToken() {
	if (!state.accessToken) {
		refreshAccessToken()
	}
	state.accessToken
}

preferences {
	page(name: "login", title: "Shark SmartClean")
    page(name: "selectVacuums", title: "Shark SmartClean")
}

def login() {
	def showUninstall = email != null && password != null
	return dynamicPage(name: "login", title: "Connect your Shark Robot", nextPage: "selectVacuums", uninstall: showUninstall) {
		section("Log in to your Shark SmartClean account:") {
			input "email", "text", title: "Email", required: true, autoCorrect:false
			input "password", "password", title: "Password", required: true, autoCorrect:false
		}
		section("To use Shark SmartClean, SmartThings encrypts and securely stores your Shark credentials.") {}
	}
}

def selectVacuums() {
    try {
		refreshAccountVacuums()
		return dynamicPage(name: "selectVacuums", title: "Connect your Shark Robot", install:true, uninstall:true) {
			section("Select which Shark Vacuums to connect"){
				input(name: "selectedVacuums", type: "enum", required:false, multiple:true, options: state.accountVacuums.collectEntries{ k, v -> [k, "${v.product_name} (${k})"] })
			}
		}
	} catch (Exception e) {
    	log.error e
        return dynamicPage(name: "selectVacuums", title: "Connect your Shark Robot", install:false, uninstall:true, nextPage:"") {
			section("") {
				paragraph "Please check your username and password"
			}
		}
	}
}

def refreshAccessToken() {
	log.debug "refreshAccessToken"
	try {
        if (state.refreshToken) {
        	log.debug "Found refresh token so attempting a refresh"
            try {
                httpPostJson([
                    uri: authServerUrl,
                    path: "/users/refresh_token.json",
                    headers: serverHeaders,
                    body: [
                        refresh_token: state.refreshToken
                    ]
                ]) { resp ->
                    state.accessToken = resp.data.access_token
                    state.refreshToken = resp.data.refresh_token
                }
            } catch (groovyx.net.http.HttpResponseException e) {
            	log.warn e
                state.accessToken = null
                if (e.response?.data?.status?.code == 14) {
                    state.refreshToken = null
                }
            }
        }

        if (!state.accessToken) {
        	log.debug "Attemtping to get access token using user creds" 
            httpPostJson([
                uri: authServerUrl,
                path: "/users/sign_in.json",
                headers: [ 'User-Agent': userAgent ],
                body: [
                	user: [
                    	email: email,
                    	password: password,
                        application: [
                        	app_id: appSettings.appid,
                            app_secret: appSettings.appsecret
                        ]
                    ]
                    
                ]
            ]) { resp ->
            	log.debug "Received access token that will expire in ${resp.data.expires_in/60/60} hours"
                state.accessToken = resp.data.access_token
                state.refreshToken = resp.data.refresh_token
            }
        }
        
        if (state.accessToken) {
        	log.debug "Attemtping to get user UUID" 
            httpGet([
                uri: authServerUrl,
                path: "/users/get_user_profile.json",
                headers: serverHeaders + [
                Authorization: "auth_token ${accessToken}"
            ]
            ]) { resp ->
            	log.debug "Received user profile, storing UUID ${resp.data.uuid}"
                state.uuid = resp.data.uuid
            }
        }
    } catch (Exception e) {
        log.error "Unhandled exception in refreshAccessToken: $e"
    }
}

private authorizedHttpRequest(Map options = [:], String path, String method, Closure closure) {
    def attempt = options.attempt ?: 0
    
    log.debug "authorizedHttpRequest ${method} ${path} attempt ${attempt}"
    try {
    	def requestParameters = [
            uri: serverUrl,
            path: path,
            headers: serverHeaders + [
                Authorization: "auth_token ${accessToken}"
            ]
        ]
    
    	if (method == "GET") {
            httpGet(requestParameters) { resp -> closure(resp) }
        } else if (method == "POST") {
        	if (options.body) {
            	requestParameters["body"] = options.body
                log.debug "authorizedHttpRequest body: ${options.body}"
                httpPostJson(requestParameters) { resp -> closure(resp) }
            } else {
        		httpPost(requestParameters) { resp -> closure(resp) }
            }
        } else {
        	log.error "Invalid method ${method}"
        }
    } catch (groovyx.net.http.HttpResponseException e) {
    	if (e.statusCode == 401) {
        	if (attempt < 3) {
                refreshAccessToken()
                authorizedHttpRequest(path, mehod, closure, body: options.body, attempt: attempt++)
            } else {
            	log.error "Failed after 3 attempts to perform request: ${path}"
            }
        } else {
        	log.error "Request failed for path: ${path}.  ${e.response?.data}"
        }
    }
}

private refreshAccountVacuums() {
	log.debug "refreshAccountVacuums"

	state.accountVacuums = [:]

	authorizedHttpRequest("/apiv1/devices.json", "GET", { resp ->
    	log.debug "Found ${resp.data.size()} devices"
        resp.data.each { vacuum ->
            log.debug "Device Found - Name: ${vacuum.device.product_name} | Model: ${vacuum.device.model} | DSN: ${vacuum.device.dsn} | Key: ${vacuum.device.key}"
            state.accountVacuums[vacuum.device.dsn] = vacuum.device
        }
    })
}

private getSelectedVacuumsMap() {
	if (!selectedVacuums) {
    	return [:]
    }
        
    selectedVacuums.collectEntries { [ (it): state.accountVacuums[it] ] }
}

private refreshVacuumProperties(dsn, vacuum) {
	log.debug "refreshVacuumProperties for ${vacuum.product_name} (${dsn})"
    
	authorizedHttpRequest("/apiv1/dsns/${dsn}/properties.json", "GET", { resp ->
        def propsMap = [:]
        resp.data.each { prop ->
            propsMap[prop.property.name] = prop.property
        }

        log.debug "Props updated for ${vacuum.product_name} (${dsn})"
        state.accountVacuums[dsn].properties = propsMap
    })
}

private ensureDevicesForSelectedVehicles() {
	if (selectedVacuums) {
        selectedVacuums.each { dsn ->
            def d = getChildDevice(dsn)
            if(!d) {
                def vacuum = state.accountVacuums[dsn]
                device = addChildDevice(app.getNamespace(), "Shark SmartClean", dsn, null, [name:"${vacuum.product_name} (${dsn})", label: vacuum.product_name])
                log.debug "created device ${device.label} with id ${dsn}"
                device.initialize()
            } else {
                log.debug "device for ${d.label} with id ${dsn} already exists"
            }
        }
    }
}

private removeNoLongerSelectedChildDevices() {
	// Delete any that are no longer in settings
	def delete = getChildDevices().findAll { !selectedVacuums }
    if (delete)
		removeChildDevices(delete)
}

private removeChildDevices(delete) {
	log.debug "deleting ${delete.size()} vacuums"
    delete.each {
        deleteChildDevice(it.deviceNetworkId)
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def uninstalled() {
	log.debug "Uninstalled, removing child devices."

    removeChildDevices(getChildDevices())
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	log.debug "Initialize"
    
    ensureDevicesForSelectedVehicles()
    removeNoLongerSelectedChildDevices()
    runEvery15Minutes(refresh)
}

def refresh(String dsnFilter = null) {
	log.debug "Refresh"
    
    def vacuums = getSelectedVacuumsMap()
    
    if (dsnFilter != null) {
    	vacuums = vacuums.findAll { dsn, vacuum -> dsn == dsnFilter }
    }
        
    vacuums.each { dsn, vacuum ->
        log.debug "Refreshing ${vacuum.product_name} (${dsn})"

        refreshVacuumProperties(dsn, vacuum)

        def childDevice = getChildDevice(dsn)
        childDevice.process(vacuum)        
    }
}