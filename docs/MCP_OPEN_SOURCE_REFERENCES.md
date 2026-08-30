# MCP open-source references

AzureQL uses these projects as design references. Source is not copied wholesale; AzureQL keeps its own domain, security and lifecycle boundaries.

## Primary references

### modelcontextprotocol/kotlin-sdk

<https://github.com/modelcontextprotocol/kotlin-sdk>

Use for:

- MCP types and lifecycle;
- Streamable HTTP transport;
- server/client integration tests;
- compatibility and conformance behavior.

AzureQL pins 0.10.0 during Phase 0 because it matches the project's Kotlin 2.2.21 compiler. The protocol adapter remains replaceable.

License: Apache-2.0 for new SDK contributions, with existing code covered as described by the upstream repository.

### danielealbano/android-remote-control-mcp

<https://github.com/danielealbano/android-remote-control-mcp>

Useful proven Android patterns:

- user-visible `specialUse` foreground service;
- explicit start/stop and bounded server shutdown;
- embedded Ktor/Netty server;
- separation between Ktor transport, authentication and tool registration;
- optional TLS and authorization layers.

Not adopted:

- boot auto-start;
- public tunnels;
- broad device-control permissions;
- arbitrary device or accessibility tools.

Those capabilities conflict with AzureQL's loopback-first, least-privilege design.

License: MIT. AzureQL borrows lifecycle and boundary ideas, not source code.

### stixez/droid-mcp

<https://github.com/stixez/droid-mcp>

Useful patterns:

- modular tool bundles;
- reusable foreground-service lifecycle;
- QR pairing and per-capability modules;
- optional audit and TLS modules.

The project currently targets an older MCP SDK baseline, so it is a lifecycle/modularity reference rather than AzureQL's protocol source of truth.

License: Apache-2.0. Its pairing and modularity ideas are candidates for later phases, after AzureQL defines its own threat model.

## Secondary references

### Mobile-MCP/Mobile-MCP

<https://github.com/Mobile-MCP/Mobile-MCP>

This project focuses on Android inter-application discovery and Intent-based communication for on-device LLMs. It may be useful later for local Android Agent integration, but it does not replace the Streamable HTTP endpoint required by desktop Agents.

### Ktor server documentation

<https://ktor.io/docs/server-engines.html>

Use for embedded server lifecycle, engine configuration and shutdown behavior. AzureQL selected Netty for the first spike because a mature Android MCP implementation uses it successfully; the official SDK sample demonstrates the same Ktor application integration with CIO.

## License rule

Before copying any non-trivial implementation, confirm the source project's license and preserve required attribution. Architectural ideas and public APIs may be referenced, but AzureQL-specific code should remain independently implemented and tested.
