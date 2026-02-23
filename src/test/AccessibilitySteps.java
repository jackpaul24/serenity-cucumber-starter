import com.deque.axe.AXE;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import net.serenitybdd.core.steps.UIInteractionSteps;
import org.junit.Assert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;

public class AccessibilitySteps extends UIInteractionSteps {

    private static final String AXE_SCRIPT_PATH = "src/test/resources/axe.min.js";

    public void checkAccessibility() throws IOException {
        WebDriver driver = getDriver(); // Ensures Serenity injects the driver

        // Inject axe.min.js
        String axeScript = new String(Files.readAllBytes(Paths.get(AXE_SCRIPT_PATH)));
        ((JavascriptExecutor) driver).executeScript(axeScript);

        // Run axe.run() and capture result as Java Map
        Object result = ((JavascriptExecutor) driver).executeScript("return axe.run()");
        ObjectMapper mapper = new ObjectMapper();

        // Convert result to JSON
        String jsonString = mapper.writeValueAsString(result);
        JSONObject jsonObject = new JSONObject(jsonString);

        // Extract violations
        JSONArray violations = jsonObject.getJSONArray("violations");

        // Filter by tags
        Set<String> filterTags = new HashSet<>(Arrays.asList("wcag2a", "wcag2aa"));
        JSONArray filteredViolations = new JSONArray();

        for (int i = 0; i < violations.length(); i++) {
            JSONObject violation = violations.getJSONObject(i);
            JSONArray tags = violation.getJSONArray("tags");

            for (int j = 0; j < tags.length(); j++) {
                if (filterTags.contains(tags.getString(j))) {
                    filteredViolations.put(violation);
                    break;
                }
            }
        }

        System.out.println("Filtered Violations: " + filteredViolations.toString(2));

        // Fail test if there are violations
        Assert.assertTrue("Accessibility violations found: " + filteredViolations.length(), filteredViolations.length() == 0);
    }
}