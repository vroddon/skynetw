package vroddon.skynetw;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 *
 * @author victor
 */
public class ClaipoServer {

    public static void startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8978), 0);
        System.out.println("Creando servicios...");
        server.createContext("/ping", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                System.out.println("pong");
                String response = "pong"; // what we return
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        });
        
        server.createContext("/claipo", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                System.out.println("Serving a claipo request");

                // Read the request body
                InputStream is = exchange.getRequestBody();
                String jsonBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                is.close();

                // Save JSON to file
                Path outputPath = Path.of("D:\\svn\\victor\\claipo\\email.json");
                Files.writeString(outputPath, jsonBody, StandardCharsets.UTF_8);
                System.out.println("Saved email data to " + outputPath);

                // Launch the jar
                ProcessBuilder pb = new ProcessBuilder(
                        "java",
                        "-jar",
                        "D:\\svn\\victor\\claipo\\target\\claipo-0.1.0.jar"
                );
                pb.start();

                String response
                        = "<html>"
                        + "<body>"
                        + "<script>alert('hello world');</script>"
                        + "Hello from Claipo"
                        + "</body>"
                        + "</html>";
                exchange.getResponseHeaders().add("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try ( OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        });
        System.out.println("XXX");
        server.setExecutor(null); // default executor
        server.start();

        System.out.println("HTTP server started on http://localhost:8978/claipo");
        
        
    }
}
