package steps;

import com.deque.axe.AXE;
import net.thucydides.core.annotations.Step;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.WebDriver;

import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class AccessibilitySteps {

    private static final URL scriptUrl = AccessibilitySteps.class.getResource("/axe.min.js");

    WebDriver driver;

    public AccessibilitySteps(WebDriver driver) {
        this.driver = driver;
    }

    @Step("Check accessibility violations on the current page")
    public void checkAccessibility() {
        JSONObject responseJSON = new AXE.Builder(driver, scriptUrl).analyze();
        JSONArray violations = responseJSON.getJSONArray("violations");

        if (violations.length() == 0) {
            System.out.println("No accessibility violations found.");
        } else {
            AXE.writeResults("accessibilityReport", responseJSON);
            System.out.println(violations.toString(2));
            assertThat("Accessibility violations found", violations.length(), is(0));
        }
    }
}