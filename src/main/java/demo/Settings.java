package demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/** Loads a sample configuration without ever printing credential values. */
public record Settings(String url, String mode, Properties jdbc, Properties app) {
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)}");

    public static Settings load(Path path, Map<String, String> env) throws IOException {
        Properties values = new Properties();
        try (var reader = Files.newBufferedReader(path)) { values.load(reader); }
        for (String key : values.stringPropertyNames()) {
            var matcher = VARIABLE.matcher(values.getProperty(key));
            StringBuilder result = new StringBuilder();
            while (matcher.find()) {
                String value = env.get(matcher.group(1));
                if (value == null || value.isBlank())
                    throw new IllegalArgumentException("Set environment variable " + matcher.group(1));
                matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(value));
            }
            matcher.appendTail(result);
            values.setProperty(key, result.toString());
        }
        String url = required(values, "app.url");
        if (!url.startsWith("jdbc:oracle:thin:@") || url.contains("?") || url.contains("\n"))
            throw new IllegalArgumentException("Use a credential-free Oracle Thin URL without query parameters; put options in the properties file");
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        if (!(lower.startsWith("jdbc:oracle:thin:@tcps://") || lower.contains("(protocol=tcps)")))
            throw new IllegalArgumentException("Use an explicit TCPS URL or TCPS descriptor from Database Connection");
        String mode = values.getProperty("app.mode", "provider");
        if (!java.util.Set.of("file", "provider", "sdk").contains(mode))
            throw new IllegalArgumentException("app.mode must be file, provider, or sdk");
        Properties jdbc = new Properties();
        for (String key : values.stringPropertyNames()) {
            if (key.startsWith("app.")) continue;
            if (!(key.startsWith("oracle.jdbc.") || key.startsWith("oracle.net.")))
                throw new IllegalArgumentException("Unexpected property: " + key);
            if (java.util.Set.of("oracle.jdbc.user", "oracle.jdbc.password").contains(key))
                throw new IllegalArgumentException("Do not set database credentials for token authentication");
            jdbc.setProperty(key, values.getProperty(key));
        }
        jdbc.setProperty("oracle.net.ssl_server_dn_match", "true");
        jdbc.putIfAbsent("oracle.net.CONNECT_TIMEOUT", "15000");
        jdbc.putIfAbsent("oracle.jdbc.ReadTimeout", "30000");
        String provider = jdbc.getProperty("oracle.jdbc.provider.accessToken");
        String tokenAuth = jdbc.getProperty("oracle.jdbc.tokenAuthentication");
        if (mode.equals("provider")) {
            if (!java.util.Set.of("ojdbc-provider-oci-token", "ojdbc-provider-azure-token").contains(provider == null ? "" : provider))
                throw new IllegalArgumentException("Set a supported accessToken provider");
            required(jdbc, "oracle.jdbc.provider.accessToken.scope");
            required(jdbc, "oracle.jdbc.provider.accessToken.authenticationMethod");
            if (tokenAuth != null) throw new IllegalArgumentException("Do not mix provider and tokenAuthentication settings");
        } else if (provider != null || (mode.equals("sdk") && tokenAuth != null)) {
            throw new IllegalArgumentException("Use only one token acquisition mechanism");
        } else if (mode.equals("file") && !"OCI_TOKEN".equals(tokenAuth)) {
            throw new IllegalArgumentException("File mode requires OCI_TOKEN");
        }
        if (mode.equals("sdk")) required(values, "app.oci.scope");
        return new Settings(url, mode, jdbc, values);
    }

    static String required(Properties values, String key) {
        String value = values.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing property " + key);
        return value;
    }
}
