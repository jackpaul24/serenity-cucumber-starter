import groovy.json.JsonSlurper
import groovy.json.JsonOutput

// ---- CONFIGURATION ----
def serenityFile = new File("${env.WORKSPACE}/target/site/serenity/serenity-summary.json")
def projectName = "ProjectX" // Or pass this as a parameter: env.PROJECT_NAME
def confluenceBaseUrl = "https://your-domain.atlassian.net/wiki"
def confluencePageId = "123456"
def spaceKey = "ENG"
def authHeader = "Basic base64encoded(email:token)"

// ---- PARSE SERENITY JSON ----
if (!serenityFile.exists()) {
    println "Serenity summary file not found: ${serenityFile}"
    return
}

def json = new JsonSlurper().parse(serenityFile)
def total = json.results.counts.total ?: 0
def passed = json.results.counts.success ?: 0
def failed = json.results.counts.failure ?: 0

def newRow = "<tr><td>${projectName}</td><td>${total}</td><td>${passed}</td><td>${failed}</td></tr>"

// ---- FETCH CURRENT PAGE FROM CONFLUENCE ----
def getPageUrl = "${confluenceBaseUrl}/rest/api/content/${confluencePageId}?expand=body.storage,version"
def getConn = new URL(getPageUrl).openConnection()
getConn.setRequestProperty("Authorization", authHeader)
getConn.setRequestProperty("Content-Type", "application/json")
def pageData = new JsonSlurper().parse(getConn.inputStream)

def version = pageData.version.number
def title = pageData.title
def currentHtml = pageData.body.storage.value

// ---- REPLACE OR INSERT ROW ----
def updatedHtml = currentHtml

def rowRegex = /<tr>\s*<td>${projectName}<\/td>.*?<\/tr>/s
if (updatedHtml =~ rowRegex) {
    updatedHtml = updatedHtml.replaceFirst(rowRegex, newRow)
    println "Updated existing row for ${projectName}"
} else {
    def tableEnd = updatedHtml.lastIndexOf("</table>")
    updatedHtml = updatedHtml.substring(0, tableEnd) + newRow + updatedHtml.substring(tableEnd)
    println "Inserted new row for ${projectName}"
}

// ---- SEND UPDATED PAGE BACK TO CONFLUENCE ----
def updatePayload = [
    id      : confluencePageId,
    type    : "page",
    title   : title,
    space   : [key: spaceKey],
    version : [number: version + 1],
    body    : [
        storage: [
            value: updatedHtml,
            representation: "storage"
        ]
    ]
]

def putUrl = "${confluenceBaseUrl}/rest/api/content/${confluencePageId}"
def putConn = new URL(putUrl).openConnection()
putConn.setDoOutput(true)
putConn.setRequestMethod("PUT")
putConn.setRequestProperty("Authorization", authHeader)
putConn.setRequestProperty("Content-Type", "application/json")
putConn.outputStream.withWriter("UTF-8") { writer ->
    writer << JsonOutput.toJson(updatePayload)
}

def responseCode = putConn.responseCode
if (responseCode == 200) {
    println "Successfully updated Confluence page with results for ${projectName}"
} else {
    println "Failed to update Confluence page: HTTP ${responseCode}"
    putConn.errorStream?.withReader { reader -> println reader.text }
}