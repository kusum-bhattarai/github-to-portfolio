terraform {
  required_version = ">= 1.5"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }
}

provider "google" {
  project = var.gcp_project_id
  region  = var.region
}

# ── Enable required GCP APIs ──────────────────────────────────────────────────

resource "google_project_service" "run" {
  service            = "run.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "artifact_registry" {
  service            = "artifactregistry.googleapis.com"
  disable_on_destroy = false
}

# ── Artifact Registry (Docker image storage) ──────────────────────────────────

resource "google_artifact_registry_repository" "backend" {
  location      = var.region
  repository_id = "portfolio-backend"
  format        = "DOCKER"

  depends_on = [google_project_service.artifact_registry]
}

# ── Cloud Run service ─────────────────────────────────────────────────────────

locals {
  image_url = "${var.region}-docker.pkg.dev/${var.gcp_project_id}/portfolio-backend/backend:latest"
}

resource "google_cloud_run_v2_service" "backend" {
  name     = "portfolio-backend"
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"

  template {
    scaling {
      min_instance_count = 0
      max_instance_count = 2
    }

    containers {
      image = local.image_url

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
      }

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "prod"
      }
      env {
        name  = "DATABASE_URL"
        value = var.database_url
      }
      env {
        name  = "DB_USERNAME"
        value = var.db_username
      }
      env {
        name  = "DB_PASSWORD"
        value = var.db_password
      }
      env {
        name  = "REDIS_HOST"
        value = var.redis_host
      }
      env {
        name  = "REDIS_PORT"
        value = var.redis_port
      }
      env {
        name  = "REDIS_PASSWORD"
        value = var.redis_password
      }
      env {
        name  = "KAFKA_BOOTSTRAP_SERVERS"
        value = var.kafka_bootstrap_servers
      }
      env {
        name  = "KAFKA_USERNAME"
        value = var.kafka_username
      }
      env {
        name  = "KAFKA_PASSWORD"
        value = var.kafka_password
      }
      env {
        name  = "GITHUB_CLIENT_ID"
        value = var.github_client_id
      }
      env {
        name  = "GITHUB_CLIENT_SECRET"
        value = var.github_client_secret
      }
      env {
        name  = "TOKEN_ENCRYPTION_KEY"
        value = var.token_encryption_key
      }
      env {
        name  = "LLM_API_KEY"
        value = var.llm_api_key
      }
      env {
        name  = "FRONTEND_URL"
        value = var.frontend_url
      }
      env {
        name  = "BACKEND_URL"
        value = var.backend_url
      }
    }
  }

  depends_on = [
    google_project_service.run,
    google_artifact_registry_repository.backend,
  ]
}

# ── Allow unauthenticated public access ───────────────────────────────────────

resource "google_cloud_run_v2_service_iam_member" "public" {
  location = google_cloud_run_v2_service.backend.location
  name     = google_cloud_run_v2_service.backend.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}
