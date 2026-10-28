package ni.edu.uam.olimpiadas.dominio;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Conexion {
    private String origen;
    private String destino;
    private int peso;
}
