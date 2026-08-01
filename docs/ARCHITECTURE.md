# Payment Replay Tool — Architecture & Functionality

## 1. OBJECTIVE

Extract payment records from production logs, sanitize sensitive data, and replay to IBM MQ in UAT. Supports **250 TPS** sustained throughput for full-day log processing.

## 2. LOG FORMAT

```
<datetime>,mq,<direction>,<bank_bic>,<switch>-MQ<siteNo>,<msg_type>,valid,<instr_id>,<msg_id>,iso20022,raw,<XML>
```

**No queue name in logs** — derived at processing time from BIC + site number.

## 3. DUAL-LEG OUTPUT

| Leg | Message Types | Output Suffix | Purpose |
|-----|--------------|---------------|---------|
| **Leg 1** | pacs.008, admn.005 | `_leg1.log` | Credit transfers & amendments |
| **Leg 3** | pacs.002 | `_leg3.log` | Payment status reports |

### filter-mask output structure:
```
output/
├── sw1/
│   ├── raw-20260728100123456789_leg1.log
│   └── raw-20260728100123456789_leg3.log
├── sw2/
│   ├── raw-20260728100156789012_leg1.log
│   └── ...
```

## 4. NAMESPACE-AWARE XML MASKING

Different banks use different XML namespace prefixes:
```xml
Bank A:  <ns3:DbtrAcct><ns3:Id><ns3:Othr><ns3:Id>1234567890</ns3:Id>...
Bank B:  <pacs:DbtrAcct><pacs:Id><pacs:Othr><pacs:Id>1234567890</pacs:Id>...
Bank C:  <DbtrAcct><Id><Othr><Id>1234567890</Id>...
```

All three match `mask-fields.yaml` path `DbtrAcct/Id/Othr/Id` because the masking engine **strips namespace prefixes** before comparing element local names.

## 5. PARALLEL PROCESSING

### filter-mask (configurable thread count)
```
┌─────────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│  Thread 1   │    │ Thread 2 │    │ Thread 3 │    │ Thread 4 │
│  sw1 file   │    │ sw2 file │    │ sw3 file │    │ sw4 file │
└──────┬──────┘    └─────┬────┘    └─────┬────┘    └─────┬────┘
       │                  │                │               │
       ▼                  ▼                ▼               ▼
┌───────────────────────────────────────────────────────────────┐
│          ConcurrentHashMap<String, BufferedWriter>             │
│     synchronized per-writer for thread-safe output            │
└───────────────────────────────────────────────────────────────┘
```

### replay (parallel legs)
```
┌─────────────────────────┐    ┌─────────────────────────┐
│   Thread: Leg 1 Replay  │    │   Thread: Leg 3 Replay  │
│   Rate: 175 msg/sec     │    │   Rate: 75 msg/sec      │
│   (70% of max)          │    │   (30% of max)          │
└────────────┬────────────┘    └────────────┬────────────┘
             │                               │
             ▼                               ▼
       ┌───────────┐                  ┌───────────┐
       │  MQ Site  │                  │  MQ Site  │
       │  1 or 2   │                  │  1 or 2   │
       └───────────┘                  └───────────┘
```

## 6. REPLAY CONFIGURATION

```yaml
replay:
  groupingIntervalSeconds: 60   # Time-window batching
  maxMessagesPerSecond: 250     # Rate limit (250 TPS)
  replayLegs: both              # Options: leg1 | leg3 | both
```

| Setting | Description |
|---------|-------------|
| `leg1` | Replay only _leg1 files |
| `leg3` | Replay only _leg3 files |
| `both` | Replay both in parallel threads (default) |

## 7. KEY CONFIGURATIONS

### ★ mask-fields.yaml (namespace-agnostic)
```yaml
fields:
  - path: "DbtrAcct/Id/Othr/Id"    # Works with any prefix: ns3:, pacs:, or none
    strategy: KEEP_LAST_4
  - path: "Cdtr/Nm"
    strategy: FULL_MASK
```

### ★ application.yaml — filterMask section
```yaml
filterMask:
  leg1FileSuffix: "_leg1"
  leg3FileSuffix: "_leg3"
  fileProcessingThreads: 4      # Parallel file processing
  writerQueueSize: 2000
```

### ★ application.yaml — replay section
```yaml
replay:
  groupingIntervalSeconds: 60
  maxMessagesPerSecond: 250
  replayLegs: both              # leg1 | leg3 | both
```

## 8. EXECUTION

```bash
# Extract + mask (produces _leg1 and _leg3 files in parallel)
java -jar payment-replay-tool-all.jar filter-mask /data/prod-logs/20260728

# Replay both legs concurrently
java -jar payment-replay-tool-all.jar replay ./output

# Replay only leg1
# (change application.yaml: replay.replayLegs: leg1)
java -jar payment-replay-tool-all.jar replay ./output
```

## 9. PERFORMANCE DESIGN (250 TPS / FULL DAY)

| Component | Strategy |
|-----------|----------|
| File I/O | BufferedReader streaming — no full-file memory load |
| Parallelism | ExecutorService thread pool (4 threads for filter-mask) |
| Masking | String-based scanning — no DOM/SAX overhead |
| Rate limiting | nanoTime-based per-second bucket limiter |
| MQ connections | Cached per-site, auto-reconnect with exponential backoff |
| Writers | ConcurrentHashMap with synchronized per-writer |
| Replay | Separate threads per leg, independent rate limiters |

Full-day at 250 TPS = ~21.6M records. With 4 parallel threads and streaming I/O, filter-mask processes at ~3000-5000 records/sec per thread. Replay sustains 250 msg/sec to MQ with proper rate limiting.
