variable "gcp_project_id" {
  description = "GCP project ID (find it in the GCP console or: gcloud projects list)"
  type        = string
}

variable "region" {
  description = "GCP region — us-central1 is in the free tier"
  type        = string
  default     = "us-central1"
}

# ── Neon Postgres ─────────────────────────────────────────────────────────────

variable "database_url" {
  description = "Neon JDBC URL, e.g. jdbc:postgresql://ep-xxx.us-east-2.aws.neon.tech/neondb?sslmode=require"
  type        = string
  sensitive   = true
}

variable "db_username" {
  description = "Neon database username"
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "Neon database password"
  type        = string
  sensitive   = true
}

# ── Upstash Redis ─────────────────────────────────────────────────────────────

variable "redis_host" {
  description = "Upstash Redis host, e.g. your-db.upstash.io"
  type        = string
  sensitive   = true
}

variable "redis_port" {
  description = "Upstash Redis port (usually 6379)"
  type        = string
  default     = "6379"
}

variable "redis_password" {
  description = "Upstash Redis password"
  type        = string
  sensitive   = true
}

# ── Upstash Kafka ─────────────────────────────────────────────────────────────

variable "kafka_bootstrap_servers" {
  description = "Upstash Kafka bootstrap, e.g. your-cluster.upstash.io:9092"
  type        = string
  sensitive   = true
}

variable "kafka_username" {
  description = "Upstash Kafka username"
  type        = string
  sensitive   = true
}

variable "kafka_password" {
  description = "Upstash Kafka password"
  type        = string
  sensitive   = true
}

# ── GitHub OAuth ──────────────────────────────────────────────────────────────

variable "github_client_id" {
  description = "GitHub OAuth App client ID"
  type        = string
  sensitive   = true
}

variable "github_client_secret" {
  description = "GitHub OAuth App client secret"
  type        = string
  sensitive   = true
}

# ── App secrets ───────────────────────────────────────────────────────────────

variable "token_encryption_key" {
  description = "Base64-encoded 32-byte AES key. Generate: openssl rand -base64 32"
  type        = string
  sensitive   = true
}

variable "llm_api_key" {
  description = "OpenAI API key"
  type        = string
  sensitive   = true
}

# ── URLs ──────────────────────────────────────────────────────────────────────

variable "frontend_url" {
  description = "Vercel frontend URL, e.g. https://your-app.vercel.app"
  type        = string
}

variable "backend_url" {
  description = "Cloud Run backend URL. Leave empty on first deploy, fill in after (see Makefile: make url)"
  type        = string
  default     = ""
}
