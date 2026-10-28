package ni.edu.uam.olimpiadas.servicios;

import ni.edu.uam.olimpiadas.dominio.Conexion;
import ni.edu.uam.olimpiadas.dominio.EstadoRuta;
import ni.edu.uam.olimpiadas.dominio.MapaDirigido;
import ni.edu.uam.olimpiadas.dominio.ResultadoRuta;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DijkstraServiceTest {
    private final DijkstraService dijkstra = new DijkstraService();

    @Test
    void obtieneCostoTresYRutaACBDelCriterioObligatorio() {
        MapaDirigido mapa = mapa("A", "B", "C");
        mapa.agregarConexion("A", "B", 4);
        mapa.agregarConexion("A", "C", 1);
        mapa.agregarConexion("C", "B", 2);

        ResultadoRuta resultado = dijkstra.calcularRuta(mapa, "A", "B");

        assertEquals(EstadoRuta.ENCONTRADA, resultado.getEstado());
        assertEquals(Long.valueOf(3), resultado.getCostoTotal());
        assertEquals(List.of("A", "C", "B"), resultado.getRuta());
    }

    @Test
    void sedeAisladaDevuelveSinRutaSinError() {
        MapaDirigido mapa = mapa("A", "B", "Aislada");
        mapa.agregarConexion("A", "B", 4);

        ResultadoRuta resultado = assertDoesNotThrow(() -> dijkstra.calcularRuta(mapa, "A", "Aislada"));

        assertEquals(EstadoRuta.SIN_RUTA, resultado.getEstado());
        assertNull(resultado.getCostoTotal());
        assertTrue(resultado.getRuta().isEmpty());
    }

    @Test
    void origenIgualADestinoDevuelveCostoCero() {
        ResultadoRuta resultado = dijkstra.calcularRuta(mapa("A"), "A", "A");

        assertEquals(EstadoRuta.ENCONTRADA, resultado.getEstado());
        assertEquals(Long.valueOf(0), resultado.getCostoTotal());
        assertEquals(List.of("A"), resultado.getRuta());
    }

    @Test
    void identidadDeSedeInexistenteSeRechaza() {
        assertThrows(IllegalArgumentException.class,
                () -> dijkstra.calcularRuta(new MapaDirigido(), "X", "X"));
    }

    @Test
    void extremosInexistentesSeRechazan() {
        MapaDirigido mapa = mapa("A");

        assertThrows(IllegalArgumentException.class, () -> dijkstra.calcularRuta(mapa, "X", "A"));
        assertThrows(IllegalArgumentException.class, () -> dijkstra.calcularRuta(mapa, "A", "X"));
    }

    @Test
    void conexionesSonDirigidas() {
        MapaDirigido mapa = mapa("A", "B");
        mapa.agregarConexion("A", "B", 4);

        assertEquals(EstadoRuta.SIN_RUTA, dijkstra.calcularRuta(mapa, "B", "A").getEstado());
    }

    @Test
    void acumulaCostosSuperioresAlMaximoDeInt() {
        MapaDirigido mapa = mapa("A", "B", "C");
        mapa.agregarConexion("A", "B", Integer.MAX_VALUE);
        mapa.agregarConexion("B", "C", Integer.MAX_VALUE);

        ResultadoRuta resultado = dijkstra.calcularRuta(mapa, "A", "C");

        assertEquals(Long.valueOf(4_294_967_294L), resultado.getCostoTotal());
        assertEquals(List.of("A", "B", "C"), resultado.getRuta());
    }

    @Test
    void ciclosDeCostoCeroNoRompenLaReconstruccion() {
        MapaDirigido mapa = mapa("A", "B", "C");
        mapa.agregarConexion("A", "B", 0);
        mapa.agregarConexion("B", "A", 0);
        mapa.agregarConexion("B", "C", 0);
        mapa.agregarConexion("C", "C", 0);

        ResultadoRuta resultado = dijkstra.calcularRuta(mapa, "A", "C");

        assertEquals(Long.valueOf(0), resultado.getCostoTotal());
        assertEquals(List.of("A", "B", "C"), resultado.getRuta());
    }

    @Test
    void descartaEntradasObsoletasDeLaCola() {
        MapaDirigido mapa = mapa("A", "B", "C", "D");
        mapa.agregarConexion("A", "B", 10);
        mapa.agregarConexion("A", "C", 1);
        mapa.agregarConexion("C", "B", 1);
        mapa.agregarConexion("B", "D", 20);

        ResultadoRuta resultado = dijkstra.calcularRuta(mapa, "A", "D");

        assertEquals(Long.valueOf(22), resultado.getCostoTotal());
        assertEquals(List.of("A", "C", "B", "D"), resultado.getRuta());
    }

    @Test
    void resolverEmpatesEsDeterministaYConservaElMapa() {
        MapaDirigido mapa = mapa("A", "B", "C", "D");
        mapa.agregarConexion("A", "B", 1);
        mapa.agregarConexion("A", "C", 1);
        mapa.agregarConexion("B", "D", 1);
        mapa.agregarConexion("C", "D", 1);

        ResultadoRuta primera = dijkstra.calcularRuta(mapa, "A", "D");
        ResultadoRuta segunda = dijkstra.calcularRuta(mapa, "A", "D");

        assertEquals(Long.valueOf(2), primera.getCostoTotal());
        assertEquals(primera.getRuta(), segunda.getRuta());
        assertEquals(4, mapa.getSedes().size());
        assertEquals(4, mapa.getConexiones().size());
    }

    @Test
    void coincideConBellmanFordEnMapasGeneradosConSemillaFija() {
        Random random = new Random(20261028L);
        int sedes = 8;
        for (int muestra = 0; muestra < 25; muestra++) {
            MapaDirigido mapa = new MapaDirigido();
            for (int i = 0; i < sedes; i++) {
                mapa.agregarSede("S" + i);
            }
            for (int i = 0; i < sedes; i++) {
                for (int j = 0; j < sedes; j++) {
                    if (random.nextInt(4) == 0) {
                        mapa.agregarConexion("S" + i, "S" + j, random.nextInt(30));
                    }
                }
            }
            for (int origen = 0; origen < sedes; origen++) {
                long[] esperadas = bellmanFord(mapa, origen, sedes);
                for (int destino = 0; destino < sedes; destino++) {
                    ResultadoRuta resultado = dijkstra.calcularRuta(mapa, "S" + origen, "S" + destino);
                    if (esperadas[destino] == Long.MAX_VALUE) {
                        assertEquals(EstadoRuta.SIN_RUTA, resultado.getEstado());
                        assertNull(resultado.getCostoTotal());
                        assertTrue(resultado.getRuta().isEmpty());
                    } else {
                        assertEquals(EstadoRuta.ENCONTRADA, resultado.getEstado());
                        assertEquals(Long.valueOf(esperadas[destino]), resultado.getCostoTotal());
                        verificarRuta(mapa, resultado, "S" + origen, "S" + destino);
                    }
                }
            }
        }
    }

    private long[] bellmanFord(MapaDirigido mapa, int origen, int cantidad) {
        long[] distancias = new long[cantidad];
        java.util.Arrays.fill(distancias, Long.MAX_VALUE);
        distancias[origen] = 0;
        for (int iteracion = 1; iteracion < cantidad; iteracion++) {
            boolean cambio = false;
            for (Conexion conexion : mapa.getConexiones()) {
                int desde = Integer.parseInt(conexion.getOrigen().substring(1));
                int hasta = Integer.parseInt(conexion.getDestino().substring(1));
                if (distancias[desde] != Long.MAX_VALUE) {
                    long propuesta = distancias[desde] + conexion.getPeso();
                    if (propuesta < distancias[hasta]) {
                        distancias[hasta] = propuesta;
                        cambio = true;
                    }
                }
            }
            if (!cambio) {
                break;
            }
        }
        return distancias;
    }

    private void verificarRuta(MapaDirigido mapa, ResultadoRuta resultado, String origen, String destino) {
        List<String> ruta = resultado.getRuta();
        assertFalse(ruta.isEmpty());
        assertEquals(origen, ruta.get(0));
        assertEquals(destino, ruta.get(ruta.size() - 1));
        assertTrue(ruta.size() <= mapa.getSedes().size());
        long suma = 0;
        for (int i = 1; i < ruta.size(); i++) {
            Integer peso = mapa.vecinosDe(ruta.get(i - 1)).get(ruta.get(i));
            assertNotNull(peso);
            suma += peso;
        }
        assertEquals(Long.valueOf(suma), resultado.getCostoTotal());
    }

    private MapaDirigido mapa(String... sedes) {
        MapaDirigido mapa = new MapaDirigido();
        for (String sede : sedes) {
            mapa.agregarSede(sede);
        }
        return mapa;
    }
}
