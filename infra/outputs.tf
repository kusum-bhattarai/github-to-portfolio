output "backend_url" {
  description = "Cloud Run service URL — copy this into terraform.tfvars as backend_url, then run: make deploy"
  value       = google_cloud_run_v2_service.backend.uri
}

output "image_url" {
  description = "Full Artifact Registry image path (used by: make push)"
  value       = "${var.region}-docker.pkg.dev/${var.gcp_project_id}/portfolio-backend/backend:latest"
}
