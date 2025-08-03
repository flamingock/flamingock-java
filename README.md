<p align="center">
  <img src="misc/logo-with-text.png" width="420px" alt="Flamingock logo" />
</p>


<h3 align="center">Auditable, versioned changes across distributed systems.</h3>
<p align="center">Evolve queues, DBs, APIs, configs, resources and more — governed, auditable, executed at startup in lockstep.</p>

<p align="center"><small><a href="https://github.com/flamingock/mongock-legacy?tab=readme-ov-file#%EF%B8%8F-mongock-is-deprecated">Coming from Mongock?</a></small></p>
<br />

<p align="center">
  <a href="https://central.sonatype.com/search?q=io.flamingock">
    <img src="https://img.shields.io/maven-central/v/io.flamingock/flamingock-core" alt="Maven Version" />
  </a>
  <a href="https://github.com/flamingock/flamingock-project/actions/workflows/build-master.yml">
    <img src="https://github.com/flamingock/flamingock-project/actions/workflows/build-master.yml/badge.svg" alt="Build" />
  </a>
  <a href="https://github.com/flamingock/flamingock-project/actions/workflows/release.yml">
    <img src="https://github.com/flamingock/flamingock-project/actions/workflows/release.yml/badge.svg" alt="Release" />
  </a>
  <a href="LICENSE.md">
    <img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="Licence" />
  </a>
</p>

**Flamingock** is an open-source engine for the governed and synchronized evolution of distributed systems — and the foundation of a broader Change-as-Code platform.

With Flamingock, your application and the systems it interacts with — including databases, message queues, feature flags, configurations, APIs, and cloud resources — evolve together, in lockstep.

**All changes are versioned, auditable**, and executed as part of the same deployment lifecycle.

No more post-deploy scripts or manual tweaks: every change is declared alongside your code and applied safely during application startup.

<br />

## 📦 Editions
Flamingock is open source at its core, and powers three editions:

- **Community Edition**: Free and self-managed. Use your own audit store (e.g., MongoDB, DynamoDB). Ideal for basic change tracking.
- **Cloud Edition**: Fully managed SaaS with a built-in audit store, dashboard, observability, governance, and premium features.
- **Self-Hosted Edition**: Same advanced capabilities as Cloud, but deployable in your own infrastructure.

> For more information about the editions, please feel free to ask us at <a href="mailto:support@flamingock.io">support@flamingock.io</a>.

<br />

## 💡 Introducing Change-as-Code (CaC)

**Automate changes. Version changes. Control changes.**

Flamingock is built around the principle of **Change-as-Code (CaC)** — the idea that **every change to your system’s behavior** (whether it's a schema update, config toggle, or infrastructure change) should be authored, versioned, and reviewed like application code.

This enables true **lockstep evolution** between your application and the systems it relies on — ensuring everything stays compatible, consistent, and in sync across environments.

No more fragile scripts or untracked console changes. With CaC:

- All changes live in your VCS and follow strict ordering
- Executions are automated and recorded in a centralized audit log
- Rollbacks and multi-environment consistency become first-class citizens

> Just like Infrastructure-as-Code reshaped provisioning, **Change-as-Code is redefining how systems evolve** — and Flamingock brings that principle to life.

<br />

## 🚀 Getting Started

- Read the [getting started tutorial](https://docs.flamingock.io/docs/getting-started/get-started).
- Explore real-world usage in the [examples repo](https://github.com/mongock/flamingock-examples).

<br />

## 🔑 Key Features

- **Change-as-Code (CaC)**: Treat changes to databases, queues, APIs, or configs as code — versioned, executable, and auditable.
- **Unified system evolution**: Orchestrate changes across your full stack: event schemas, feature flags, databases, S3, SaaS APIs, and more.
- **Programmatic or declarative**: Write changes in Java/Kotlin or define them in YAML using official or custom templates.
- **Startup-Time synchronization**: Apply versioned ChangeUnits when your app starts — keeping deployments consistent and safe.
- **Audit logging & rollback**: Every change is recorded externally; rollback logic is built into each ChangeUnit.
- **Multi-stage workflows**: Organize and execute your changes in coordinated, stage-based flows across instances.
- **Native GraalVM support**: Compatible with native image builds for fast startup and low memory usage.
- **Cloud-ready, OSS-Core**: Use locally, self-host, or plug into our managed Cloud — all powered by the same open source core.

<br />

## 🤝 Contributing

We welcome contributions from the community!  
If you'd like to report a bug, suggest a feature, or open a pull request, check out our [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

<br />

## 📢 Get Involved

⭐ Star the project to show support
- Report issues in the [issue tracker](https://github.com/mongock/flamingock-project/issues)
- Join the conversation in [GitHub discussions](https://github.com/mongock/flamingock-project/discussions)

<br />

## 📜 License

Flamingock is open source under the [Apache License 2.0](LICENSE.md).


