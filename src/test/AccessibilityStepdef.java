@Steps
AccessibilitySteps accessibilitySteps;

@When("the user checks the accessibility of the form")
public void user_checks_accessibility() {
    accessibilitySteps.checkAccessibility();
}