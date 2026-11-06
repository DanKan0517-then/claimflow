# ---- build the React app ----
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---- nginx: gateway + static files ----
FROM nginx:1.27-alpine
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx/snippets/ /etc/nginx/snippets/
COPY nginx/templates/ /etc/nginx/templates/
COPY --from=web /web/dist /usr/share/nginx/html
ENV SERVER_NAME=localhost \
    API_RATE_LIMIT=30
HEALTHCHECK --interval=10s --timeout=3s CMD wget -qO- http://127.0.0.1/nginx-health || exit 1
