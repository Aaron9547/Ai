# 构建上下文须为 web/ 目录：docker build -f user-web/Dockerfile .
FROM docker.m.daocloud.io/library/node:22-alpine AS build
WORKDIR /build
COPY vite-allowed-hosts.ts vite-allowed-hosts.ts
WORKDIR /build/user-web
COPY user-web/package.json user-web/package-lock.json ./
RUN npm ci
COPY user-web/ .
ENV VITE_API_BASE=
ENV VITE_TENANT_CODE=default
ENV VITE_TENANT_ID=1
RUN npm run build

FROM docker.m.daocloud.io/library/nginx:1.27-alpine
COPY user-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/user-web/dist /usr/share/nginx/html
EXPOSE 80
