package ni.edu.uam.olimpiadas.persistencia;

import ni.edu.uam.olimpiadas.dominio.EstadoMapa;

public interface RepositorioMapa {
    boolean existe();

    EstadoMapa cargar();

    void guardar(EstadoMapa estado);
}
