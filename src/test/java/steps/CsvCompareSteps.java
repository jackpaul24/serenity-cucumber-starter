package steps;

import net.thucydides.core.annotations.Step;
import net.thucydides.core.annotations.Steps;
import net.thucydides.core.annotations.Title;
import net.thucydides.core.steps.ScenarioSteps;
import org.junit.Assert;
import io.cucumber.java.en.*;
import utils.CsvComparator;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class CsvCompareSteps extends ScenarioSteps {

    private String baselineDir;
    private String actualDir;
    private String baselineFilePath;
    private String actualFilePath;
    private CsvComparator.ComparisonResult comparisonResult;

    @Given("the baseline and actual CSV directories are configured")
    public void read_csv_directories_from_config() {
        Properties props = CsvComparator.loadProperties();
        baselineDir = props.getProperty("csv.baseline.dir");
        actualDir = props.getProperty("csv.actual.dir");
        Assert.assertNotNull("Baseline directory not configured", baselineDir);
        Assert.assertNotNull("Actual directory not configured", actualDir);
    }

    @When("I compare the CSV file {string} ignoring columns {string}")
    public void compare_csv_files(String filename, String ignoreColumns) {
        baselineFilePath = baselineDir + filename;
        actualFilePath = actualDir + filename;
        List<String> ignoreList = ignoreColumns.isEmpty() ? List.of() : Arrays.asList(ignoreColumns.split(","));
        comparisonResult = CsvComparator.compareCsvFiles(
                baselineFilePath,
                actualFilePath,
                "ID", // key column
                ignoreList
        );
    }

    @Then("the differences should be reported in the test report")
    public void report_differences() {
        CsvComparator.logComparisonResult(comparisonResult, baselineFilePath, actualFilePath);
        Assert.assertTrue("Differences found:\n" + comparisonResult.getSummary(),
                comparisonResult.isMatched());
    }
}

