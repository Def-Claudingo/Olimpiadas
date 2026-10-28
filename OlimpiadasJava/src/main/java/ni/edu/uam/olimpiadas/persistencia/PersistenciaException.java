package ni.edu.uam.olimpiadas.persistencia;

public class PersistenciaException extends RuntimeException {
    public PersistenciaException(String mensaje) {
        super(mensaje);
    }

    public PersistenciaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
