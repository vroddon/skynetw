package vroddon.skynetw;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;


/**
 * D:\svn\skynetw\run.bat corre automáticamente al inicio
 * Sirve en: https://localhost:8978/ping, https://localhost:8978/claipo
 * @author victor
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("Hello World!");
        new Thread(() -> {
            try {
                startServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();        
        
        UsageTracker.main(args);
    }
    private static void startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8978), 0);
        server.createContext("/ping", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
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
                ProcessBuilder pb = new ProcessBuilder(
                        "java",
                        "-jar",
                        "D:\\svn\\victor\\claipo\\target\\claipo-0.1.0.jar"
                );
                pb.start();  // non-blocking launch                
                
                String response =
                        "<html>" +
                        "<body>" +
                        "<script>alert('hello world');</script>" +
                        "Hello from Claipo" +
                        "</body>" +
                        "</html>";

                exchange.getResponseHeaders().add("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, response.getBytes().length);

                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        });

        server.setExecutor(null); // default executor
        server.start();

        System.out.println("HTTP server started on http://localhost:8978/claipo");
    }    
}
