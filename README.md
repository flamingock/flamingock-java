<p align="center">
  <img src="misc/logo-with-text.png" width="420px" alt="Flamingock logo" />
</p>

<h3 align="center">Auditable, versioned changes across distributed systems.</h3>
<p align="center">Evolve queues, DBs, APIs, configs, resources and more — governed, auditable, applied at startup in lockstep.</p>

<p align="center">
  <small>
    Coming from <a href="https://github.com/flamingock/mongock-legacy#%EF%B8%8F-mongock-is-deprecated">Mongock</a>?  
    Learn about the transition and why Flamingock is its evolution.
  </small>
</p>

<p align="center">
  <a href="https://central.sonatype.com/search?q=io.flamingock">
    <img src="https://img.shields.io/maven-central/v/io.flamingock/flamingock-core" alt="Maven Version" />
  </a>
  <a href="https://github.com/flamingock/flamingock-java/actions/workflows/build.yml">
    <img src="https://github.com/flamingock/flamingock-java/actions/workflows/build.yml/badge.svg" alt="Build" />
  </a>
  <a href="LICENSE.md">
    <img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License" />
  </a>
</p>


<p align="center">
  <small>
    <a href="https://docs.flamingock.io/get-started/quick-start">Quick start in minutes</a>
  </small>
</p>




## 🧩 In plain English for developers

When you deploy an app, it usually depends on things outside your code —
a database schema, a queue, a permission, a config value.

Keeping all of that in sync across environments often means:
manual scripts, tribal knowledge, and hoping nothing breaks.

With **Flamingock**, your application and its required changes evolve together.

Changes are:
- applied in a strict, deterministic order
- executed automatically at startup
- recorded in an external audit log

If Flamingock cannot guarantee a safe outcome, it **stops** —
preventing silent corruption or partial execution.

**The result:** every deployment behaves as a single, consistent unit —
code and system evolution, moving forward together.

---

## What is Flamingock?

**Flamingock** brings **Change-as-Code (CaC)** to your application stack.

It applies **versioned, auditable changes** to the external systems your application depends on —
such as schemas, databases, message brokers, APIs, cloud resources, and configuration —
**as part of the application lifecycle itself**.

Flamingock runs **inside your application** (or via a CLI), not in CI/CD, ensuring every environment starts in a **known, consistent state**.

**No manual scripts.  
No hidden steps.  
No drift.**

---

## 💡 What Flamingock manages

Modern applications rarely fail because of bad code.
They fail because **external systems evolve out of sync** —
schemas, queues, permissions, configs, and cloud resources changed manually, inconsistently, and without auditability.

Flamingock closes that gap by managing **application-level system changes**, including:

- Database schemas and reference data
- Message queues, topics, and schemas
- APIs and configuration values
- Cloud resources directly tied to application behavior
- Feature flags, permissions, and runtime policies

### What Flamingock does *not* manage

Flamingock is **not Infrastructure-as-Code**.

It does not provision servers, clusters, or networks —
those belong in tools like Terraform or Pulumi.

**Infrastructure-as-Code defines where systems run.**  
**Change-as-Code defines how systems evolve.**

Flamingock complements IaC by managing the evolution layer IaC does not cover.

---

## 💡 Change-as-Code (CaC)

**Automate changes.  
Version changes.  
Control evolution.**

Change-as-Code means treating every system change
that affects application behavior as executable, versioned code.

With CaC:
- changes live in your VCS
- execution order is explicit and deterministic
- every run is auditable
- environments stay aligned over time

Just as Infrastructure-as-Code reshaped provisioning,
**Change-as-Code reshapes how systems evolve**.

## 🚀 Getting started

```kotlin
plugins {
    id("io.flamingock") version "[VERSION]"
}

flamingock {
    community()
}
```

- Read the [Quick Start guide](https://docs.flamingock.io/get-started/quick-start)
- Explore [examples](https://github.com/flamingock/flamingock-java-examples)

<br />

## 🔑 Key features

- **Unified system evolution**  
  Orchestrate changes across your full stack — databases, event schemas, queues, cloud resources, configs, and policies — as a single, ordered evolution.

- **Change-as-Code (CaC)**  
  Define every system change as code: versioned, reviewable, executable, and auditable.

- **Programmatic or declarative**  
  Author changes in Java/Kotlin or define them declaratively using YAML with official or custom templates.

- **Startup-time synchronization**  
  Apply all required changes when the application starts, ensuring every environment is consistent before serving traffic.

- **Safety by default**  
  Flamingock refuses to proceed when it cannot guarantee a safe outcome.  
  No silent failures. No partial execution.  
  Manual intervention is required instead of hidden corruption.

- **Audit logging**  
  Every execution is recorded externally with full traceability: what ran, when, by whom, and with what result.

- **OSS core, enterprise-ready**  
  A production-grade foundation you own and control.

- **Native GraalVM support**  
  Fully compatible with native image builds for fast startup and low memory usage.

- **Coordinated multi-environment execution**  
  Safely manage change execution across multiple environments and application instances.

---

## 📘 Learn more

- [Official documentation](https://docs.flamingock.io)
- [Core concepts](https://docs.flamingock.io/get-started/core-concepts)
- [Why Change-as-Code matters](https://docs.flamingock.io/get-started/Change-as-Code)
- [Examples repository](https://github.com/flamingock/flamingock-java-examples)

---

## 🤝 Contributing

Flamingock is built in the open.

If you’d like to report a bug, suggest an improvement, or contribute code,
please see [CONTRIBUTING.md](CONTRIBUTING.md).

---

## 📢 Get involved

⭐ Star the project to show support  
- Report issues via the [issue tracker](https://github.com/flamingock/flamingock-java/issues)  
- Join discussions on [GitHub Discussions](https://github.com/flamingock/flamingock-java/discussions)

---

## 📜 License

Flamingock is open source under the [Apache License 2.0](LICENSE.md).
