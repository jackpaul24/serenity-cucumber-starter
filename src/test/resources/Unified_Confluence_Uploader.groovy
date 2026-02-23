
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.util.regex.Pattern
import java.util.regex.Matcher
import java.nio.file.Files
import java.net.URLEncoder

// ===== CONFIG =====
def serenityFile = new File("${env.WORKSPACE}/target/site/serenity/serenity-summary.json")
def reportFile = new File("${env.WORKSPACE}/target/site/serenity/index.html")
def projectName = System.getenv("PROJECT_NAME") ?: "ProjectX"
def confluenceBaseUrl = "https://your-domain.atlassian.net/wiki"
def confluencePageId = "123456"
def spaceKey = "ENG"
def authToken = System.getenv("CONFLUENCE_AUTH_TOKEN")

def now = new Date().format("yyyy-MM-dd HH:mm")

// ===== PARSE SERENITY REPORT =====
if (!serenityFile.exists()) {
    println "❌ Serenity summary not found: ${serenityFile.absolutePath}"
    return
}
def json = new JsonSlurper().parse(serenityFile)
def total = json.results.counts.total ?: 0
def passed = json.results.counts.success ?: 0
def failed = json.results.counts.failure ?: 0

// ===== UPLOAD index.html as ATTACHMENT =====
if (!reportFile.exists()) {
    println "❌ Report file not found: ${reportFile.absolutePath}"
    return
}
println "📦 Uploading report: ${reportFile.name}"
def uploadUrl = "${confluenceBaseUrl}/rest/api/content/${confluencePageId}/child/attachment"
def checkUrl = "${uploadUrl}?filename=${URLEncoder.encode(reportFile.name, "UTF-8")}"
def checkConn = new URL(checkUrl).openConnection()
checkConn.setRequestProperty("Authorization", authToken)
checkConn.setRequestProperty("Accept", "application/json")

def method = "POST"
if (checkConn.responseCode == 200) {
    def checkJson = new JsonSlurper().parse(checkConn.inputStream)
    if (checkJson.results && checkJson.results.size() > 0) {
        method = "PUT"
        uploadUrl += "/" + checkJson.results[0].id + "/data"
        println "📝 File exists, updating attachment."
    }
}

def boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
def uploadConn = new URL(uploadUrl).openConnection()
uploadConn.setDoOutput(true)
uploadConn.setRequestMethod(method)
uploadConn.setRequestProperty("Authorization", authToken)
uploadConn.setRequestProperty("X-Atlassian-Token", "no-check")
uploadConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)

uploadConn.outputStream.withStream { out ->
    out.write("--${boundary}\r\n".getBytes("UTF-8"))
    out.write("Content-Disposition: form-data; name=\"file\"; filename=\"${reportFile.name}\"\r\n".getBytes("UTF-8"))
    out.write("Content-Type: text/html\r\n\r\n".getBytes("UTF-8"))
    out.write(Files.readAllBytes(reportFile.toPath()))
    out.write("\r\n--${boundary}--\r\n".getBytes("UTF-8"))
}

if (!(uploadConn.responseCode in [200, 201])) {
    println "❌ Upload failed: HTTP ${uploadConn.responseCode}"
    uploadConn.errorStream?.withReader { reader -> println reader.text }
    return
}
println "✅ Report uploaded to Confluence"

// ===== UPDATE CONFLUENCE PAGE =====
def getPageUrl = "${confluenceBaseUrl}/rest/api/content/${confluencePageId}?expand=version,body.storage"
def getConn = new URL(getPageUrl).openConnection()
getConn.setRequestProperty("Authorization", authToken)
getConn.setRequestProperty("Content-Type", "application/json")
def pageJson = new JsonSlurper().parse(getConn.inputStream)

def version = pageJson.version.number
def title = pageJson.title
def currentHtml = pageJson.body.storage.value

def downloadLink = "${confluenceBaseUrl}/download/attachments/${confluencePageId}/${URLEncoder.encode(reportFile.name, "UTF-8")}"
def reportCell = "<a href='${downloadLink}'>Download Report</a>"

def rowPattern = Pattern.compile("<tr>\s*<td>${projectName}</td>(.*?)</tr>", Pattern.DOTALL)
def matcher = rowPattern.matcher(currentHtml)
def newRow = ""

if (matcher.find()) {
    newRow = "<tr><td>${projectName}</td><td>${total}</td><td>${passed}</td><td>${failed}</td><td>${reportCell}</td><td>${now}</td></tr>"
    currentHtml = matcher.replaceFirst(Matcher.quoteReplacement(newRow))
    println "✅ Row updated in Confluence"
} else {
    newRow = "<tr><td>${projectName}</td><td>${total}</td><td>${passed}</td><td>${failed}</td><td>${reportCell}</td><td>${now}</td></tr>"
    def tableEnd = currentHtml.lastIndexOf("</table>")
    currentHtml = currentHtml.substring(0, tableEnd) + newRow + currentHtml.substring(tableEnd)
    println "➕ New row inserted for ${projectName}"
}

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

if (putConn.responseCode == 200) {
    println "✅ Confluence page updated successfully."
} else {
    println "❌ Failed to update page. HTTP ${putConn.responseCode}"
    putConn.errorStream?.withReader { reader -> println reader.text }
}
