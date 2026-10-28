package ni.edu.uam.olimpiadas.servicios;

import ni.edu.uam.olimpiadas.dominio.EstadoMapa;
import ni.edu.uam.olimpiadas.dominio.EstadoRuta;
import ni.edu.uam.olimpiadas.dominio.MapaDirigido;
import ni.edu.uam.olimpiadas.dominio.ResultadoRuta;
import ni.edu.uam.olimpiadas.persistencia.PersistenciaException;
import ni.edu.uam.olimpiadas.persistencia.RepositorioMapa;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ServicioMapa {
    private final RepositorioMapa repositorio;
    private final DijkstraService dijkstra;
    private EstadoMapa estado;

    public ServicioMapa(RepositorioMapa repositorio, DijkstraService dijkstra) {
        if (repositorio == null || dijkstra == null) {
            throw new IllegalArgumentException("El repositorio y el servicio de rutas son obligatorios.");
        }
        this.repositorio = repositorio;
        this.dijkstra = dijkstra;
        if (repositorio.existe()) {
            EstadoMapa recuperado = repositorio.cargar();
            validarEstadoRecuperado(recuperado);
            estado = copiarEstado(recuperado);
        } else {
            estado = new EstadoMapa(new MapaDirigido(), List.of());
        }
    }

    public void agregarSede(String nombre) {
        MapaDirigido candidato = estado.getMapa().copia();
        candidato.agregarSede(nombre);
        confirmar(new EstadoMapa(candidato, List.of()));
    }

    public void agregarConexion(String origen, String destino, int peso) {
        MapaDirigido candidato = estado.getMapa().copia();
        candidato.agregarConexion(origen, destino, peso);
        confirmar(new EstadoMapa(candidato, List.of()));
    }

    public ResultadoRuta calcularRuta(String origen, String destino) {
        ResultadoRuta resultado = dijkstra.calcularRuta(estado.getMapa(), origen, destino);
        List<ResultadoRuta> historial = new ArrayList<>(historial());
        historial.add(copiarResultado(resultado));
        confirmar(new EstadoMapa(estado.getMapa().copia(), List.copyOf(historial)));
        return copiarResultado(resultado);
    }

    public MapaDirigido consultarMapa() {
        return estado.getMapa().copia();
    }

    public List<ResultadoRuta> historial() {
        return estado.getHistorial().stream().map(ServicioMapa::copiarResultado).toList();
    }

    public void importar(RepositorioMapa origen) {
        exigirRepositorio(origen);
        EstadoMapa importado = origen.cargar();
        validarEstadoRecuperado(importado);
        confirmar(new EstadoMapa(importado.getMapa().copia(), List.of()));
    }

    public void exportar(RepositorioMapa destino) {
        exigirRepositorio(destino);
        destino.guardar(copiarEstado(estado));
    }

    private void confirmar(EstadoMapa candidato) {
        repositorio.guardar(copiarEstado(candidato));
        estado = candidato;
    }

    private void validarEstadoRecuperado(EstadoMapa recuperado) {
        try {
            if (recuperado == null || recuperado.getMapa() == null || recuperado.getHistorial() == null) {
                throw new IllegalArgumentException("El estado recuperado no incluye mapa e historial.");
            }
            for (ResultadoRuta resultado : recuperado.getHistorial()) {
                if (resultado == null || resultado.getEstado() == null || resultado.getRuta() == null) {
                    throw new IllegalArgumentException("El historial contiene una consulta incompleta.");
                }
                ResultadoRuta esperado = dijkstra.calcularRuta(recuperado.getMapa(), resultado.getOrigen(), resultado.getDestino());
                if (resultado.getEstado() != esperado.getEstado()
                        || !Objects.equals(resultado.getCostoTotal(), esperado.getCostoTotal())) {
                    throw new IllegalArgumentException("El historial contiene un resultado que no es mínimo o un estado SIN_RUTA incorrecto.");
                }
                validarTrayecto(recuperado.getMapa(), resultado);
            }
        } catch (IllegalArgumentException e) {
            throw new PersistenciaException("Estado persistido inválido: " + e.getMessage(), e);
        }
    }

    private static void validarTrayecto(MapaDirigido mapa, ResultadoRuta resultado) {
        List<String> ruta = resultado.getRuta();
        if (resultado.getEstado() == EstadoRuta.SIN_RUTA) {
            if (!ruta.isEmpty()) {
                throw new IllegalArgumentException("SIN_RUTA requiere una ruta vacía.");
            }
            return;
        }
        if (ruta.isEmpty() || !resultado.getOrigen().equals(ruta.get(0))
                || !resultado.getDestino().equals(ruta.get(ruta.size() - 1))) {
            throw new IllegalArgumentException("Los extremos del trayecto son incorrectos.");
        }
        long costo = 0;
        for (int i = 1; i < ruta.size(); i++) {
            Integer peso = mapa.vecinosDe(ruta.get(i - 1)).get(ruta.get(i));
            if (peso == null) {
                throw new IllegalArgumentException("El trayecto incluye una conexión inexistente.");
            }
            costo += peso;
        }
        if (costo != resultado.getCostoTotal()) {
            throw new IllegalArgumentException("El costo del trayecto no coincide con el resultado.");
        }
    }

    private static EstadoMapa copiarEstado(EstadoMapa original) {
        return new EstadoMapa(original.getMapa().copia(),
                original.getHistorial().stream().map(ServicioMapa::copiarResultado).toList());
    }

    private static ResultadoRuta copiarResultado(ResultadoRuta original) {
        return new ResultadoRuta(original.getOrigen(), original.getDestino(), original.getEstado(),
                original.getCostoTotal(), List.copyOf(original.getRuta()));
    }

    private static void exigirRepositorio(RepositorioMapa repositorio) {
        if (repositorio == null) {
            throw new IllegalArgumentException("El repositorio es obligatorio.");
        }
    }
}
