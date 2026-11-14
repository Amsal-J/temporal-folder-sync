# Temporal Folder Sync

Temporal (Java SDK) app that polls folder1 on an interval and copies
new files (unique by filename) into folder2. Containerized using Docker.

## Quick start (Docker)

Requires only [Docker Desktop](https://www.docker.com/products/docker-desktop/).

```bash
git clone https://github.com/YOUR_USERNAME/temporal-folder-sync.git
cd temporal-folder-sync
docker compose up --build
```

- Drop files into `folder1/`. They're copied to `folder2/` on the next poll (every 60s).
- Temporal Web UI: http://localhost:8233
- Stop everything: `Ctrl+C`, then `docker compose down`

## Running locally without Docker

Requires Java 21, Maven, and the Temporal CLI. Each terminal command will require its own terminal window to be run on (3 total terminal windows).

Download links for the required programs to run locally:
- **Java 21**: [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21) (or [Oracle JDK 21](https://www.oracle.com/java technologies/downloads/#java21))
- **Maven**: [Download](https://maven.apache.org/download.cgi) · [Install guide](https://maven.apache.org/install.html)
- **Temporal CLI**: [Install guide](https://docs.temporal.io/cli/setup-cli) (macOS: `brew install temporal`)

Running the project locally:

1. `temporal server start-dev --db-filename ~/temporal.db` to start the server, will continue indefinitely
2. `mvn compile exec:java -Dexec.mainClass="foldersync.FolderSyncWorker"` to start the worker, will continue indefinitely
3. `mvn compile exec:java -Dexec.mainClass="foldersync.FolderSyncStarter"` to start the workflow, will stop once workflow has started

To stop the execution of program being run locally:
1. `Ctrl+C` the worker tab.
2. Run the command `temporal workflow terminate --workflow-id folder-sync --reason "stopping sync"` to stop the workflow.
3. Then you may close down the Temporal Web UI Server by pressing `Ctrl+C` on that terminal window.

## Tests

Run `mvn test` in a terminal window.