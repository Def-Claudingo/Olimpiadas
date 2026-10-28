package ni.edu.uam.olimpiadas.dominio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MapaDirigido {
    private final Map<String, Map<String, Integer>> adyacencias = new LinkedHashMap<>();

    public void agregarSede(String sede) {
        validarNombre(sede);
        if (adyacencias.containsKey(sede)) {
            throw new IllegalArgumentException("La sede ya existe: " + sede + ".");
        }
        adyacencias.put(sede, new LinkedHashMap<>());
    }

    public void agregarConexion(String origen, String destino, int peso) {
        if (peso < 0) {
            throw new IllegalArgumentException("El peso debe ser un entero no negativo.");
        }
        validarSedeExistente(origen);
        validarSedeExistente(destino);
        Map<String, Integer> vecinos = adyacencias.get(origen);
        if (vecinos.containsKey(destino)) {
            throw new IllegalArgumentException("La conexión ya existe: " + origen + " → " + destino + ".");
        }
        vecinos.put(destino, peso);
    }

    public Set<String> getSedes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(adyacencias.keySet()));
    }

    public List<Conexion> getConexiones() {
        List<Conexion> conexiones = new ArrayList<>();
        adyacencias.forEach((origen, vecinos) -> vecinos.forEach((destino, peso) ->
                conexiones.add(new Conexion(origen, destino, peso))));
        return Collections.unmodifiableList(conexiones);
    }

    public Map<String, Integer> vecinosDe(String sede) {
        validarSedeExistente(sede);
        return Collections.unmodifiableMap(new LinkedHashMap<>(adyacencias.get(sede)));
    }

    public MapaDirigido copia() {
        MapaDirigido copia = new MapaDirigido();
        adyacencias.forEach((sede, vecinos) -> copia.adyacencias.put(sede, new LinkedHashMap<>(vecinos)));
        return copia;
    }

    public void validarSedeExistente(String sede) {
        validarNombre(sede);
        if (!adyacencias.containsKey(sede)) {
            throw new IllegalArgumentException("La sede no existe: " + sede + ".");
        }
    }

    private static void validarNombre(String sede) {
        if (sede == null || sede.isBlank()) {
            throw new IllegalArgumentException("El nombre de la sede no puede estar vacío.");
        }
        if (sede.length() > 100) {
            throw new IllegalArgumentException("El nombre de la sede no puede superar 100 caracteres.");
        }
        if (!sede.equals(sede.strip())) {
            throw new IllegalArgumentException("El nombre de la sede no puede tener espacios al inicio o al final.");
        }
        if (sede.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("El nombre de la sede no puede contener caracteres de control.");
        }
        if (sede.codePoints().anyMatch(caracter -> caracter >= Character.MIN_SURROGATE
                && caracter <= Character.MAX_SURROGATE)) {
            throw new IllegalArgumentException("El nombre de la sede debe contener Unicode válido.");
        }
    }
}
