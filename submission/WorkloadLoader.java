import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class WorkloadLoader {
    private WorkloadLoader() {
    }

    public static List<Job> load(Path path) throws IOException {
        List<Job> jobs = new ArrayList<>();
        List<String> lines = Files.readAllLines(path);

        for (int lineNumber = 0; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (lineNumber == 0 && line.toLowerCase().startsWith("id,")) {
                continue;
            }

            String[] fields = line.split(",", -1);
            if (fields.length != 6) {
                throw new IllegalArgumentException(
                        "Expected 6 fields at line " + (lineNumber + 1));
            }

            try {
                String id = fields[0].trim();
                long arrivalMs = Long.parseLong(fields[1].trim());
                int priority = Integer.parseInt(fields[2].trim());
                long workMs = Long.parseLong(fields[3].trim());
                ResourceType resource = ResourceType.valueOf(fields[4].trim().toUpperCase());
                long resourceMs = Long.parseLong(fields[5].trim());

                if (id.isEmpty() || arrivalMs < 0 || priority < 1
                        || workMs < 0 || resourceMs < 0) {
                    throw new IllegalArgumentException("Invalid value");
                }
                if (resource == ResourceType.NONE && resourceMs != 0) {
                    throw new IllegalArgumentException(
                            "NONE jobs must have resourceMs=0");
                }

                jobs.add(new Job(id, arrivalMs, priority, workMs,
                    resource, resourceMs, jobs.size()));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "Invalid workload line " + (lineNumber + 1) + ": " + line,
                        exception);
            }
        }

        if (jobs.isEmpty()) {
            throw new IllegalArgumentException("Workload contains no jobs");
        }
        return jobs;
    }
}
