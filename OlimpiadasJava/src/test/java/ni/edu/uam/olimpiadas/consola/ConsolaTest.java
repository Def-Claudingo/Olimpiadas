package ni.edu.uam.olimpiadas.consola;

import ni.edu.uam.olimpiadas.persistencia.ArchivoMapaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ConsolaTest {
    @TempDir
    Path temporal;

    @Test
    void calculaRutaYPersisteHistorialEntreInvocaciones() {
        Path archivo = temporal.resolve("estado.mapa");
        crearMapa(archivo);

        Ejecucion calculo = ejecutar(archivo, "", "ruta", "A", "B");
        Ejecucion historial = ejecutar(archivo, "", "historial");

        assertEquals(0, calculo.codigo());
        assertTrue(calculo.salida().contains("costo=3 | ruta=A,C,B"));
        assertTrue(calculo.errores().isEmpty());
        assertEquals(0, historial.codigo());
        assertTrue(historial.salida().contains("costo=3 | ruta=A,C,B"));
    }

    @Test
    void sinRutaEsUnResultadoNormalConCodigoCero() {
        Path archivo = temporal.resolve("estado.mapa");
        crearMapa(archivo);
        assertEquals(0, ejecutar(archivo, "", "sede", "Aislada").codigo());

        Ejecucion resultado = ejecutar(archivo, "", "ruta", "A", "Aislada");

        assertEquals(0, resultado.codigo());
        assertTrue(resultado.salida().contains("SIN_RUTA | A -> Aislada"));
        assertTrue(resultado.errores().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.5", "texto", "2147483648", "999999999999999999999"})
    void pesosInvalidosDevuelvenDosYConservanElEstado(String peso) throws IOException {
        Path archivo = temporal.resolve("estado.mapa");
        crearMapa(archivo);
        String previo = Files.readString(archivo);

        Ejecucion resultado = ejecutar(archivo, "", "conexion", "B", "A", peso);

        assertEquals(2, resultado.codigo());
        assertTrue(resultado.errores().startsWith("ERROR:"));
        assertFalse(resultado.errores().contains("Exception"));
        assertEquals(previo, Files.readString(archivo));
    }

    @Test
    void rechazaComandosConArgumentosFaltantesOSobrantes() throws IOException {
        Path archivo = temporal.resolve("estado.mapa");
        crearMapa(archivo);
        String previo = Files.readString(archivo);
        String[][] invalidos = {
                {"sede"}, {"conexion", "A", "B"}, {"ruta", "A"},
                {"mapa", "extra"}, {"historial", "extra"}, {"importar"},
                {"exportar"}, {"desconocido"}
        };

        for (String[] comando : invalidos) {
            Ejecucion resultado = ejecutar(archivo, "", comando);
            assertEquals(2, resultado.codigo(), Arrays.toString(comando));
            assertTrue(resultado.errores().startsWith("ERROR:"));
            assertEquals(previo, Files.readString(archivo));
        }
    }

    @Test
    void opcionArchivoSinRutaProduceErrorControlado() {
        Ejecucion resultado = ejecutarArgumentos("", "--archivo");

        assertEquals(2, resultado.codigo());
        assertTrue(resultado.errores().contains("Indique una ruta"));
    }

    @Test
    void sedeInexistenteProduceErrorControladoSinModificarElArchivo() throws IOException {
        Path archivo = temporal.resolve("estado.mapa");
        crearMapa(archivo);
        String previo = Files.readString(archivo);

        Ejecucion resultado = ejecutar(archivo, "", "ruta", "A", "X");

        assertEquals(2, resultado.codigo());
        assertTrue(resultado.errores().startsWith("ERROR:"));
        assertEquals(previo, Files.readString(archivo));
    }

    @Test
    void archivoCorruptoProduceCodigoUnoSinSobrescribirlo() throws IOException {
        Path archivo = temporal.resolve("corrupto.mapa");
        Files.writeString(archivo, "datos corruptos");

        Ejecucion resultado = ejecutar(archivo, "", "mapa");

        assertEquals(1, resultado.codigo());
        assertTrue(resultado.errores().startsWith("ERROR DE ARCHIVO:"));
        assertFalse(resultado.errores().contains("Exception"));
        assertEquals("datos corruptos", Files.readString(archivo));
    }

    @Test
    void ayudaFuncionaAunqueElArchivoDeEstadoEsteCorrupto() throws IOException {
        Path archivo = temporal.resolve("corrupto.mapa");
        Files.writeString(archivo, "datos corruptos");

        Ejecucion resultado = ejecutar(archivo, "", "--help");

        assertEquals(0, resultado.codigo());
        assertTrue(resultado.salida().contains("Uso:"));
        assertTrue(resultado.errores().isEmpty());
    }

    @Test
    void finDeEntradaCierraElMenuSinCrearArchivo() {
        Path archivo = temporal.resolve("nuevo.mapa");

        Ejecucion resultado = ejecutar(archivo, "");

        assertEquals(0, resultado.codigo());
        assertTrue(resultado.errores().isEmpty());
        assertFalse(Files.exists(archivo));
    }

    @Test
    void finDeEntradaDuranteUnaOperacionNoModificaElMapa() throws IOException {
        Path archivo = temporal.resolve("estado.mapa");
        crearMapa(archivo);
        String previo = Files.readString(archivo);

        Ejecucion resultado = ejecutar(archivo, "2\nB\nA\n");

        assertEquals(0, resultado.codigo());
        assertTrue(resultado.errores().isEmpty());
        assertEquals(previo, Files.readString(archivo));
    }

    @Test
    void menuSeRecuperaDeErrorYPermiteLaSiguienteOperacion() {
        Path archivo = temporal.resolve("estado.mapa");

        Ejecucion resultado = ejecutar(archivo, "9\n1\nSede Central\n0\n");

        assertEquals(0, resultado.codigo());
        assertTrue(resultado.errores().contains("Seleccione una opción"));
        assertTrue(resultado.salida().contains("SEDE_GUARDADA: Sede Central"));
        assertTrue(new ArchivoMapaRepository(archivo).cargar().getMapa().getSedes().contains("Sede Central"));
    }

    @Test
    void importarYExportarFuncionanConRutasQueContienenEspacios() {
        Path original = temporal.resolve("mapa original.mapa");
        Path exportado = temporal.resolve("mapa exportado.mapa");
        Path destino = temporal.resolve("mapa destino.mapa");
        crearMapa(original);

        Ejecucion exportacion = ejecutar(original, "", "exportar", exportado.toString());
        Ejecucion importacion = ejecutar(destino, "", "importar", exportado.toString());
        Ejecucion resultado = ejecutar(destino, "", "ruta", "A", "B");

        assertEquals(0, exportacion.codigo());
        assertEquals(0, importacion.codigo());
        assertEquals(0, resultado.codigo());
        assertTrue(resultado.salida().contains("costo=3 | ruta=A,C,B"));
    }

    private void crearMapa(Path archivo) {
        for (String sede : new String[]{"A", "B", "C"}) {
            assertEquals(0, ejecutar(archivo, "", "sede", sede).codigo());
        }
        assertEquals(0, ejecutar(archivo, "", "conexion", "A", "B", "4").codigo());
        assertEquals(0, ejecutar(archivo, "", "conexion", "A", "C", "1").codigo());
        assertEquals(0, ejecutar(archivo, "", "conexion", "C", "B", "2").codigo());
    }

    private Ejecucion ejecutar(Path archivo, String entrada, String... comando) {
        String[] argumentos = new String[comando.length + 2];
        argumentos[0] = "--archivo";
        argumentos[1] = archivo.toString();
        System.arraycopy(comando, 0, argumentos, 2, comando.length);
        return ejecutarArgumentos(entrada, argumentos);
    }

    private Ejecucion ejecutarArgumentos(String entrada, String... argumentos) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ByteArrayOutputStream errores = new ByteArrayOutputStream();
        try (PrintStream flujoSalida = new PrintStream(salida, true, StandardCharsets.UTF_8);
             PrintStream flujoErrores = new PrintStream(errores, true, StandardCharsets.UTF_8)) {
            Consola consola = new Consola(new StringReader(entrada), flujoSalida, flujoErrores);
            int codigo = consola.ejecutar(argumentos);
            return new Ejecucion(codigo, salida.toString(StandardCharsets.UTF_8), errores.toString(StandardCharsets.UTF_8));
        }
    }

    private record Ejecucion(int codigo, String salida, String errores) {
    }
}
