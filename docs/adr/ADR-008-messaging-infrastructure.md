# ADR-008: Messaging infrastructure

Status: Accepted (2026-11-23)

## Context

ADR-003 decides that every service has an outbox and an inbox. It does not say where the code lives, how the poller fails, how consumers fail, who declares the topics and how the correlation id travels.

`v0.3.0` builds this in inventory-service and order-service. payment-service follows in `v0.4.0`.

## Decision

### One copy per service

Each service has its own copy of the outbox and inbox code, in the package `messaging`. There is no shared module: ADR-005 limits the project to five modules, and `common-events` has no Spring code. The copies are small.

A fix in one copy is applied to the others in the same milestone. This decision is reviewed when a copy differs from another one or when a fourth service needs the code.

### Outbox

- The writer needs a transaction and throws without one.
- A row holds the topic, the key (the order id) and the envelope as JSON, written by `MessageCodec` (ADR-006).
- The poller runs every 500 ms in one transaction. It selects up to 100 unpublished rows with `FOR UPDATE SKIP LOCKED`, ordered by `created_at`. For each row, in that order, it sends the record, waits up to 10 seconds for the acknowledgement and marks the row as published.
- When a send fails, the poller stops. It commits the marks of the rows that were acknowledged. The failed row and the rows after it stay unpublished, so the order of messages for one key is kept and few messages are sent twice.
- A crash between the send and the commit can send a row twice. The inbox of the consumer drops the repeat (ADR-003).
- A cleanup deletes published rows older than 7 days, once an hour.
- The producer keeps a broker outage short: `max.block.ms` is 5 seconds, the request timeout 10 seconds and the delivery timeout 15 seconds.

### Inbox and handlers

- `processed_message` is as in ADR-003. The column `consumer` holds the consumer group.
- A handler runs the inbox insert, the state change and the outbox write in one transaction.
- When two transactions change the same row, the second one fails on the version. The whole transaction rolls back, the inbox row included, and the handler starts again in a new transaction. It tries at most 3 times. Then the exception goes to the error handling of the consumer.
- Two events of the same order can arrive at the same time from `inventory.events` and `payment.events`, because two listeners read them. This is why the attempts exist.

### Consumers

- Keys and values are Strings. `MessageCodec` decodes the value.
- The offset is committed by hand after the handler returns, so after the database commit.
- A failing handler is tried again 3 times, one second apart. Then the record goes to `<topic>.dlt` on the same partition.
- These records go to `<topic>.dlt` without a retry, because a retry cannot fix them: an unknown message type, an invalid message, a payload that the service does not handle and an unknown order id.
- A record without a value is an invalid message. When a record goes to the dead-letter topic, its offset is committed at once, so a restart does not send it again.
- A consumer group that is new reads a topic from the beginning.
- Each listener has one thread until `v0.6.0` measures whether more threads help.

### Topics

Each service declares in code every topic it reads or writes and the dead-letter topic of every topic it reads. A topic has 3 partitions and 1 replica. Two services can declare the same topic with the same settings.

| Service           | Reads                                | Writes                                   |
|:------------------|:-------------------------------------|:-----------------------------------------|
| inventory-service | `inventory.commands`                 | `inventory.events`                       |
| order-service     | `inventory.events`, `payment.events` | `inventory.commands`, `payment.commands` |
| payment-service   | `payment.commands`                   | `payment.events`                         |

### Correlation id

The first command of an order gets the id of the API request (ADR-007). A message that a handler writes keeps the correlation id of the message it handles.

While a handler runs, the consumer puts the id in the logging context as `correlationId` and removes it afterwards. The log pattern prints it.

### Redis gate

The gate of inventory-service stays outside the transaction of a handler. The handler asks it before the transaction and tells it the result after the commit (ADR-001).

When the hold was not granted, when the message was a repeat or when the handler fails, the handler releases the gate key of its order. A repeat cannot leave a stale key. The gate deletes only the key of the same order, so this costs at most one database call.

### Test profile

The `test` profile turns off the listeners, the outbox jobs and the creation of topics. A test that needs Kafka turns them on.

## Consequences

- A message can be sent twice. A consumer handles it once.
- Messages of one order keep their order inside one topic. Between two topics there is no order. The saga ignores a message that does not fit the state of the order and logs a warning.
- A record in a dead-letter topic waits for a person. Nothing reads these topics and there is no replay. Listed in the Known limitations.
- Rows of the inbox are not deleted in `v0.3.0`. **Planned:** delete rows older than the retention of the topics, because older messages cannot be delivered again.
- A message waits up to about 500 ms in the outbox for each hop.
- With more than one instance of a service, two pollers can send the rows of one key out of order. The project runs one instance of each service.
- The same code exists once per service.

## Rejected alternatives

- **A shared messaging module.** It adds a Spring module and ties the services to one version of the code. ADR-005 limits the number of modules.
- **Kafka transactions instead of an outbox.** They do not include the database commit, so the state change and the message can still disagree.
- **Commit the offset before the handler.** A crash after the commit loses the message.
- **Retry forever.** A message that always fails would block its partition.
