# Gateway Service

## Ansvar

Gateway Service är frontendens gemensamma ingång till Microservices Webshops backend.
Den vidarebefordrar anrop till auth-, product-, order- och payment-service.

Vid betalning, statushämtning och avbrytning verifieras kundens JWT hos auth-services `/auth/me`.
Kundnamnet hämtas från den verifierade identiteten. Interna betalningsanrop använder tjänstens
gemensamma hemlighet, som aldrig skickas till webbläsaren.

## Kontohantering

| Gateway-anrop | Vidarebefordras till auth-service |
| --- | --- |
| `POST /gateway/auth/register` | `POST /appusers` |
| `POST /gateway/auth/password-reset` | `POST /appusers/password-reset` |
| `DELETE /gateway/auth/account` | `DELETE /appusers/me` |

Registrering och demoåterställning kräver ingen inloggning. Kontoradering vidarebefordrar användarens
Bearer-token och nuvarande lösenord; auth-service avgör vilket konto som får raderas.
Gatewayn bevarar auth-servicens statuskoder och felsvar.

Lösenordsåterställningen är endast för demo och verifierar inte kontots ägare.

## Tekniker

Java 21, Spring Boot 4, Spring Security, REST och Maven.

## Databas

Gatewayn har ingen egen databas.

## Miljövariabler

| Variabel | Lokalt | Render |
| --- | --- | --- |
| `AUTH_SERVICE_URL` | `http://localhost:8080` | `https://microservices-webshop-auth-service.onrender.com` |
| `PRODUCT_SERVICE_URL` | `http://localhost:8081` | `https://microservices-webshop-product-service.onrender.com` |
| `ORDER_SERVICE_URL` | `http://localhost:8082` | `https://microservices-webshop-order-service.onrender.com` |
| `PAYMENT_SERVICE_URL` | `http://localhost:8084` | `https://microservices-webshop-payment-service.onrender.com` |
| `ALLOWED_ORIGINS` | `http://localhost:5173` | `https://microservices-webshop-frontend.onrender.com` |
| `INTERNAL_SERVICE_TOKEN` | Gemensam intern hemlighet, minst 32 tecken. | Samma värde i gateway, payment, order och product. |
| `SERVER_PORT` | Valfri, standard 9003. | Imagen använder normalt port 9003. |

Tjänsteadresserna anges utan extra sökvägar som `/gateway`. Flera origins anges kommaseparerat.
Hemliga värden ska inte sparas i Git.

## Starta applikationen lokalt

Sätt miljövariablerna och starta från projektmappen.

**macOS/Linux:**

```bash
./mvnw spring-boot:run
```

**Windows PowerShell:**

```powershell
.\mvnw.cmd spring-boot:run
```

Kräver Java 21 och nåbara backendtjänster.
Lokal API-bas: `http://localhost:9003/gateway`.

## Render

| Tjänst | Render-adress |
| --- | --- |
| Frontend – öppna webshoppen | [microservices-webshop-frontend.onrender.com](https://microservices-webshop-frontend.onrender.com) |
| Auth Service | [microservices-webshop-auth-service.onrender.com](https://microservices-webshop-auth-service.onrender.com) |
| Product Service | [microservices-webshop-product-service.onrender.com](https://microservices-webshop-product-service.onrender.com) |
| Order Service | [microservices-webshop-order-service.onrender.com](https://microservices-webshop-order-service.onrender.com) |
| Email Service | [microservices-webshop-email-service.onrender.com](https://microservices-webshop-email-service.onrender.com) |
| Gateway Service | [microservices-webshop-gateway-service.onrender.com](https://microservices-webshop-gateway-service.onrender.com) |
| Payment Service | [microservices-webshop-payment-service.onrender.com](https://microservices-webshop-payment-service.onrender.com) |
- Docker-image: `ynsuldg/microservices-webshop-gateway-service:1.0.0`.

Använd **Web Service → Existing Image**, sätt miljövariablerna i Render och behåll Docker-imagens startkommando.
Efter en ny image från senaste `main` väljs **Manual Deploy → Deploy latest reference**.
En äldre Actions-omkörning kan skriva över samma tagg med äldre kod.

Backendadressen är ett API, inte en vanlig webbsida. Rotadressen kan ge 401 eller 404 utan att tjänsten är nere.
Render Free kan ge väntetid när tjänsterna väcks. Se [Render-dokumentationen](https://render.com/docs/deploying-an-image).

Frontendens API-bas på Render är `https://microservices-webshop-gateway-service.onrender.com/gateway`.

## Relaterade repositories

- [Auth Service](https://github.com/ynsuldg/microservices-webshop-auth-service)
- [Product Service](https://github.com/ynsuldg/microservices-webshop-product-service)
- [Order Service](https://github.com/ynsuldg/microservices-webshop-order-service)
- [Email Service](https://github.com/ynsuldg/microservices-webshop-email-service)
- [Payment Service](https://github.com/ynsuldg/microservices-webshop-payment-service)
- [Frontend](https://github.com/ynsuldg/microservices-webshop-frontend)
