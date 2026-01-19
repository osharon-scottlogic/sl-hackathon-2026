# Java Server Implementation Guide

This document specifies the concrete Java implementation of the server design defined in `server-design.md`, `commManager-design.md` and `gameEngine-design.md`. Refer to that document for high-level architecture, and messaging semantics. This guide focuses on Java-specific technology choices, and implementation notes.

## Tech Stack

- **Language**: Java 21+
- **Transport**: WebSocket (javax.websocket API)
- **Serialization**: Jackson (com.fasterxml.jackson.databind)
- **Concurrency**: Java built-in utilities (BlockingQueue, ConcurrentHashMap, Thread)
- **Server Container**: Tyrus or embedded WebSocket server
- **Logging**: SLF4J with Logback
- **Build**: Gradle
- **Testing**: JUnit 4, Mockito
- **No Framework**: Minimal dependencies; no Spring or other heavy frameworks

## Deployment Notes

**Local Development**:

- Run `gradle build` to compile and run all unit tests.
- Run `gradle run` to start the server on port 8080.
- Connect WebSocket client to `ws://localhost:8080/game`.