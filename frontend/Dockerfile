# Step 40/41 - the web tier: Nginx serving the landing site at /, the React app at /app/,
# and reverse-proxying /api/ to the API gateway (same origin: no CORS needed in production).

FROM node:22-alpine AS build
WORKDIR /src
COPY landing/tailwind/package*.json landing/tailwind/
RUN cd landing/tailwind && npm ci --no-audit --no-fund
COPY web/package*.json web/
RUN cd web && npm ci --no-audit --no-fund
COPY . .
RUN cd landing/tailwind && npm run build \
 && cd ../../web && npm run build

FROM nginx:1.27-alpine
COPY --from=build /src/landing /usr/share/nginx/html/landing
COPY --from=build /src/web/dist /usr/share/nginx/html/app
RUN rm -rf /usr/share/nginx/html/landing/tailwind/node_modules /usr/share/nginx/html/landing/tailwind/src
HEALTHCHECK --interval=15s --timeout=3s CMD wget -qO- http://127.0.0.1/healthz || exit 1
