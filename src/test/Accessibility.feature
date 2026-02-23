Feature: Webform Accessibility Compliance

  Scenario: Webform is accessible
    Given the user navigates to the webform
    When the user checks the accessibility of the form
    Then no accessibility violations should be found