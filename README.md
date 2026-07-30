# Payment Replay Tool

A production-grade Java 8 command-line utility for extracting, sanitizing, and replaying payment messages from production log files to IBM MQ in UAT environments.

## Application Overview

The Payment Replay Tool provides two main functionalities:

1. **filter-mask** - Reads production application log files, extracts specific MQ queue records, masks sensitive information inside XML payloads, maps production bank identifiers to UAT equivalents, and generates sanitized output files.

2. **replay** - Reads sanitized output files and replays XML messages into IBM MQ with time-based scheduling and rate limiting.

## Architecture

### Design Principles

- **Command Pattern** - Extensible CLI with pluggable commands
- **Strategy Pattern** - Configurable masking algorithms without code changes
- **Streaming I/O** - Line-by-line processing supports multi-GB log files
- **Manual Dependency Injection** - Lightweight constructor injection, no framework overhead
- **Fail-safe processing** - Individual record failures don't halt the pipeline

### Package Structure

```
com.payment.replay
├── PaymentReplayApplication     # Entry point, DI bootstrap
├── command/                     # CLI commands (filter-mask, replay)
├── config/                      # YAML configuration loading
├── parser/                      # Log file scanning and parsing
├── masking/                     # XML field masking strategies
├── mapping/                     # Bank BIC mapping and queue resolution
├── replay/                      # Time grouping and replay orchestration
├── mq/                          # IBM MQ connection and publishing
├── logging/                     # Metrics collection and error reporting
├── model/                       # Domain objects (LogRecord, ReplayMessage, etc.)
└── exception/                   # Custom exception hierarchy
```

### Processing Pipeline

#### filter-mask

```
Log Files → Scanner → Stream Parser → Filter → Mask XML → Map BIC → Write Output
                                         ↓
                              Queue Pattern + Bank List
```

#### replay

```
Sanitized Files → Reader → Time Grouper → Rate Limiter → MQ Publisher
                                                              ↓
                                               Site 1 MQ / Site 2 MQ
```

## Installation

### Prerequisites

- Java 8 (JDK 1.8+)
- Gradle 7.x (or use the included wrapper)
- IBM MQ Client libraries (included via Gradle dependency)

### Build

```bash
# Build the application
gradle clean build

# Create the fat JAR with all dependencies
gradle fatJar

# The executable JAR is at:
# build/libs/payment-replay-tool-all.jar
```

### Verify Build

```bash
gradle test
```

## Configuration

All configuration is externalized to YAML files. By default, the application reads from the classpath (`src/main/resources/`). To override, pass `-Dconfig.dir=/path/to/config` at runtime.

### application.yaml

Main application configuration including output directory, MQ settings, replay parameters, and metrics.

```yaml
output:
  directory: ./output

replay:
  groupingIntervalSeconds: 60
  maxMessagesPerSecond: 250
  inputDirectory: ./output

mq:
  site1:
    queueManager: QM_SITE1
    host: mq-site1.example.com
    port: 1414
    channel: SVRCONN.SITE1
    retryCount: 3
    retryDelayMs: 5000
  site2:
    queueManager: QM_SITE2
    host: mq-site2.example.com
    port: 1414
    channel: SVRCONN.SITE2
    retryCount: 3
    retryDelayMs: 5000
```

### bank-list.yaml

Defines which bank BICs to process. Records from banks NOT in this list are ignored.

```yaml
banks:
  - bic: DBSSSGSG
    name: DBS Bank
    country: SG
  - bic: OCBCSGSG
    name: OCBC Bank
    country: SG
```

### bank-mapping.yaml

Maps production BIC codes to UAT equivalents. Applied to both XML content and queue names.

```yaml
banks:
  - productionBic: DBSSSGSG
    uatBic: DBSSSGS0
  - productionBic: OCBCSGSG
    uatBic: OCBCUTS0
```

### mask-fields.yaml

Defines XML fields to mask and their masking strategy. Add new fields here without any code changes.

```yaml
fields:
  - path: "DbtrAcct/Id/Othr/Id"
    strategy: KEEP_LAST_N
    parameters:
      n: 4
      maskChar: "*"
  - path: "Cdtr/Nm"
    strategy: FULL_MASK
    parameters:
      maskChar: "X"
```

#### Supported Masking Strategies

| Strategy | Description | Example |
|----------|-------------|---------|
| `FULL_MASK` | Replace all characters | `John Smith` → `XXXXXXXXXX` |
| `KEEP_FIRST_N` | Keep first N chars visible | `1234567890` → `1234******` |
| `KEEP_LAST_N` | Keep last N chars visible | `1234567890` → `******7890` |
| `CUSTOM_PATTERN` | Regex-based replacement | Configurable pattern/replacement |

## IBM MQ Setup

### Queue Configuration

The tool expects queues with the naming pattern:
```
<BANK_BIC>_REQUEST.TO.G3_<SITE_NUMBER>
```

Examples:
- `DBSSSGS0_REQUEST.TO.G3_1` (Site 1)
- `OCBCUTS0_REQUEST.TO.G3_2` (Site 2)

### Connection Properties

| Property | Description |
|----------|-------------|
| `queueManager` | Queue Manager name |
| `host` | MQ server hostname |
| `port` | MQ listener port (default: 1414) |
| `channel` | Server connection channel |
| `username` | Authentication user (optional) |
| `password` | Authentication password (optional) |
| `retryCount` | Number of connection retry attempts |
| `retryDelayMs` | Base delay between retries (exponential backoff) |

### Connection Behavior

- Connections are created on first use and cached per site
- Exponential backoff on connection failures (base delay * 2^attempt)
- Automatic reconnection on broken connections during message send
- Thread-safe connection management

## Command Execution

### filter-mask

Extracts, masks, and sanitizes production log files:

```bash
java -jar payment-replay-tool-all.jar filter-mask /path/to/logs

# With external config directory
java -Dconfig.dir=/etc/payment-replay -jar payment-replay-tool-all.jar filter-mask /data/prod-logs/20260728
```

**Input structure:**
```
/path/to/logs/
├── sw1/
│   └── raw-202607280001.log
├── sw2/
│   └── raw-202607280001.log
├── sw3/
└── sw4/
```

**Output:** Sanitized files in the configured output directory preserving the same folder structure and filenames.

### replay

Replays sanitized messages to IBM MQ with time-based scheduling:

```bash
java -jar payment-replay-tool-all.jar replay /path/to/sanitized-output

# With external config
java -Dconfig.dir=/etc/payment-replay -jar payment-replay-tool-all.jar replay ./output
```

**Replay behavior:**
- Messages are grouped by time interval (default: 60 seconds)
- Within each batch, messages are sent respecting rate limits (default: 250/sec)
- Site routing is automatic based on queue name (G3_1 → Site 1 MQ, G3_2 → Site 2 MQ)

## Log Format

The application processes log files with the following format:

```
<datetime>,mq,<direction>,<bank_bic>,<switch>,<msg_type>,<valid>,<instr_id>,<msg_id>...<queue_name>...<xml_payload>
```

**Example:**
```
2026-07-28T10:01:15.123Z,mq,OUT,DBSSSGSG,sw1,pacs.008,valid,INSTR001,MSG001...DBSSSGSG_REQUEST.TO.G3_1...<Document>...</Document>
```

Fields separated by commas for metadata, with `...` separating queue name and XML payload (which may contain commas).

## Output Files

### Logs

| File | Content |
|------|---------|
| `logs/application.log` | All application messages |
| `logs/error.log` | Errors only |
| `logs/metrics.log` | Processing statistics |

### Error Reports

Generated as CSV at `reports/error-report-yyyyMMdd.csv`:

```csv
Timestamp,RecordID,ErrorType,ErrorDescription,SourceFile,LineNumber
2026-07-28 10:01:17.789,MSG003,BANK_MAPPING_ERROR,No UAT mapping for BIC: UNKNOWN,raw-001.log,5
```

### Metrics Output

Printed to console and written to `metrics.log`:

```
=== Processing Summary ===
  Files processed:       4
  Records scanned:       15000
  Records matched:       12500
  Records masked:        12500
  Records written:       12498
  Bank mapping failures: 2
  Duration:              4s 231ms
  Throughput:            2954.4 records/sec
```

## Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| `ConfigurationException: Configuration file not found` | Missing YAML file | Ensure all YAML files exist in classpath or config directory |
| `MQ connection failed: RC=2009` | Connection broken | Check MQ server availability, network connectivity |
| `MQ connection failed: RC=2035` | Auth failure | Verify username/password in application.yaml |
| `No log files found` | Wrong input path | Verify input directory has sw1-sw4 subfolders with .log files |
| `No UAT mapping found for BIC` | Missing bank mapping | Add the BIC to bank-mapping.yaml |
| `OutOfMemoryError` | N/A (streaming) | Increase heap if processing metadata accumulates: `-Xmx512m` |

### Debug Mode

Enable debug logging by modifying `log4j2.xml`:

```xml
<Logger name="com.payment.replay" level="DEBUG" additivity="false">
```

### MQ Connectivity Test

To verify MQ connectivity independently:

```bash
# Check if MQ port is accessible
nc -zv mq-site1.example.com 1414

# Test with IBM MQ sample programs if available
amqsputc QUEUE_NAME QM_SITE1
```

## Project Structure

```
payment-replay-tool/
├── build.gradle                 # Build configuration
├── settings.gradle              # Project settings
├── gradle/wrapper/              # Gradle wrapper
├── src/
│   ├── main/
│   │   ├── java/com/payment/replay/
│   │   │   ├── PaymentReplayApplication.java
│   │   │   ├── command/         # CLI commands
│   │   │   ├── config/          # Configuration classes
│   │   │   ├── exception/       # Custom exceptions
│   │   │   ├── logging/         # Metrics and error reporting
│   │   │   ├── mapping/         # Bank BIC mapping
│   │   │   ├── masking/         # XML masking strategies
│   │   │   ├── model/           # Domain models
│   │   │   ├── mq/             # IBM MQ integration
│   │   │   ├── parser/          # Log file parsing
│   │   │   └── replay/          # Time-based replay
│   │   └── resources/
│   │       ├── application.yaml
│   │       ├── bank-list.yaml
│   │       ├── bank-mapping.yaml
│   │       ├── mask-fields.yaml
│   │       └── log4j2.xml
│   └── test/java/               # Unit tests
├── samples/
│   ├── input/                   # Sample input log files
│   ├── output/                  # Expected sanitized output
│   └── error-report-20260728.csv
└── README.md
```

## Future Enhancements

- **Parallel file processing** - Process multiple log files concurrently with thread pool
- **Dry-run mode** - Validate without sending to MQ for testing configuration changes
- **Real-time replay** - Replay messages matching original timestamps in real time
- **Web dashboard** - Lightweight HTTP endpoint for monitoring replay progress
- **Kafka support** - Alternative message broker for replay target
- **Compression** - Support reading gzipped log files directly
- **Checkpoint/resume** - Resume processing after interruption from last checkpoint
- **Configurable retry per record** - Retry individual failed messages N times before moving on
- **AWS Secrets Manager integration** - Load MQ credentials from AWS Secrets Manager
- **Docker packaging** - Containerized deployment with pre-built image
