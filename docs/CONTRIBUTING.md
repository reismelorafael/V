# Contributing Guidelines

## Diretrizes de Contribuição

---

<div align="center">

**Vectras VM - Contribution Guide**

*Version 1.0.0 | January 2026*

</div>

---

## Table of Contents / Índice

1. [Introduction](#1-introduction)
2. [Code of Conduct](#2-code-of-conduct)
3. [Getting Started](#3-getting-started)
4. [Development Environment](#4-development-environment)
5. [Contribution Process](#5-contribution-process)
6. [Coding Standards](#6-coding-standards)
7. [Testing Guidelines](#7-testing-guidelines)
8. [Documentation Guidelines](#8-documentation-guidelines)
9. [Issue Reporting](#9-issue-reporting)
10. [Pull Request Process](#10-pull-request-process)
11. [Release Process](#11-release-process)
12. [Recognition](#12-recognition)

---

## 1. Introduction

### English

Thank you for your interest in contributing to Vectras VM! This document provides guidelines for contributing to the project, whether through code, documentation, bug reports, or feature suggestions.

Vectras VM is an open-source project licensed under the GNU General Public License v2.0. By contributing, you agree that your contributions will be licensed under the same terms.

### Português

Obrigado pelo seu interesse em contribuir com o Vectras VM! Este documento fornece diretrizes para contribuir com o projeto, seja através de código, documentação, relatórios de bugs ou sugestões de funcionalidades.

O Vectras VM é um projeto open-source licenciado sob a GNU General Public License v2.0. Ao contribuir, você concorda que suas contribuições serão licenciadas sob os mesmos termos.

---

## 2. Code of Conduct

### Our Pledge

We as members, contributors, and leaders pledge to make participation in our community a harassment-free experience for everyone, regardless of age, body size, visible or invisible disability, ethnicity, sex characteristics, gender identity and expression, level of experience, education, socio-economic status, nationality, personal appearance, race, religion, or sexual identity and orientation.

### Expected Behavior

- Use welcoming and inclusive language
- Be respectful of differing viewpoints and experiences
- Gracefully accept constructive criticism
- Focus on what is best for the community
- Show empathy towards other community members

### Unacceptable Behavior

- Trolling, insulting/derogatory comments, and personal or political attacks
- Public or private harassment
- Publishing others' private information without explicit permission
- Other conduct which could reasonably be considered inappropriate

### Enforcement

Instances of abusive, harassing, or otherwise unacceptable behavior may be reported to the project team via GitHub issues or the community channels (Telegram/Discord). All complaints will be reviewed and investigated promptly and fairly.

---

## 3. Getting Started

### Ways to Contribute

| Contribution Type | Description | Skill Level |
|------------------|-------------|-------------|
| Bug Reports | Report issues you find | Beginner |
| Feature Requests | Suggest new features | Beginner |
| Documentation | Improve or translate docs | Beginner-Intermediate |
| Bug Fixes | Fix reported issues | Intermediate |
| Feature Development | Implement new features | Intermediate-Advanced |
| Code Review | Review pull requests | Intermediate-Advanced |
| Testing | Test pre-release versions | All levels |

### First-Time Contributors

If you're new to open source or this project, look for issues labeled:

- `good first issue` - Simple issues good for newcomers
- `help wanted` - Issues where help is needed
- `documentation` - Documentation improvements

---

## 4. Development Environment

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Android Studio | Hedgehog (2023.1.1)+ | Recommended IDE |
| JDK | 17+ | Required for Gradle |
| Android SDK | API 23+ | Min SDK level |
| Git | 2.x | Version control |
| Gradle | 8.x | Build system (wrapper included) |

### Repository Setup

```bash
# Clone the repository
git clone https://github.com/xoureldeen/Vectras-VM-Android.git
cd Vectras-VM-Android

# Create a feature branch
git checkout -b feature/your-feature-name

# Open in Android Studio
# File → Open → Select the cloned directory
```

### Building the Project

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew test

# Check code style
./gradlew lint
```

### Firebase Configuration

For builds that require Firebase (analytics, crashlytics):

1. Create a Firebase project at https://console.firebase.google.com/
2. Add your Android app with package name `com.vectras.vm`
3. Download `google-services.json` to the `app/` directory

For builds without Firebase, use the placeholder configuration documented in [FIREBASE.md](../app/FIREBASE.md).

---

## 5. Contribution Process

### Overview

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  1. Fork    │────▶│ 2. Branch   │────▶│ 3. Develop  │
│  Repository │     │  Creation   │     │  & Test     │
└─────────────┘     └─────────────┘     └──────┬──────┘
                                               │
┌─────────────┐     ┌─────────────┐     ┌──────▼──────┐
│ 6. Merged   │◀────│ 5. Review   │◀────│ 4. Pull     │
│  to Main    │     │  Process    │     │  Request    │
└─────────────┘     └─────────────┘     └─────────────┘
```

### Step-by-Step Guide

1. **Fork the Repository**
   - Navigate to https://github.com/xoureldeen/Vectras-VM-Android
   - Click the "Fork" button

2. **Clone Your Fork**
   ```bash
   git clone https://github.com/YOUR_USERNAME/Vectras-VM-Android.git
   cd Vectras-VM-Android
   git remote add upstream https://github.com/xoureldeen/Vectras-VM-Android.git
   ```

3. **Create a Branch**
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/bug-description
   ```

4. **Make Changes**
   - Follow the coding standards
   - Write tests for new functionality
   - Update documentation as needed

5. **Commit Changes**
   ```bash
   git add .
   git commit -m "feat: description of feature"
   # or
   git commit -m "fix: description of fix"
   ```

6. **Push and Create PR**
   ```bash
   git push origin feature/your-feature-name
   ```
   Then open a pull request on GitHub.

---

## 6. Coding Standards

### General Principles

- **Readability**: Code should be self-documenting where possible
- **Simplicity**: Prefer simple solutions over complex ones
- **Consistency**: Follow existing code style in the project
- **Maintainability**: Consider long-term maintenance implications
- **Accessibility**: Actionable UI elements (including FABs and icon-only controls) must always define an accessible `android:contentDescription`, even when a visible label exists; do not suppress lint checks for missing descriptions.

### Java Style Guide

```java
// Package names: lowercase
package com.vectras.vm.example;

// Class names: PascalCase
public class ExampleClass {
    
    // Constants: UPPER_SNAKE_CASE
    private static final int MAX_BUFFER_SIZE = 1024;
    
    // Fields: camelCase
    private String exampleField;
    
    // Methods: camelCase
    public void exampleMethod() {
        // Local variables: camelCase
        int localVariable = 0;
    }
}
```

### Kotlin Style Guide

```kotlin
// Follow Kotlin official coding conventions
// https://kotlinlang.org/docs/coding-conventions.html

class VectraExample {
    // Properties
    private val maxSize = 1024
    private var currentCount = 0
    
    // Functions
    fun processData(input: ByteArray): Int {
        // Use meaningful names
        val processedSize = input.size
        return processedSize
    }
    
    // Use data classes for simple data holders
    data class EventData(
        val type: String,
        val timestamp: Long
    )
}
```

### XML/Layout Standards

```xml
<!-- Use meaningful IDs -->
<Button
    android:id="@+id/btn_start_vm"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="@string/start_vm" />

<!-- Extract strings to strings.xml -->
<!-- Use dp for spacing, sp for text sizes -->
```

### Commit Message Convention

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `style`: Code style (formatting, missing semi-colons, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

**Examples:**
```
feat(vectra): add event priority system
fix(qemu): resolve memory leak in buffer allocation
docs(readme): update installation instructions
```

---

## 7. Testing Guidelines

### Test Types

| Type | Purpose | Location |
|------|---------|----------|
| Unit Tests | Test individual components | `app/src/test/` |
| Integration Tests | Test component interactions | `app/src/androidTest/` |
| UI Tests | Test user interface | `app/src/androidTest/` |

### Writing Tests

```kotlin
// Unit test example
class VectraStateTest {
    @Test
    fun `setFlag should enable bit at specified index`() {
        val state = VectraState()
        state.setFlag(100, true)
        assertTrue(state.getFlag(100))
    }
    
    @Test
    fun `getFlag should return false for unset flag`() {
        val state = VectraState()
        assertFalse(state.getFlag(500))
    }
}
```

### Running Tests

```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.vectras.vm.vectra.VectraStateTest"

# Run with coverage
./gradlew testDebugUnitTestCoverage
```

---

## 8. Documentation Guidelines

### Documentation Types

| Type | Format | Location |
|------|--------|----------|
| API Documentation | KDoc/Javadoc | Source files |
| Architecture Docs | Markdown | `docs/` |
| User Guides | Markdown | `docs/` or wiki |
| README | Markdown | Root directory |

### Writing Documentation

```kotlin
/**
 * Represents a priority-based event in the Vectra Core framework.
 *
 * Events are processed by the [VectraCycle] in priority order,
 * with higher priority events processed first.
 *
 * @property type The type of event
 * @property priority Higher values indicate higher priority
 * @property timestamp Creation timestamp in nanoseconds
 * @property payload Optional binary payload data
 *
 * @see VectraCycle
 * @see VectraEventBus
 * @since 1.0.0
 */
data class VectraEvent(
    val type: EventType,
    val priority: Int,
    val timestamp: Long = System.nanoTime(),
    val payload: ByteArray? = null
)
```

### Markdown Style

- Use ATX-style headers (`# Header`)
- Use fenced code blocks with language specification
- Include table of contents for long documents
- Add cross-references to related documents

---

## 9. Issue Reporting

### Bug Reports

When reporting a bug, include:

```markdown
## Bug Description
[Clear description of the bug]

## Steps to Reproduce
1. Step one
2. Step two
3. Step three

## Expected Behavior
[What should happen]

## Actual Behavior
[What actually happens]

## Environment
- Device: [e.g., Samsung Galaxy S21]
- Android Version: [e.g., Android 13]
- Vectras VM Version: [e.g., 3.5.1]
- QEMU Bootstrap Version: [e.g., 9.2.2]

## Additional Context
[Screenshots, logs, etc.]
```

### Feature Requests

```markdown
## Feature Description
[Clear description of the feature]

## Use Case
[Why is this feature needed?]

## Proposed Solution
[How might this be implemented?]

## Alternatives Considered
[Other approaches you've thought of]
```

---

## 10. Pull Request Process

### PR Checklist

Before submitting a PR, ensure:

- [ ] Code compiles without errors
- [ ] All existing tests pass
- [ ] New tests added for new functionality
- [ ] Code follows project style guidelines
- [ ] Documentation updated if needed
- [ ] Commit messages follow convention
- [ ] PR description explains changes

### PR Template

```markdown
## Description
[Brief description of changes]

## Type of Change
- [ ] Bug fix (non-breaking change that fixes an issue)
- [ ] New feature (non-breaking change that adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing performed

## Checklist
- [ ] My code follows the style guidelines
- [ ] I have performed a self-review
- [ ] I have commented my code where necessary
- [ ] I have updated the documentation
- [ ] My changes generate no new warnings
- [ ] New and existing tests pass locally
```

### Review Process

1. **Initial Review**: Maintainer checks PR scope and quality
2. **Code Review**: Detailed review of code changes
3. **Testing**: Automated tests run via CI/CD
4. **Approval**: At least one maintainer approval required
5. **Merge**: Squash and merge to main branch

---

## 11. Release Process

### Version Numbering

Vectras VM follows [Semantic Versioning](https://semver.org/):

```
MAJOR.MINOR.PATCH

MAJOR: Breaking changes
MINOR: New features (backward compatible)
PATCH: Bug fixes (backward compatible)
```

### Release Channels

| Channel | Stability | Update Frequency |
|---------|-----------|------------------|
| Stable | Production-ready | Major/minor releases |
| Beta | Testing | Per-commit builds |

### Release Checklist

- [ ] All tests passing
- [ ] Changelog updated
- [ ] Version numbers updated
- [ ] Documentation current
- [ ] APK signed with release key
- [ ] Release notes prepared

---

## 12. Recognition

### Contributors

All contributors are recognized in:

- GitHub Contributors page
- Release notes (for significant contributions)
- README acknowledgments (for major contributions)

### Contributor License Agreement

By contributing to Vectras VM, you agree that:

1. Your contributions are your original work
2. You have the right to submit the contributions
3. Your contributions are licensed under GNU GPL v2.0
4. You grant the project maintainers permission to use your contributions

---

## Community Channels

| Platform | Link | Purpose |
|----------|------|---------|
| GitHub Issues | [Issues](https://github.com/xoureldeen/Vectras-VM-Android/issues) | Bug reports, feature requests |
| GitHub Discussions | [Discussions](https://github.com/xoureldeen/Vectras-VM-Android/discussions) | General discussions |
| Telegram | [Vectras OS](https://t.me/vectras_os) | Community chat |
| Discord | [Vectras VM](https://discord.gg/t8TACrKSk7) | Community chat |

---

## Document Cross-References

| Document | Relevance |
|----------|-----------|
| [README.md](../README.md) | Project overview |
| [LICENSE](../LICENSE) | GNU GPL v2.0 license |
| [ARCHITECTURE.md](ARCHITECTURE.md) | System architecture |
| [GLOSSARY.md](GLOSSARY.md) | Technical terminology |
| [LEGAL_AND_LICENSES.md](LEGAL_AND_LICENSES.md) | Licensing, attribution, compliance |
| [DOCUMENTATION_STANDARDS.md](DOCUMENTATION_STANDARDS.md) | Documentation standards |

---

*© 2024-2026 Vectras VM Development Team. Licensed under GNU GPL v2.0*

*Document Version: 1.0.0 | Last Updated: January 2026*
