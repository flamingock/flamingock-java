# 🔐 Security Policy

This policy applies to the **Flamingock Community Edition and open-source client library**.  
For customers using the Cloud Edition, security issues are handled through our internal support and SLA channels.

## 🛠️ Supported Versions

As of the first release (v1.0.0), only the **latest stable version** of the client library is officially supported for security fixes.

Older versions may continue to work, but we **do not commit to backporting fixes** unless otherwise stated.

> We’ll update this section once long-term support (LTS) versions are defined.

---

## 🧾 Reporting a Vulnerability

We take security seriously and appreciate responsible disclosures.

If you discover a security issue, please report it via one of the following:

- **GitHub Security Advisories** (preferred): [Submit a report](https://github.com/mongock/flamingock-project/security/advisories)
- **Email**: Send details to [`development@flamingock.io`](mailto:development@flamingock.io)

Please **do not create public GitHub issues** for security-related topics.

---

## 🧪 What to Include in a Report

To help us respond quickly and accurately, include the following:

- A clear summary of the vulnerability
- A proof-of-concept (code samples required; optional reproduction video)
- A description of the impact and what the vulnerability can access
- Optionally, an estimated [CVSS 3.1 score](https://www.first.org/cvss/)

We aim to acknowledge valid reports within **72 hours** and fix confirmed issues as soon as possible, depending on complexity and severity.

---

## 🧯 Disclosure & Patch Process

Once a vulnerability is confirmed:

1. A fix will be developed and tested internally
2. A patch will be published in a new release
3. A public security advisory will be issued
4. Detailed disclosure may follow after a short delay to allow users to upgrade

If a CVE is warranted, we will request and publish one through GitHub's CVE issuance system.

---

## 🪪 CVE and Public Credit

- **CVEs:** We may issue CVEs for confirmed vulnerabilities, depending on severity and scope.
- **Credit:** With your permission, we are happy to acknowledge researchers or reporters publicly (e.g., changelog, advisory, or blog post).

---

## 📌 Additional Notes

- This policy applies **only** to the open-source client library.
- Vulnerabilities affecting the Cloud Edition are handled privately with customers under appropriate support agreements.
- Flamingock does **not** currently offer bounties or paid rewards for vulnerability reports.

Thank you for helping us keep Flamingock safe for everyone!
