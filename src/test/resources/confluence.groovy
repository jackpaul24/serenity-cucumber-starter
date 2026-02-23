import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.util.regex.Pattern
import java.util.regex.Matcher

// ===== CONFIG =====
def serenityFile = new File("${env.WORKSPACE}/target/site/serenity/serenity-summary.json")
def projectName = "ProjectX"  // Replace with env.PROJECT_NAME if needed
def confluenceBaseUrl = "https://your-domain.atlassian.net/wiki"
def confluencePageId = "123456"
def spaceKey = "ENG"
def authToken = System.getenv("CONFLUENCE_AUTH_TOKEN")  // Secure Jenkins env var

// ===== BUILD REPORT LINK (from publishHTML) =====
def buildUrl = System.getenv("BUILD_URL")
def targetEnv = System.getenv("TARGET_ENVIRONMENT") ?: "env"
def reportName = "Serenity_Report_${targetEnv}"
def reportUrl = "${buildUrl}${reportName}/"

// ===== PARSE SERENITY REPORT =====
if (!serenityFile.exists()) {
    println "❌ Serenity summary file not found: ${serenityFile.absolutePath}"
    return
}

def json = new JsonSlurper().parse(serenityFile)
def total = json.results.counts.total ?: 0
def passed = json.results.counts.success ?: 0
def failed = json.results.counts.failure ?: 0
def now = new Date().format("yyyy-MM-dd HH:mm")

// ===== GET CURRENT CONFLUENCE PAGE =====
def getPageUrl = "${confluenceBaseUrl}/rest/api/content/${confluencePageId}?expand=version,body.storage"
def getConn = new URL(getPageUrl).openConnection()
getConn.setRequestProperty("Authorization", authToken)
getConn.setRequestProperty("Content-Type", "application/json")
def pageJson = new JsonSlurper().parse(getConn.inputStream)

def version = pageJson.version.number
def title = pageJson.title
def currentHtml = pageJson.body.storage.value

// ===== BUILD REGEX TO MATCH ROW =====
def rowPattern = Pattern.compile("<tr>\\s*<td>${projectName}</td>(.*?)</tr>", Pattern.DOTALL)
def matcher = rowPattern.matcher(currentHtml)
def newRow = ""
def reportCell = "<a href='${reportUrl}'>View Report</a>"

if (matcher.find()) {
    def existingTds = matcher.group(1)
    def tdMatches = (existingTds =~ /<td>(.*?)<\/td>/).collect()
    if (tdMatches.size() > 3) {
        reportCell = tdMatches[3][1]  // Preserve existing report link if present
    }

    newRow = "<tr><td>${projectName}</td><td>${total}</td><td>${passed}</td><td>${failed}</td><td>${reportCell}</td><td>${now}</td></tr>"
    currentHtml = matcher.replaceFirst(Matcher.quoteReplacement(newRow))

    println "✅ Updated row for '${projectName}'"
} else {
    // Insert new row with constructed report link
    newRow = "<tr><td>${projectName}</td><td>${total}</td><td>${passed}</td><td>${failed}</td><td>${reportCell}</td><td>${now}</td></tr>"
    def tableEnd = currentHtml.lastIndexOf("</table>")
    currentHtml = currentHtml.substring(0, tableEnd) + newRow + currentHtml.substring(tableEnd)

    println "➕ Inserted new row for '${projectName}'"
}

// ===== PUSH TO CONFLUENCE =====
def updatePayload = [
    id      : confluencePageId,
    type    : "page",
    title   : title,
    space   : [key: spaceKey],
    version : [number: version + 1],
    body    : [
        storage: [
            value: currentHtml,
            representation: "storage"
        ]
    ]
]

def putUrl = "${confluenceBaseUrl}/rest/api/content/${confluencePageId}"
def putConn = new URL(putUrl).openConnection()
putConn.setDoOutput(true)
putConn.setRequestMethod("PUT")
putConn.setRequestProperty("Authorization", authToken)
putConn.setRequestProperty("Content-Type", "application/json")
putConn.outputStream.withWriter("UTF-8") { writer ->
    writer << JsonOutput.toJson(updatePayload)
}

def responseCode = putConn.responseCode
if (responseCode == 200) {
    println "✅ Confluence page updated successfully for ${projectName}"
} else {
    println "❌ Failed to update Confluence page: HTTP ${responseCode}"
    putConn.errorStream?.withReader { reader -> println reader.text }
}