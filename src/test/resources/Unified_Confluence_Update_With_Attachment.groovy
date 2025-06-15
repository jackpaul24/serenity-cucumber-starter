
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.util.regex.Pattern
import java.util.regex.Matcher
import java.nio.file.Files
import java.net.URLEncoder

// === CONFIG ===
def projectName = System.getenv("PROJECT_NAME") ?: "UnknownProject"
def confluenceBaseUrl = "https://your-domain.atlassian.net/wiki"
def confluencePageId = "123456"
def spaceKey = "ENG"
def authToken = System.getenv("CONFLUENCE_AUTH_TOKEN")  // Basic base64(email:api_token)
def buildUrl = System.getenv("BUILD_URL")
def targetEnv = System.getenv("TARGET_ENVIRONMENT") ?: "env"
def now = new Date().format("yyyy-MM-dd HH:mm")
def serenityFile = new File("${env.WORKSPACE}/target/site/serenity/serenity-summary.json")
def attachmentFile = new File("${env.WORKSPACE}/target/site/serenity/index.html")

try {
    def shouldPublish = System.getenv("PUBLISH_CONFLUENCE_RESULTS")?.toBoolean()
    if (!shouldPublish) {
        println "🔕 Skipping Confluence update — checkbox not selected."
        return
    }

    if (!serenityFile.exists()) {
        println "❌ Serenity summary file not found: ${serenityFile.absolutePath}"
        return
    }

    // === PARSE SERENITY SUMMARY ===
    def json = new JsonSlurper().parse(serenityFile)
    def total = json.results.counts.total ?: 0
    def passed = json.results.counts.success ?: 0
    def failed = json.results.counts.failure ?: 0

    // === UPLOAD HTML REPORT TO CONFLUENCE ===
    if (!attachmentFile.exists()) {
        println "❌ Report file not found: ${attachmentFile.absolutePath}"
        return
    }

    def uploadUrl = "${confluenceBaseUrl}/rest/api/content/${confluencePageId}/child/attachment"
    def checkUrl = "${uploadUrl}?filename=${URLEncoder.encode(attachmentFile.name, "UTF-8")}"
    def checkConn = new URL(checkUrl).openConnection()
    checkConn.setRequestProperty("Authorization", authToken)
    checkConn.setRequestProperty("Accept", "application/json")

    def method = "POST"
    if (checkConn.responseCode == 200) {
        def checkJson = new JsonSlurper().parse(checkConn.inputStream)
        if (checkJson.results && checkJson.results.size() > 0) {
            method = "PUT"
            uploadUrl += "/" + checkJson.results[0].id + "/data"
            println "📝 File already exists, updating attachment."
        } else {
            println "🆕 Uploading new attachment."
        }
    }

    def boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
    def uploadConn = new URL(uploadUrl).openConnection()
    uploadConn.setDoOutput(true)
    uploadConn.setRequestMethod(method)
    uploadConn.setRequestProperty("Authorization", authToken)
    uploadConn.setRequestProperty("X-Atlassian-Token", "no-check")
    uploadConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)

    def output = new StringBuilder()
    output.append("--${boundary}\r\n")
    output.append("Content-Disposition: form-data; name=\"file\"; filename=\"${attachmentFile.name}\"\r\n")
    output.append("Content-Type: text/html\r\n\r\n")

    uploadConn.outputStream.withStream { out ->
        out.write(output.toString().getBytes("UTF-8"))
        out.write(Files.readAllBytes(attachmentFile.toPath()))
        out.write("\r\n--${boundary}--\r\n".getBytes("UTF-8"))
    }

    def uploadRespCode = uploadConn.responseCode
    if (uploadRespCode != 200 && uploadRespCode != 201) {
        println "❌ Upload failed: HTTP ${uploadRespCode}"
        uploadConn.errorStream?.withReader { reader -> println reader.text }
        return
    }

    println "✅ Attachment uploaded."

    // === GET PAGE CONTENT ===
    def getPageUrl = "${confluenceBaseUrl}/rest/api/content/${confluencePageId}?expand=version,body.storage"
    def getConn = new URL(getPageUrl).openConnection()
    getConn.setRequestProperty("Authorization", authToken)
    getConn.setRequestProperty("Content-Type", "application/json")
    def pageJson = new JsonSlurper().parse(getConn.inputStream)

    def version = pageJson.version.number
    def title = pageJson.title
    def currentHtml = pageJson.body.storage.value

    def downloadLink = "${confluenceBaseUrl}/download/attachments/${confluencePageId}/${URLEncoder.encode(attachmentFile.name, "UTF-8")}"
    def reportCell = "<a href='${downloadLink}'>Download Report</a>"

    // === BUILD NEW ROW ===
    def rowPattern = Pattern.compile("<tr>\s*<td>${projectName}</td>(.*?)</tr>", Pattern.DOTALL)
    def matcher = rowPattern.matcher(currentHtml)
    def newRow = "<tr><td>${projectName}</td><td>${total}</td><td>${passed}</td><td>${failed}</td><td>${reportCell}</td><td>${now}</td></tr>"

    if (matcher.find()) {
        currentHtml = matcher.replaceFirst(Matcher.quoteReplacement(newRow))
        println "✅ Updated row for '${projectName}'"
    } else {
        def tableEnd = currentHtml.lastIndexOf("</table>")
        currentHtml = currentHtml.substring(0, tableEnd) + newRow + currentHtml.substring(tableEnd)
        println "➕ Inserted new row for '${projectName}'"
    }

    // === UPDATE PAGE ===
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
        println "✅ Confluence page updated successfully."
    } else {
        println "❌ Failed to update Confluence page. HTTP ${responseCode}"
        putConn.errorStream?.withReader { reader -> println reader.text }
    }

} catch (Exception ex) {
    println "❌ Exception occurred for project '${projectName}': ${ex.message}"
    throw ex
}
