Feature: Compare CSV files for differences

  Scenario Outline: Compare baseline and actual CSV files and report differences
    Given the baseline and actual CSV directories are configured
    When I compare the CSV file "<filename>" ignoring columns "<ignoreColumns>"
    Then the differences should be reported in the test report

    Examples:
      | filename      | ignoreColumns      |
      | data1.csv     | LastUpdated,Notes  |
      | data2.csv     | Timestamp          |
      | data3.csv     |                    |

