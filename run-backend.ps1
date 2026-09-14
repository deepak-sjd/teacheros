# Run this from the project root: .\run-backend.ps1
# Sets Java 21 and the correct (non-conflicting) database connection
# for THIS terminal session only - never touches your system-wide
# settings or your other "portfolio" project.

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

$env:DB_URL = "jdbc:postgresql://localhost:5433/teacheros"
$env:DB_USERNAME = "teacheros"
$env:DB_PASSWORD = "teacheros"

# Object storage (MinIO locally) - localhost:9000 is the mapped host port,
# same pattern as DB_URL above. If you're running the backend itself
# inside docker-compose instead of via mvn, that path already overrides
# this to the internal "minio:9000" hostname - see docker-compose.yml.
$env:STORAGE_ENDPOINT = "http://localhost:9000"
$env:STORAGE_ACCESS_KEY = "teacheros"
$env:STORAGE_SECRET_KEY = "teacheros123"
$env:STORAGE_BUCKET = "teacheros-study-material"

Write-Host "Using Java:" -NoNewline
java -version

Write-Host "`nStarting Postgres and MinIO containers (if not already running)..."
docker compose up postgres minio -d

Write-Host "`nStarting backend on http://localhost:8080 ...`n"
Set-Location backend
mvn spring-boot:run
