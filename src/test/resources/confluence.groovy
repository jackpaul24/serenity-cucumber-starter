// Get current timestamp
def now = new Date().format("yyyy-MM-dd HH:mm")

// Regex to match the full row based on Project Name
def rowRegex = /<tr>\s*<td>${projectName}<\/td>(.*?)<\/tr>/s

if (updatedHtml =~ rowRegex) {
    def matcher = (updatedHtml =~ rowRegex)
    matcher.find()
    def existingTds = matcher[1]  // captures all other <td> values

    // Extract all <td> values AFTER the project name
    def tdMatches = (existingTds =~ /<td>(.*?)<\/td>/).collect()
    def reportCell = tdMatches.size() > 3 ? tdMatches[3][1] : "—"
    def newCells = "<td>${total}</td><td>${passed}</td><td>${failed}</td><td>${reportCell}</td><td>${now}</td>"

    def newRow = "<tr><td>${projectName}</td>${newCells}</tr>"
    updatedHtml = updatedHtml.replaceFirst(rowRegex, newRow)

    println "Updated existing row for ${projectName}"
} else {
    // Insert new row with current date and dummy report link
    def newRow = "<tr><td>${projectName}</td><td>${total}</td><td>${passed}</td><td>${failed}</td><td><a href='https://reports.example.com/${projectName}/latest'>View Report</a></td><td>${now}</td></tr>"
    def tableEnd = updatedHtml.lastIndexOf("</table>")
    updatedHtml = updatedHtml.substring(0, tableEnd) + newRow + updatedHtml.substring(tableEnd)
    println "Inserted new row for ${projectName}"
}