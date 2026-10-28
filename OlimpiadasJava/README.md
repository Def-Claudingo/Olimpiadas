# Mapa de sedes — Dijkstra

Aplicación de consola para calcular trayectos mínimos entre sedes de un mapa dirigido. Guarda el mapa y los resultados de las consultas en un archivo local UTF-8 y los recupera al iniciar. Todos los datos de ejemplo son ficticios.

## Requisitos y ejecución

- JDK 17 o superior. Compilación objetivo Java 17; validación local con Temurin 21.0.11.
- Maven 3.9.9 o superior de la serie 3.x; validación local con Maven 3.9.16.
- Internet únicamente para descargar las dependencias durante la primera compilación. El JAR funciona sin Internet y sin servicios de pago.

Desde la raíz del repositorio:

```shell
cd OlimpiadasJava
mvn clean verify
java -jar target/olimpiadas-java-1.0.0.jar
```

En IntelliJ IDEA, abrir `OlimpiadasJava/pom.xml` como proyecto Maven, seleccionar JDK 17 o superior y ejecutar `ni.edu.uam.olimpiadas.Main`. Maven configura el procesador de anotaciones de Lombok.

Sin argumentos se abre un menú: sedes, conexiones, ruta, mapa, historial, importar, exportar y salir. Cada operación correcta se guarda inmediatamente. El fin de la entrada cierra el menú y descarta cualquier operación que aún no tenga todos sus datos.

El archivo predeterminado es `datos/estado.mapa`, relativo al directorio de ejecución. Para utilizar otro:

```shell
java -jar target/olimpiadas-java-1.0.0.jar --archivo datos/equipo.mapa
```

## Comandos

La opción `--archivo RUTA`, cuando se utilice, debe preceder al comando. Use comillas para nombres o rutas con espacios.

| Comando | Operación |
|---|---|
| `sede NOMBRE` | Agrega una sede |
| `conexion ORIGEN DESTINO PESO` | Agrega una conexión dirigida |
| `ruta ORIGEN DESTINO` | Calcula y guarda una consulta |
| `mapa` | Lista sedes y conexiones |
| `historial` | Muestra los resultados guardados del mapa actual |
| `importar ARCHIVO` | Valida y reemplaza el mapa actual; limpia su historial |
| `exportar ARCHIVO` | Guarda una copia completa del mapa y su historial |
| `ayuda` o `--help` | Muestra la sintaxis |

Importar reemplaza el mapa solo después de validar íntegramente el archivo y guardarlo correctamente. Exportar reemplaza el archivo de destino si ya existe. Conserve los respaldos en una ruta distinta al archivo de trabajo.

Códigos de salida: `0` operación válida, incluido `SIN_RUTA`; `1` fallo de archivo o lectura; `2` argumento o dato inválido. Los errores se escriben en la salida de errores sin traza de excepción. En el menú, los errores de una operación permiten continuar.

## Demostración de aceptación y recarga

Después de compilar, desde PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/demo.ps1
```

El script crea archivos nuevos bajo `datos/`, ejecuta cada comando en un proceso Java independiente, comprueba los códigos de salida y verifica por SHA-256 que el peso negativo no modifica el archivo. No sobrescribe un estado de demostración preexistente.

También puede ejecutar manualmente:

```shell
java -jar target/olimpiadas-java-1.0.0.jar --archivo datos/demo.mapa importar ejemplos/aceptacion.mapa
java -jar target/olimpiadas-java-1.0.0.jar --archivo datos/demo.mapa ruta A B
java -jar target/olimpiadas-java-1.0.0.jar --archivo datos/demo.mapa ruta A AISLADA
java -jar target/olimpiadas-java-1.0.0.jar --archivo datos/demo.mapa ruta A A
java -jar target/olimpiadas-java-1.0.0.jar --archivo datos/demo.mapa conexion B A -1
java -jar target/olimpiadas-java-1.0.0.jar --archivo datos/demo.mapa historial
java -jar target/olimpiadas-java-1.0.0.jar --archivo datos/demo.mapa mapa
java -jar target/olimpiadas-java-1.0.0.jar --archivo datos/demo.mapa exportar datos/respaldo.mapa
```

Resultados esperados:

```text
ENCONTRADA | A -> B | costo=3 | ruta=A,C,B
SIN_RUTA | A -> AISLADA
ENCONTRADA | A -> A | costo=0 | ruta=A
ERROR: El peso debe ser un entero no negativo.
```

La invocación posterior de `historial` recupera las tres consultas desde disco. `mapa` sigue mostrando solo tres conexiones: el intento con peso `-1` no agrega `B -> A`. `ejemplos/costos-long.mapa` permite consultar `Norte -> Sur` con costo `4294967294`, superior a `Integer.MAX_VALUE`. `ejemplos/invalido-negativo.mapa` demuestra una importación rechazada sin modificar el estado previo.

## Diseño

| Capa | Responsabilidad |
|---|---|
| `dominio` | Grafo por listas de adyacencia, validaciones e información de conexiones/resultados |
| `servicios/DijkstraService` | Cola de prioridad, relajación de distancias y reconstrucción mediante predecesores |
| `servicios/ServicioMapa` | Casos de uso, historial y confirmación de cambios después del guardado |
| `persistencia` | Contrato del repositorio, lectura estricta y escritura atómica del archivo |
| `consola` | Menú, comandos, presentación y errores controlados |

`MapaDirigido` conserva sus invariantes mediante métodos de modificación validados y copias defensivas. Los modelos de transferencia usan `@Getter`, `@Setter`, `@NoArgsConstructor` y `@AllArgsConstructor` de Lombok; modificar una copia no modifica el estado interno.

Decisiones:

- Conexiones dirigidas con peso `int` entre `0` y `2147483647`. Distancias y sumas en `long`, con validación de desbordamiento.
- Nombres exactos y sensibles a mayúsculas; de 1 a 100 unidades UTF-16, sin espacios extremos, caracteres de control ni sustitutos Unicode aislados. Se permiten espacios internos y Unicode válido. El menú recorta espacios extremos al leer.
- Se rechazan sedes y conexiones duplicadas. Para cambiar un peso se importa un mapa nuevo. Se permiten ciclos, conexiones a la misma sede y pesos cero.
- Origen igual a destino exige que la sede exista y retorna costo cero y una ruta con esa sede.
- `SIN_RUTA` tiene costo `null` y ruta vacía; cero se reserva para trayectos válidos de costo cero.
- La cola admite entradas antiguas y las descarta al extraerlas. Los empates se resuelven por nombre de sede; se reconstruye una ruta mínima válida, sin prometer la menor secuencia lexicográfica completa.
- Al modificar/importar el mapa se limpia el historial, porque sus resultados pertenecen a la versión anterior. Reiniciar conserva el historial del mapa actual; exportar también lo incluye.
- Cada cambio trabaja sobre una copia. Primero se guarda y después se confirma en memoria. Si el disco falla, la operación se rechaza completa, incluso si era una consulta cuyo resultado debía persistirse.
- Al cargar/importar se validan el formato, las sedes, cada arista del trayecto, el costo y la optimalidad de las consultas. Un estado corrupto no se sustituye silenciosamente por un mapa vacío.

## Complejidad

Sean `V` las sedes y `E` las conexiones. El grafo ocupa `O(V + E)`.

Dijkstra usa `PriorityQueue` con entradas repetidas, descarte de entradas obsoletas y parada al extraer el destino con su distancia vigente. Tiempo: `O((V + E) log(V + E))`, equivalente a `O((V + E) log V)` para este grafo sin aristas paralelas, usando `log(max(2,V))` en casos degenerados. Espacio auxiliar: `O(V + E)` por distancias, predecesores y hasta `O(E)` entradas en la cola. Reconstruir una ruta cuesta `O(V)` en el peor caso.

La persistencia tiene un costo adicional: reescribe la instantánea completa. Para `H` consultas almacenadas y `R` sedes acumuladas en sus trayectos, la serialización y validación estructural dependen también del tamaño del historial y de las adyacencias recorridas. Como las consultas recuperadas se recalculan para comprobar optimalidad, iniciar/importar cuesta hasta `O(H (V + E) log(max(2,V)))`, además de leer el archivo. Estas garantías se refieren al algoritmo y no a los tiempos del disco.

## Formato de intercambio

Texto UTF-8 sin BOM, versión 1. Los campos se separan con tabulaciones reales. La exportación usa LF; la lectura admite LF y CRLF. No se aceptan líneas vacías, comentarios ni registros desconocidos. El orden de entrada es libre; la salida ordena sedes, conexiones y consultas.

```text
MAPA	1
SEDE	A
SEDE	B
CONEXION	A	B	4
CONSULTA	A	B	ENCONTRADA	4	A	B
```

Para una consulta sin trayecto: `CONSULTA`, origen, destino, `SIN_RUTA`, `-`, separados por tabulaciones. En una consulta encontrada, todos los campos después del costo son sedes de la ruta. Los ejemplos en `ejemplos/` contienen tabulaciones listas para importar.

El guardado escribe un temporal en la misma carpeta, fuerza su contenido al disco y hace un reemplazo atómico. Si el sistema de archivos no permite ese reemplazo, se rechaza la operación y se conserva el archivo anterior.

## Pruebas automatizadas

```shell
mvn test
mvn clean verify
```

JUnit prueba los tres criterios obligatorios, costos `long`, pesos cero, ciclos, dirección de aristas, identidad, datos inexistentes, duplicados, Unicode inválido, copias defensivas, persistencia, importación/exportación, historial manipulado y fallos de guardado. Una prueba determinista compara 1600 pares origen/destino contra una implementación independiente de Bellman-Ford. Las pruebas de consola cubren también el menú, EOF y errores de argumentos. Los reportes se generan en `target/surefire-reports/`.

GitHub Actions ejecuta `mvn clean verify` con Java 17 y 21. Las dependencias y versiones de los complementos de compilación, pruebas y empaquetado están declaradas en `pom.xml`.

Dependencias: Lombok 1.18.48, solo durante compilación; JUnit Jupiter 5.11.4, solo durante pruebas. No hay dependencias externas en tiempo de ejecución. La versión objetivo usa [`maven.compiler.release`](https://maven.apache.org/plugins/maven-compiler-plugin/examples/set-compiler-release.html).

## Limitaciones conocidas y entrega

- Aplicación local para un único proceso que escriba cada archivo. No sincroniza procesos simultáneos ni la replicación de OneDrive. No se requiere concurrencia en el enunciado.
- El mapa y el historial completos se mantienen en memoria. El historial no tiene paginación ni límite automático; se limpia al cambiar el mapa.
- No incluye eliminación individual de sedes/aristas ni edición de pesos; esos cambios se realizan importando el mapa completo.
- Requiere un sistema de archivos con reemplazo atómico. No incluye recuperación ante daño físico del disco; exportar genera respaldos explícitos.
- Interfaz de consola, sin API HTTP porque el enunciado permite consola. La terminal debe usar UTF-8 para visualizar correctamente los acentos; en Windows clásico puede ejecutar `chcp 65001`.

Repositorio público: [Def-Claudingo/Olimpiadas](https://github.com/Def-Claudingo/Olimpiadas). El líder del equipo puede entregar ese enlace, la carpeta `OlimpiadasJava` y la demostración anterior. El repositorio contiene fuentes, pruebas y datos de ejemplo; `datos/` y `target/` quedan excluidos de Git.
