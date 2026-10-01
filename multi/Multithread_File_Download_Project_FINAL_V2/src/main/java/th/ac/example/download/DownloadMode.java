package th.ac.example.download;

public enum DownloadMode {
    TRADITIONAL,
    NIO;

    public static DownloadMode parse(String value) {
        if (value == null) throw new IllegalArgumentException("mode is required");
        return switch (value.toLowerCase()) {
            case "traditional", "trad", "io" -> TRADITIONAL;
            case "nio", "native", "transfer" -> NIO;
            default -> throw new IllegalArgumentException("mode must be traditional or nio");
        };
    }
}
