PROJECT_ID  ?= $(shell grep '^gcp_project_id' infra/terraform.tfvars 2>/dev/null | sed 's/.*= *"\(.*\)"/\1/')
REGION      ?= us-central1
IMAGE_URL    = $(REGION)-docker.pkg.dev/$(PROJECT_ID)/portfolio-backend/backend:latest

# ── One-time setup ────────────────────────────────────────────────────────────

.PHONY: setup
setup:
	@echo "==> Authenticating with GCP..."
	gcloud auth login
	gcloud auth application-default login
	gcloud config set project $(PROJECT_ID)
	@echo "==> Configuring Docker for Artifact Registry..."
	gcloud auth configure-docker $(REGION)-docker.pkg.dev
	@echo "==> Initialising Terraform..."
	cd infra && terraform init

# ── Infrastructure ────────────────────────────────────────────────────────────

# Step 1: create only the Artifact Registry (so we can push the image)
.PHONY: registry
registry:
	cd infra && terraform apply -target=google_artifact_registry_repository.backend -auto-approve

# Step 2: build and push the backend Docker image
.PHONY: push
push:
	@if [ -z "$(PROJECT_ID)" ]; then echo "ERROR: set gcp_project_id in infra/terraform.tfvars"; exit 1; fi
	docker build --platform linux/amd64 -t $(IMAGE_URL) ./backend
	docker push $(IMAGE_URL)

# Step 3: deploy Cloud Run (run again after filling in backend_url in terraform.tfvars)
.PHONY: deploy
deploy:
	cd infra && terraform apply -auto-approve

# Show the Cloud Run URL (copy into terraform.tfvars as backend_url, then re-run make deploy)
.PHONY: url
url:
	@cd infra && terraform output backend_url

# ── Convenience ───────────────────────────────────────────────────────────────

# Full first-time deploy in order (run 'make url' + update tfvars + 'make deploy' after)
.PHONY: first-deploy
first-deploy: registry push deploy

.PHONY: plan
plan:
	cd infra && terraform plan

.PHONY: destroy
destroy:
	cd infra && terraform destroy
