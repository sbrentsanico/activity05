# Activity 04 – Modular Monolith Integration

**Stack:** Java 21 · Spring Boot 3.3 · React 18 (Vite) · Supabase (PostgreSQL)

**Author:** Sanico  
**Packages:** `edu.cit.sanico.shop` (Order module) · `edu.cit.sanico.inventory` (Inventory module)

---

## Project Structure

```
activity04/
├── backend/                          # Spring Boot monolith
│   ├── pom.xml
│   ├── .env.example                  # credential template (copy to .env)
│   └── src/main/java/edu/cit/sanico/
│       ├── ShopApplication.java      # @SpringBootApplication entry point
│       ├── config/CorsConfig.java
│       ├── inventory/                # Inventory module
│       │   ├── InventoryItem.java
│       │   ├── InventoryRepository.java
│       │   ├── InventoryService.java        # public interface
│       │   ├── InventoryServiceImpl.java    # package-private implementation
│       │   └── ReserveResult.java
│       └── shop/                     # Order module
│           ├── Order.java
│           ├── OrderRepository.java
│           ├── OrderRequest.java
│           ├── OrderResponse.java
│           ├── OrderService.java
│           ├── OrderServiceImpl.java
│           └── OrderController.java
├── frontend/                         # React / Vite SPA
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── App.jsx
│       └── main.jsx
├── db/
│   └── schema.sql                    # CREATE TABLE + seed INSERT
└── README.md
```

---

## Supabase Setup Steps

1. **Create a free project** at <https://supabase.com>.
2. In the Supabase Dashboard navigate to **SQL Editor** and run the contents of `db/schema.sql`.  
   This creates the `inventory` and `orders` tables and seeds the three products.
3. Go to **Project Settings → Database** and copy:
   - **Host** (looks like `db.<project-ref>.supabase.co`)
   - **Database password** (the one you chose at project creation)
4. In `backend/`, copy `.env.example` to `.env` and fill in the values:
   ```env
   DB_URL=jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres
   DB_USERNAME=postgres
   DB_PASSWORD=your-password
   ```
   > **.env is git-ignored and must never be committed.**

---

## Running the Application

### Backend

> **Prerequisite:** Java 21 + Maven 3.9+

```bash
cd backend

# Option A — IntelliJ Run Configuration
# Add DB_URL, DB_USERNAME, DB_PASSWORD as environment variables in the run config.

# Option B — PowerShell
$env:DB_URL="jdbc:postgresql://db.<ref>.supabase.co:5432/postgres"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="your-password"
mvn spring-boot:run
```

The API will start on **http://localhost:8080**.

### Frontend

> **Prerequisite:** Node.js 18+

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173** in your browser.

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/inventory` | List all products (drives the dropdown) |
| `POST` | `/api/orders` | Place an order |

### POST /api/orders

**Request:**
```json
{ "productId": "P100", "quantity": 2 }
```

**Response (CONFIRMED):**
```json
{
  "status": "CONFIRMED",
  "reason": null,
  "inventory": { "productId": "P100", "name": "Wireless Mouse", "stock": 23 }
}
```

**Response (REJECTED):**
```json
{
  "status": "REJECTED",
  "reason": "Insufficient stock. Requested: 5, Available: 0",
  "inventory": { "productId": "P300", "name": "USB-C Hub", "stock": 0 }
}
```

---

## Network Tab Evidence

### Confirmed Order (P100 – Wireless Mouse, qty 2)
![img_1.png](img_1.png)
*Endpoint: `POST /api/orders` · Status: `200 OK` · Response body shows `"status":"CONFIRMED"`*

### Rejected Order (P300 – USB-C Hub, qty 1 — stock is 0)
![img_2.png](img_2.png)
*Endpoint: `POST /api/orders` · Status: `200 OK` · Response body shows `"status":"REJECTED"`*

### Orders table after the two operations
![img_3.png](img_3.png)
---

## Reflection (300–500 words)

### 1. In-process vs. Microservice Integration — What do you get for free, and what would you need to add back?

In the modular monolith, the Order module calls `InventoryService.reserve()` as a plain Java method call. This gives us several things for free: **atomicity** (both the inventory decrement and the order insert happen inside the same database transaction), **zero latency** (no serialization, no network hop, no TCP overhead), **type safety** (the compiler checks the method signature at build time), and **simplicity** (no service discovery, no load balancing, no retry logic).

If we split Order and Inventory into separate microservices, all of these have to be added back manually. We would need an HTTP client (or gRPC/messaging) to make the call, a retry/circuit-breaker strategy (e.g., Resilience4j) to handle network failures, a distributed transaction mechanism — likely a Saga pattern with compensating transactions — to replace the single ACID transaction we currently get from JPA, and contract tests (e.g., Pact) to catch interface mismatches that the compiler currently catches for free. The operational complexity increases significantly: two deployment pipelines, two sets of environment variables, inter-service authentication, and distributed tracing to debug cross-service failures.

### 2. Why does package-private visibility on `InventoryServiceImpl` matter?

The `InventoryServiceImpl` class has no `public` modifier, so Java's access-control rules prevent any code outside `edu.cit.sanico.inventory` from referencing it by name. The Order module is forced to use only `InventoryService` (the public interface) for both compile-time type declarations and runtime injection.

If `InventoryServiceImpl` were public, a careless developer could write `new InventoryServiceImpl(repo)` directly inside `OrderServiceImpl` or inject the concrete class (`@Autowired InventoryServiceImpl impl`). This would couple the Order module to implementation details — internal field names, constructor signatures, concrete behaviour — making future refactoring much harder. Every change to the implementation class would risk breaking the Order module. Package-private visibility uses the language itself to enforce the boundary: the contract is the interface, and the implementation is a hidden detail.

### 3. When would you extract Inventory into its own microservice, and what would need to change?

The right time to extract Inventory is when its **scaling needs**, **deployment cadence**, or **team ownership** diverge significantly from Order. For example, if Inventory needs to integrate with a warehouse management system on its own release schedule, or if it must scale to handle real-time IoT stock updates while Order remains low-traffic.

In the code, the changes would be:
- Replace the in-process `InventoryService` call in `OrderServiceImpl` with an HTTP client (e.g., Spring's `RestClient` or `WebClient`) pointing at the Inventory service URL.
- Move `InventoryServiceImpl`, `InventoryRepository`, and `InventoryItem` (the JPA entity) into a separate Spring Boot project with its own database schema.
- Keep the `InventoryService` interface in a shared library (or redefine the contract as an OpenAPI spec) so the Order service can still compile against a typed client.
- Replace the single JPA transaction with a Saga: on rejection, no rollback is needed (stock was never decremented); on partial failure after confirmation, a compensating "un-reserve" event would need to be published.
- Add service discovery (e.g., Eureka or Kubernetes DNS), a circuit breaker, and distributed tracing.
