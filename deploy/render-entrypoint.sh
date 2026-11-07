#!/bin/sh
# Starts the Spring Boot API and nginx in one container (Render free tier demo).
# If either process dies, the container exits so Render restarts it.
set -e
export SERVER_PORT=8080
envsubst '${PORT} ${API_RATE_LIMIT}' < /etc/nginx/claimflow.conf.template > /etc/nginx/http.d/default.conf

java $JAVA_OPTS -jar /app/app.jar &
JAVA_PID=$!
nginx -g 'daemon off;' &
NGINX_PID=$!

trap 'kill -TERM $JAVA_PID $NGINX_PID 2>/dev/null' TERM INT
while kill -0 $JAVA_PID 2>/dev/null && kill -0 $NGINX_PID 2>/dev/null; do
  sleep 5
done
echo "A process exited - stopping container"
kill -TERM $JAVA_PID $NGINX_PID 2>/dev/null || true
exit 1
