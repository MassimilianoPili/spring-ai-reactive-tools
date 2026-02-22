# Pubblicazione `spring-ai-reactive-tools` su Maven Central

## Contesto

La libreria `spring-ai-reactive-tools` e' funzionante e testata nel progetto SIMOGE-MCP (28 tool registrati, 20 reattivi + 8 sincroni). Ora la prepariamo per la pubblicazione su Maven Central, con:
- **Rename groupId**: `io.github.simoge` → `io.github.massimilianopili`
- **Rename package Java**: `io.github.simoge.ai.reactive` → `io.github.massimilianopili.ai.reactive`
- **ArtifactId invariato**: `spring-ai-reactive-tools`
- **Deploy manuale** via `mvn deploy` (no CI/CD)

---

## FASE 0 — Setup infrastruttura (manuale, una tantum)

### 0.1 Account Sonatype Central Portal
1. Andare su **https://central.sonatype.com**, login con GitHub (`massimilianopili`)
2. Menu **Namespaces** → **Add Namespace** → `io.github.massimilianopili`
3. Verifica: creare repo temporaneo pubblico su GitHub con il nome indicato dal portale (es. `OSSRH-XXXXXX`), poi cliccare Verify. Dopo verifica, eliminare il repo temp.

### 0.2 Token Central Portal
1. Central Portal → icona profilo → **View Account** → **Generate User Token**
2. Salvare `username` e `password` generati (servono per `settings.xml`)

### 0.3 Chiave GPG
```bash
gpg --full-generate-key          # RSA 4096, no scadenza
gpg --list-secret-keys --keyid-format long   # annotare KEY_ID
gpg --keyserver keyserver.ubuntu.com --send-keys KEY_ID
gpg --keyserver keys.openpgp.org --send-keys KEY_ID
```

### 0.4 Maven `settings.xml`
Creare `C:/Users/massimiliano.pili/.m2/settings.xml`:
```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>TOKEN_USERNAME</username>
      <password>TOKEN_PASSWORD</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>gpg-sign</id>
      <properties>
        <!-- <gpg.passphrase>LA_TUA_PASSPHRASE</gpg.passphrase> -->
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>gpg-sign</activeProfile>
  </activeProfiles>
</settings>
```

---

## FASE 1 — Rename package nella libreria

**Progetto**: `c:/NoCloud/Progetti/Vari/spring-ai-reactive-tools/`

### 1.1 Spostamento directory
```bash
# Main sources
mkdir -p src/main/java/io/github/massimilianopili/ai/reactive/{annotation,callback,config,provider,util}
mv src/main/java/io/github/simoge/ai/reactive/annotation/* src/main/java/io/github/massimilianopili/ai/reactive/annotation/
mv src/main/java/io/github/simoge/ai/reactive/callback/*    src/main/java/io/github/massimilianopili/ai/reactive/callback/
mv src/main/java/io/github/simoge/ai/reactive/config/*      src/main/java/io/github/massimilianopili/ai/reactive/config/
mv src/main/java/io/github/simoge/ai/reactive/provider/*    src/main/java/io/github/massimilianopili/ai/reactive/provider/
mv src/main/java/io/github/simoge/ai/reactive/util/*        src/main/java/io/github/massimilianopili/ai/reactive/util/
rm -rf src/main/java/io/github/simoge

# Test sources
mkdir -p src/test/java/io/github/massimilianopili/ai/reactive
mv src/test/java/io/github/simoge/ai/reactive/* src/test/java/io/github/massimilianopili/ai/reactive/
rm -rf src/test/java/io/github/simoge
```

### 1.2 Find-and-replace in tutti i 9 file Java
In **tutti** i file (8 main + 1 test), sostituire:
- `io.github.simoge.ai.reactive` → `io.github.massimilianopili.ai.reactive`

File interessati:
| File | Cosa cambia |
|------|------------|
| `annotation/ReactiveTool.java` | package |
| `annotation/EnableReactiveTools.java` | package + import config |
| `callback/ReactiveToolCallback.java` | package |
| `callback/ReactiveToolCallbackAdapter.java` | package + 2 import |
| `config/ReactiveToolAutoConfiguration.java` | package + 2 import |
| `config/ReactiveToolProperties.java` | package |
| `provider/ReactiveMethodToolCallbackProvider.java` | package + 2 import |
| `util/ReactiveToolSerializer.java` | package |
| `ReactiveToolCallbackAdapterTest.java` (test) | package + 3 import |

### 1.3 Aggiornare auto-config imports
File: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `io.github.simoge.ai.reactive.config.ReactiveToolAutoConfiguration`
- → `io.github.massimilianopili.ai.reactive.config.ReactiveToolAutoConfiguration`

---

## FASE 2 — Aggiornare `pom.xml` per Maven Central

File: `c:/NoCloud/Progetti/Vari/spring-ai-reactive-tools/pom.xml`

Modifiche chiave rispetto al pom attuale:
- **groupId**: `io.github.simoge` → `io.github.massimilianopili`
- **url**: → `https://github.com/massimilianopili/spring-ai-reactive-tools`
- **Aggiungere sezioni** `<developers>` e `<scm>` (obbligatorie per Central)
- **Aggiungere 4 plugin**:
  1. `maven-source-plugin` 3.3.1 — genera `-sources.jar`
  2. `maven-javadoc-plugin` 3.11.2 — genera `-javadoc.jar` (con `<doclint>none</doclint>`)
  3. `maven-gpg-plugin` 3.2.7 — firma GPG (con `pinentry-mode loopback` per Windows)
  4. `central-publishing-maven-plugin` 0.6.0 — upload su Central Portal (`autoPublish=true`, `waitUntil=published`)

---

## FASE 3 — GitHub repo + file progetto

```bash
cd "c:/NoCloud/Progetti/Vari/spring-ai-reactive-tools"
git init && git branch -M main
```
- Creare `.gitignore` (target/, .idea/, *.iml, etc.)
- Creare `LICENSE` (Apache 2.0)
- Opzionale: `README.md`
- Creare repo su GitHub: `gh repo create massimilianopili/spring-ai-reactive-tools --public --source=. --remote=origin`
- `git add . && git commit && git push -u origin main`

---

## FASE 4 — Aggiornare il progetto MCP (consumer)

**Progetto**: `c:/NoCloud/Progetti/Vari/mcp/`

### 4.1 `pom.xml` — cambiare groupId dipendenza
```xml
<!-- VECCHIO -->
<groupId>io.github.simoge</groupId>
<!-- NUOVO -->
<groupId>io.github.massimilianopili</groupId>
```

### 4.2 Aggiornare import in 6 file Java
Sostituzione identica in tutti e 6:
`io.github.simoge.ai.reactive.annotation.ReactiveTool` → `io.github.massimilianopili.ai.reactive.annotation.ReactiveTool`

File:
- `tools/ApiProxyTools.java`
- `tools/devops/DevOpsBoardTools.java`
- `tools/devops/DevOpsGitTools.java`
- `tools/devops/DevOpsPipelineTools.java`
- `tools/devops/DevOpsReleaseTools.java`
- `tools/devops/DevOpsWorkItemTools.java`

---

## FASE 5 — Build, test e deploy

### 5.1 Test locale
```bash
cd "c:/NoCloud/Progetti/Vari/spring-ai-reactive-tools"
mvn clean test                    # verifica test passano
mvn clean install                 # installa localmente con nuove coordinate

cd "c:/NoCloud/Progetti/Vari/mcp"
mvn clean compile                 # verifica MCP compila con nuove coordinate
mvn clean package -DskipTests     # ri-genera il jar MCP
```

### 5.2 Deploy su Maven Central
```bash
cd "c:/NoCloud/Progetti/Vari/spring-ai-reactive-tools"
mvn clean deploy
```
Questo: compila → testa → genera sources/javadoc jar → firma GPG → upload su Central Portal → attende pubblicazione.

### 5.3 Troubleshooting comuni
| Errore | Causa | Fix |
|--------|-------|-----|
| `401 Unauthorized` | Token errato in settings.xml | Rigenerare su Central Portal |
| `GPG signing failed` | Nessuna chiave o passphrase errata | `gpg --list-secret-keys` |
| `Validation failed: missing POM elements` | Manca name/scm/developers/license | Controllare pom.xml |
| `Namespace not verified` | Namespace non verificato | Completare verifica su Central Portal |

---

## FASE 6 — Verifica

1. **Central Portal**: https://central.sonatype.com → deployment "Published"
2. **Maven Central**: `https://repo1.maven.org/maven2/io/github/massimilianopili/spring-ai-reactive-tools/0.1.0/`
3. **Search**: https://search.maven.org → `g:io.github.massimilianopili` (puo' servire ~30 min)
4. **MCP server**: cancellare cache locale, rebuild MCP, verificare che scarichi da Central
```bash
rm -rf "$HOME/.m2/repository/io/github/massimilianopili/spring-ai-reactive-tools"
cd "c:/NoCloud/Progetti/Vari/mcp"
mvn clean compile    # scarica da Central
```

---

## Riepilogo file da modificare

| # | File | Progetto | Azione |
|---|------|----------|--------|
| 1 | `pom.xml` | Libreria | groupId + url + developers + scm + 4 plugin |
| 2 | 8 file Java main | Libreria | Rename package + spostamento directory |
| 3 | 1 file Java test | Libreria | Rename package + spostamento directory |
| 4 | `AutoConfiguration.imports` | Libreria | FQCN classe auto-config |
| 5 | `pom.xml` | MCP server | groupId dipendenza |
| 6 | 6 file Java | MCP server | Import statement |
| 7 | `~/.m2/settings.xml` | Globale | Creare da zero (token + GPG) |
