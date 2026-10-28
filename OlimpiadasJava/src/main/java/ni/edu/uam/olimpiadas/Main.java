package ni.edu.uam.olimpiadas;

import ni.edu.uam.olimpiadas.consola.Consola;

import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        var salida = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        var errores = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        int codigo = new Consola(new InputStreamReader(System.in, StandardCharsets.UTF_8), salida, errores)
                .ejecutar(args);
        if (codigo != 0) {
            System.exit(codigo);
        }
    }
}
