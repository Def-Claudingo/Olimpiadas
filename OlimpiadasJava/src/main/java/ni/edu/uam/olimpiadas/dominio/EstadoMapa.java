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
public class EstadoMapa {
    private MapaDirigido mapa;
    private List<ResultadoRuta> historial;
}
