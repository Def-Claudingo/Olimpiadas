package ni.edu.uam.olimpiadas.dominio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MapaDirigidoTest {
    @Test
    void pesoNegativoSeRechazaSinAgregarConexion() {
        MapaDirigido mapa = mapaConDosSedes();

        assertThrows(IllegalArgumentException.class, () -> mapa.agregarConexion("A", "B", -1));

        assertTrue(mapa.getConexiones().isEmpty());
        assertTrue(mapa.vecinosDe("A").isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", " A", "A ", "A\tB", "A\nB", "A\rB"})
    void rechazaNombresInvalidosSinAgregarSedes(String nombre) {
        MapaDirigido mapa = new MapaDirigido();

        assertThrows(IllegalArgumentException.class, () -> mapa.agregarSede(nombre));

        assertTrue(mapa.getSedes().isEmpty());
    }

    @Test
    void rechazaNombreDemasiadoLargo() {
        MapaDirigido mapa = new MapaDirigido();

        assertThrows(IllegalArgumentException.class, () -> mapa.agregarSede("A".repeat(101)));
        assertTrue(mapa.getSedes().isEmpty());
    }

    @Test
    void permiteNombreConEspaciosInternosYUnicode() {
        MapaDirigido mapa = new MapaDirigido();

        mapa.agregarSede("Sede León");

        assertTrue(mapa.getSedes().contains("Sede León"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\uD800", "\uDC00", "A\uD800B", "A\uDC00B", "\uD800\uD800", "\uDC00\uD800"})
    void rechazaSustitutosUnicodeAisladosSinCambiarElMapa(String nombre) {
        MapaDirigido mapa = new MapaDirigido();

        assertThrows(IllegalArgumentException.class, () -> mapa.agregarSede(nombre));

        assertTrue(mapa.getSedes().isEmpty());
    }

    @Test
    void permiteUnicodeSuplementarioValido() {
        MapaDirigido mapa = new MapaDirigido();
        String nombre = "Sede \uD83C\uDFEB";

        mapa.agregarSede(nombre);

        assertTrue(mapa.getSedes().contains(nombre));
    }

    @Test
    void rechazaSedeDuplicadaSinCambiarElMapa() {
        MapaDirigido mapa = mapaConDosSedes();

        assertThrows(IllegalArgumentException.class, () -> mapa.agregarSede("A"));
        assertEquals(2, mapa.getSedes().size());
    }

    @Test
    void rechazaConexionDuplicadaConservandoSuCosto() {
        MapaDirigido mapa = mapaConDosSedes();
        mapa.agregarConexion("A", "B", 4);

        assertThrows(IllegalArgumentException.class, () -> mapa.agregarConexion("A", "B", 8));

        assertEquals(Integer.valueOf(4), mapa.vecinosDe("A").get("B"));
        assertEquals(1, mapa.getConexiones().size());
    }

    @Test
    void rechazaExtremosInexistentesSinCrearSedes() {
        MapaDirigido mapa = mapaConDosSedes();

        assertThrows(IllegalArgumentException.class, () -> mapa.agregarConexion("X", "B", 1));
        assertThrows(IllegalArgumentException.class, () -> mapa.agregarConexion("A", "X", 1));

        assertEquals(2, mapa.getSedes().size());
        assertTrue(mapa.getConexiones().isEmpty());
    }

    @Test
    void permitePesoCeroYAutoconexion() {
        MapaDirigido mapa = mapaConDosSedes();

        mapa.agregarConexion("A", "A", 0);

        assertEquals(Integer.valueOf(0), mapa.vecinosDe("A").get("A"));
    }

    @Test
    void noExponeColeccionesMutables() {
        MapaDirigido mapa = mapaConDosSedes();
        mapa.agregarConexion("A", "B", 4);

        assertThrows(UnsupportedOperationException.class, () -> mapa.getSedes().clear());
        assertThrows(UnsupportedOperationException.class, () -> mapa.vecinosDe("A").put("B", 99));
        assertThrows(UnsupportedOperationException.class, () -> mapa.getConexiones().clear());
    }

    @Test
    void modificarConexionDevueltaNoModificaElMapa() {
        MapaDirigido mapa = mapaConDosSedes();
        mapa.agregarConexion("A", "B", 4);
        List<Conexion> conexiones = mapa.getConexiones();

        conexiones.get(0).setPeso(-1);
        conexiones.get(0).setDestino("X");

        assertEquals(Integer.valueOf(4), mapa.vecinosDe("A").get("B"));
        assertFalse(mapa.vecinosDe("A").containsKey("X"));
    }

    @Test
    void copiaEsIndependienteDelOriginal() {
        MapaDirigido original = mapaConDosSedes();
        MapaDirigido copia = original.copia();

        copia.agregarSede("C");
        copia.agregarConexion("A", "B", 7);

        assertFalse(original.getSedes().contains("C"));
        assertTrue(original.getConexiones().isEmpty());
    }

    private MapaDirigido mapaConDosSedes() {
        MapaDirigido mapa = new MapaDirigido();
        mapa.agregarSede("A");
        mapa.agregarSede("B");
        return mapa;
    }
}
