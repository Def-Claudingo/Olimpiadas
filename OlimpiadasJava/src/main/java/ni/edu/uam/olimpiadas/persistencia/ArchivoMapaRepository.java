package ni.edu.uam.olimpiadas.persistencia;

import ni.edu.uam.olimpiadas.dominio.Conexion;
import ni.edu.uam.olimpiadas.dominio.EstadoMapa;
import ni.edu.uam.olimpiadas.dominio.EstadoRuta;
import ni.edu.uam.olimpiadas.dominio.MapaDirigido;
import ni.edu.uam.olimpiadas.dominio.ResultadoRuta;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ArchivoMapaRepository implements RepositorioMapa {
    private static final String CABECERA = "MAPA\t1";
    private final Path archivo;

    public ArchivoMapaRepository(Path archivo) {
        if (archivo == null) {
            throw new IllegalArgumentException("La ruta del archivo es obligatoria.");
        }
        this.archivo = archivo.toAbsolutePath().normalize();
        if (this.archivo.getFileName() == null) {
            throw new IllegalArgumentException("La ruta debe indicar un archivo, no la raíz del sistema.");
        }
    }

    @Override
    public boolean existe() {
        return !Files.notExists(archivo);
    }

    @Override
    public EstadoMapa cargar() {
        try {
            List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
            if (lineas.isEmpty() || !CABECERA.equals(lineas.get(0))) {
                throw new IllegalArgumentException("Cabecera inválida; se esperaba MAPA y versión 1 separados por tabulación.");
            }
            List<Fila> filas = new ArrayList<>();
            for (int i = 1; i < lineas.size(); i++) {
                String[] campos = lineas.get(i).split("\t", -1);
                if (!List.of("SEDE", "CONEXION", "CONSULTA").contains(campos[0])) {
                    throw new IllegalArgumentException("Registro desconocido en línea " + (i + 1) + ".");
                }
                filas.add(new Fila(i + 1, campos));
            }
            MapaDirigido mapa = new MapaDirigido();
            List<ResultadoRuta> historial = new ArrayList<>();
            for (Fila fila : filas) {
                if (fila.campos()[0].equals("SEDE")) {
                    procesarSede(fila, mapa);
                }
            }
            for (Fila fila : filas) {
                if (fila.campos()[0].equals("CONEXION")) {
                    procesarConexion(fila, mapa);
                }
            }
            for (Fila fila : filas) {
                if (fila.campos()[0].equals("CONSULTA")) {
                    historial.add(procesarConsulta(fila, mapa));
                }
            }
            return new EstadoMapa(mapa, List.copyOf(historial));
        } catch (IOException | IllegalArgumentException e) {
            throw new PersistenciaException("No se pudo cargar el mapa de " + archivo + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void guardar(EstadoMapa estado) {
        byte[] contenido;
        try {
            contenido = serializar(estado).getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new PersistenciaException("No se puede guardar un estado inválido: " + e.getMessage(), e);
        }
        Path temporal = null;
        IOException fallo = null;
        try {
            Path carpeta = archivo.getParent();
            Files.createDirectories(carpeta);
            temporal = Files.createTempFile(carpeta, ".mapa-", ".tmp");
            try (FileChannel canal = FileChannel.open(temporal, StandardOpenOption.WRITE)) {
                ByteBuffer datos = ByteBuffer.wrap(contenido);
                while (datos.hasRemaining()) {
                    canal.write(datos);
                }
                canal.force(true);
            }
            Files.move(temporal, archivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            fallo = e;
            throw new PersistenciaException("El sistema de archivos no permite un guardado atómico. El archivo original se conserva.", e);
        } catch (IOException e) {
            fallo = e;
            throw new PersistenciaException("No se pudo guardar el mapa en " + archivo + ": " + e.getMessage(), e);
        } finally {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException e) {
                    if (fallo != null) {
                        fallo.addSuppressed(e);
                    } else {
                        throw new PersistenciaException("No se pudo limpiar el archivo temporal " + temporal + ".", e);
                    }
                }
            }
        }
    }

    private static void procesarSede(Fila fila, MapaDirigido mapa) {
        try {
            exigirCantidad(fila.campos(), 2);
            mapa.agregarSede(fila.campos()[1]);
        } catch (IllegalArgumentException e) {
            throw errorLinea(fila, e);
        }
    }

    private static void procesarConexion(Fila fila, MapaDirigido mapa) {
        try {
            exigirCantidad(fila.campos(), 4);
            mapa.agregarConexion(fila.campos()[1], fila.campos()[2], Integer.parseInt(fila.campos()[3]));
        } catch (IllegalArgumentException e) {
            throw errorLinea(fila, e);
        }
    }

    private static ResultadoRuta procesarConsulta(Fila fila, MapaDirigido mapa) {
        try {
            String[] campos = fila.campos();
            if (campos.length < 5) {
                throw new IllegalArgumentException("Consulta incompleta.");
            }
            EstadoRuta estado = EstadoRuta.valueOf(campos[3]);
            Long costo;
            List<String> ruta;
            if (estado == EstadoRuta.SIN_RUTA) {
                exigirCantidad(campos, 5);
                if (!campos[4].equals("-")) {
                    throw new IllegalArgumentException("SIN_RUTA debe tener costo '-'.");
                }
                costo = null;
                ruta = List.of();
            } else {
                if (campos.length < 6) {
                    throw new IllegalArgumentException("Una ruta encontrada debe incluir sus sedes.");
                }
                costo = Long.parseLong(campos[4]);
                ruta = List.copyOf(Arrays.asList(campos).subList(5, campos.length));
            }
            ResultadoRuta resultado = new ResultadoRuta(campos[1], campos[2], estado, costo, ruta);
            validarResultado(mapa, resultado);
            return resultado;
        } catch (IllegalArgumentException e) {
            throw errorLinea(fila, e);
        }
    }

    private static String serializar(EstadoMapa estado) {
        if (estado == null || estado.getMapa() == null || estado.getHistorial() == null) {
            throw new IllegalArgumentException("El mapa y el historial son obligatorios.");
        }
        StringBuilder contenido = new StringBuilder(CABECERA).append('\n');
        MapaDirigido mapa = estado.getMapa();
        for (String sede : mapa.getSedes()) {
            contenido.append("SEDE\t").append(sede).append('\n');
        }
        for (Conexion conexion : mapa.getConexiones()) {
            contenido.append("CONEXION\t").append(conexion.getOrigen()).append('\t')
                    .append(conexion.getDestino()).append('\t').append(conexion.getPeso()).append('\n');
        }
        for (ResultadoRuta resultado : estado.getHistorial()) {
            validarResultado(mapa, resultado);
            contenido.append("CONSULTA\t").append(resultado.getOrigen()).append('\t')
                    .append(resultado.getDestino()).append('\t').append(resultado.getEstado()).append('\t');
            if (resultado.getEstado() == EstadoRuta.SIN_RUTA) {
                contenido.append('-');
            } else {
                contenido.append(resultado.getCostoTotal());
                for (String sede : resultado.getRuta()) {
                    contenido.append('\t').append(sede);
                }
            }
            contenido.append('\n');
        }
        return contenido.toString();
    }

    private static void validarResultado(MapaDirigido mapa, ResultadoRuta resultado) {
        if (resultado == null || resultado.getEstado() == null || resultado.getRuta() == null) {
            throw new IllegalArgumentException("La consulta debe incluir estado y ruta.");
        }
        if (!mapa.getSedes().contains(resultado.getOrigen()) || !mapa.getSedes().contains(resultado.getDestino())) {
            throw new IllegalArgumentException("La consulta contiene una sede inexistente.");
        }
        if (resultado.getEstado() == EstadoRuta.SIN_RUTA) {
            if (resultado.getCostoTotal() != null || !resultado.getRuta().isEmpty()) {
                throw new IllegalArgumentException("SIN_RUTA requiere costo nulo y ruta vacía.");
            }
            return;
        }
        List<String> ruta = resultado.getRuta();
        if (resultado.getCostoTotal() == null || resultado.getCostoTotal() < 0 || ruta.isEmpty()
                || !resultado.getOrigen().equals(ruta.get(0))
                || !resultado.getDestino().equals(ruta.get(ruta.size() - 1))) {
            throw new IllegalArgumentException("El costo o los extremos de la ruta son inválidos.");
        }
        long costo = 0;
        for (int i = 1; i < ruta.size(); i++) {
            Integer peso = mapa.vecinosDe(ruta.get(i - 1)).get(ruta.get(i));
            if (peso == null) {
                throw new IllegalArgumentException("La ruta contiene una conexión inexistente.");
            }
            costo += peso;
        }
        if (costo != resultado.getCostoTotal()) {
            throw new IllegalArgumentException("El costo declarado no coincide con la ruta.");
        }
    }

    private static void exigirCantidad(String[] campos, int cantidad) {
        if (campos.length != cantidad) {
            throw new IllegalArgumentException("Cantidad de campos incorrecta: se esperaban " + cantidad + ".");
        }
    }

    private static IllegalArgumentException errorLinea(Fila fila, IllegalArgumentException causa) {
        return new IllegalArgumentException("Línea " + fila.numero() + ": " + causa.getMessage(), causa);
    }

    private record Fila(int numero, String[] campos) {
    }
}
