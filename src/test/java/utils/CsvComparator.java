package utils;

import com.opencsv.CSVReader;
import net.thucydides.core.annotations.Step;
import net.thucydides.core.steps.StepEventBus;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class CsvComparator {

    public static Properties loadProperties() {
        Properties props = new Properties();
        try (FileReader reader = new FileReader("serenity.properties")) {
            props.load(reader);
        } catch (IOException e) {
            // Try config.properties as fallback
            try (FileReader reader = new FileReader("config.properties")) {
                props.load(reader);
            } catch (IOException ex) {
                throw new RuntimeException("Could not load properties file", ex);
            }
        }
        return props;
    }

    public static ComparisonResult compareCsvFiles(
            String baselinePath,
            String actualPath,
            String keyColumn,
            List<String> ignoreColumns
    ) {
        try {
            Map<String, Map<String, String>> baselineRows = loadCsvAsMap(baselinePath, keyColumn, ignoreColumns);
            Map<String, Map<String, String>> actualRows = loadCsvAsMap(actualPath, keyColumn, ignoreColumns);

            Set<String> allKeys = new HashSet<>();
            allKeys.addAll(baselineRows.keySet());
            allKeys.addAll(actualRows.keySet());

            List<String> diffRows = new ArrayList<>();
            List<String> diffColumns = new ArrayList<>();

            for (String key : allKeys) {
                Map<String, String> baseRow = baselineRows.get(key);
                Map<String, String> actualRow = actualRows.get(key);

                if (baseRow == null) {
                    diffRows.add("Row with key '" + key + "' missing in baseline");
                    continue;
                }
                if (actualRow == null) {
                    diffRows.add("Row with key '" + key + "' missing in actual");
                    continue;
                }

                for (String col : baseRow.keySet()) {
                    String baseVal = baseRow.get(col);
                    String actualVal = actualRow.get(col);
                    if (!Objects.equals(baseVal, actualVal)) {
                        diffColumns.add("Row '" + key + "', Column '" + col + "': baseline='" + baseVal + "', actual='" + actualVal + "'");
                    }
                }
            }

            boolean matched = diffRows.isEmpty() && diffColumns.isEmpty();
            return new ComparisonResult(matched, diffRows, diffColumns, ignoreColumns);

        } catch (Exception e) {
            throw new RuntimeException("Error comparing CSV files", e);
        }
    }

    private static Map<String, Map<String, String>> loadCsvAsMap(String path, String keyColumn, List<String> ignoreColumns) throws IOException {
        try (CSVReader reader = new CSVReader(new FileReader(path))) {
            String[] header = reader.readNext();
            if (header == null) throw new RuntimeException("CSV file " + path + " is empty");

            List<String> columns = Arrays.stream(header)
                    .filter(col -> !ignoreColumns.contains(col.trim()))
                    .collect(Collectors.toList());

            int keyIdx = Arrays.asList(header).indexOf(keyColumn);
            if (keyIdx == -1) throw new RuntimeException("Key column '" + keyColumn + "' not found in " + path);

            Map<String, Map<String, String>> rows = new HashMap<>();
            String[] row;
            while ((row = reader.readNext()) != null) {
                String key = row[keyIdx];
                Map<String, String> rowMap = new HashMap<>();
                for (int i = 0; i < header.length; i++) {
                    String col = header[i];
                    if (!ignoreColumns.contains(col.trim())) {
                        rowMap.put(col, i < row.length ? row[i] : "");
                    }
                }
                rows.put(key, rowMap);
            }
            return rows;
        }
    }

    @Step("CSV Comparison Report")
    public static void logComparisonResult(ComparisonResult result, String baselinePath, String actualPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("Compared files:\nBaseline: ").append(baselinePath)
          .append("\nActual: ").append(actualPath)
          .append("\nIgnored columns: ").append(result.getIgnoredColumns())
          .append("\n");

        if (result.isMatched()) {
            sb.append("No differences found.");
        } else {
            sb.append("Row differences:\n");
            result.getRowDifferences().forEach(r -> sb.append("  ").append(r).append("\n"));
            sb.append("Column differences:\n");
            result.getColumnDifferences().forEach(c -> sb.append("  ").append(c).append("\n"));
        }
        StepEventBus.getEventBus().stepFinished(sb.toString());
    }

    public static class ComparisonResult {
        private final boolean matched;
        private final List<String> rowDifferences;
        private final List<String> columnDifferences;
        private final List<String> ignoredColumns;

        public ComparisonResult(boolean matched, List<String> rowDifferences, List<String> columnDifferences, List<String> ignoredColumns) {
            this.matched = matched;
            this.rowDifferences = rowDifferences;
            this.columnDifferences = columnDifferences;
            this.ignoredColumns = ignoredColumns;
        }

        public boolean isMatched() { return matched; }
        public List<String> getRowDifferences() { return rowDifferences; }
        public List<String> getColumnDifferences() { return columnDifferences; }
        public List<String> getIgnoredColumns() { return ignoredColumns; }
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            if (!rowDifferences.isEmpty()) {
                sb.append("Row differences:\n");
                rowDifferences.forEach(r -> sb.append(r).append("\n"));
            }
            if (!columnDifferences.isEmpty()) {
                sb.append("Column differences:\n");
                columnDifferences.forEach(c -> sb.append(c).append("\n"));
            }
            return sb.toString();
        }
    }
}

