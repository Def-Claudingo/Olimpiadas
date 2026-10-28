package ni.edu.uam.olimpiadas.dominio;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoRuta {
    private String origen;
    private String destino;
    private EstadoRuta estado;
    private Long costoTotal;
    private List<String> ruta;
}
