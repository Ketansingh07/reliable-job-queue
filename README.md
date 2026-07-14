# Reliable Job Queue

---

## 1. What is This Project?

Think of a restaurant.

When you place an order, the waiter doesn't cook your food right there at your table. Instead:
1. Waiter writes your order on a slip (creates a **job**)
2. Puts the slip on the kitchen counter (adds to a **queue**)
3. Tells you "Order placed!" immediately (fast response to user)
4. A chef picks up the slip when ready (a **worker** processes the job)
5. Chef cooks your food (job gets processed in the background)

**This project is that kitchen system — but for software.**

Instead of food orders, we handle tasks like:
- Sending emails
- Generating PDF reports
- Syncing payments

Instead of doing these slow tasks while the user waits, we say "Got it!" instantly and do the work in the background.

---

## 2. What is a "Job"?

A **job** is simply a task that needs to be done later.

In our code, a Job has:
```
jobId       → A unique ID (like an order number: "abc-123")
jobType     → What kind of work ("EMAIL", "REPORT_GEN")
payload     → The data needed ("send email to user@test.com")
status      → Where is it now? (PENDING → PROCESSING → COMPLETED or DEAD)
maxRetries  → How many times to retry if it fails (e.g., 3)
retryCount  → How many times it has already failed (starts at 0)
errorMessage→ Why did it fail? ("SMTP server timeout")
createdAt   → When was this job created
```

**Real-world analogy:** A job is like a sticky note that says "Call this customer back" — it has all the info needed to complete the task.

---

## 3. What is a "Queue"?

A **queue** is a waiting line. Like a line at a bank.

- First person in line gets served first (FIFO = First In, First Out)
- New people join at the back
- The teller serves from the front

In our project, we have THREE queues:

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  queue:pending      → Jobs waiting in line                  │
│                       (like people waiting at the bank)     │
│                                                             │
│  queue:processing   → Job currently being worked on         │
│                       (person currently at the counter)     │
│                                                             │
│  queue:dead_letter  → Jobs that failed too many times       │
│                       (person who got rejected 3 times,     │
│                        now sitting in a "problem" chair)    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. What is Redis?

**Redis** is a super-fast database that stores data in your computer's RAM (memory).

Normal databases (like MySQL, PostgreSQL) store data on a hard disk. Reading from disk is slow.
Redis stores everything in RAM — which is 100x faster.

**Why do we use Redis for our queue?**

1. **Speed** — Adding/removing jobs takes less than 1 millisecond
2. **Atomic operations** — Redis guarantees that when we move a job from "pending" to "processing", it happens as ONE unbreakable action. No job can get lost in between.
3. **Built-in list support** — Redis has a data structure called "List" which is perfect for queues (push to one end, pop from the other)

**Think of Redis as:** A whiteboard in the kitchen where you stick order slips. Everyone can see it, it's fast to add/remove slips, and it's always up-to-date.

**How to get Redis:**
- **Option A: Upstash** (free, cloud-hosted) — Go to https://upstash.com, create a free account, create a Redis database. You get a host, port, and password. No installation needed.
- **Option B: Docker** (local) — Run `docker compose up -d` (explained below in Docker section)

---

## 5. What is a "Worker"?

A **worker** is a background process that keeps checking: "Is there a new job for me?"

Imagine a chef in the kitchen who:
1. Looks at the order counter every 1 second
2. If there's an order slip → picks it up and starts cooking
3. If no order → waits 1 second and checks again
4. If cooking fails (burned the food) → tries again
5. If it fails 3 times → throws the slip in the "problem" bin

In our code, the `JobWorker` class does exactly this:
```
Every 1 second:
    1. Check queue:pending for a job
    2. If found → move it to queue:processing (so no one else grabs it)
    3. Find the right handler for this job type
    4. Execute the handler
    5. If SUCCESS → remove from processing, mark as COMPLETED
    6. If FAILURE → increment retry count
        - If retries left → put back in pending (with a delay)
        - If no retries left → move to dead_letter queue
```

---

## 6. What is a "Thread"?

Your computer can do multiple things at the same time. Each "thing" is called a **thread**.

**Example:** You're listening to music AND typing a document. That's 2 threads.

In our application:
- **Main thread** → Runs the web server (handles your API requests)
- **Scheduler thread** → Runs the worker (checks for jobs every 1 second)

They run simultaneously. So while the worker is processing a job in the background, the web server can still accept new job requests from users.

**The `@Scheduled(fixedDelay = 1000)` annotation** tells Spring: "Run this method on a separate thread, and after it finishes, wait 1000 milliseconds (1 second) before running it again."

---

## 7. What is "Exponential Backoff"?

When something fails, you don't want to retry immediately and keep hammering a broken service.

**Bad approach:**
```
Fail → retry immediately → fail → retry immediately → fail → retry immediately
```
This is like calling someone who's busy — calling every 1 second is annoying and useless.

**Good approach (Exponential Backoff):**
```
Fail → wait 2 seconds → retry
Fail → wait 4 seconds → retry
Fail → wait 8 seconds → retry
Fail → wait 16 seconds → retry
```

Each time, you wait DOUBLE the previous time. This gives the broken service time to recover.

**Formula:** `wait time = 2^retryCount seconds`
- Retry 1: 2^1 = 2 seconds
- Retry 2: 2^2 = 4 seconds
- Retry 3: 2^3 = 8 seconds

---

## 8. What is a "Dead Letter Queue" (DLQ)?

When a job fails too many times (exceeds maxRetries), we don't just delete it. That would lose data.

Instead, we move it to a special queue called the **Dead Letter Queue**.

**Think of it as:** A "problem orders" folder. A manager can look at it later, figure out what went wrong, fix the issue, and retry the job manually.

In our project:
- You can VIEW all dead letter jobs: `GET /api/v1/jobs/dlq`
- You can RETRY a dead letter job: `POST /api/v1/jobs/dlq/{jobId}/retry`

---

## 9. What Makes This Queue "Reliable"?

Here's the problem with a simple queue:

```
Simple queue:
    Worker POPS job from queue → processes it

    What if worker CRASHES after popping but BEFORE finishing?
    → The job is GONE. Lost forever. Nobody knows about it.
```

**Our solution — the "Reliable Queue Pattern":**

```
Our queue:
    Worker MOVES job from "pending" → "processing" (atomically)
    → processes it
    → removes from "processing"

    What if worker CRASHES after moving but BEFORE finishing?
    → The job is still in "processing" queue!
    → We can detect it and retry it.
    → NO JOB IS EVER LOST.
```

The magic is the `BLMOVE` Redis command — it pops from one list AND pushes to another list in a SINGLE atomic operation. There's no moment where the job exists in neither list.

---

## 10. What is the "Strategy Pattern"?

Different job types need different processing logic:
- EMAIL jobs → send an email
- REPORT_GEN jobs → generate a PDF

Instead of writing one giant `if-else`:
```java
// BAD - hard to maintain
if (job.getType().equals("EMAIL")) { ... }
else if (job.getType().equals("REPORT_GEN")) { ... }
else if (job.getType().equals("PAYMENT")) { ... }
// keeps growing forever...
```

We use the **Strategy Pattern** — each job type has its own handler class:
```java
// GOOD - each handler is independent
EmailJobHandler      → handles "EMAIL" jobs
ReportJobHandler     → handles "REPORT_GEN" jobs
PaymentJobHandler    → handles "PAYMENT" jobs (you add this yourself)
```

To add a new job type, you just create a new class. You don't touch any existing code. This is the **Open/Closed Principle** — open for extension, closed for modification.

---

## 11. What is Docker?

**Docker** is a tool that packages software into "containers" — like shipping containers for code.

**Problem it solves:** "It works on my machine but not on yours."

**Example:** To run this project, you need Redis. Without Docker:
- Download Redis
- Install it
- Configure it
- Start it
- Hope it doesn't conflict with other stuff on your machine

**With Docker:**
```bash
docker compose up -d
```
One command. Done. Redis is running. Delete it when you're done. No mess on your machine.

**docker-compose.yml** is a recipe file that says: "I need Redis version 7, expose it on port 6379, and save its data in a volume."

**Note:** If you can't use Docker (like on a corporate laptop), you can use **Upstash** — a free cloud-hosted Redis. No installation needed.

---

## 12. What is Spring Boot Actuator?

**Actuator** adds production-ready monitoring endpoints to your app automatically.

After adding it, you get:
- `GET /actuator/health` → Is the app healthy? Is Redis connected?
- `GET /actuator/info` → App information
- `GET /actuator/metrics` → Performance numbers

We also added a **custom health indicator** that checks:
- If DLQ has more than 10 jobs → report app as UNHEALTHY (something is seriously wrong)

This is what production systems use for monitoring and alerting.

---

## 13. What is "Graceful Shutdown"?

When you stop the application (Ctrl+C), what happens to a job that's currently being processed?

**Without graceful shutdown:**
```
You press Ctrl+C → App dies immediately → Job in processing is abandoned
```

**With graceful shutdown:**
```
You press Ctrl+C → App says "I'm shutting down, let me finish current work"
→ Waits up to 30 seconds for in-flight jobs to complete
→ Then shuts down cleanly
```

We configured this with:
```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

---

## 14. Project Structure Explained

```
reliable-job-queue/
│
├── src/main/java/com/reliable/job/queue/
│   │
│   ├── model/
│   │   └── Job.java                    ← The "order slip" — defines what a job looks like
│   │
│   ├── handler/
│   │   ├── JobHandler.java             ← Interface — the "contract" every handler must follow
│   │   ├── EmailJobHandler.java        ← Knows how to process EMAIL jobs
│   │   └── ReportJobHandler.java       ← Knows how to process REPORT_GEN jobs
│   │
│   ├── service/
│   │   ├── RedisJobQueue.java          ← Talks to Redis — enqueue, dequeue, DLQ operations
│   │   └── JobWorker.java              ← The "chef" — polls queue and processes jobs
│   │
│   ├── config/
│   │   ├── RedisConfig.java            ← Tells Spring how to connect to Redis
│   │   └── JobQueueHealthIndicator.java← Custom health check for monitoring
│   │
│   ├── JobController.java              ← REST API — how users create jobs and check status
│   ├── DashboardController.java        ← Serves the HTML monitoring dashboard
│   └── ReliableJobQueueApplication.java← The starting point of the app
│
├── src/main/resources/
│   ├── application.yml                 ← Production config (Upstash cloud Redis)
│   ├── application-local.yml           ← Local development config
│   └── templates/
│       └── dashboard.html              ← Live monitoring UI (auto-refreshes every 5s)
│
├── src/test/java/                      ← Unit tests (test without real Redis)
│
├── docker-compose.yml                  ← One-command Redis setup
├── Dockerfile                          ← Package the app into a container
├── pom.xml                             ← Dependencies (what libraries we use)
└── README.md                           ← This file
```

---

## 15. How to Set Up and Run

### Prerequisites (what you need installed)
- **Java 21** — The programming language runtime. Download from https://adoptium.net/
- **Maven 3.9+** — Build tool for Java. Download from https://maven.apache.org/download.cgi
- **Redis** — Either via Docker OR Upstash (cloud, free)

### Option A: Using Upstash (No installation, free cloud Redis)

1. Go to https://upstash.com and create a free account
2. Create a new Redis database (pick the closest region to you)
3. Copy the host, port, and password from the dashboard
4. Update `src/main/resources/application.yml` with your values:
   ```yaml
   spring:
     data:
       redis:
         host: your-host.upstash.io
         port: 6380
         password: your-password
         ssl:
           enabled: true
   ```
   Or set environment variable:
   ```bash
   # Windows PowerShell
   $env:REDIS_PASSWORD="your-password-here"
   
   # Windows CMD
   set REDIS_PASSWORD=your-password-here
   
   # Mac/Linux
   export REDIS_PASSWORD=your-password-here
   ```

5. Run the application:
   ```bash
   mvn spring-boot:run
   ```

### Option B: Using Docker (Local Redis)

1. Install Docker Desktop from https://docker.com
2. Start Redis:
   ```bash
   docker compose up -d
   ```
   This downloads Redis and starts it in the background.
3. Run the application with local profile:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

### Verify it's running

Open your browser:
- Dashboard: http://localhost:8080/dashboard
- Health check: http://localhost:8080/actuator/health
- Queue stats: http://localhost:8080/api/v1/jobs/stats

---

## 16. How to Use It (Step by Step)

### Step 1: Create a job
```bash
curl -X POST "http://localhost:8080/api/v1/jobs?type=EMAIL&payload=user@example.com&maxRetries=3"
```

**What happens behind the scenes:**
1. Controller receives the request
2. Creates a Job object with a unique ID
3. Pushes it to `queue:pending` in Redis
4. Returns the job details to you immediately

**Response:**
```json
{
    "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "jobType": "EMAIL",
    "payload": "user@example.com",
    "status": "PENDING",
    "maxRetries": 3,
    "retryCount": 0
}
```

### Step 2: Worker picks it up (automatic, within 1 second)
The JobWorker (running in background):
1. Calls BLMOVE → moves job from pending to processing
2. Finds EmailJobHandler (because type = "EMAIL")
3. Calls emailHandler.handle(job)
4. If success → removes from processing, marks COMPLETED

### Step 3: Check job status
```bash
curl http://localhost:8080/api/v1/jobs/a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

**Response:**
```json
{
    "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "jobType": "EMAIL",
    "payload": "user@example.com",
    "status": "COMPLETED",
    "retryCount": 0
}
```

### Step 4: Check queue statistics
```bash
curl http://localhost:8080/api/v1/jobs/stats
```

**Response:**
```json
{
    "pending": 0,
    "processing": 0,
    "deadLetter": 0
}
```

### Step 5: View the dashboard
Open http://localhost:8080/dashboard in your browser.
You'll see a dark-themed monitoring page showing:
- Live queue counts (pending, processing, dead letter)
- A table of failed jobs with a "Retry" button

---

## 17. What to Expect (Scenarios)

### Scenario 1: Happy Path (everything works)
```
You create a job → status: PENDING
Worker picks it up (within 1s) → status: PROCESSING
Handler executes successfully → status: COMPLETED
Job disappears from Redis after 60 minutes (TTL)
```

### Scenario 2: Temporary Failure (service is briefly down)
```
You create a job → status: PENDING
Worker picks it up → status: PROCESSING
Handler throws an exception (e.g., email server timeout)
→ retryCount becomes 1
→ Job goes back to PENDING with 2-second backoff
Worker picks it up again after 2s → tries again
→ This time it works → status: COMPLETED
```

### Scenario 3: Permanent Failure (service is broken)
```
You create a job with maxRetries=3 → status: PENDING
Attempt 1: fails → wait 2s → back to PENDING
Attempt 2: fails → wait 4s → back to PENDING
Attempt 3: fails → MAX RETRIES EXCEEDED
→ Job moves to queue:dead_letter → status: DEAD
→ Shows up on dashboard with error message
→ You investigate, fix the issue
→ Click "Retry" on dashboard (or POST /api/v1/jobs/dlq/{id}/retry)
→ Job goes back to PENDING with fresh retry count
```

### Scenario 4: Worker crashes mid-processing
```
Job is in queue:processing (worker was working on it)
Worker process dies unexpectedly
Job is STILL in queue:processing — NOT LOST
When worker restarts, it can detect orphaned jobs
```

---

## 18. API Reference

| Method | URL | What it does |
|--------|-----|--------------|
| `POST` | `/api/v1/jobs?type=EMAIL&payload=hello&maxRetries=3` | Create a new job |
| `GET` | `/api/v1/jobs/{jobId}` | Check status of a specific job |
| `GET` | `/api/v1/jobs/stats` | How many jobs in each queue |
| `GET` | `/api/v1/jobs/dlq` | List all failed jobs |
| `POST` | `/api/v1/jobs/dlq/{jobId}/retry` | Retry a failed job |
| `GET` | `/dashboard` | Visual monitoring page |
| `GET` | `/actuator/health` | App health status |

---

## 19. Technologies Used (and Why)

| Technology | What it is | Why we use it |
|-----------|------------|---------------|
| **Java 21** | Programming language | Modern features, widely used in enterprise |
| **Spring Boot 3.4** | Framework | Auto-configuration, dependency injection, scheduling |
| **Redis** | In-memory database | Fast, atomic operations, perfect for queues |
| **Thymeleaf** | Template engine | Renders the dashboard HTML page |
| **Spring Actuator** | Monitoring library | Health checks, metrics out of the box |
| **Docker** | Containerization | Run Redis with one command |
| **Maven** | Build tool | Manages dependencies, builds the project |
| **JUnit 5 + Mockito** | Testing | Unit tests without needing real Redis |

---

## 20. Interview Talking Points

If asked about this project, here's how to explain it:

**"What does it do?"**
> It's a reliable background job processing system. Instead of making users wait for slow operations like sending emails, we queue the work and process it asynchronously with guaranteed delivery.

**"Why Redis?"**
> Redis provides atomic list operations like BLMOVE that let us move a job between queues in a single unbreakable step. This prevents job loss even if the worker crashes.

**"How do you handle failures?"**
> Exponential backoff retries — each retry waits longer (2s, 4s, 8s). If all retries fail, the job moves to a Dead Letter Queue for manual investigation and retry.

**"How is this different from just using a message broker like RabbitMQ?"**
> Conceptually similar, but this demonstrates understanding of the underlying mechanics. In production I'd use SQS or RabbitMQ, but knowing how reliability patterns work under the hood makes me better at debugging and configuring those systems.

**"How would you scale this?"**
> Run multiple worker instances. Since BLMOVE is atomic, multiple workers can safely compete for jobs without duplicates. Redis handles the coordination.
