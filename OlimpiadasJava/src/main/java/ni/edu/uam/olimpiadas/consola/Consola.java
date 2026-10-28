package ni.edu.uam.olimpiadas.consola;

import ni.edu.uam.olimpiadas.dominio.EstadoRuta;
import ni.edu.uam.olimpiadas.dominio.ResultadoRuta;
import ni.edu.uam.olimpiadas.persistencia.ArchivoMapaRepository;
import ni.edu.uam.olimpiadas.persistencia.PersistenciaException;
import ni.edu.uam.olimpiadas.servicios.DijkstraService;
import ni.edu.uam.olimpiadas.servicios.ServicioMapa;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Arrays;

public final class Consola {
    private final BufferedReader entrada;
    private final PrintStream salida;
    private final PrintStream errores;

    public Consola(Reader entrada, PrintStream salida, PrintStream errores) {
        this.entrada = new BufferedReader(entrada);
        this.salida = salida;
        this.errores = errores;
    }

    public int ejecutar(String[] argumentos) {
        try {
            Path archivo = Path.of("datos", "estado.mapa");
            int inicio = 0;
            if (argumentos.length > 0 && argumentos[0].equals("--archivo")) {
                if (argumentos.length < 2 || argumentos[1].isBlank()) {
                    throw new IllegalArgumentException("Indique una ruta después de --archivo.");
                }
                archivo = Path.of(argumentos[1]);
                inicio = 2;
            }
            String[] comando = Arrays.copyOfRange(argumentos, inicio, argumentos.length);
            if (comando.length == 1 && (comando[0].equals("ayuda") || comando[0].equals("--help"))) {
                ayuda();
                return 0;
            }
            var servicio = new ServicioMapa(new ArchivoMapaRepository(archivo), new DijkstraService());
            if (comando.length == 0) {
                salida.println("MAPA DE SEDES | " + archivo.toAbsolutePath().normalize());
                menu(servicio);
            } else {
                ejecutarComando(servicio, comando);
            }
            return 0;
        } catch (IllegalArgumentException ex) {
            errores.println("ERROR: " + ex.getMessage());
            return 2;
        } catch (PersistenciaException ex) {
            errores.println("ERROR DE ARCHIVO: " + ex.getMessage());
            return 1;
        } catch (IOException ex) {
            errores.println("ERROR DE ENTRADA: no se pudo leer la consola.");
            return 1;
        }
    }

    private void menu(ServicioMapa servicio) throws IOException {
        while (true) {
            salida.println();
            salida.println("1 Sede | 2 Conexión | 3 Ruta | 4 Mapa | 5 Historial | 6 Importar | 7 Exportar | 0 Salir");
            try {
                String opcion = leer("Opción: ");
                switch (opcion) {
                    case "0" -> {
                        salida.println("Estado guardado. Hasta luego.");
                        return;
                    }
                    case "1" -> ejecutarComando(servicio, new String[]{"sede", leer("Nombre: ")});
                    case "2" -> ejecutarComando(servicio, new String[]{"conexion", leer("Origen: "),
                            leer("Destino: "), leer("Peso: ")});
                    case "3" -> ejecutarComando(servicio, new String[]{"ruta", leer("Origen: "), leer("Destino: ")});
                    case "4" -> ejecutarComando(servicio, new String[]{"mapa"});
                    case "5" -> ejecutarComando(servicio, new String[]{"historial"});
                    case "6" -> ejecutarComando(servicio, new String[]{"importar", leer("Archivo de origen: ")});
                    case "7" -> ejecutarComando(servicio, new String[]{"exportar", leer("Archivo de destino: ")});
                    default -> throw new IllegalArgumentException("Seleccione una opción entre 0 y 7.");
                }
            } catch (FinEntradaException ex) {
                salida.println();
                return;
            } catch (IllegalArgumentException | PersistenciaException ex) {
                errores.println("ERROR: " + ex.getMessage());
            }
        }
    }

    private String leer(String mensaje) throws IOException {
        salida.print(mensaje);
        salida.flush();
        String valor = entrada.readLine();
        if (valor == null) {
            throw new FinEntradaException();
        }
        return valor.strip();
    }

    private void ejecutarComando(ServicioMapa servicio, String[] comando) {
        switch (comando[0]) {
            case "sede" -> {
                exigirCantidad(comando, 2, "sede NOMBRE");
                servicio.agregarSede(comando[1]);
                salida.println("SEDE_GUARDADA: " + comando[1]);
            }
            case "conexion" -> {
                exigirCantidad(comando, 4, "conexion ORIGEN DESTINO PESO");
                int peso;
                try {
                    peso = Integer.parseInt(comando[3]);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("El peso debe ser un entero entre 0 y 2147483647.");
                }
                servicio.agregarConexion(comando[1], comando[2], peso);
                salida.println("CONEXION_GUARDADA: " + comando[1] + " -> " + comando[2] + " = " + peso);
            }
            case "ruta" -> {
                exigirCantidad(comando, 3, "ruta ORIGEN DESTINO");
                mostrarRuta(servicio.calcularRuta(comando[1], comando[2]));
            }
            case "mapa" -> {
                exigirCantidad(comando, 1, "mapa");
                var mapa = servicio.consultarMapa();
                salida.println("SEDES: " + String.join(", ", mapa.getSedes()));
                mapa.getConexiones().forEach(conexion -> salida.println(
                        conexion.getOrigen() + " -> " + conexion.getDestino() + " = " + conexion.getPeso()));
                salida.println("TOTAL: " + mapa.getSedes().size() + " sedes, " + mapa.getConexiones().size() + " conexiones");
            }
            case "historial" -> {
                exigirCantidad(comando, 1, "historial");
                var historial = servicio.historial();
                if (historial.isEmpty()) {
                    salida.println("SIN_CONSULTAS");
                } else {
                    historial.forEach(this::mostrarRuta);
                }
            }
            case "importar" -> {
                exigirCantidad(comando, 2, "importar ARCHIVO");
                servicio.importar(new ArchivoMapaRepository(Path.of(comando[1])));
                salida.println("MAPA_IMPORTADO: " + comando[1]);
            }
            case "exportar" -> {
                exigirCantidad(comando, 2, "exportar ARCHIVO");
                servicio.exportar(new ArchivoMapaRepository(Path.of(comando[1])));
                salida.println("MAPA_EXPORTADO: " + comando[1]);
            }
            default -> throw new IllegalArgumentException("Comando desconocido. Use ayuda para ver los comandos.");
        }
    }

    private void mostrarRuta(ResultadoRuta resultado) {
        String trayecto = resultado.getOrigen() + " -> " + resultado.getDestino();
        if (resultado.getEstado() == EstadoRuta.SIN_RUTA) {
            salida.println("SIN_RUTA | " + trayecto);
        } else {
            salida.println("ENCONTRADA | " + trayecto + " | costo=" + resultado.getCostoTotal()
                    + " | ruta=" + String.join(",", resultado.getRuta()));
        }
    }

    private void exigirCantidad(String[] comando, int cantidad, String uso) {
        if (comando.length != cantidad) {
            throw new IllegalArgumentException("Uso: " + uso);
        }
    }

    private void ayuda() {
        salida.println("Uso: java -jar target/olimpiadas-java-1.0.0.jar [--archivo RUTA] [COMANDO]");
        salida.println("Sin comando se abre el menú. Archivo predeterminado: datos/estado.mapa.");
        salida.println("sede NOMBRE | conexion ORIGEN DESTINO PESO | ruta ORIGEN DESTINO");
        salida.println("mapa | historial | importar ARCHIVO | exportar ARCHIVO | ayuda");
        salida.println("Use comillas para nombres o rutas que contengan espacios.");
        salida.println("Importar reemplaza el mapa y limpia el historial; exportar reemplaza el archivo de destino.");
    }

    private static final class FinEntradaException extends RuntimeException {
    }
}
