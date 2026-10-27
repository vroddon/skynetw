package vroddon.skynetw;

import vroddon.skynet.claipo.ClaipoServer;
import java.io.IOException;


/**
 * https://github.com/vroddon/skynetw
 * D:\svn\skynetw\run.bat corre automáticamente al inicio. Estes el verdadero agente, pues.
 * Sirve en: https://localhost:8978/ping, https://localhost:8978/claipo
 * @author victor
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("Hello World!");
        new Thread(() -> {
            try {
                ClaipoServer.startServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();        
        
        UsageTracker.main(args);
    }

}
