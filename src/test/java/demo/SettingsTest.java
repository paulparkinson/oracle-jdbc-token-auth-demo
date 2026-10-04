package demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @TempDir Path temp;
    private static final String BASE = "app.url=jdbc:oracle:thin:@tcps://example.invalid:1522/service\napp.mode=file\noracle.jdbc.tokenAuthentication=OCI_TOKEN\n";
    private Settings load(String text, Map<String,String> env) throws Exception {
        Path file = temp.resolve("test.properties");
        Files.writeString(file, text);
        return Settings.load(file, env);
    }
    @Test void substitutesWithoutInterpretingSecretCharacters() throws Exception {
        var s = load(BASE + "oracle.jdbc.tokenLocation=${TOKEN_DIR}\n", Map.of("TOKEN_DIR", "/tmp/a$b\\c"));
        assertEquals("/tmp/a$b\\c", s.jdbc().getProperty("oracle.jdbc.tokenLocation"));
    }
    @Test void missingVariableNamesOnlyTheVariable() {
        var error = assertThrows(IllegalArgumentException.class, () -> load(BASE + "oracle.jdbc.tokenLocation=${TOKEN_DIR}\n", Map.of()));
        assertEquals("Set environment variable TOKEN_DIR", error.getMessage());
    }
    @Test void rejectsPlaintextTransport() {
        assertThrows(IllegalArgumentException.class, () -> load(BASE.replace("tcps://", "tcp://"), Map.of()));
    }
    @Test void rejectsUrlPropertyOverrides() {
        assertThrows(IllegalArgumentException.class, () -> load(BASE.replace("/service", "/service?oracle.net.ssl_server_dn_match=false"), Map.of()));
    }
    @Test void enforcesServerIdentityVerification() throws Exception {
        assertEquals("true", load(BASE + "oracle.net.ssl_server_dn_match=false\n", Map.of()).jdbc().getProperty("oracle.net.ssl_server_dn_match"));
    }
    @Test void rejectsMixedTokenMechanisms() {
        assertThrows(IllegalArgumentException.class, () -> load(BASE + "oracle.jdbc.provider.accessToken=ojdbc-provider-oci-token\n", Map.of()));
    }
    @Test void rejectsDatabasePassword() {
        assertThrows(IllegalArgumentException.class, () -> load(BASE + "password=never-log-this\n", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> load(BASE + "oracle.jdbc.password=never-log-this\n", Map.of()));
    }
    @Test void everyShippedProfileLoadsWithoutNetwork() throws Exception {
        var env = Map.of("ADB_JDBC_URL", "jdbc:oracle:thin:@tcps://example.invalid:1522/service",
                "OCI_DB_TOKEN_DIR", "/tmp/tokens", "OCI_CONFIG_FILE", "/tmp/oci-config", "OCI_PROFILE", "DEFAULT",
                "OCI_DB_SCOPE", "urn:oracle:db::id::compartment::database", "ENTRA_DB_SCOPE", "https://example.invalid/db/.default",
                "AZURE_TENANT_ID", "tenant", "AZURE_CLIENT_ID", "client");
        try (var files = Files.list(Path.of("config"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".properties")).toList()) {
                var s = Settings.load(file, env);
                assertFalse(s.jdbc().containsKey("app.url"), file.toString());
            }
        }
    }
    @Test void poolConfigurationDoesNotConnectAndForcesFreshSessionsOnlyWhenRequested() throws Exception {
        var s = load(BASE, Map.of());
        var ds = TokenAuthDemo.pool(s, null, true);
        assertEquals(2, ds.getMaxPoolSize());
        assertEquals(1, ds.getMaxConnectionReuseCount());
        assertEquals("OCI_TOKEN", ds.getConnectionProperties().getProperty("oracle.jdbc.tokenAuthentication"));
        assertNull(ds.getUser());
        assertEquals(0, TokenAuthDemo.pool(s, null, false).getMaxConnectionReuseCount());
    }
}
