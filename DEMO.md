# Demo: autenticación y autorización con Auth0

Una recorrida paso a paso por el servicio, para hacer en tu propio tenant de Auth0. Vas a ver:

1. Sin token no se entra: **401**
2. Con un token válido sí: **200**
3. El scope (RBAC de Auth0) decide qué puede hacer cada usuario: **200 vs 403**
4. Por qué hay que validar el `aud`: un token de otra API no entra
5. El scope no alcanza para "sólo el dueño": eso lo decide el código del servicio

## Preparación

Necesitás una cuenta de Auth0 (alcanza con la gratuita), Node.js 20.12 o más nuevo y JDK 21 (si no lo
tenés, Gradle lo descarga).

1. Creá los recursos en Auth0 siguiendo [`setup/README.md`](setup/README.md), pasos 1 a 5. Al terminar
   vas a tener la API, el rol, las aplicaciones y dos usuarios creados, y el `.env` de la raíz completo.
2. Abrí **dos terminales**:

   ```shell
   # Terminal 1: el servicio (en la raíz del repo)
   ./gradlew bootRun

   # Terminal 2: tokens y requests (en setup/)
   cd setup
   API=http://localhost:8080
   ```

3. Tené a mano la contraseña de los usuarios (`DEMO_USER_PASSWORD` en `setup/.env`) y
   [jwt.io](https://jwt.io) abierto en el browser.

Los dos usuarios:

| Usuario | Rol | Tiene `read:snippets` |
|---|---|---|
| `reader@example.com` | `snippet-reader` | Sí |
| `no-role@example.com` | (ninguno) | No |

## 1. Sin token: 401

```shell
curl -i $API/                # 200: "/" es público
curl -i $API/snippets        # 401
```

401 significa "no sé quién sos". El servicio no tiene usuarios ni contraseñas propios: sólo acepta
tokens emitidos por Auth0.

## 2. Conseguir un token

```shell
export A=$(npm run --silent token)
```

Se abre el login de Auth0 en el browser: entrá como **`reader@example.com`**. La primera vez, Auth0 te
pide autorizar la aplicación: aceptá. La terminal muestra los claims del token:

```
  sub:   auth0|...
  aud:   https://auth0-integration-demo
  scope: read:snippets
  exp:   ...
```

El script usa el flujo **Authorization Code + PKCE**, el mismo que usa un frontend: la contraseña la ve
sólo Auth0, y el script recibe un token firmado. Podés ver cómo funciona en
[`setup/get-token.mjs`](setup/get-token.mjs).

Pegá el token en jwt.io (`echo $A`) y mirá los claims:

- `iss`: quién lo emitió (tu tenant)
- `aud`: para quién es (la API)
- `exp`: hasta cuándo vale
- `scope`: qué puede hacer
- `sub`: quién es el usuario

El token está **firmado, no cifrado**: cualquiera puede leerlo, pero nadie puede modificarlo sin romper
la firma.

## 3. Con token: 200

```shell
curl -i -H "Authorization: Bearer $A" $API/snippets      # 200
```

El servicio validó la firma (con las claves públicas de Auth0), el `iss`, el `aud` y el `exp`, sin
llamar a Auth0 en cada request. Mirá `src/main/resources/application.yml`: toda esa validación son dos
líneas de configuración.

## 4. El scope: 200 vs 403

```shell
curl -i -H "Authorization: Bearer $A" $API/scopedAuthenticate      # 200
```

Ahora con el otro usuario:

```shell
export B=$(npm run --silent token)     # entrá como no-role@example.com
curl -i -H "Authorization: Bearer $B" $API/scopedAuthenticate      # 403
curl -i -H "Authorization: Bearer $B" $API/snippets                # 200: está autenticado
```

Compará los claims que imprimió el script: los dos tokens pidieron `read:snippets`, pero sólo el de
`reader` lo tiene.

403 significa "sé quién sos y no podés". La API tiene RBAC activado en Auth0: el scope sólo se da si el
usuario tiene un rol con ese permiso. Del lado del servicio, la regla está en
`OAuth2ResourceServerSecurityConfiguration.kt`: `hasAuthority("SCOPE_read:snippets")`.

## 5. ¿Por qué validar el `aud`?

En el dashboard de Auth0 andá a **Applications → APIs → Auth0 Management API → Test** y copiá el token.

```shell
M=<token de la Management API>
curl -i -H "Authorization: Bearer $M" $API/snippets       # 401
```

Pegalo en jwt.io: la firma es válida y el `iss` es el mismo, pero el `aud` es la Management API.

Auth0 firma con la misma clave todos los tokens del tenant. La firma dice **quién** lo emitió; el `aud`,
**para quién**. Si el servicio no chequea el `aud`, acepta cualquier token del tenant.

Para comprobarlo: comentá la línea `audiences:` en `application.yml`, reiniciá el servicio y repetí el
curl: ahora da **200**. Después volvé a descomentarla.

## 6. "Sólo el dueño": eso es del servicio

El scope dice qué puede hacer la aplicación sobre la API, no sobre **cuál** snippet. Eso lo decide el
código del servicio, en `OwnedSnippetController.kt`.

```shell
# reader crea un snippet; la respuesta trae el id
curl -s -H "Authorization: Bearer $A" -H 'Content-Type: text/plain' -d 'mi secreto' $API/owned-snippets
ID=<el id de la respuesta>

curl -i -H "Authorization: Bearer $A" $API/owned-snippets/$ID      # 200: es el dueño
curl -i -H "Authorization: Bearer $B" $API/owned-snippets/$ID      # 403: no lo es
```

La regla en el código:

```kotlin
if (snippet.ownerId != jwt.subject) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
}
```

El dueño se guarda como el `sub` del token. Auth0 resuelve **quién sos**; quién puede hacer qué sobre
cada snippet es lógica de dominio, y le corresponde al servicio.

## Resumen

- El login y la emisión de tokens se delegan en Auth0.
- Cada servicio valida el token: firma, `iss`, `aud` y `exp`.
- El scope autoriza a la aplicación sobre la API.
- Quién puede qué sobre cada recurso lo decide el código del servicio.

## Si algo falla

| Síntoma | Qué hacer |
|---|---|
| `npm run token` abre el login pero queda colgado | Revisá la terminal: si dice `Could not listen on localhost:8485`, hay otro `npm run token` corriendo |
| El login entra directo con el usuario anterior | No debería pasar (`prompt=login`); si pasa, abrí `https://<AUTH0_DOMAIN>/v2/logout` |
| Todo da 401 con token | Compará el `aud` y el `iss` del token en jwt.io con el `.env`, y revisá que el servicio se haya levantado con ese `.env` |
| `/scopedAuthenticate` da 403 con `reader` | El token no tiene `scope: read:snippets`: revisá que se haya pedido (`npm run token` lo pide por defecto) |
| Los snippets de `/owned-snippets` desaparecieron | Viven en memoria: se borran al reiniciar el servicio |
| Errores al crear los recursos con Pulumi | Ver la sección *Troubleshooting* de [`setup/README.md`](setup/README.md) |
