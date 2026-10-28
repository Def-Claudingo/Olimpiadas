package ni.edu.uam.olimpiadas.persistencia;

import ni.edu.uam.olimpiadas.dominio.EstadoMapa;
import ni.edu.uam.olimpiadas.dominio.EstadoRuta;
import ni.edu.uam.olimpiadas.dominio.MapaDirigido;
import ni.edu.uam.olimpiadas.dominio.ResultadoRuta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArchivoMapaRepositoryTest {
    @TempDir
    Path temporal;

    @Test
    void guardaYRecargaMapaYResultadosEnUnaInstanciaNueva() {
        Path archivo = temporal.resolve("estado.mapa");
        MapaDirigido mapa = new MapaDirigido();
        mapa.agregarSede("León");
        mapa.agregarSede("Sede Central");
        mapa.agregarSede("Aislada");
        mapa.agregarConexion("León", "Sede Central", 7);
        ResultadoRuta encontrada = new ResultadoRuta("León", "Sede Central", EstadoRuta.ENCONTRADA,
                7L, List.of("León", "Sede Central"));
        ResultadoRuta sinRuta = new ResultadoRuta("León", "Aislada", EstadoRuta.SIN_RUTA,
                null, List.of());

        new ArchivoMapaRepository(archivo).guardar(new EstadoMapa(mapa, List.of(encontrada, sinRuta)));
        EstadoMapa recuperado = new ArchivoMapaRepository(archivo).cargar();

        assertEquals(mapa.getSedes(), recuperado.getMapa().getSedes());
        assertEquals(Integer.valueOf(7), recuperado.getMapa().vecinosDe("León").get("Sede Central"));
        assertEquals(2, recuperado.getHistorial().size());
        assertEquals(Long.valueOf(7), recuperado.getHistorial().get(0).getCostoTotal());
        assertEquals(List.of("León", "Sede Central"), recuperado.getHistorial().get(0).getRuta());
        assertEquals(EstadoRuta.SIN_RUTA, recuperado.getHistorial().get(1).getEstado());
        assertNull(recuperado.getHistorial().get(1).getCostoTotal());
    }

    @Test
    void archivoAusenteSeDistingueDeUnMapaVacio() {
        ArchivoMapaRepository repositorio = new ArchivoMapaRepository(temporal.resolve("no-existe.mapa"));

        assertFalse(repositorio.existe());
        assertThrows(PersistenciaException.class, repositorio::cargar);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "MAPA\t2\n",
            "MAPA\t1\nDESCONOCIDO\tA\n",
            "MAPA\t1\nSEDE\tA\nSEDE\tA\n",
            "MAPA\t1\nSEDE\tA\nCONEXION\tA\tX\t1\n",
            "MAPA\t1\nSEDE\tA\nSEDE\tB\nCONEXION\tA\tB\t-1\n",
            "MAPA\t1\nSEDE\tA\nSEDE\tB\nCONEXION\tA\tB\t2147483648\n",
            "MAPA\t1\nSEDE\tA\nSEDE\tB\nCONEXION\tA\tB\tuno\n",
            "MAPA\t1\nSEDE\tA\nCONEXION\tA\n",
            "MAPA\t1\nSEDE\tA\nCONSULTA\tA\tA\tENCONTRADA\t-1\tA\n",
            "MAPA\t1\nSEDE\tA\nCONSULTA\tA\tA\tENCONTRADA\t9223372036854775808\tA\n",
            "MAPA\t1\nSEDE\tA\nCONSULTA\tA\tA\tOTRO\t0\tA\n"
    })
    void rechazaArchivosInvalidosConErrorControlado(String contenido) throws IOException {
        Path archivo = temporal.resolve("invalido.mapa");
        Files.writeString(archivo, contenido, StandardCharsets.UTF_8);

        PersistenciaException error = assertThrows(PersistenciaException.class,
                () -> new ArchivoMapaRepository(archivo).cargar());

        assertNotNull(error.getMessage());
        assertFalse(error.getMessage().isBlank());
        assertEquals(contenido, Files.readString(archivo, StandardCharsets.UTF_8));
    }

    @Test
    void rechazaUtf8Malformado() throws IOException {
        Path archivo = temporal.resolve("encoding.mapa");
        Files.write(archivo, new byte[]{(byte) 0xC3, (byte) 0x28});

        assertThrows(PersistenciaException.class, () -> new ArchivoMapaRepository(archivo).cargar());
    }

    @Test
    void falloDeEscrituraEsControladoYNoReemplazaUnDirectorio() throws IOException {
        Path destino = Files.createDirectory(temporal.resolve("directorio.mapa"));
        Path evidencia = destino.resolve("conservar.txt");
        Files.writeString(evidencia, "conservar");
        EstadoMapa estado = new EstadoMapa(new MapaDirigido(), List.of());

        assertThrows(PersistenciaException.class, () -> new ArchivoMapaRepository(destino).guardar(estado));

        assertTrue(Files.isDirectory(destino));
        assertEquals("conservar", Files.readString(evidencia));
    }

    @Test
    void reemplazaElEstadoPrevioSinDuplicarRegistros() {
        Path archivo = temporal.resolve("estado.mapa");
        ArchivoMapaRepository repositorio = new ArchivoMapaRepository(archivo);
        MapaDirigido primero = new MapaDirigido();
        primero.agregarSede("A");
        repositorio.guardar(new EstadoMapa(primero, List.of()));
        MapaDirigido segundo = new MapaDirigido();
        segundo.agregarSede("B");

        repositorio.guardar(new EstadoMapa(segundo, List.of()));

        assertEquals(segundo.getSedes(), repositorio.cargar().getMapa().getSedes());
    }
}
