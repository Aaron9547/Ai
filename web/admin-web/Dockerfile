# 构建上下文须为 web/ 目录：docker build -f admin-web/Dockerfile .
FROM docker.m.daocloud.io/library/node:22-alpine AS build
WORKDIR /build
COPY vite-allowed-hosts.ts vite-allowed-hosts.ts
WORKDIR /build/admin-web
COPY admin-web/package.json admin-web/package-lock.json ./
RUN npm ci
COPY admin-web/ .
ENV VITE_API_BASE=
RUN npm run build

FROM docker.m.daocloud.io/library/nginx:1.27-alpine
COPY admin-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/admin-web/dist /usr/share/nginx/html
EXPOSE 80
