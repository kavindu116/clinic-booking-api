#!/usr/bin/env bash
# =====================================================================
# VM eke duwana deploy script eka.
#
#   ./deploy.sh          -- pull, build, restart
#   ./deploy.sh logs     -- app logs
#   ./deploy.sh status   -- containers + health
#   ./deploy.sh backup   -- database dump ekak
# =====================================================================
set -euo pipefail

cd "$(dirname "$0")"
COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env"

if [[ ! -f .env ]]; then
    echo "ERROR: deploy/.env is missing. Copy .env.prod.example and fill it in."
    exit 1
fi

case "${1:-deploy}" in
    deploy)
        echo "==> Pulling latest code"
        git -C .. pull --ff-only

        echo "==> Building the application image (this takes a few minutes)"
        $COMPOSE build app

        echo "==> Starting the stack"
        $COMPOSE up -d

        echo "==> Waiting for the app to report ready"
        for i in $(seq 1 60); do
            if docker exec clinic-api wget -qO- http://localhost:8080/actuator/health/readiness 2>/dev/null | grep -q UP; then
                echo "    ready after ${i}0 seconds"
                break
            fi
            sleep 10
            [[ $i -eq 60 ]] && { echo "    still not ready -- check: ./deploy.sh logs"; exit 1; }
        done

        echo "==> Removing dangling images"
        docker image prune -f

        source .env
        echo
        echo "Deployed: https://${PUBLIC_HOST}/swagger-ui.html"
        ;;

    logs)
        $COMPOSE logs -f --tail=200 "${2:-app}"
        ;;

    status)
        $COMPOSE ps
        echo
        docker exec clinic-api wget -qO- http://localhost:8080/actuator/health 2>/dev/null || echo "app not responding"
        ;;

    backup)
        source .env
        mkdir -p backups
        FILE="backups/clinicdb-$(date +%Y%m%d-%H%M%S).sql.gz"
        docker exec clinic-postgres pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" | gzip > "$FILE"
        echo "Wrote $FILE ($(du -h "$FILE" | cut -f1))"
        # Anthima 7ak withrai thiyaganne
        ls -t backups/*.sql.gz 2>/dev/null | tail -n +8 | xargs -r rm --
        ;;

    down)
        $COMPOSE down
        ;;

    *)
        echo "Usage: ./deploy.sh [deploy|logs|status|backup|down]"
        exit 1
        ;;
esac
