package ni.edu.uam.olimpiadas.servicios;

import ni.edu.uam.olimpiadas.dominio.EstadoMapa;
import ni.edu.uam.olimpiadas.dominio.EstadoRuta;
import ni.edu.uam.olimpiadas.dominio.MapaDirigido;
import ni.edu.uam.olimpiadas.dominio.ResultadoRuta;
import ni.edu.uam.olimpiadas.persistencia.ArchivoMapaRepository;
import ni.edu.uam.olimpiadas.persistencia.PersistenciaException;
import ni.edu.uam.olimpiadas.persistencia.RepositorioMapa;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServicioMapaTest {
    @TempDir
    Path temporal;

    @Test
    void iniciaVacioCuandoNoExisteElArchivo() {
        ServicioMapa servicio = servicio(temporal.resolve("nuevo.mapa"));

        assertTrue(servicio.consultarMapa().getSedes().isEmpty());
        assertTrue(servicio.historial().isEmpty());
    }

    @Test
    void recuperaEntradasYResultadoDespuesDeReiniciar() {
        Path archivo = temporal.resolve("estado.mapa");
        ServicioMapa primeraSesion = servicio(archivo);
        completarMapa(primeraSesion);
        primeraSesion.calcularRuta("A", "B");

        ServicioMapa segundaSesion = servicio(archivo);

        assertEquals(3, segundaSesion.consultarMapa().getSedes().size());
        assertEquals(3, segundaSesion.consultarMapa().getConexiones().size());
        assertEquals(1, segundaSesion.historial().size());
        assertEquals(Long.valueOf(3), segundaSesion.historial().get(0).getCostoTotal());
        assertEquals(List.of("A", "C", "B"), segundaSesion.historial().get(0).getRuta());
    }

    @Test
    void resultadoSinRutaTambienSePersisteYRecarga() {
        Path archivo = temporal.resolve("estado.mapa");
        ServicioMapa servicio = servicio(archivo);
        servicio.agregarSede("A");
        servicio.agregarSede("Aislada");
        servicio.calcularRuta("A", "Aislada");

        ResultadoRuta recuperada = servicio(archivo).historial().get(0);

        assertEquals(EstadoRuta.SIN_RUTA, recuperada.getEstado());
        assertNull(recuperada.getCostoTotal());
        assertTrue(recuperada.getRuta().isEmpty());
    }

    @Test
    void exportarEImportarPreservaMapaPeroReiniciaHistorial() {
        ServicioMapa original = servicio(temporal.resolve("original.mapa"));
        completarMapa(original);
        original.calcularRuta("A", "B");
        Path exportacion = temporal.resolve("exportado.mapa");
        original.exportar(new ArchivoMapaRepository(exportacion));
        ServicioMapa destino = servicio(temporal.resolve("destino.mapa"));
        destino.agregarSede("Anterior");

        destino.importar(new ArchivoMapaRepository(exportacion));

        assertEquals(original.consultarMapa().getSedes(), destino.consultarMapa().getSedes());
        assertFalse(destino.consultarMapa().getSedes().contains("Anterior"));
        assertTrue(destino.historial().isEmpty());
        assertEquals(Long.valueOf(3), destino.calcularRuta("A", "B").getCostoTotal());
        assertEquals(1, new ArchivoMapaRepository(exportacion).cargar().getHistorial().size());
    }

    @Test
    void importacionInvalidaConservaMemoriaYArchivoOriginal() throws IOException {
        Path archivo = temporal.resolve("estado.mapa");
        ServicioMapa servicio = servicio(archivo);
        completarMapa(servicio);
        servicio.calcularRuta("A", "B");
        String previo = Files.readString(archivo, StandardCharsets.UTF_8);
        Path corrupto = temporal.resolve("corrupto.mapa");
        Files.writeString(corrupto, "MAPA\t1\nSEDE\tX\nCONEXION\tX\tY\t-1\n");

        assertThrows(PersistenciaException.class, () -> servicio.importar(new ArchivoMapaRepository(corrupto)));

        assertEquals(previo, Files.readString(archivo, StandardCharsets.UTF_8));
        assertEquals(3, servicio.consultarMapa().getSedes().size());
        assertEquals(1, servicio.historial().size());
    }

    @Test
    void importarArchivoAusenteNoBorraElMapa() {
        ServicioMapa servicio = servicio(temporal.resolve("estado.mapa"));
        completarMapa(servicio);

        assertThrows(PersistenciaException.class,
                () -> servicio.importar(new ArchivoMapaRepository(temporal.resolve("ausente.mapa"))));

        assertEquals(3, servicio.consultarMapa().getSedes().size());
    }

    @Test
    void modificarMapaBorraResultadosDeLaVersionAnterior() {
        Path archivo = temporal.resolve("estado.mapa");
        ServicioMapa servicio = servicio(archivo);
        completarMapa(servicio);
        servicio.calcularRuta("A", "B");

        servicio.agregarSede("D");

        assertTrue(servicio.historial().isEmpty());
        assertTrue(servicio(archivo).historial().isEmpty());
    }

    @Test
    void operacionInvalidaConservaArchivoYHistorial() throws IOException {
        Path archivo = temporal.resolve("estado.mapa");
        ServicioMapa servicio = servicio(archivo);
        completarMapa(servicio);
        servicio.calcularRuta("A", "B");
        String previo = Files.readString(archivo);

        assertThrows(IllegalArgumentException.class, () -> servicio.agregarConexion("B", "A", -1));
        assertThrows(IllegalArgumentException.class, () -> servicio.calcularRuta("A", "X"));

        assertEquals(previo, Files.readString(archivo));
        assertEquals(1, servicio.historial().size());
    }

    @Test
    void falloDeGuardadoNoConfirmaCambiosEnMemoria() {
        RepositorioControlado repositorio = new RepositorioControlado();
        ServicioMapa servicio = new ServicioMapa(repositorio, new DijkstraService());
        completarMapa(servicio);
        repositorio.fallar = true;

        assertThrows(PersistenciaException.class, () -> servicio.agregarSede("D"));
        assertThrows(PersistenciaException.class, () -> servicio.agregarConexion("B", "A", 9));
        assertThrows(PersistenciaException.class, () -> servicio.calcularRuta("A", "B"));

        assertFalse(servicio.consultarMapa().getSedes().contains("D"));
        assertTrue(servicio.consultarMapa().vecinosDe("B").isEmpty());
        assertTrue(servicio.historial().isEmpty());
        assertEquals(3, repositorio.estado.getMapa().getSedes().size());
    }

    @Test
    void falloDeGuardadoDeImportacionNoReemplazaElMapa() {
        RepositorioControlado repositorio = new RepositorioControlado();
        ServicioMapa servicio = new ServicioMapa(repositorio, new DijkstraService());
        completarMapa(servicio);
        RepositorioControlado importado = new RepositorioControlado();
        MapaDirigido otro = new MapaDirigido();
        otro.agregarSede("X");
        importado.guardar(new EstadoMapa(otro, List.of()));
        repositorio.fallar = true;

        assertThrows(PersistenciaException.class, () -> servicio.importar(importado));

        assertTrue(servicio.consultarMapa().getSedes().contains("A"));
        assertFalse(servicio.consultarMapa().getSedes().contains("X"));
    }

    @Test
    void objetosDevueltosNoPermitenCambiarElEstadoInterno() {
        ServicioMapa servicio = servicio(temporal.resolve("estado.mapa"));
        completarMapa(servicio);
        ResultadoRuta resultado = servicio.calcularRuta("A", "B");

        resultado.setCostoTotal(999L);
        resultado.setRuta(List.of("X"));
        servicio.consultarMapa().agregarSede("X");
        ResultadoRuta lectura = servicio.historial().get(0);
        lectura.setCostoTotal(888L);
        lectura.setRuta(List.of("Y"));

        assertFalse(servicio.consultarMapa().getSedes().contains("X"));
        assertEquals(Long.valueOf(3), servicio.historial().get(0).getCostoTotal());
        assertEquals(List.of("A", "C", "B"), servicio.historial().get(0).getRuta());
    }

    @Test
    void inicioConArchivoCorruptoFallaSinReiniciarDatos() throws IOException {
        Path archivo = temporal.resolve("corrupto.mapa");
        Files.writeString(archivo, "contenido corrupto");

        assertThrows(PersistenciaException.class, () -> servicio(archivo));

        assertEquals("contenido corrupto", Files.readString(archivo));
    }

    @Test
    void historialDeCostoIncorrectoSeRechazaAlIniciar() throws IOException {
        Path archivo = temporal.resolve("inconsistente.mapa");
        Files.writeString(archivo,
                "MAPA\t1\nSEDE\tA\nSEDE\tB\nCONEXION\tA\tB\t4\n"
                        + "CONSULTA\tA\tB\tENCONTRADA\t9\tA\tB\n");

        assertThrows(PersistenciaException.class, () -> servicio(archivo));
    }

    @Test
    void historialConRutaNoMinimaSeRechazaAlIniciar() throws IOException {
        Path archivo = temporal.resolve("no-minima.mapa");
        Files.writeString(archivo,
                "MAPA\t1\nSEDE\tA\nSEDE\tB\nSEDE\tC\n"
                        + "CONEXION\tA\tB\t4\nCONEXION\tA\tC\t1\nCONEXION\tC\tB\t2\n"
                        + "CONSULTA\tA\tB\tENCONTRADA\t4\tA\tB\n");

        assertThrows(PersistenciaException.class, () -> servicio(archivo));
    }

    private ServicioMapa servicio(Path archivo) {
        return new ServicioMapa(new ArchivoMapaRepository(archivo), new DijkstraService());
    }

    private void completarMapa(ServicioMapa servicio) {
        servicio.agregarSede("A");
        servicio.agregarSede("B");
        servicio.agregarSede("C");
        servicio.agregarConexion("A", "B", 4);
        servicio.agregarConexion("A", "C", 1);
        servicio.agregarConexion("C", "B", 2);
    }

    private static final class RepositorioControlado implements RepositorioMapa {
        private EstadoMapa estado;
        private boolean fallar;

        @Override
        public boolean existe() {
            return estado != null;
        }

        @Override
        public EstadoMapa cargar() {
            if (estado == null) {
                throw new PersistenciaException("No existe el archivo de prueba.");
            }
            return estado;
        }

        @Override
        public void guardar(EstadoMapa nuevo) {
            if (fallar) {
                throw new PersistenciaException("No se pudo guardar el estado de prueba.");
            }
            estado = nuevo;
        }
    }
}
