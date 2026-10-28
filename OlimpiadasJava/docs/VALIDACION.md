# Validación local

Fecha: 2026-10-28.

Entorno: Windows, Temurin 21.0.11, Maven 3.9.16; compilación con `release 17`.

## Compilación y pruebas

Comando: `mvn --batch-mode --no-transfer-progress clean verify`.

Resultado: `BUILD SUCCESS`, 83 pruebas, 0 fallos, 0 errores y 0 omitidas.

| Suite | Pruebas |
|---|---:|
| ConsolaTest | 16 |
| MapaDirigidoTest | 25 |
| ArchivoMapaRepositoryTest | 17 |
| DijkstraServiceTest | 11 |
| ServicioMapaTest | 14 |

La comparación determinista contra Bellman-Ford valida adicionalmente 1600 pares origen/destino dentro de una prueba.

## Demostración del JAR

Comando: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/demo.ps1`.

Resultado: código de salida 0 y `DEMOSTRACION_COMPLETA`.

```text
ENCONTRADA | A -> B | costo=3 | ruta=A,C,B
SIN_RUTA | A -> AISLADA
ENCONTRADA | A -> A | costo=0 | ruta=A
ERROR: El peso debe ser un entero no negativo.
SEDES: A, B, C, AISLADA
A -> B = 4
A -> C = 1
C -> B = 2
TOTAL: 4 sedes, 3 conexiones
```

Se comprobó que el SHA-256 del estado no cambia después de intentar agregar peso -1. Cada consulta se ejecutó en un proceso Java nuevo, y otra invocación recuperó las tres consultas en `historial`. La exportación se importó en un archivo distinto y volvió a producir costo 3 y ruta A,C,B.

La ejecución de GitHub Actions queda registrada en la pestaña Actions del repositorio; este informe documenta exclusivamente la validación local.
