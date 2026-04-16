# LeetCLI

A terminal-based LeetCode client written in Java. Browse, solve, and submit problems without leaving your terminal.

## Features

- **Full TUI editor** — syntax highlighting, auto-indent, bracket completion, text selection
- **Multi-language** — switch between Java, Python, C++, Go, JS, etc. with F8
- **Run & submit** — execute against test cases (F5) or submit (F6) directly
- **Auto-save** — code saves to `solutions/` automatically; reloads on next open
- **Interactive problem browser** — filter and search problems from the terminal

## Quick Start

**Requires Java 20+ and Maven.**

```bash
git clone https://github.com/Mystic-77/leetcli.git
cd leetcli
mvn clean package -q
```

Add the repo folder to your PATH, then:

```bash
# Windows
leetcli solve two-sum

# Linux/macOS
./leetcli.sh solve two-sum
```

### Docker

```bash
docker compose run leetcli login
docker compose run leetcli solve two-sum
```

## Commands

| Command | Description |
|---------|-------------|
| `login` | Authenticate via session cookie |
| `whoami` | Show profile and stats |
| `list` | Browse and filter problems |
| `solve` | Open the TUI editor for a problem |

## Key Bindings

| Key | Action |
|-----|--------|
| F5 | Run code |
| F6 | Submit solution |
| F7 | Load from file |
| F8 | Switch language |
| Ctrl+S | Save |
| Ctrl+Arrow | Switch panel |
| Shift+Arrow | Select text |
| Esc | Quit |
