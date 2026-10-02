#!/bin/bash
set -e

REGISTRY="localhost:30500"
IMAGE="code-review-app"
TAG="latest"
FULL_IMAGE="${REGISTRY}/${IMAGE}:${TAG}"

echo "=== Multi-Agent GitHub Code Review — k3d Deploy ==="
echo ""

# ── 1. Fill secrets from .env ────────────────────────────────────────────────
echo ">>> Loading secrets from .env..."
if [ ! -f ../.env ]; then
  echo "ERROR: .env not found. Copy .env.example to .env and fill in values."
  exit 1
fi
source ../.env

# Patch secret file with real values
sed \
  -e "s|REPLACE_WITH_GITHUB_TOKEN|${GITHUB_TOKEN}|g" \
  -e "s|REPLACE_WITH_OPENAI_API_KEY|${OPENAI_API_KEY}|g" \
  01-secret.yaml | kubectl apply -f -
echo ">>> Secrets applied."

# ── 2. Build JAR ─────────────────────────────────────────────────────────────
echo ""
echo ">>> Building JAR..."
cd ..
./gradlew clean build -x test
cd k8s
echo ">>> JAR built."

# ── 3. Build Docker image ─────────────────────────────────────────────────────
echo ""
echo ">>> Building Docker image: ${FULL_IMAGE}..."
docker build -t "${FULL_IMAGE}" ..
echo ">>> Image built."

# ── 4. Push to local k3d registry ────────────────────────────────────────────
echo ""
echo ">>> Pushing to local registry at ${REGISTRY}..."
docker push "${FULL_IMAGE}"
echo ">>> Image pushed."

# ── 5. Apply manifests ────────────────────────────────────────────────────────
echo ""
echo ">>> Applying Kubernetes manifests..."
kubectl apply -f 00-namespace.yaml
kubectl apply -f 02-configmap.yaml
kubectl apply -f 03-postgres.yaml
kubectl apply -f 04-temporal.yaml
kubectl apply -f 05-app.yaml
kubectl apply -f 06-ingress.yaml
echo ">>> Manifests applied."

# ── 6. Wait for rollout ───────────────────────────────────────────────────────
echo ""
echo ">>> Waiting for postgres..."
kubectl rollout status deployment/postgres -n code-review --timeout=120s

echo ">>> Waiting for temporal..."
kubectl rollout status deployment/temporal -n code-review --timeout=180s

echo ">>> Waiting for app..."
kubectl rollout status deployment/code-review-app -n code-review --timeout=180s

# ── 7. Summary ────────────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  ✅  Multi-Agent Code Review deployed to k3d                ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  App API    : http://code-review.localtest.me               ║"
echo "║  GraphiQL   : http://code-review.localtest.me/graphiql      ║"
echo "║  Temporal UI: http://temporal-ui.code-review.localtest.me   ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "Trigger a review:"
echo "  curl -X POST http://code-review.localtest.me/api/review/trigger \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"repoUrl\":\"https://github.com/Skpandey15/auth-service-jwt.git\",\"branch\":\"main\"}'"
