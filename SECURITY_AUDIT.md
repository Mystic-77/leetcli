# Security Audit Report: LeetCLI Java Codebase

**Date:** 2026-03-29
**Scope:** `/Users/soumikbhatta/Projects/leetcli/src/main/java/com/leetcli/`
**Files Reviewed:** 18 Java files

---

## Executive Summary

The LeetCLI codebase has **3 CRITICAL** and **4 HIGH** severity security vulnerabilities. The most severe issue is the plaintext storage of authentication credentials (session cookies and CSRF tokens) in a local JSON config file without encryption. Additionally, there are insufficient input validation issues and path traversal risks in file I/O operations.

**Risk Level:** HIGH
**Immediate Action Required:** YES

---

## CRITICAL Issues

### 1. Plaintext Credential Storage in ConfigManager

**File:** `/Users/soumikbhatta/Projects/leetcli/src/main/java/com/leetcli/config/ConfigManager.java`
**Lines:** 31-32, 41, 49
**Severity:** CRITICAL

**Issue:** Session cookies (`LEETCODE_SESSION`) and CSRF tokens are stored in plaintext in `~/.leetcli/config.json`. An attacker with filesystem access can read the file and hijack the user's LeetCode session.

**Evidence:**
```java
// Line 31-32: Storing secrets as plaintext
public void set(String key, String value) {
    config.put(key, value);
}

// Line 41: Writing to disk unencrypted
Files.writeString(CONFIG_FILE, GSON.toJson(config));
```

**Impact:**
- Session hijacking: Anyone with access to `~/.leetcli/config.json` can impersonate the user
- No protection against disk forensics or malware reading config files
- Default file permissions on most Unix systems make the file world-readable if umask is loose

**Fix:**
1. Encrypt sensitive values before storage using `javax.crypto.Cipher` with AES
2. Use a key derivation function (KDF) like PBKDF2 or Argon2 for the encryption key
3. Store only an encrypted blob; decrypt on load
4. Set restrictive file permissions (0600) on the config file

**Alternative:** Use OS credential storage (macOS Keychain, Linux Secret Service, Windows Credential Manager) via libraries like `java-keyring`.

---

### 2. Insecure Cookie Transmission in HTTP Headers

**File:** `/Users/soumikbhatta/Projects/leetcli/src/main/java/com/leetcli/api/LeetCodeClient.java`
**Lines:** 320-322
**Severity:** CRITICAL

**Issue:** Session cookies and CSRF tokens are included in plaintext in HTTP request headers without any protection. If an HTTPS connection is compromised (e.g., via a MITM attack with a rogue CA), or if the client is misconfigured, credentials are exposed over the wire.

**Evidence:**
```java
// Line 320-322: Sending auth headers with no HTTPS guarantee
if (session != null && csrfToken != null) {
    builder.header("Cookie", "LEETCODE_SESSION=" + session + "; csrftoken=" + csrfToken)
           .header("X-Csrftoken", csrfToken);
}
```

**Impact:**
- MITM attack can intercept credentials if HTTPS certificate validation is bypassed
- No evidence of certificate pinning or HTTP Public Key Pinning (HPKP)
- OkHttpClient doesn't enforce HTTPS by default

**Fix:**
1. Implement certificate pinning for `leetcode.com` using OkHttpClient's `CertificatePinner`
2. Validate HTTPS connections explicitly; fail if certificate validation fails
3. Use SameSite cookie flags if applicable to the API
4. Add `Strict-Transport-Security` header enforcement on client side

**Code Example:**
```java
CertificatePinner pinner = new CertificatePinner.Builder()
    .add("leetcode.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    .build();

OkHttpClient httpClient = new OkHttpClient.Builder()
    .certificatePinner(pinner)
    .build();
```

---

### 3. Path Traversal Vulnerability in File I/O

**File:** `/Users/soumikbhatta/Projects/leetcli/src/main/java/com/leetcli/tui/MainTUI.java`
**Lines:** 323-326, 340-344, 353-356, 369-373
**Severity:** CRITICAL

**Issue:** The problem slug (from LeetCode API response) is used directly in file paths without sanitization. A malicious API response with a crafted slug like `../../../../../../etc/passwd` could write files outside the `solutions/` directory.

**Evidence:**
```java
// Line 324-326: No validation of problem.getTitleSlug()
String slug = problem.getTitleSlug().replace("-", "_");
String ext = SyntaxHighlighter.getFileExtension(currentLang);
Path file = Path.of(System.getProperty("user.dir"), "solutions", slug + ext);

// Attacker-controlled slug: "../../etc/passwd"
// Resolves to: /current/dir/../../etc/passwd (path traversal)
```

**Impact:**
- Write arbitrary files on the user's system
- Overwrite sensitive files
- Potential for privilege escalation if the CLI is run with elevated privileges
- Malicious LeetCode API response (via MITM or API compromise) could exploit this

**Fix:**
1. Validate slug to contain only alphanumeric, underscore, and hyphen
2. Use `Path.normalize()` and verify the path stays within `solutions/`
3. Use a whitelist approach for safe characters

**Code Example:**
```java
private Path getSafeSolutionFile(String slug, String ext) {
    // Validate slug
    if (!slug.matches("^[a-zA-Z0-9_-]+$")) {
        throw new IllegalArgumentException("Invalid slug: " + slug);
    }

    Path dir = Path.of(System.getProperty("user.dir"), "solutions");
    Path file = dir.resolve(slug + ext).normalize();

    // Verify path is under solutions dir
    if (!file.startsWith(dir.normalize())) {
        throw new IllegalArgumentException("Path traversal detected");
    }

    return file;
}
```

---

## HIGH Issues

### 4. Insufficient Input Validation on User-Supplied Difficulty Filter

**File:** `/Users/soumikbhatta/Projects/leetcli/src/main/java/com/leetcli/commands/ListCommand.java`
**Lines:** 28-29, 93-94
**Severity:** HIGH

**Issue:** The `difficulty` parameter is passed directly to the GraphQL query after only a `.toUpperCase()` call. No validation that it's one of the allowed values (EASY, MEDIUM, HARD). A malicious actor could inject unexpected values into the GraphQL query.

**Evidence:**
```java
// Line 93-94: Directly converting user input to uppercase without validation
if (difficulty != null && !difficulty.isBlank()) {
    filters.put("difficulty", difficulty.toUpperCase());
}
```

**Impact:**
- GraphQL query injection (if the API doesn't validate)
- Unexpected API behavior or information disclosure
- Denial of service if invalid values cause API errors

**Fix:**
1. Whitelist allowed values

```java
private static final Set<String> VALID_DIFFICULTIES =
    Set.of("EASY", "MEDIUM", "HARD");

if (difficulty != null && !difficulty.isBlank()) {
    String upper = difficulty.toUpperCase();
    if (!VALID_DIFFICULTIES.contains(upper)) {
        throw new IllegalArgumentException("Invalid difficulty: " + difficulty);
    }
    filters.put("difficulty", upper);
}
```

---

### 5. No HTTPS Enforcement

**File:** `/Users/soumikbhatta/Projects/leetcli/src/main/java/com/leetcli/api/LeetCodeClient.java`
**Line:** 24
**Severity:** HIGH

**Issue:** While the BASE_URL uses HTTPS, there is no explicit enforcement that all connections must use HTTPS. The OkHttpClient doesn't validate or enforce secure connections.

**Evidence:**
```java
// Line 24: HTTPS is hardcoded, but not enforced
private static final String BASE_URL = "https://leetcode.com";
```

**Impact:**
- If the URL is ever modified (e.g., via code injection or misconfiguration), HTTP could be used
- No protection against downgrade attacks
- No HSTS header handling

**Fix:**
1. Add runtime validation to ensure HTTPS-only connections
2. Reject any non-HTTPS URLs at the client initialization

```java
private final OkHttpClient httpClient;

public LeetCodeClient(ConfigManager configManager) {
    this.configManager = configManager;

    // Enforce HTTPS only
    if (!BASE_URL.startsWith("https://")) {
        throw new IllegalArgumentException("BASE_URL must use HTTPS");
    }

    this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();
}
```

---

### 6. No Server Certificate Validation

**File:** `/Users/soumikbhatta/Projects/leetcli/src/main/java/com/leetcli/api/LeetCodeClient.java`
**Lines:** 32-39
**Severity:** HIGH

**Issue:** The OkHttpClient is configured without any explicit certificate validation pinning or custom trust manager. It relies on the system's default CA store, which is vulnerable to MITM attacks if a rogue CA certificate is installed.

**Impact:**
- MITM attacks can intercept all API traffic including authentication tokens
- No protection against compromised or rogue CAs
- Credentials sent over the wire can be captured

**Fix:** (See CRITICAL Issue #2 for certificate pinning implementation)

---

### 7. No Input Validation on Search Keyword

**File:** `/Users/soumikbhatta/Projects/leetcli/src/main/java/com/leetcli/commands/ListCommand.java`
**Lines:** 31-32, 96-97
**Severity:** MEDIUM (downgraded from HIGH)

**Issue:** The `search` parameter is passed to the GraphQL query without validation. While less dangerous than difficulty (because it's a substring search), it could still cause unexpected behavior.

**Evidence:**
```java
// Line 96-97: No validation of search keyword
if (searchKeyword != null && !searchKeyword.isBlank()) {
    filters.put("searchKeywords", searchKeyword);
}
```

**Fix:**
1. Validate length (max 100 characters)
2. Sanitize special characters or use parameterized GraphQL variables (already being done, so risk is lower)

---

## MEDIUM Issues

### 8. Unvalidated Problem Reference in SolveCommand

**File:** `/Users/soumikbhatta/Projects/leetcli/src/main/java/com/leetcli/commands/SolveCommand.java`
**Lines:** 39-50, 57-59
**Severity:** MEDIUM

**Issue:** The `problemRef` parameter is used to fetch a problem from the API. While the numeric ID is validated with a regex, there's no upper/lower bound checking. An attacker could pass extremely large numbers, causing DoS via excessive API calls.

**Evidence:**
```java
// Line 39: Regex only checks for digits, not range
if (problemRef.matches("\\d+")) {
    int id = Integer.parseInt(problemRef);  // Could be Integer.MAX_VALUE
    int skip = Math.max(0, id - 5);
    problems = client.listProblems(20, skip, null, null);
}
```

**Impact:**
- Resource exhaustion: Very large IDs result in massive skip offsets
- DoS against LeetCode API or the user's rate limits
- Unnecessary network traffic

**Fix:**
```java
if (problemRef.matches("\\d+")) {
    int id = Integer.parseInt(problemRef);
    if (id < 1 || id > 10000) {  // Assuming max ~10k problems
        System.err.println("Problem ID out of valid range (1-10000)\n");
        return;
    }
    // ... rest of logic
}
```

---

### 9. Weak Error Message Handling (Information Disclosure)

**File:** `/Users/soumikbhatta/Projects/leetcli/src/main/java/com/leetcli/api/LeetCodeClient.java`
**Lines:** 298-299
**Severity:** MEDIUM

**Issue:** Exception messages are included in responses. While this is a CLI (not an API), overly detailed error messages could expose internal API details or library information to the user's terminal history.

**Evidence:**
```java
// Line 298-299: Exposes raw API error details
throw new IOException("LeetCode API returned HTTP " + response.code()
        + ": " + response.message());
```

**Impact:**
- Potential information disclosure if terminal history is captured
- Stack traces exposed in some error paths (line 99 in SolveCommand)

**Fix:**
```java
if (!response.isSuccessful()) {
    String genericMsg = "LeetCode API returned HTTP " + response.code();
    // Log detailed message internally if needed
    throw new IOException(genericMsg);
}
```

---

## SUMMARY TABLE

| Issue | Severity | File | Line(s) | Status |
|-------|----------|------|---------|--------|
| Plaintext credential storage | CRITICAL | ConfigManager.java | 31-32, 41, 49 | NOT FIXED |
| Insecure cookie transmission | CRITICAL | LeetCodeClient.java | 320-322 | NOT FIXED |
| Path traversal in file I/O | CRITICAL | MainTUI.java | 323-326, 340-344 | NOT FIXED |
| No difficulty input validation | HIGH | ListCommand.java | 93-94 | NOT FIXED |
| No HTTPS enforcement | HIGH | LeetCodeClient.java | 24 | NOT FIXED |
| No certificate validation | HIGH | LeetCodeClient.java | 32-39 | NOT FIXED |
| No search keyword validation | MEDIUM | ListCommand.java | 96-97 | NOT FIXED |
| Unvalidated problem ID range | MEDIUM | SolveCommand.java | 39-50 | NOT FIXED |
| Weak error handling | MEDIUM | LeetCodeClient.java | 298-299 | NOT FIXED |

---

## Remediation Priority

1. **CRITICAL (Fix immediately before any release):**
   - Encrypt credentials in ConfigManager
   - Implement certificate pinning in LeetCodeClient
   - Sanitize file paths in MainTUI

2. **HIGH (Fix before next commit):**
   - Add difficulty/search keyword validation
   - Enforce HTTPS explicitly

3. **MEDIUM (Fix in next sprint):**
   - Add problem ID range validation
   - Improve error message handling
   - Add rate limiting

---

## OWASP Top 10 Mapping

- **A02:2021 – Cryptographic Failures:** Plaintext credential storage, no HTTPS enforcement
- **A03:2021 – Injection:** Limited GraphQL injection risk due to parameterized variables, but input validation needed
- **A04:2021 – Insecure Design:** No threat modeling; path traversal vulnerability
- **A01:2021 – Broken Access Control:** No certificate pinning; session hijacking possible

---

## Recommendations

1. **Immediate:** Encrypt credentials at rest using AES-256
2. **Short-term:** Implement certificate pinning and input validation
3. **Long-term:**
   - Add integration tests for security controls
   - Use OWASP Dependency-Check to scan dependencies
   - Implement a security testing pipeline
   - Consider using a secrets manager (Vault, AWS Secrets Manager)

---

**Report Generated:** 2026-03-29 by Security Reviewer
