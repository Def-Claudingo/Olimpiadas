package ni.edu.uam.olimpiadas.servicios;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import ni.edu.uam.olimpiadas.dominio.EstadoRuta;
import ni.edu.uam.olimpiadas.dominio.MapaDirigido;
import ni.edu.uam.olimpiadas.dominio.ResultadoRuta;

public final class DijkstraService {
    public ResultadoRuta calcularRuta(MapaDirigido mapa, String origen, String destino) {
        if (mapa == null) {
            throw new IllegalArgumentException("El mapa es obligatorio.");
        }
        mapa.validarSedeExistente(origen);
        mapa.validarSedeExistente(destino);
        if (origen.equals(destino)) {
            return new ResultadoRuta(origen, destino, EstadoRuta.ENCONTRADA, 0L, List.of(origen));
        }

        Map<String, Long> distancias = new HashMap<>();
        Map<String, String> anteriores = new HashMap<>();
        PriorityQueue<Entrada> pendientes = new PriorityQueue<>(Comparator.comparingLong(Entrada::distancia)
                .thenComparing(Entrada::sede));
        distancias.put(origen, 0L);
        pendientes.add(new Entrada(origen, 0L));

        while (!pendientes.isEmpty()) {
            Entrada actual = pendientes.remove();
            if (actual.distancia() != distancias.get(actual.sede())) {
                continue;
            }
            if (actual.sede().equals(destino)) {
                return new ResultadoRuta(origen, destino, EstadoRuta.ENCONTRADA, actual.distancia(),
                        reconstruirRuta(anteriores, origen, destino));
            }
            for (Map.Entry<String, Integer> conexion : mapa.vecinosDe(actual.sede()).entrySet()) {
                if (actual.distancia() > Long.MAX_VALUE - conexion.getValue()) {
                    throw new IllegalArgumentException("El costo del trayecto supera el límite de long.");
                }
                long candidata = actual.distancia() + conexion.getValue();
                Long anterior = distancias.get(conexion.getKey());
                if (anterior == null || candidata < anterior) {
                    distancias.put(conexion.getKey(), candidata);
                    anteriores.put(conexion.getKey(), actual.sede());
                    pendientes.add(new Entrada(conexion.getKey(), candidata));
                }
            }
        }
        return new ResultadoRuta(origen, destino, EstadoRuta.SIN_RUTA, null, List.of());
    }

    private List<String> reconstruirRuta(Map<String, String> anteriores, String origen, String destino) {
        List<String> ruta = new ArrayList<>();
        String actual = destino;
        while (!actual.equals(origen)) {
            ruta.add(actual);
            actual = anteriores.get(actual);
        }
        ruta.add(origen);
        Collections.reverse(ruta);
        return List.copyOf(ruta);
    }

    private record Entrada(String sede, long distancia) {
    }
}
