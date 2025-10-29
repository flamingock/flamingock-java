<p align="center">
  <img src="misc/logo-with-text.png" width="420px" alt="Flamingock logo" />
</p>

<h3 align="center">Auditable, versioned changes across distributed systems.</h3>
<p align="center">Evolve queues, DBs, APIs, configs, resources and more — governed, auditable, applied at startup in lockstep.</p>

<p align="center"><small>
  Coming from <a href="https://github.com/flamingock/mongock-legacy#%EF%B8%8F-mongock-is-deprecated">Mongock</a>?  
  Learn about the transition and why Flamingock is its evolution.
</small></p>

<p align="center">
  <a href="https://central.sonatype.com/search?q=io.flamingock">
    <img src="https://img.shields.io/maven-central/v/io.flamingock/flamingock-core" alt="Maven Version" />
  </a>
  <a href="https://github.com/flamingock/flamingock-java/actions/workflows/release.yml">
    <img src="https://github.com/flamingock/flamingock-java/actions/workflows/release.yml/badge.svg" alt="Release" />
  </a>
  <a href="https://github.com/flamingock/flamingock-java/actions/workflows/build-master.yml">
    <img src="https://github.com/flamingock/flamingock-java/actions/workflows/build-master.yml/badge.svg" alt="Build Master" />
  </a>
  <a href="https://github.com/flamingock/flamingock-java/actions/workflows/build.yml">
    <img src="https://github.com/flamingock/flamingock-java/actions/workflows/build.yml/badge.svg" alt="Build Dev" />
  </a>
  <a href="LICENSE.md">
    <img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License" />
  </a>
</p>

**Flamingock** brings *Change-as-Code (CaC)* to your entire stack.  
It applies **versioned, auditable changes** to the external systems your application depends on — such as schemas, message brokers, databases, APIs, cloud services, and any other external system your application needs.

It runs **inside your application** (or via the **CLI**) — not in CI/CD — ensuring that every environment starts with the exact data, schema, and configuration it needs to run correctly.  
No manual scripts. No drift. No surprises.

<br />

### 🧩 In plain English for developers

When you deploy an app, it often depends on things outside your code —  
like a database schema, a queue, or a few configuration values.  
Normally, keeping all of those in sync across environments means extra scripts, manual fixes, or hoping nothing drifts.

With **Flamingock**, your app and its changes travel together.  
Every update runs in order and safely, as part of the app startup — no hidden steps or guesswork.  
If something goes wrong, Flamingock either recovers safely or stops before anything breaks.

The result: each deployment behaves like a single, consistent package —  
your code and all its required changes evolve together, predictably and with peace of mind.

<br />

## 💡 What Flamingock manages
Flamingock focuses on **application-level changes** that your code requires to run safely:

- Database schemas and reference data
- Message queues and schemas
- APIs and configuration values
- Cloud service resources directly tied to your app
- Configuration changes (feature flags, secrets, runtime values)

### What Flamingock does *not* manage
Flamingock is **not an infrastructure-as-code tool**. It does not provision servers, clusters, or networks — those belong in Terraform, Pulumi, or similar. Instead, Flamingock **complements them by handling the runtime changes your application depends on**.

<br />

## 📦 Editions

Flamingock is open source at its core and powers three editions:

- **Community Edition (Open Source)** — Free and self-managed. Use your own audit store (e.g., MongoDB, DynamoDB). Ideal for basic change tracking.
- **Cloud Edition (SaaS)** — Fully managed SaaS with a built-in audit store, dashboard, observability, governance, and premium features.
- **Self-Hosted Edition** — Same enterprise-grade features as Cloud, deployable in your own infrastructure.

> For inquiries about the Cloud or Self-Hosted editions, contact us at <a href="mailto:support@flamingock.io">support@flamingock.io</a>.

<br />

## 💡 Introducing Change-as-Code (CaC)

**Automate changes. Version changes. Control changes.**

Flamingock is built around the principle of **Change-as-Code (CaC)** — the idea that **every change to your system’s behavior** (whether it's a schema update, config toggle, or database change) should be authored, versioned, and reviewed like application code.

This enables true **lockstep evolution between your application and the systems it relies on** — ensuring everything stays compatible, consistent, and in sync across environments.

No more fragile scripts or untracked console changes. With CaC:

- All changes live in your VCS and follow strict ordering
- Executions are automated and recorded in a centralized audit log
- Rollbacks and multi-environment consistency become first-class citizens

> Just like Infrastructure-as-Code reshaped provisioning, **Change-as-Code is redefining how systems evolve** — and Flamingock brings that principle to life.

<br />

## 🚀 Getting started

```kotlin
implementation(platform("io.flamingock:flamingock-community-bom:$latestVersion"))
implementation("io.flamingock:flamingock-community")

annotationProcessor("io.flamingock:flamingock-processor:$latestVersion")
```

- Read the [Quick Start guide](https://docs.flamingock.io/get-started/quick-start)
- Explore [examples](https://github.com/flamingock/flamingock-java-examples)

<br />

## 🔑 Key features

- **Unified system evolution**: Orchestrate changes across your full stack — event schemas, feature flags, databases, S3, SaaS APIs, and more.
- **Change-as-Code (CaC)**: Treat changes to databases, queues, APIs, or configs as code — versioned, executable, and auditable.
- **Programmatic or declarative**: Write changes in Java/Kotlin or define them in YAML using official or custom templates.
- **Startup-Time synchronization**: Apply versioned Changes when your app starts — ensuring environments stay consistent and safe.
- **Safety by default**: When Flamingock cannot guarantee a safe outcome, it stops and requires manual intervention. No silent data corruption. Built-in rollback, and advanced safe recovery are available in the Cloud Edition.
- **Audit logging**: Every change is recorded externally, with full traceability (what, when, by whom, and result). 
- **Cloud-ready, OSS-Core**: Use locally, self-host, or connect to our managed Cloud — all powered by the same open source foundation.
- **Native GraalVM support**: Fully compatible with native image builds for fast startup and low memory usage.
- **Coordinated multi-environment workflows**: Manage complex change sequences across multiple environments or application instances.

<br />

## 📘 Learn more

- [Official documentation](https://docs.flamingock.io)
- [Quick Start guide](https://docs.flamingock.io/get-started/quick-start)
- [Core concepts](https://docs.flamingock.io/get-started/core-concepts)
- [Why Change-as-Code matters](https://docs.flamingock.io/get-started/Change-as-Code)

<br />

## 🤝 Contributing

We welcome contributions from the community!  
If you'd like to report a bug, suggest a feature, or open a pull request, check out our [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

<br />

## 📢 Get involved

⭐ Star the project to show support
- Report issues in the [issue tracker](https://github.com/flamingock/flamingock-java/issues)
- Join the conversation in [GitHub discussions](https://github.com/flamingock/flamingock-java/discussions)

<br />

## 📜 License

Flamingock is open source under the [Apache License 2.0](LICENSE.md).

