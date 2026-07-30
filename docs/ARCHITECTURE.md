# Payment Replay Tool
## Architecture & Functionality Overview

---

## 1. OBJECTIVE

### Problem Statement

Production payment logs contain real transaction data that cannot be directly used in UAT/testing environments due to:
- Sensitive personal information (account numbers, names, addresses)
- Production bank identifiers that don't exist in UAT
- Need to replay traffic patterns for performance/integration testing

### Solution

A command-line utility that:
1. **Extracts** relevant payment records from production logs
2. **Sanitizes** sensitive data using configurable masking
3. **Maps** production identifiers to UAT equivalents
4. **Replays** sanitized messages to IBM MQ preserving original timing

---

## 2. HIGH-LEVEL ARCHITECTURE

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        PAYMENT REPLAY TOOL                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   ┌──────────────────────┐         ┌──────────────────────────┐        │
│   │   COMMAND: filter-mask│         │   COMMAND: replay         │        │
│   │                      │         │                          │        │
│   │  Production Logs ──▶ │         │  Sanitized Files ──▶     │        │
│   │  Sanitized Output    │         │  IBM MQ (Site 1 & 2)     │        │
│   └──────────────────────┘         └──────────────────────────┘        │
│                                                                          │
├─────────────────────────────────────────────────────────────────────────┤
│                        SHARED SERVICES                                    │
│                                                                          │
│   ┌──────────┐  ┌──────────┐  ┌───────────┐  ┌──────────────┐         │
│   │  Config  │  │ Metrics  │  │  Error    │  │   Logging    │         │
│   │  Loader  │  │Collector │  │ Reporter  │  │  (SLF4J)     │         │
│   └──────────┘  └──────────┘  └───────────┘  └──────────────┘         │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 3. FUNCTIONALITY 1: filter-mask

### Purpose
Extract payment records from production logs, mask sensitive data, and map to UAT identifiers.

### Data Flow

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  SCAN       │    │  PARSE &    │    │  MASK       │    │  MAP        │    │  WRITE      │
│  Log Files  │───▶│  FILTER     │───▶│  XML Fields │───▶│  Bank BIC   │───▶│  Output     │
│             │    │             │    │             │    │             │    │             │
│ sw1/sw2/    │    │ Queue name  │    │ Account No  │    │ DBSSSGSG    │    │ Same dir    │
│ sw3/sw4     │    │ Bank BIC    │    │ Names       │    │  → DBSSSGS0 │    │ structure   │
│ raw-*.log   │    │ filter      │    │ Addresses   │    │             │    │             │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
                          │
                          ▼
                   ┌─────────────┐
                   │  FILTER     │
                   │  CRITERIA   │
                   │             │
                   │ Queue:      │
                   │ BANK_REQUEST│
                   │ .TO.G3_N   │
                   │             │
                   │ BIC in      │
                   │ bank-list   │
                   └─────────────┘
```

### Log Record Format

```
┌────────────────────────────────────────────────────────────────────────────────────┐
│ 2026-07-28T10:01:15.123Z,mq,OUT,DBSSSGSG,sw1,pacs.008,valid,INSTR001,MSG001       │
│ ├─ timestamp            │   │   │        │   │        │     │        │             │
│ ├─ type (always "mq")───┘   │   │        │   │        │     │        │             │
│ ├─ direction (IN/OUT)────────┘   │        │   │        │     │        │             │
│ ├─ bank BIC──────────────────────┘        │   │        │     │        │             │
│ ├─ switch name────────────────────────────┘   │        │     │        │             │
│ ├─ message type───────────────────────────────┘        │     │        │             │
│ ├─ valid flag──────────────────────────────────────────┘     │        │             │
│ ├─ instruction ID────────────────────────────────────────────┘        │             │
│ └─ message ID─────────────────────────────────────────────────────────┘             │
│                                                                                      │
│ ...DBSSSGSG_REQUEST.TO.G3_1...<Document xmlns="...">...</Document>                   │
│    ├─ queue name (between first ...)    ├─ XML payload (after second ...)            │
└────────────────────────────────────────────────────────────────────────────────────┘
```

### Masking Example

```
BEFORE (Production):
┌──────────────────────────────────────────────────┐
│ <DbtrAcct>                                       │
│   <Id><Othr><Id>1234567890123456</Id></Othr></Id>│  ← Account number
│ </DbtrAcct>                                      │
│ <Cdtr><Nm>Tan Wei Lin</Nm></Cdtr>                │  ← Customer name
│ <InstgAgt><BIC>DBSSSGSG</BIC></InstgAgt>         │  ← Production BIC
└──────────────────────────────────────────────────┘

AFTER (Sanitized):
┌──────────────────────────────────────────────────┐
│ <DbtrAcct>                                       │
│   <Id><Othr><Id>************3456</Id></Othr></Id>│  ← KEEP_LAST_4
│ </DbtrAcct>                                      │
│ <Cdtr><Nm>XXXXXXXXXXX</Nm></Cdtr>                │  ← FULL_MASK
│ <InstgAgt><BIC>DBSSSGS0</BIC></InstgAgt>         │  ← UAT BIC mapped
└──────────────────────────────────────────────────┘
```

---

## 4. FUNCTIONALITY 2: replay

### Purpose
Replay sanitized messages to IBM MQ with time-based scheduling matching original traffic patterns.

### Data Flow

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  READ       │    │  GROUP BY   │    │  RATE LIMIT │    │  PUBLISH    │
│  Sanitized  │───▶│  TIME       │───▶│  & SCHEDULE │───▶│  TO MQ      │
│  Files      │    │  INTERVAL   │    │             │    │             │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
                                                                │
                                                    ┌───────────┴───────────┐
                                                    ▼                       ▼
                                           ┌──────────────┐       ┌──────────────┐
                                           │   MQ SITE 1  │       │   MQ SITE 2  │
                                           │              │       │              │
                                           │ Queue:       │       │ Queue:       │
                                           │ BANK_REQUEST │       │ BANK_REQUEST │
                                           │ .TO.G3_1     │       │ .TO.G3_2     │
                                           └──────────────┘       └──────────────┘
```

### Time Grouping

```
Original timestamps from log:

10:01:00 ─┐
10:01:15 ─┤  Batch "10:01" (2 messages)
10:01:45 ─┘

10:02:01 ─┐
10:02:30 ─┤  Batch "10:02" (3 messages)
10:02:55 ─┘

10:03:30 ─── Batch "10:03" (1 message)

Each batch processed sequentially with rate limiting (max 250 msg/sec)
```

### MQ Site Routing

```
Queue Name                          Target
──────────────────────────────────  ──────────
DBSSSGS0_REQUEST.TO.G3_1    ──▶    MQ Site 1
OCBCUTS0_REQUEST.TO.G3_1    ──▶    MQ Site 1
DBSSSGS0_REQUEST.TO.G3_2    ──▶    MQ Site 2
OCBCUTS0_REQUEST.TO.G3_2    ──▶    MQ Site 2
```

---

## 5. KEY CONFIGURATIONS

### ★ Most Important: mask-fields.yaml

Controls what sensitive data is masked. **Add new fields without code changes.**

```yaml
fields:
  # Account Numbers - keep last 4 for traceability
  - path: "DbtrAcct/Id/Othr/Id"        # Debtor account
    strategy: KEEP_LAST_4
    parameters: { n: 4, maskChar: "*" }

  - path: "CdtrAcct/Id/Othr/Id"        # Creditor account
    strategy: KEEP_LAST_4
    parameters: { n: 4, maskChar: "*" }

  # Personal Names - full mask
  - path: "Cdtr/Nm"                     # Creditor name
    strategy: FULL_MASK
    parameters: { maskChar: "X" }

  - path: "Dbtr/Nm"                     # Debtor name
    strategy: FULL_MASK
    parameters: { maskChar: "X" }

  # NRIC / ID numbers
  - path: "Id/PrvtId/Othr/Id"
    strategy: KEEP_LAST_4
    parameters: { n: 4, maskChar: "*" }
```

### ★ Most Important: bank-mapping.yaml

Maps production BICs to UAT. **Must match your UAT environment setup.**

```yaml
banks:
  - productionBic: DBSSSGSG             # DBS Production
    uatBic: DBSSSGS0                    # DBS UAT

  - productionBic: OCBCSGSG             # OCBC Production
    uatBic: OCBCUTS0                    # OCBC UAT
```

### ★ Most Important: bank-list.yaml

Controls which banks' records are extracted. **Acts as a whitelist filter.**

```yaml
banks:
  - bic: DBSSSGSG
  - bic: OCBCSGSG
  # Add more banks as needed - records from unlisted banks are IGNORED
```

### ★ Critical: MQ Connection (application.yaml)

```yaml
mq:
  site1:
    queueManager: QM_SITE1             # ← Must match UAT QM
    host: mq-site1.uat.example.com     # ← UAT MQ hostname
    port: 1414
    channel: SVRCONN.SITE1             # ← Server connection channel
    retryCount: 3                      # ← Retry attempts on failure
    retryDelayMs: 5000                 # ← Base delay (exponential backoff)
  site2:
    queueManager: QM_SITE2
    host: mq-site2.uat.example.com
    port: 1414
    channel: SVRCONN.SITE2
```

### ★ Tuning: Replay Settings (application.yaml)

```yaml
replay:
  groupingIntervalSeconds: 60          # ← Group messages in 60s windows
  maxMessagesPerSecond: 250            # ← Rate limit to avoid MQ overload
```

---

## 6. EXECUTION FLOW

### Command: filter-mask

```bash
java -jar payment-replay-tool-all.jar filter-mask /data/prod-logs/20260728
```

```
┌──────────────────────────────────────────────────────────────────┐
│ INPUT:  /data/prod-logs/20260728/                                │
│         ├── sw1/raw-202607280001.log   (500MB)                   │
│         ├── sw1/raw-202607280002.log   (500MB)                   │
│         ├── sw2/raw-202607280001.log   (450MB)                   │
│         ├── sw3/raw-202607280001.log   (480MB)                   │
│         └── sw4/raw-202607280001.log   (520MB)                   │
│                                                                  │
│ PROCESSING:                                                      │
│  • Streams each file line by line (no full load to memory)       │
│  • Filters: queue = BANK_REQUEST.TO.G3_N AND bic in bank-list   │
│  • Masks: sensitive XML fields per mask-fields.yaml              │
│  • Maps: production BIC → UAT BIC per bank-mapping.yaml          │
│                                                                  │
│ OUTPUT: ./output/                                                │
│         ├── sw1/raw-202607280001.log   (sanitized)               │
│         ├── sw1/raw-202607280002.log   (sanitized)               │
│         ├── sw2/raw-202607280001.log   (sanitized)               │
│         ├── sw3/raw-202607280001.log   (sanitized)               │
│         └── sw4/raw-202607280001.log   (sanitized)               │
│                                                                  │
│ REPORTS:                                                         │
│  • logs/application.log    - processing details                  │
│  • logs/metrics.log        - statistics & throughput              │
│  • reports/error-report-20260728.csv  - failed records           │
└──────────────────────────────────────────────────────────────────┘
```

### Command: replay

```bash
java -jar payment-replay-tool-all.jar replay ./output
```

```
┌──────────────────────────────────────────────────────────────────┐
│ INPUT:  ./output/ (sanitized files from filter-mask)             │
│                                                                  │
│ PROCESSING:                                                      │
│  • Reads all sanitized records                                   │
│  • Groups by time: 10:01, 10:02, 10:03, ...                    │
│  • For each batch:                                               │
│    - Send messages to MQ (respecting 250/sec limit)             │
│    - Route to Site 1 or Site 2 based on queue name              │
│    - Retry on connection failures (exponential backoff)          │
│                                                                  │
│ OUTPUT:                                                          │
│  • Messages delivered to IBM MQ queues                           │
│  • Per-minute statistics:                                        │
│                                                                  │
│    Time       Read     Sent    Failed                            │
│    ─────────────────────────────────                             │
│    10:01      1200     1198       2                              │
│    10:02      1500     1500       0                              │
│    10:03       800      800       0                              │
│                                                                  │
│  • Throughput: ~2500 messages/sec                                │
└──────────────────────────────────────────────────────────────────┘
```

---

## 7. DESIGN PATTERNS USED

```
┌────────────────────┬────────────────────────────────────────────────┐
│ Pattern            │ Usage                                          │
├────────────────────┼────────────────────────────────────────────────┤
│ Command            │ CLI routing (filter-mask, replay)              │
│ Strategy           │ Masking algorithms (FullMask, KeepLastN, etc.) │
│ Builder            │ Immutable model construction                   │
│ Factory            │ MaskingStrategyFactory                         │
│ Observer           │ Metrics collection via callbacks               │
│ Template Method    │ File processing pipeline                       │
└────────────────────┴────────────────────────────────────────────────┘
```

---

## 8. ERROR HANDLING STRATEGY

```
                    Record Processing Error
                            │
                            ▼
                ┌───────────────────────┐
                │  Log error details    │
                │  (file, line, type)   │
                └───────────┬───────────┘
                            │
                            ▼
                ┌───────────────────────┐
                │  Write to CSV report  │
                │  (error-report.csv)   │
                └───────────┬───────────┘
                            │
                            ▼
                ┌───────────────────────┐
                │  Increment failure    │
                │  counter in metrics   │
                └───────────┬───────────┘
                            │
                            ▼
                ┌───────────────────────┐
                │  CONTINUE processing  │  ← Never halt for individual failures
                │  next record          │
                └───────────────────────┘
```

---

## 9. CONFIGURATION QUICK REFERENCE

| Config File | Purpose | When to Change |
|---|---|---|
| **bank-list.yaml** | Which banks to process | Adding/removing banks from scope |
| **bank-mapping.yaml** | Prod BIC → UAT BIC | UAT environment changes |
| **mask-fields.yaml** | What XML fields to mask | New sensitive field discovered |
| **application.yaml** (mq) | MQ connection details | New MQ environment or port change |
| **application.yaml** (replay) | Rate limiting & grouping | Performance tuning |
| **log4j2.xml** | Log levels and file paths | Debugging or changing log location |

---

## 10. SECURITY CONSIDERATIONS

```
┌────────────────────────────────────────────────────────────────────┐
│ ✓  No sensitive data in output files (masked)                      │
│ ✓  Production BICs replaced with UAT BICs                          │
│ ✓  MQ credentials in config (not hardcoded)                        │
│ ✓  Error reports don't contain original sensitive values            │
│ ✓  Streaming processing - no full file in memory                   │
│ ✓  All masking rules externalized (audit-friendly)                 │
└────────────────────────────────────────────────────────────────────┘
```

---

## 11. RUNNING THE TOOL

```bash
# Step 1: Build
gradle fatJar

# Step 2: Extract & Mask production logs
java -jar build/libs/payment-replay-tool-all.jar filter-mask /data/prod-logs/20260728

# Step 3: Review output (optional)
head -5 ./output/sw1/raw-202607280001.log

# Step 4: Replay to MQ
java -jar build/libs/payment-replay-tool-all.jar replay ./output

# With external config:
java -Dconfig.dir=/etc/payment-replay \
     -jar build/libs/payment-replay-tool-all.jar filter-mask /data/prod-logs
```
